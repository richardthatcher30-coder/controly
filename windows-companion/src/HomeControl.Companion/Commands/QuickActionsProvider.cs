using System.IO;
using System.Linq;
using System.Text.Json;
using System.Threading.Tasks;
using HomeControl.Companion.QuickActions;

namespace HomeControl.Companion.Commands;

/// <summary>Serves the enabled subset of <see cref="QuickAction.All"/> to the phone, and executes one when triggered.</summary>
internal static class QuickActionsProvider
{
    public static Task<JsonElement?> ListAsync()
    {
        var enabled = new QuickActionsStore().EnabledActions();

        using var stream = new MemoryStream();
        using (var writer = new Utf8JsonWriter(stream))
        {
            writer.WriteStartArray();
            foreach (var action in enabled)
            {
                writer.WriteStartObject();
                writer.WriteString("appId", action.Id);
                writer.WriteString("displayName", action.Label);
                writer.WriteEndObject();
            }
            writer.WriteEndArray();
        }

        var element = JsonDocument.Parse(stream.ToArray()).RootElement;
        return Task.FromResult<JsonElement?>(element);
    }

    public static Task<JsonElement?> TriggerAsync(JsonElement? parameters)
    {
        var actionId = parameters?.GetProperty("actionId").GetString();
        var action = QuickAction.All.FirstOrDefault(a => a.Id == actionId);
        action?.Execute();
        return Task.FromResult<JsonElement?>(null);
    }
}
