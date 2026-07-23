using System;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace HomeControl.Companion.Commands;

internal static class ClipboardAccess
{
    public static Task<JsonElement?> GetAsync()
    {
        string text = string.Empty;
        RunOnStaThread(() => text = Clipboard.ContainsText() ? Clipboard.GetText() : string.Empty);
        return Task.FromResult<JsonElement?>(JsonSerializer.SerializeToElement(new { text }));
    }

    public static Task<JsonElement?> SetAsync(JsonElement? parameters)
    {
        var text = parameters?.GetProperty("text").GetString() ?? string.Empty;
        RunOnStaThread(() => Clipboard.SetText(text));
        return Task.FromResult<JsonElement?>(null);
    }

    private static void RunOnStaThread(Action action)
    {
        // Windows clipboard access requires an STA thread. The WebSocket
        // handler runs on a thread-pool (MTA) thread, so hop to a throwaway
        // STA thread rather than requiring the caller to marshal to the UI.
        if (Thread.CurrentThread.GetApartmentState() == ApartmentState.STA)
        {
            action();
            return;
        }

        var thread = new Thread(() => action());
        thread.SetApartmentState(ApartmentState.STA);
        thread.Start();
        thread.Join();
    }
}
