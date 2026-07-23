namespace HomeControl.Companion.AppButtons;

/// <summary>One user-configured launch button — the phone only ever displays and launches these, never defines them.</summary>
internal sealed class AppButtonEntry
{
    public string Id { get; set; } = string.Empty;
    public string Label { get; set; } = string.Empty;
    public string ExecutablePath { get; set; } = string.Empty;
}
