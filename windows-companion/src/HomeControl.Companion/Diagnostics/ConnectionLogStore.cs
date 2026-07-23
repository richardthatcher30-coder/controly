using System;
using System.Collections.Generic;
using System.Linq;

namespace HomeControl.Companion.Diagnostics;

/// <summary>
/// An in-memory, most-recent-first log of pairing/auth attempts, for the
/// "Connection attempts" window. Deliberately not persisted to disk — it's
/// a live diagnostic view, not an audit trail.
/// </summary>
internal sealed class ConnectionLogStore
{
    private const int MaxEntries = 200;

    private readonly object _lock = new();
    private readonly List<ConnectionLogEntry> _entries = [];

    public event EventHandler<ConnectionLogEntry>? EntryAdded;

    public event EventHandler? Cleared;

    public IReadOnlyList<ConnectionLogEntry> Snapshot()
    {
        lock (_lock)
        {
            return _entries.ToList();
        }
    }

    public void Add(ConnectionLogEntry entry)
    {
        lock (_lock)
        {
            _entries.Insert(0, entry);
            if (_entries.Count > MaxEntries)
            {
                _entries.RemoveAt(_entries.Count - 1);
            }
        }
        EntryAdded?.Invoke(this, entry);
    }

    public void Clear()
    {
        lock (_lock)
        {
            _entries.Clear();
        }
        Cleared?.Invoke(this, EventArgs.Empty);
    }
}
