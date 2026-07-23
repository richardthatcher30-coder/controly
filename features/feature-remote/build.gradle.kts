plugins {
    alias(libs.plugins.homecontrol.android.feature)
    alias(libs.plugins.homecontrol.android.hilt)
}

android {
    namespace = "com.homecontrol.feature.remote"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
}
