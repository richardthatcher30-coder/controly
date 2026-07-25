using System;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;

namespace HomeControl.Companion.Networking;

/// <summary>
/// Windows Firewall silently drops inbound UDP discovery broadcasts unless an
/// explicit allow rule exists for this exact exe path — with no prompt and no
/// visible error, the PC just never shows up in the phone's device list, and
/// there's nothing to point the user at to diagnose it. Registering a rule
/// needs admin rights; the app itself deliberately doesn't run elevated (see
/// the installer's PrivilegesRequired=lowest), so this launches a single
/// elevated `netsh` call just for that one action instead of elevating the
/// whole app.
/// </summary>
internal static class FirewallRuleManager
{
    private const string RuleName = "Controly Companion";

    private static readonly string AttemptedFlagPath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "HomeControl", "Companion", "firewall_setup_attempted.flag");

    public static bool IsRuleRegistered()
    {
        var exePath = Environment.ProcessPath;
        if (exePath is null) return false;

        var output = RunNetsh($"advfirewall firewall show rule name=\"{RuleName}\" verbose");
        return output is not null && output.Contains(exePath, StringComparison.OrdinalIgnoreCase);
    }

    /// <summary>Whether an automatic first-run attempt has already happened — so a user who cancels the UAC prompt isn't asked again on every launch. The manual tray menu toggle always works regardless of this.</summary>
    public static bool WasAutoAttempted() => File.Exists(AttemptedFlagPath);

    public static void MarkAutoAttempted()
    {
        Directory.CreateDirectory(Path.GetDirectoryName(AttemptedFlagPath)!);
        File.WriteAllText(AttemptedFlagPath, DateTime.UtcNow.ToString("O"));
    }

    /// <returns>true if the rule is registered afterward (already was, or the user approved elevation); false if the user declined the UAC prompt or it otherwise failed.</returns>
    public static bool TryRegister()
    {
        if (IsRuleRegistered()) return true;

        var exePath = Environment.ProcessPath;
        if (exePath is null) return false;

        var args = $"advfirewall firewall add rule name=\"{RuleName}\" dir=in action=allow " +
                   $"program=\"{exePath}\" protocol=UDP localport=58600 profile=private,public enable=yes";
        RunNetshElevated(args);
        return IsRuleRegistered();
    }

    public static void Unregister()
    {
        RunNetshElevated($"advfirewall firewall delete rule name=\"{RuleName}\"");
    }

    private static void RunNetshElevated(string arguments)
    {
        try
        {
            using var process = Process.Start(new ProcessStartInfo
            {
                FileName = "netsh",
                Arguments = arguments,
                UseShellExecute = true,
                Verb = "runas",
                WindowStyle = ProcessWindowStyle.Hidden,
            });
            process?.WaitForExit(10_000);
        }
        catch (Win32Exception)
        {
            // User declined the UAC prompt, or elevation is otherwise unavailable — not an error, just leaves the rule unregistered.
        }
    }

    private static string? RunNetsh(string arguments)
    {
        try
        {
            using var process = Process.Start(new ProcessStartInfo
            {
                FileName = "netsh",
                Arguments = arguments,
                UseShellExecute = false,
                RedirectStandardOutput = true,
                CreateNoWindow = true,
            });
            if (process is null) return null;
            var output = process.StandardOutput.ReadToEnd();
            process.WaitForExit(5_000);
            return output;
        }
        catch
        {
            return null;
        }
    }
}
