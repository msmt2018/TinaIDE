plugins {
    id("tina.android.library")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.legacy.kapt)
}

android {
    namespace = "com.wuxianggujun.tinaide.core.storage"
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project.dependencies.project(":core:common"))
    implementation(project.dependencies.project(":core:i18n"))
    implementation(project.dependencies.project(":core:project"))
    implementation(libs.timber)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.androidx.core.ktx)

    // Compose（仅为 StoragePermissionRequester 提供 @Composable 宿主）
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.koin.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)
}
