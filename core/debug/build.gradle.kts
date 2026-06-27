plugins {
    id("tina.android.library")
}

android {
    namespace = "com.wuxianggujun.tinaide.core.debug"
}

dependencies {
    implementation(project.dependencies.project(":core:common"))
    implementation(project.dependencies.project(":core:i18n"))
    implementation(project.dependencies.project(":core:proot"))
    implementation(libs.timber)
    implementation(libs.kotlinx.coroutines)
}
