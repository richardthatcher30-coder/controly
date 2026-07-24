plugins {
    alias(libs.plugins.homecontrol.android.library)
}

android {
    namespace = "com.homecontrol.plugins.registry"
}

dependencies {
    api(project(":core:plugin-api"))
    implementation(project(":plugins:plugin-androidtv"))
    implementation(project(":plugins:plugin-windows"))
    implementation(project(":plugins:plugin-sonytv"))
    implementation(project(":plugins:plugin-samsungtv"))
    implementation(libs.koin.core)
}
