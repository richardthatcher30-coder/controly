using System;

namespace HomeControl.Companion.Diagnostics;

internal enum ConnectionEventType
{
    Connected,
    PairRequestReceived,
    PairApproved,
    PairDenied,
    PairTimedOut,
    AuthSucceeded,
    AuthFailed,
    Disconnected,
}

internal sealed record ConnectionLogEntry(
    DateTimeOffset Timestamp,
    string RemoteAddress,
    string? DeviceName,
    ConnectionEventType EventType,
    string Detail);
