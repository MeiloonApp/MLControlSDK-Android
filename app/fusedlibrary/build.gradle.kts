plugins {
    alias(libs.plugins.android.newFusedlibrary)
}

androidFusedLibrary {
    namespace = "com.example.fusedLibrary"
    minSdk {
        version = release(25)
    }
}

dependencies {
    include(project(":ControlCoreModule"))

    include(files("../ControlCoreModule/libs/BmpConvert_V1.6.0_10604-release.aar"))
    include(files("../ControlCoreModule/libs/GifConvert_V1.3.0_42-release.aar"))
    include(files("../ControlCoreModule/libs/jl_audio_V1.3.0_10301-release.aar"))
    include(files("../ControlCoreModule/libs/jl_bluetooth_rcsp_V4.1.0_40116-release.aar"))
    include(files("../ControlCoreModule/libs/jl_bt_ota_V1.10.0_10932-release.aar"))
    include(files("../ControlCoreModule/libs/jl_eq_V1.1.0_10101-release.aar"))
    include(files("../ControlCoreModule/libs/jldecryption_v0.4-release.aar"))

}