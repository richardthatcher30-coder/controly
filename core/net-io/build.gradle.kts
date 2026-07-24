plugins {
    alias(libs.plugins.homecontrol.kmp.library)
}

kotlin {
    androidLibrary {
        namespace = "com.homecontrol.core.netio"
    }
}
