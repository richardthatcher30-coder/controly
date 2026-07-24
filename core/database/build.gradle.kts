plugins {
    alias(libs.plugins.homecontrol.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.homecontrol.core.database"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.koin.android)
}
