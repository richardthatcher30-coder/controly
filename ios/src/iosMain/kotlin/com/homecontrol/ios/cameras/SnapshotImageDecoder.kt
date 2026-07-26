package com.homecontrol.ios.cameras

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

/**
 * Raw JPEG bytes -> a Compose [ImageBitmap], via Skia directly. Compose
 * Multiplatform has no `BitmapFactory`-equivalent bundled as a simple public
 * API on non-Android targets, but does ship Skia (Skiko) as part of the
 * `compose.ui` artifact itself for exactly this reason -- no new Gradle
 * dependency needed beyond what `ios/build.gradle.kts` already declares.
 * Isolated in its own file (rather than inlined into the grid screen)
 * specifically so this one narrow decode call is easy to find and fix in
 * isolation if this Compose Multiplatform version's exact Skia API surface
 * turns out to differ.
 */
fun decodeJpegToImageBitmap(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
