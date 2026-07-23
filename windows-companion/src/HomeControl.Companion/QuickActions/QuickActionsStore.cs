using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;

namespace HomeControl.Companion.QuickActions;

/// <summary>Which of the fixed <see cref="QuickAction.All"/> the user has enabled — everything defaults to on the first time this runs (no file yet), matching the "all enabled by default" behavior asked for.</summary>
internal sealed class QuickActionsStore
{
    private static readonly string FilePath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "HomeControl",
        "quick_actions.json");

    private readonly object _lock = new();

    public bool IsEnabled(string actionId) => LoadAll().GetValueOrDefault(actionId, true);

    public void SetEnabled(string actionId, bool enabled)
    {
        lock (_lock)
        {
            var all = LoadAll();
            all[actionId] = enabled;
            Directory.CreateDirectory(Path.GetDirectoryName(FilePath)!);
            File.WriteAllText(FilePath, JsonSerializer.Serialize(all));
        }
    }

    public List<QuickAction> EnabledActions() =>
        QuickAction.All.Where(action => IsEnabled(action.Id)).ToList();

    private Dictionary<string, bool> LoadAll()
    {
        lock (_lock)
        {
            if (!File.Exists(FilePath)) return [];
            return TryDeserialize();
        }
    }

    private static Dictionary<string, bool> TryDeserialize()
    {
        try
        {
            return JsonSerializer.Deserialize<Dictionary<string, bool>>(File.ReadAllText(FilePath)) ?? [];
        }
        catch (Exception)
        {
            return [];
        }
    }
}
