using System;
using System.IO;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using HomeControl.Companion.Commands;
using HomeControl.Companion.Diagnostics;
using HomeControl.Companion.Pairing;
using HomeControl.Companion.Protocol;

namespace HomeControl.Companion.Networking;

/// <summary>
/// One WebSocket connection's worth of protocol state: unpaired connections
/// may only pair or authenticate; only an authenticated connection may send
/// commands. "Never allow anonymous control" is enforced right here.
/// </summary>
internal sealed class ClientSession
{
    private readonly WebSocket _socket;
    private readonly PairingManager _pairingManager;
    private readonly ConnectionLogStore _connectionLog;
    private readonly string _remoteAddress;
    private readonly CommandDispatcher _commandDispatcher = new();

    private TrustedClient? _authenticatedClient;

    public ClientSession(WebSocket socket, PairingManager pairingManager, ConnectionLogStore connectionLog, string remoteAddress)
    {
        _socket = socket;
        _pairingManager = pairingManager;
        _connectionLog = connectionLog;
        _remoteAddress = remoteAddress;
    }

    /// <summary>Raised whenever authentication state changes — the only thing the live "Connected" status in <see cref="InfoForm"/> cares about.</summary>
    public event EventHandler? StateChanged;

    public string? DeviceName => _authenticatedClient?.DeviceName;

    public string? AuthenticatedPublicKeyBase64 => _authenticatedClient?.PublicKeyBase64;

    public bool IsAuthenticated => _authenticatedClient is not null;

    /// <summary>Unpairing from the Info window should take effect immediately rather than leaving this session able to keep sending commands until it next disconnects on its own.</summary>
    public async Task DisconnectAsync()
    {
        if (_socket.State != WebSocketState.Open) return;
        try
        {
            await _socket.CloseAsync(WebSocketCloseStatus.NormalClosure, "Unpaired", CancellationToken.None);
        }
        catch (WebSocketException)
        {
            // Already closing/closed from the other side — nothing more to do.
        }
    }

    public async Task RunAsync(CancellationToken cancellationToken)
    {
        _connectionLog.Add(new ConnectionLogEntry(DateTimeOffset.Now, _remoteAddress, null, ConnectionEventType.Connected, "WebSocket connection opened"));

        while (_socket.State == WebSocketState.Open && !cancellationToken.IsCancellationRequested)
        {
            var message = await ReceiveMessageAsync(cancellationToken);
            if (message is null) break;
            await DispatchAsync(message, cancellationToken);
        }

        _connectionLog.Add(new ConnectionLogEntry(DateTimeOffset.Now, _remoteAddress, _authenticatedClient?.DeviceName, ConnectionEventType.Disconnected, "Connection closed"));
    }

    private async Task<WireMessage?> ReceiveMessageAsync(CancellationToken cancellationToken)
    {
        using var messageStream = new MemoryStream();
        var buffer = new byte[16 * 1024];
        WebSocketReceiveResult result;
        do
        {
            result = await _socket.ReceiveAsync(buffer, cancellationToken);
            if (result.MessageType == WebSocketMessageType.Close)
            {
                await _socket.CloseAsync(WebSocketCloseStatus.NormalClosure, null, cancellationToken);
                return null;
            }
            messageStream.Write(buffer, 0, result.Count);
        } while (!result.EndOfMessage);

        try
        {
            return JsonSerializer.Deserialize<WireMessage>(messageStream.ToArray(), WireMessage.JsonOptions);
        }
        catch (JsonException)
        {
            return null;
        }
    }

    private async Task DispatchAsync(WireMessage message, CancellationToken cancellationToken)
    {
        switch (message.Type)
        {
            case WireMessageType.PairRequest:
                await HandlePairRequestAsync(message, cancellationToken);
                break;
            case WireMessageType.AuthRequest:
                await HandleAuthRequestAsync(message, cancellationToken);
                break;
            case WireMessageType.Command:
                await HandleCommandAsync(message, cancellationToken);
                break;
            default:
                await SendAsync(WireMessage.Error("Unexpected message type"), cancellationToken);
                break;
        }
    }

    private async Task HandlePairRequestAsync(WireMessage message, CancellationToken cancellationToken)
    {
        if (message.ClientPublicKey is null || message.DeviceName is null)
        {
            await SendAsync(WireMessage.Error("Missing pairing fields"), cancellationToken);
            return;
        }

        _connectionLog.Add(new ConnectionLogEntry(DateTimeOffset.Now, _remoteAddress, message.DeviceName, ConnectionEventType.PairRequestReceived, "Pairing requested"));

        var outcome = _pairingManager.BeginPairing(message.ClientPublicKey, message.DeviceName);
        await SendAsync(WireMessage.PairChallenge(_pairingManager.ServerPublicKeyBase64), cancellationToken);

        var approved = await outcome.ApprovalTask;
        if (approved)
        {
            _authenticatedClient = outcome.TrustedClient;
            StateChanged?.Invoke(this, EventArgs.Empty);
            _connectionLog.Add(new ConnectionLogEntry(DateTimeOffset.Now, _remoteAddress, message.DeviceName, ConnectionEventType.PairApproved, "Pairing approved by user"));
        }
        else
        {
            _connectionLog.Add(new ConnectionLogEntry(DateTimeOffset.Now, _remoteAddress, message.DeviceName, ConnectionEventType.PairDenied, "Pairing denied by user"));
        }
        await SendAsync(WireMessage.PairResult(approved), cancellationToken);
    }

    private async Task HandleAuthRequestAsync(WireMessage message, CancellationToken cancellationToken)
    {
        var client = message.ClientPublicKey is not null
            ? _pairingManager.FindTrustedClient(message.ClientPublicKey)
            : null;

        if (client is null)
        {
            _connectionLog.Add(new ConnectionLogEntry(DateTimeOffset.Now, _remoteAddress, null, ConnectionEventType.AuthFailed, "Unknown public key"));
            await SendAsync(WireMessage.AuthResult(false), cancellationToken);
            return;
        }

        var nonce = PairingManager.GenerateNonce();
        await SendAsync(WireMessage.AuthChallenge(Convert.ToBase64String(nonce)), cancellationToken);

        var response = await ReceiveMessageAsync(cancellationToken);
        var proof = response?.ProofResponse is { } proofBase64 ? Convert.FromBase64String(proofBase64) : null;

        if (proof is not null && _pairingManager.VerifyProof(client.PublicKeyBase64, nonce, proof))
        {
            _authenticatedClient = client;
            StateChanged?.Invoke(this, EventArgs.Empty);
            _connectionLog.Add(new ConnectionLogEntry(DateTimeOffset.Now, _remoteAddress, client.DeviceName, ConnectionEventType.AuthSucceeded, "Reconnected"));
            await SendAsync(WireMessage.AuthResult(true), cancellationToken);
        }
        else
        {
            _connectionLog.Add(new ConnectionLogEntry(DateTimeOffset.Now, _remoteAddress, client.DeviceName, ConnectionEventType.AuthFailed, "Proof-of-possession failed"));
            await SendAsync(WireMessage.AuthResult(false), cancellationToken);
        }
    }

    private async Task HandleCommandAsync(WireMessage message, CancellationToken cancellationToken)
    {
        if (_authenticatedClient is null)
        {
            await SendAsync(WireMessage.Error("Not authenticated"), cancellationToken);
            return;
        }

        var resultData = await _commandDispatcher.DispatchAsync(message);
        await SendAsync(WireMessage.CommandResult(true, resultData), cancellationToken);
    }

    private Task SendAsync(WireMessage message, CancellationToken cancellationToken)
    {
        var bytes = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(message, WireMessage.JsonOptions));
        return _socket.SendAsync(bytes, WebSocketMessageType.Text, endOfMessage: true, cancellationToken);
    }
}
