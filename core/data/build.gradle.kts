plugins {
    alias(libs.plugins.homecontrol.android.library)
    alias(libs.plugins.homecontrol.android.hilt)
}

android {
    namespace = "com.homecontrol.core.data"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:plugin-api"))
    implementation(libs.kotlinx.coroutines.android)
}
