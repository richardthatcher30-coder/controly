using System;

namespace HomeControl.Companion.Pairing;

internal sealed class TrustedClient
{
    public required string PublicKeyBase64 { get; init; }
    public required string DeviceName { get; init; }
    public required DateTimeOffset PairedAt { get; init; }
}
