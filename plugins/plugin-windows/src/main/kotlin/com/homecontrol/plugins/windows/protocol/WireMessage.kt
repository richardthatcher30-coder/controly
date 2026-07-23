package com.homecontrol.plugins.windows.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Mirrors `HomeControl.Companion`'s `WireMessage` exactly — same field
 * names, same [SerialName] values for [WireMessageType] (the C# side uses
 * `JsonStringEnumConverter` with default member-name serialization, so the
 * strings here must match the C# enum member names verbatim).
 */
@Serializable
enum class WireMessageType {
    @SerialName("PairRequest") PAIR_REQUEST,
    @SerialName("PairChallenge") PAIR_CHALLENGE,
    @SerialName("PairResult") PAIR_RESULT,
    @SerialName("AuthRequest") AUTH_REQUEST,
    @SerialName("AuthChallenge") AUTH_CHALLENGE,
    @SerialName("AuthResult") AUTH_RESULT,
    @SerialName("Command") COMMAND,
    @SerialName("CommandResult") COMMAND_RESULT,
    @SerialName("Error") ERROR,
}

@Serializable
data class WireMessage(
    val type: WireMessageType,
    val clientPublicKey: String? = null,
    val deviceName: String? = null,
    val serverPublicKey: String? = null,
    val success: Boolean? = null,
    val nonce: String? = null,
    val proofResponse: String? = null,
    val action: String? = null,
    val params: JsonElement? = null,
    val data: JsonElement? = null,
    val message: String? = null,
)
