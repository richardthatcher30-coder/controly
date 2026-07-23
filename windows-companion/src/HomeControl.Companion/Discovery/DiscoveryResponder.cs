using System;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using HomeControl.Companion;

namespace HomeControl.Companion.Discovery;

/// <summary>
/// Answers the same UDP broadcast beacon `core:discovery` sends from the
/// Android app (port 58600, "HOMECONTROL_DISCOVER" / "HOMECONTROL_HERE") —
/// so this PC shows up in the app's device list without needing mDNS. The
/// response includes this PC's MAC address so the phone can Wake-on-LAN it
/// later, once it's paired, without needing another discovery round.
/// </summary>
internal sealed class DiscoveryResponder : IDisposable
{
    private const int Port = 58600;
    private const string DiscoverMessage = "HOMECONTROL_DISCOVER";
    private const string ResponsePrefix = "HOMECONTROL_HERE";

    private readonly UdpClient _client = new(Port);
    private readonly CancellationTokenSource _cts = new();

    public void Start()
    {
        _ = ListenAsync(_cts.Token);
    }

    private async Task ListenAsync(CancellationToken cancellationToken)
    {
        while (!cancellationToken.IsCancellationRequested)
        {
            try
            {
                var result = await _client.ReceiveAsync(cancellationToken);
                var text = Encoding.UTF8.GetString(result.Buffer);
                if (text.StartsWith(DiscoverMessage, StringComparison.Ordinal))
                {
                    // "HomeControl (HOSTNAME)" is the display name shown in the
                    // app's device list — deliberately unmistakable among SSDP
                    // noise from routers, printers, and Windows' own UPnP host.
                    var response = Encoding.UTF8.GetBytes(
                        $"{ResponsePrefix} HomeControl ({NetworkInfo.HostName}) {NetworkInfo.GetPrimaryMacAddress()}");
                    await _client.SendAsync(response, result.RemoteEndPoint, cancellationToken);
                }
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (SocketException)
            {
                // Transient network hiccup — keep listening.
            }
        }
    }

    public void Dispose()
    {
        _cts.Cancel();
        _client.Dispose();
        _cts.Dispose();
    }
}
