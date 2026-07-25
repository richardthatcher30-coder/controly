plugins {
    alias(libs.plugins.homecontrol.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidLibrary {
        namespace = "com.homecontrol.core.companionprotocol"
        withHostTest {}
    }

    sourceSets.commonMain.dependencies {
        implementation(libs.kotlinx.serialization.json)
        api(libs.cryptography.core)
    }

    sourceSets.commonTest.dependencies {
        implementation(libs.kotlinx.coroutines.test)
    }

    sourceSets.androidMain.dependencies {
        implementation(libs.cryptography.provider.jdk)
    }

    sourceSets.iosMain.dependencies {
        implementation(libs.cryptography.provider.apple)
    }
}
