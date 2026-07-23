plugins {
    alias(libs.plugins.homecontrol.android.library)
    alias(libs.plugins.homecontrol.android.hilt)
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
}
