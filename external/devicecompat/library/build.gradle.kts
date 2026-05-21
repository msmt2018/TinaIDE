plugins {
    id("com.android.library")
}

android {
    namespace = "com.hjq.device.compat"
    compileSdk = 36

    defaultConfig {
        minSdk = 17
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        ignoreWarnings = true
    }
}

dependencies {
    compileOnly("androidx.annotation:annotation:1.9.1")
}
