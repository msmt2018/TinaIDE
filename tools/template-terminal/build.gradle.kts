plugins {
    id("com.android.application")
}

android {
    namespace = "com.tinaide.template.terminal"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tinaide.template.placeholder.padpadpadpadpad"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation(project(":tools:template-common"))
    implementation(project(":termux-terminal:terminal-view"))
    implementation(libs.kotlin.stdlib)
}
