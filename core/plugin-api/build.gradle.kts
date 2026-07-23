plugins {
    alias(libs.plugins.homecontrol.jvm.library)
}

dependencies {
    // `api`, not `implementation`: every module that depends on plugin-api
    // needs core:model's types visible too, since they appear directly in
    // IDevicePlugin's signatures.
    api(project(":core:model"))
}
