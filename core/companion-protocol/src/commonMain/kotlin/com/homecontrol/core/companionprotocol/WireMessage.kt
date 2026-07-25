package com.homecontrol.core.companionprotocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Mirrors `HomeControl.Companion`'s `WireMessage` (the Windows Companion
 * server, `windows-companion/src/HomeControl.Companion/Protocol/WireMessage.cs`)
 * and the existing Android client's copy
 * (`plugins/plugin-windows/.../protocol/WireMessage.kt`) field-for-field —
 * this is the one schema all three implementations (Windows server, Android
 * `plugin-windows` client, this module's iOS/Android-Companion consumers)
 * must agree on byte-for-byte. [SerialName] values match the C# enum member
 * names verbatim (`JsonStringEnumConverter`'s default serialization).
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
