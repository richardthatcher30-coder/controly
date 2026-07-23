using System;
using System.IO;
using System.Text.Json;
using System.Threading.Tasks;
using HomeControl.Companion.AppButtons;

namespace HomeControl.Companion.Commands;

/// <summary>Serves the user-curated app button list (see <see cref="AppButtonsStore"/>) to the phone — same JSON shape the old auto-scanned list used, so the phone side didn't need to change field names.</summary>
internal static class AppButtonsProvider
{
    /// <summary>The phone can add a URL button too — typed there, but still stored and owned by <see cref="AppButtonsStore"/> on the PC, same as one added from the PC's own "Manage app buttons" window.</summary>
    public static Task<JsonElement?> AddUrlAsync(JsonElement? parameters)
    {
        var url = parameters?.GetProperty("url").GetString();
        if (string.IsNullOrWhiteSpace(url)) return Task.FromResult<JsonElement?>(null);
        if (!url.Contains("://")) url = "https://" + url;

        var label = parameters?.TryGetProperty("label", out var labelProp) == true ? labelProp.GetString() : null;
        if (string.IsNullOrWhiteSpace(label)) label = url;

        new AppButtonsStore().Add(label, url);
        return Task.FromResult<JsonElement?>(null);
    }

    public static Task<JsonElement?> ListAsync()
    {
        var entries = new AppButtonsStore().List();

        using var stream = new MemoryStream();
        using (var writer = new Utf8JsonWriter(stream))
        {
            writer.WriteStartArray();
            foreach (var entry in entries)
            {
                writer.WriteStartObject();
                writer.WriteString("appId", entry.ExecutablePath);
                writer.WriteString("displayName", entry.Label);
                var iconBase64 = IconEncoder.ExtractBase64(entry.ExecutablePath);
                if (iconBase64 is not null)
                {
                    writer.WriteString("iconBase64", iconBase64);
                }
                writer.WriteEndObject();
            }
            writer.WriteEndArray();
        }

        var element = JsonDocument.Parse(stream.ToArray()).RootElement;
        return Task.FromResult<JsonElement?>(element);
    }
}
