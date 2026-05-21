plugins {
    id("tina.android.library")
}

android {
    namespace = "com.wuxianggujun.tinaide.core.search"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines)
}
