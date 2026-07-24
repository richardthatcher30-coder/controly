plugins {
    alias(libs.plugins.homecontrol.android.feature)
}

android {
    namespace = "com.homecontrol.feature.devices"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:discovery"))
    implementation(project(":core:data"))
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
}
