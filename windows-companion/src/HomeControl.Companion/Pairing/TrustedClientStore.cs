using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;

namespace HomeControl.Companion.Pairing;

/// <summary>
/// Persists trusted client public keys as plain JSON — they're public keys
/// and device names, not secrets, so unlike the certificate or any session
/// material there's nothing here that needs encryption at rest.
/// </summary>
internal sealed class TrustedClientStore
{
    private static readonly string FilePath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "HomeControl", "Companion", "trusted_clients.json");

    private readonly ConcurrentDictionary<string, TrustedClient> _clients;

    public TrustedClientStore()
    {
        _clients = new ConcurrentDictionary<string, TrustedClient>(
            Load().ToDictionary(client => client.PublicKeyBase64));
    }

    public TrustedClient? Find(string publicKeyBase64) =>
        _clients.TryGetValue(publicKeyBase64, out var client) ? client : null;

    public IReadOnlyList<TrustedClient> GetAll() => _clients.Values.ToList();

    public void Add(TrustedClient client)
    {
        _clients[client.PublicKeyBase64] = client;
        Save();
    }

    private static List<TrustedClient> Load()
    {
        if (!File.Exists(FilePath)) return [];
        var json = File.ReadAllText(FilePath);
        return JsonSerializer.Deserialize<List<TrustedClient>>(json) ?? [];
    }

    private void Save()
    {
        Directory.CreateDirectory(Path.GetDirectoryName(FilePath)!);
        var json = JsonSerializer.Serialize(_clients.Values.ToList());
        File.WriteAllText(FilePath, json);
    }
}
