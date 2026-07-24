plugins {
    alias(libs.plugins.homecontrol.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.homecontrol.plugins.windows"
}

dependencies {
    api(project(":core:plugin-api"))
    implementation(project(":core:security"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.websockets)
    implementation(libs.koin.android)
}
