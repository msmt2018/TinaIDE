plugins {
    id("tina.android.library")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.wuxianggujun.tinaide.feature.output"
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:compile"))
    implementation(project(":core:config"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:i18n"))
    implementation(libs.androidx.activity)
    implementation(libs.activity.compose)
    implementation(libs.timber)
    implementation(libs.kotlinx.coroutines)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui)
    implementation(libs.lifecycle.runtime.compose)
}
