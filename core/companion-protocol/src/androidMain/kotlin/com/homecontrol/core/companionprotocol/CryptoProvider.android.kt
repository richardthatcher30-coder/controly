package com.homecontrol.core.companionprotocol

import dev.whyoleg.cryptography.CryptographyProvider

// Confirmed working (including ECDH) via the JDK provider through `.Default`
// already -- testAndroidHostTest passes 3/3 against this exact resolution.
internal actual val companionCryptographyProvider: CryptographyProvider = CryptographyProvider.Default
