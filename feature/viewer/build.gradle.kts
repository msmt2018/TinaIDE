plugins {
    id("tina.android.library")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.wuxianggujun.tinaide.feature.viewer"
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project.dependencies.project(":core:designsystem"))
    implementation(project.dependencies.project(":core:i18n"))
    implementation(libs.kotlinx.coroutines)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui)
    implementation(libs.coil.compose)
}
