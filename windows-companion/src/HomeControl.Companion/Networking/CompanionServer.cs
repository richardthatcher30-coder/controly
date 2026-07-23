using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using HomeControl.Companion.Certificates;
using HomeControl.Companion.Diagnostics;
using HomeControl.Companion.Discovery;
using HomeControl.Companion.Pairing;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Logging;

namespace HomeControl.Companion.Networking;

/// <summary>
/// Hosts the WebSocket endpoint (wss://, self-signed cert) and the UDP
/// discovery responder. One <see cref="ClientSession"/> per connection.
/// </summary>
internal sealed class CompanionServer
{
    public const int Port = 7591;

    private readonly PairingManager _pairingManager;
    private readonly DiscoveryResponder _discoveryResponder = new();
    private readonly ConcurrentDictionary<ClientSession, byte> _activeSessions = new();
    private WebApplication? _app;

    public CompanionServer(PairingManager pairingManager, ConnectionLogStore connectionLog)
    {
        _pairingManager = pairingManager;
        ConnectionLog = connectionLog;
    }

    public ConnectionLogStore ConnectionLog { get; }

    /// <summary>Raised whenever a session connects, authenticates, or disconnects — the live "Connected" status in <see cref="InfoForm"/> is driven entirely by this.</summary>
    public event EventHandler? ConnectionsChanged;

    /// <summary>Device names of every currently authenticated session (usually zero or one, but nothing stops two phones pairing with the same PC).</summary>
    public IReadOnlyList<string> ConnectedDeviceNames =>
        _activeSessions.Keys.Where(session => session.IsAuthenticated).Select(session => session.DeviceName!).ToList();

    public void Start()
    {
        var certificate = SelfSignedCertificateProvider.GetOrCreate();

        var builder = WebApplication.CreateBuilder();
        builder.Logging.ClearProviders();
        builder.WebHost.ConfigureKestrel(options =>
        {
            options.Listen(IPAddress.Any, Port, listenOptions => listenOptions.UseHttps(certificate));
        });

        _app = builder.Build();
        _app.UseWebSockets();

        _app.Map("/companion", async context =>
        {
            if (!context.WebSockets.IsWebSocketRequest)
            {
                context.Response.StatusCode = StatusCodes.Status400BadRequest;
                return;
            }

            var remoteAddress = context.Connection.RemoteIpAddress?.ToString() ?? "unknown";
            using var socket = await context.WebSockets.AcceptWebSocketAsync();
            var session = new ClientSession(socket, _pairingManager, ConnectionLog, remoteAddress);

            _activeSessions[session] = 0;
            session.StateChanged += OnSessionStateChanged;
            ConnectionsChanged?.Invoke(this, EventArgs.Empty);
            try
            {
                await session.RunAsync(context.RequestAborted);
            }
            finally
            {
                session.StateChanged -= OnSessionStateChanged;
                _activeSessions.TryRemove(session, out _);
                ConnectionsChanged?.Invoke(this, EventArgs.Empty);
            }
        });

        _ = _app.StartAsync();
        _discoveryResponder.Start();
    }

    public void Stop()
    {
        _discoveryResponder.Dispose();
        _app?.StopAsync().GetAwaiter().GetResult();
    }

    private void OnSessionStateChanged(object? sender, EventArgs e) => ConnectionsChanged?.Invoke(this, EventArgs.Empty);
}
