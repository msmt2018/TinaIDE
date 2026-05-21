plugins {
    id("tina.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.wuxianggujun.tinaide.core.model"
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
