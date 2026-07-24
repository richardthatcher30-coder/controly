plugins {
    alias(libs.plugins.homecontrol.android.library)
}

android {
    namespace = "com.homecontrol.core.security"
}

dependencies {
    implementation(libs.koin.core)
}
