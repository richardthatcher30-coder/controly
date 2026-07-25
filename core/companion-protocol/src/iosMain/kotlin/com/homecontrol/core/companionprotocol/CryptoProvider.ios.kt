package com.homecontrol.core.companionprotocol

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.providers.apple.Apple

internal actual val companionCryptographyProvider: CryptographyProvider = CryptographyProvider.Apple
