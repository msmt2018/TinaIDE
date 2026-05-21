plugins {
    id("tina.android.library")
}

android {
    namespace = "com.wuxianggujun.tinaide.core.debug"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:i18n"))
    implementation(project(":core:proot"))
    implementation(libs.timber)
    implementation(libs.kotlinx.coroutines)
}
