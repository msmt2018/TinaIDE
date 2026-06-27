plugins {
    id("tina.android.library")
}

android {
    namespace = "com.wuxianggujun.tinaide.core.search"
}

dependencies {
    implementation(project.dependencies.project(":core:common"))
    implementation(libs.kotlinx.coroutines)
}
