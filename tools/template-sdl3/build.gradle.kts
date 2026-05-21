plugins {
    id("com.android.application")
}

val copySDLSources = tasks.register<Copy>("copySDLSources") {
    from(rootProject.file("app/src/main/java/org/libsdl/app"))
    into(layout.buildDirectory.dir("generated/sdl/org/libsdl/app"))
}

android {
    namespace = "com.tinaide.template.sdl3"
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

    sourceSets {
        getByName("main") {
            java.srcDir(layout.buildDirectory.dir("generated/sdl"))
        }
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation(project(":tools:template-common"))
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(copySDLSources)
}
