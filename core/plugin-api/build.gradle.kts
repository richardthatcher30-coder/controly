plugins {
    alias(libs.plugins.homecontrol.kmp.library)
}

kotlin {
    androidLibrary {
        namespace = "com.homecontrol.core.pluginapi"
    }

    sourceSets.commonMain.dependencies {
        // `api`, not `implementation`: every module that depends on plugin-api
        // needs core:model's types visible too, since they appear directly in
        // IDevicePlugin's signatures.
        api(project(":core:model"))
    }
}
