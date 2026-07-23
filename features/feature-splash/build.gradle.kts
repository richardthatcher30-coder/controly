plugins {
    alias(libs.plugins.homecontrol.android.feature)
}

android {
    namespace = "com.homecontrol.feature.splash"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(libs.kotlinx.coroutines.android)
}
