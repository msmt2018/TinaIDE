plugins {
    id("tina.android.library")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.wuxianggujun.tinaide.core.editorview"
    buildFeatures {
        compose = true
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project.dependencies.project(":core:common"))
    implementation(project.dependencies.project(":core:text-engine"))
    implementation(project.dependencies.project(":core:config"))
    implementation(project.dependencies.project(":core:designsystem"))
    implementation(project.dependencies.project(":core:i18n"))
    implementation(project.dependencies.project(":core:editor-lsp"))

    implementation(libs.kotlinx.coroutines)
    implementation(libs.timber)
    implementation(libs.androidx.collection)
    coreLibraryDesugaring(libs.desugar)

    implementation(platform(libs.compose.bom))
    testImplementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    testImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.compose.ui.test.junit4)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation("androidx.compose.foundation:foundation")
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.tree.sitter)
    api(project.dependencies.project(":core:tree-sitter"))
}
