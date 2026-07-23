using System.Diagnostics;
using System.Text.Json;
using System.Threading.Tasks;

namespace HomeControl.Companion.Commands;

/// <summary>
/// Launches whatever the phone's custom button was configured with — an
/// app name known to Windows' "App Paths" registry (e.g. "chrome"), a full
/// executable path, or a URL. <c>UseShellExecute</c> is what makes all
/// three work through the same call, the same way typing them into Run
/// (Win+R) would.
/// </summary>
internal static class AppLauncher
{
    public static Task<JsonElement?> LaunchAsync(JsonElement? parameters)
    {
        var target = parameters?.GetProperty("target").GetString();
        if (!string.IsNullOrWhiteSpace(target))
        {
            Process.Start(new ProcessStartInfo(target) { UseShellExecute = true });
        }
        return Task.FromResult<JsonElement?>(null);
    }
}
