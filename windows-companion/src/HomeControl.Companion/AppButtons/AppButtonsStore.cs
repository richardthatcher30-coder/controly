using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;

namespace HomeControl.Companion.AppButtons;

/// <summary>
/// The user-curated list of app launch buttons, authored here on the PC
/// (where a real file picker can locate the exact .exe) and only ever read,
/// never edited, from the phone — see the "Apps" screen in the mobile app.
/// Persisted as plain JSON under the user's AppData; there's nothing here
/// sensitive enough to need the encrypted store the pairing keys use.
/// </summary>
internal sealed class AppButtonsStore
{
    private static readonly string FilePath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "HomeControl",
        "app_buttons.json");

    private readonly object _lock = new();

    public event EventHandler? Changed;

    public List<AppButtonEntry> List()
    {
        lock (_lock)
        {
            if (!File.Exists(FilePath)) return [];
            try
            {
                var json = File.ReadAllText(FilePath);
                return JsonSerializer.Deserialize<List<AppButtonEntry>>(json) ?? [];
            }
            catch (Exception)
            {
                return [];
            }
        }
    }

    public AppButtonEntry Add(string label, string executablePath)
    {
        var entry = new AppButtonEntry
        {
            Id = Guid.NewGuid().ToString("N"),
            Label = label,
            ExecutablePath = executablePath,
        };

        lock (_lock)
        {
            var entries = List();
            entries.Add(entry);
            Save(entries);
        }

        Changed?.Invoke(this, EventArgs.Empty);
        return entry;
    }

    public void Remove(string id)
    {
        lock (_lock)
        {
            var entries = List().Where(e => e.Id != id).ToList();
            Save(entries);
        }

        Changed?.Invoke(this, EventArgs.Empty);
    }

    private static void Save(List<AppButtonEntry> entries)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(FilePath)!);
        File.WriteAllText(FilePath, JsonSerializer.Serialize(entries));
    }
}
