plugins {
    id("tina.android.library")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.wuxianggujun.tinaide.feature.workspace"
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project.dependencies.project(":core:config"))
    implementation(project.dependencies.project(":core:designsystem"))
    implementation(project.dependencies.project(":core:i18n"))
    implementation(project.dependencies.project(":core:ndk"))
    implementation(project.dependencies.project(":core:proot"))
    implementation(project.dependencies.project(":core:storage"))
    implementation(libs.androidx.activity)
    implementation(libs.activity.compose)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.coroutines)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(project.dependencies.project(":immersionbar-local"))
    implementation(project.dependencies.project(":immersionbar-ktx-local"))
}
