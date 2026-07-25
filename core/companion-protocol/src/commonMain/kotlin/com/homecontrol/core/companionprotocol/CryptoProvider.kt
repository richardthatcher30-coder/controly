package com.homecontrol.core.companionprotocol

import dev.whyoleg.cryptography.CryptographyProvider

/**
 * `CryptographyProvider.Default` resolves to a provider that does NOT
 * implement ECDH on Apple targets -- confirmed on real hardware
 * (`CryptographyProvider.get(ECDH)` throws `IllegalStateException: Algorithm
 * not found`) even though `cryptography-provider-apple` 0.5.0's CryptoKit-
 * based `Apple` provider (`dev.whyoleg.cryptography.providers.apple.Apple`,
 * accessed via `CryptographyProvider.Apple`) does support it -- `.Default`
 * just doesn't resolve to it. Each platform picks its provider explicitly
 * instead of relying on `.Default`'s resolution.
 */
internal expect val companionCryptographyProvider: CryptographyProvider
