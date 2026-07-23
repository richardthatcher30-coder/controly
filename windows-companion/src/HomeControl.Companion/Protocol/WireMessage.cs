using System.Text.Json;
using System.Text.Json.Serialization;

namespace HomeControl.Companion.Protocol;

[JsonConverter(typeof(JsonStringEnumConverter))]
internal enum WireMessageType
{
    PairRequest,
    PairChallenge,
    PairResult,
    AuthRequest,
    AuthChallenge,
    AuthResult,
    Command,
    CommandResult,
    Error,
}

/// <summary>
/// One JSON envelope for every message in both directions — most fields are
/// only meaningful for a subset of <see cref="Type"/> values, kept as one
/// flat shape for a simple wire format rather than a tagged-union hierarchy.
/// </summary>
internal sealed class WireMessage
{
    /// <summary>
    /// The Kotlin side (kotlinx.serialization) uses camelCase property names
    /// by default; plain <c>System.Text.Json</c> defaults to the C# property
    /// names as declared (PascalCase). Every (de)serialization of a
    /// <see cref="WireMessage"/> must use this so the two sides actually
    /// agree on field names — without it, every field silently comes back
    /// null instead of throwing, which is much harder to notice.
    /// </summary>
    public static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    };

    public WireMessageType Type { get; set; }
    public string? ClientPublicKey { get; set; }
    public string? DeviceName { get; set; }
    public string? ServerPublicKey { get; set; }
    public bool? Success { get; set; }
    public string? Nonce { get; set; }
    public string? ProofResponse { get; set; }
    public string? Action { get; set; }
    public JsonElement? Params { get; set; }
    public JsonElement? Data { get; set; }
    public string? Message { get; set; }

    public static WireMessage PairChallenge(string serverPublicKey) => new()
    {
        Type = WireMessageType.PairChallenge,
        ServerPublicKey = serverPublicKey,
    };

    public static WireMessage PairResult(bool success) => new()
    {
        Type = WireMessageType.PairResult,
        Success = success,
    };

    public static WireMessage AuthChallenge(string nonce) => new()
    {
        Type = WireMessageType.AuthChallenge,
        Nonce = nonce,
    };

    public static WireMessage AuthResult(bool success) => new()
    {
        Type = WireMessageType.AuthResult,
        Success = success,
    };

    public static WireMessage CommandResult(bool success, JsonElement? data) => new()
    {
        Type = WireMessageType.CommandResult,
        Success = success,
        Data = data,
    };

    public static WireMessage Error(string message) => new()
    {
        Type = WireMessageType.Error,
        Message = message,
    };
}
