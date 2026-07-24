plugins {
    alias(libs.plugins.homecontrol.android.library)
}

android {
    namespace = "com.homecontrol.core.data"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:plugin-api"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.core)
}
