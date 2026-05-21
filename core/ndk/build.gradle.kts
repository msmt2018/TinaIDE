plugins {
    id("tina.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.wuxianggujun.tinaide.core.ndk"
    buildFeatures {
        aidl = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:i18n"))
    implementation(project(":core:model"))
    implementation(libs.timber)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
}
