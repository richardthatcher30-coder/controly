package com.homecontrol.ios.screens.settings

import androidx.compose.runtime.Composable

private data class OssLibrary(val name: String, val license: String)

// Reflects what this iOS build actually links, not a copy of Android's list
// (which includes AndroidX Room/Media3/Security-Crypto/OkHttp -- none of
// which are on the iOS dependency graph; see ios/build.gradle.kts).
private val LIBRARIES = listOf(
    OssLibrary("JetBrains Compose Multiplatform", "Apache License 2.0"),
    OssLibrary("Kotlin & kotlinx.coroutines", "Apache License 2.0"),
    OssLibrary("kotlinx.serialization", "Apache License 2.0"),
    OssLibrary("Ktor", "Apache License 2.0"),
    OssLibrary("cryptography-kotlin", "Apache License 2.0"),
)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    LegalTextScreen(title = "Licenses and notices", onBack = onBack) {
        Body("Controly is built with the following open-source software:")
        LIBRARIES.forEach { library ->
            Section(library.name)
            Body(library.license)
        }
        Spacer24()
    }
}
