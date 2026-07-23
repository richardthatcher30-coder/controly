using System.Diagnostics;
using System.Text.Json;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace HomeControl.Companion.Commands;

internal static class PowerActions
{
    public static Task<JsonElement?> ShutdownAsync() => RunShutdownCommand("/s /t 0");

    public static Task<JsonElement?> RestartAsync() => RunShutdownCommand("/r /t 0");

    public static Task<JsonElement?> SleepAsync()
    {
        Application.SetSuspendState(PowerState.Suspend, force: false, disableWakeEvent: false);
        return Task.FromResult<JsonElement?>(null);
    }

    private static Task<JsonElement?> RunShutdownCommand(string arguments)
    {
        Process.Start(new ProcessStartInfo("shutdown", arguments) { CreateNoWindow = true, UseShellExecute = false });
        return Task.FromResult<JsonElement?>(null);
    }
}
