plugins {
    id("tina.android.library")
}

android {
    namespace = "com.wuxianggujun.tinaide.feature.output"
}

dependencies {
    implementation(project.dependencies.project(":core:common"))
    implementation(project.dependencies.project(":core:i18n"))
}
