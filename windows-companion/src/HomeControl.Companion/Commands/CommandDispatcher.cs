using System.Text.Json;
using System.Threading.Tasks;
using HomeControl.Companion.Protocol;

namespace HomeControl.Companion.Commands;

internal sealed class CommandDispatcher
{
    public Task<JsonElement?> DispatchAsync(WireMessage message) => message.Action switch
    {
        "key_event" => KeyboardInput.HandleKeyEventAsync(message.Params),
        "type_text" => KeyboardInput.HandleTypeTextAsync(message.Params),
        "mouse_move" => MouseInput.HandleMoveAsync(message.Params),
        "mouse_click" => MouseInput.HandleClickAsync(message.Params),
        "launch_app" => AppLauncher.LaunchAsync(message.Params),
        "list_apps" => AppButtonsProvider.ListAsync(),
        "add_url_button" => AppButtonsProvider.AddUrlAsync(message.Params),
        "list_quick_actions" => QuickActionsProvider.ListAsync(),
        "trigger_quick_action" => QuickActionsProvider.TriggerAsync(message.Params),
        "clipboard_get" => ClipboardAccess.GetAsync(),
        "clipboard_set" => ClipboardAccess.SetAsync(message.Params),
        "shutdown" => PowerActions.ShutdownAsync(),
        "restart" => PowerActions.RestartAsync(),
        "sleep" => PowerActions.SleepAsync(),
        _ => Task.FromResult<JsonElement?>(null),
    };
}
