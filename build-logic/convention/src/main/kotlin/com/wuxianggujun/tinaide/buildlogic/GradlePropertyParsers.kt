package com.wuxianggujun.tinaide.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project

internal fun Project.resolveBooleanGradleProperty(name: String, default: Boolean): Boolean {
    val raw = providers.gradleProperty(name).orNull?.trim() ?: return default
    return when {
        raw.equals("true", ignoreCase = true) || raw == "1" -> true
        raw.equals("false", ignoreCase = true) || raw == "0" -> false
        else -> throw GradleException(
            "Invalid boolean gradle property '$name=$raw'. Expected true/false.",
        )
    }
}
