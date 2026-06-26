plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.meiloon.mlcontrolcore_aos"
    compileSdk = 36

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    defaultConfig {
        applicationId = "com.meiloon.mlcontrolcore_aos"
        minSdk = 26
        targetSdk = 36
        versionCode = 57
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            buildConfigField("boolean", "SHOW_LOG", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            buildConfigField( "boolean", "SHOW_LOG", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))

    implementation(libs.androidx.cardview)
    implementation(libs.androidx.gridlayout)
    implementation(libs.androidx.graphics.path)

    implementation(libs.avloadingindicatorview)
    // animatedbottombar
    implementation("nl.joery.animatedbottombar:library:1.1.0")

    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.core.core.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment.ktx)

    implementation(libs.core.splashscreen)

    implementation(libs.androidx.lifecycle.process)
    //
    implementation(libs.bundles.update)
    // Room
    implementation(libs.bundles.room)
    annotationProcessor(libs.androidx.room.compiler)
    // autodispose
    implementation(libs.bundles.autodispose)
    //
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // Logger
    implementation(libs.logger)
    // calligraphy3
    implementation(libs.bundles.calligraphy3)
    // retrofit
    implementation(libs.bundles.retrofit)
    // eventbus
    implementation(libs.eventbus)
    // glide
    implementation(libs.bundles.glide)
    // rxjava
    implementation(libs.bundles.rxjava)
    implementation(libs.kotlinx.coroutines.rx3)
    // immersionbar
    implementation(libs.bundles.immersionbar)
    // basic
    implementation(libs.bundles.basic)
    // material3
    implementation(libs.androidx.compose.material3)
    // constraintlayout
    implementation(libs.constraintlayout)
    implementation(libs.androidx.constraintlayout)
    // navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    // mqtt
    implementation(libs.bundles.mqtt)
    // lifecycle
    implementation(libs.bundles.lifecycle)
    // RxAndroidBle
    implementation(libs.rxandroidble)
    // androidx
    implementation(libs.bundles.androidx)
    implementation(libs.permissionx)
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
