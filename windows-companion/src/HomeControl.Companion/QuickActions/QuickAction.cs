using System;
using System.Diagnostics;
using HomeControl.Companion.Commands;

namespace HomeControl.Companion.QuickActions;

/// <summary>One fixed system shortcut. The set is closed (not user-defined like app buttons) — only whether each one is enabled is configurable, via <see cref="QuickActionsStore"/>.</summary>
internal sealed class QuickAction
{
    public required string Id { get; init; }
    public required string Label { get; init; }
    public required Action Execute { get; init; }

    public static readonly QuickAction[] All =
    [
        new QuickAction
        {
            Id = "task_manager",
            // A real Ctrl+Alt+Del cannot be sent by any regular Windows
            // application — Windows blocks that combination at the OS
            // level for every process, precisely so it can't be spoofed by
            // malware (it's the "Secure Attention Sequence"). Opening Task
            // Manager directly is the closest real equivalent and what
            // most people actually want from Ctrl+Alt+Del anyway.
            Label = "Task Manager",
            Execute = () => Process.Start(new ProcessStartInfo("taskmgr.exe") { UseShellExecute = true }),
        },
        new QuickAction
        {
            Id = "windows_key",
            Label = "Windows Key",
            Execute = () => KeyboardInput.SendVirtualKey(0x5B), // VK_LWIN
        },
        new QuickAction
        {
            Id = "f12",
            Label = "F12",
            Execute = () => KeyboardInput.SendVirtualKey(0x7B), // VK_F12
        },
        new QuickAction
        {
            Id = "escape",
            Label = "Esc",
            Execute = () => KeyboardInput.SendVirtualKey(0x1B), // VK_ESCAPE
        },
        new QuickAction
        {
            Id = "open_videos",
            Label = "Videos",
            Execute = () => OpenFolder(Environment.GetFolderPath(Environment.SpecialFolder.MyVideos)),
        },
        new QuickAction
        {
            Id = "open_pictures",
            Label = "Pictures",
            Execute = () => OpenFolder(Environment.GetFolderPath(Environment.SpecialFolder.MyPictures)),
        },
        new QuickAction
        {
            Id = "open_downloads",
            Label = "Downloads",
            // .NET has no SpecialFolder entry for Downloads — "shell:Downloads"
            // is the documented Explorer virtual-folder syntax and avoids
            // needing SHGetKnownFolderPath/COM interop for one folder.
            Execute = () => OpenFolder("shell:Downloads"),
        },
    ];

    private static void OpenFolder(string path) =>
        Process.Start(new ProcessStartInfo("explorer.exe", $"\"{path}\"") { UseShellExecute = true });
}
