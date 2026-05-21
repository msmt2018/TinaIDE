plugins {
    id("com.android.library")
}

android {
    namespace = "com.hjq.permissions"
    compileSdk = 36

    defaultConfig {
        minSdk = 17
        consumerProguardFiles("proguard-permissions.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    compileOnly("androidx.fragment:fragment:1.8.8")
    // DeviceCompat - 本地模块（避免 JitPack 依赖）
    implementation(project(":devicecompat-local"))
}
