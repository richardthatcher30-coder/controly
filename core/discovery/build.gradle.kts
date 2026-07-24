plugins {
    alias(libs.plugins.homecontrol.android.library)
}

android {
    namespace = "com.homecontrol.core.discovery"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.android)
}
