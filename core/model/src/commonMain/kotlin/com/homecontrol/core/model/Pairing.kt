package com.homecontrol.core.model

/**
 * How a plugin authenticates a new pairing. These genuinely differ per device
 * family — see docs/ARCHITECTURE.md §4.3 — so `feature-pairing` renders a
 * different flow per strategy rather than forcing every device through an
 * identical "enter 6 digits" UI that some devices don't actually support.
 */
enum class PairingStrategy {
    /** Phone shows a code-entry field; the code authenticates a key exchange. */
    CODE_VERIFICATION,

    /** The device itself shows an Allow/Deny prompt; the phone polls for the result. */
    ON_SCREEN_APPROVAL,

    /** Our own protocol: the companion app displays the code, the phone confirms it. */
    PRESHARED_FROM_COMPANION,
}

/** What the pairing UI collected from the user, matching [PairingStrategy]. */
sealed interface PairingInput {
    data class Code(val code: String) : PairingInput

    /** A credential configured directly on the device itself rather than exchanged during pairing — e.g. Sony Bravia's IP Control pre-shared key. */
    data class Psk(val key: String) : PairingInput
    data object None : PairingInput
}

sealed interface PairingResult {
    data class Success(val pairedDevice: PairedDevice) : PairingResult
    data class AwaitingApproval(val message: String) : PairingResult

    /** [detail], when present, is a user-facing explanation more specific than [reason] alone — e.g. naming the exact IP/port a plugin couldn't reach. */
    data class Failed(val reason: PairingFailureReason, val detail: String? = null) : PairingResult
}

enum class PairingFailureReason {
    INVALID_CODE,
    TIMEOUT,
    REJECTED_BY_DEVICE,
    NETWORK_ERROR,
    ALREADY_PAIRED,
}

/**
 * The single most common real-world failure this project has hit: a router
 * or firewall silently drops the connection to a specific port rather than
 * actively refusing it, which surfaces to the app as a plain connect
 * timeout with no further detail. Naming the exact IP and port here is what
 * turns that into something a user can actually act on, rather than a bare
 * "NETWORK_ERROR"/"TIMEOUT" they have no way to diagnose themselves.
 */
fun unreachablePortMessage(ipAddress: String, port: Int): String =
    "Can't reach $ipAddress on port $port. Check that your router/firewall isn't blocking this port for $ipAddress, then try again."
