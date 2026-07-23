plugins {
    alias(libs.plugins.homecontrol.android.feature)
    alias(libs.plugins.homecontrol.android.hilt)
}

android {
    namespace = "com.homecontrol.feature.devices"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:discovery"))
    implementation(project(":core:data"))
    implementation(libs.hilt.navigation.compose)
}
