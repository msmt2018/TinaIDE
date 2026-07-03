plugins {
    id("com.android.library")
}

fun Project.parseBooleanGradleProperty(name: String, default: Boolean): Boolean {
    val raw = providers.gradleProperty(name).orNull?.trim() ?: return default
    return when {
        raw.equals("true", ignoreCase = true) || raw == "1" -> true
        raw.equals("false", ignoreCase = true) || raw == "0" -> false
        else -> throw GradleException("Invalid boolean gradle property '$name=$raw'. Expected true/false.")
    }
}

val requestedTaskNames = gradle.startParameter.taskNames
val devAbiMapping = mapOf("arm64" to "arm64-v8a", "x86_64" to "x86_64")
val localDevAbi = providers.gradleProperty("tina.devAbi").orNull?.trim().orEmpty().ifBlank { "arm64" }
require(localDevAbi in devAbiMapping) {
    "Unsupported -Ptina.devAbi=$localDevAbi. Expected one of ${devAbiMapping.keys}."
}
val buildAllAbiRequested =
    parseBooleanGradleProperty("tina.allAbi", default = false) ||
        (System.getenv("CI")?.equals("true", ignoreCase = true) == true) ||
        requestedTaskNames.any { it.contains("AllAbi", ignoreCase = true) }
val configuredNativeAbis =
    if (buildAllAbiRequested) {
        listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
    } else {
        listOf(devAbiMapping.getValue(localDevAbi))
    }

android {
    namespace = "com.termux.terminal"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        
        externalNativeBuild {
            ndkBuild {
                cFlags("-std=c11", "-Wall", "-Wextra", "-Werror", "-Os", "-fno-stack-protector", "-Wl,--gc-sections")
            }
        }

        ndk {
            abiFilters += configuredNativeAbis
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.annotation)
    testImplementation(libs.junit)
}
