plugins {
    id("tina.android.library")
}

android {
    namespace = "com.wuxianggujun.tinaide.exec.integration"
}

dependencies {
    implementation(project.dependencies.project(":tina-exec:runtime"))
}
