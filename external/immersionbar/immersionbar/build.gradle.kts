plugins {
    id("com.android.library")
}

android {
    namespace = "com.gyf.immersionbar"
    compileSdk = 36

    defaultConfig {
        minSdk = 19
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    compileOnly("androidx.appcompat:appcompat:1.7.1")
}
