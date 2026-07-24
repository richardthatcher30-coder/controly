plugins {
    alias(libs.plugins.homecontrol.android.feature)
}

android {
    namespace = "com.homecontrol.feature.dashboard"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.androidx.compose.material.icons.extended)
}
