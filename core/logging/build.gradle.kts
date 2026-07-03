plugins {
    id("tina.android.library")
}

android {
    namespace = "com.wuxianggujun.tinaide.core.logging"
}

dependencies {
    implementation(project.dependencies.project(":core:common"))
    implementation(project.dependencies.project(":core:i18n"))
    implementation(project.dependencies.project(":core:proot"))
    implementation(project.dependencies.project(":core:storage"))
    implementation(libs.timber)
    implementation(libs.kotlinx.coroutines)
}
