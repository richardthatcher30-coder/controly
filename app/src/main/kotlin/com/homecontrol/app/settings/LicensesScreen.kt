package com.homecontrol.app.settings

import androidx.compose.runtime.Composable

private data class OssLibrary(val name: String, val license: String)

private val LIBRARIES = listOf(
    OssLibrary("AndroidX Jetpack Compose", "Apache License 2.0"),
    OssLibrary("AndroidX Navigation", "Apache License 2.0"),
    OssLibrary("AndroidX Room", "Apache License 2.0"),
    OssLibrary("AndroidX Media3 (ExoPlayer)", "Apache License 2.0"),
    OssLibrary("AndroidX Security Crypto", "Apache License 2.0"),
    OssLibrary("Dagger / Hilt", "Apache License 2.0"),
    OssLibrary("Kotlin & kotlinx.coroutines", "Apache License 2.0"),
    OssLibrary("kotlinx.serialization", "Apache License 2.0"),
    OssLibrary("Ktor", "Apache License 2.0"),
    OssLibrary("OkHttp", "Apache License 2.0"),
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
