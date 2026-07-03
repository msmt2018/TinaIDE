plugins {
    id("tina.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.wuxianggujun.tinaide.core.packages"
}

dependencies {
    implementation(project.dependencies.project(":core:common")) // 复用 TarExtractor
    implementation(project.dependencies.project(":core:i18n"))
    implementation(project.dependencies.project(":core:network"))
    implementation(project.dependencies.project(":core:project"))
    implementation(project.dependencies.project(":core:proot"))
    implementation(libs.timber)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
}
