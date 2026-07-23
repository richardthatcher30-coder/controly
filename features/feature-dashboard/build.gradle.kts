plugins {
    alias(libs.plugins.homecontrol.android.feature)
    alias(libs.plugins.homecontrol.android.hilt)
}

android {
    namespace = "com.homecontrol.feature.dashboard"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
}
