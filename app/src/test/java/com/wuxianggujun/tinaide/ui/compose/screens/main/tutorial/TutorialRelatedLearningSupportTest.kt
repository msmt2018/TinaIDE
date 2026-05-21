package com.wuxianggujun.tinaide.ui.compose.screens.main.tutorial

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.help.HelpCategory
import com.wuxianggujun.tinaide.core.help.HelpDocument
import com.wuxianggujun.tinaide.tutorial.data.Tutorial
import com.wuxianggujun.tinaide.tutorial.data.TutorialCategory
import com.wuxianggujun.tinaide.tutorial.data.TutorialType
import org.junit.Test

class TutorialRelatedLearningSupportTest {

    @Test
    fun buildPresentation_shouldStripRelatedSectionAndBuildTutorialAndHelpCards() {
        val buildTutorial = Tutorial(
            id = "build_project",
            titleRes = 1,
            descriptionRes = 2,
            category = TutorialCategory.BUILD,
            type = TutorialType.ARTICLE,
            estimatedMinutes = 20,
            order = 0,
            contentUrl = "help/build-project.md",
        )
        val currentTutorial = Tutorial(
            id = "plugin_quick_start",
            titleRes = 3,
            descriptionRes = 4,
            category = TutorialCategory.ADVANCED,
            type = TutorialType.ARTICLE,
            estimatedMinutes = 12,
            order = 1,
            contentUrl = "help/plugin-quick-start.md",
        )
        val helpDocument = HelpDocument(
            id = "plugins-settings",
            title = "插件设置说明",
            category = HelpCategory.ADVANCED,
            fileName = "plugins-settings.md",
            summary = "查看插件安装、启停和日志入口。",
        )

        val markdown = """
# 插件开发快速开始

正文内容。

## 相关文档

- [构建项目](build-project.md)
- [插件设置说明](plugins-settings.md)
- [当前教程](plugin-quick-start.md)
- [构建项目（重复）](./build-project.md)
        """.trimIndent()

        val presentation = TutorialRelatedLearningSupport.buildPresentation(
            markdown = markdown,
            currentTutorialId = "plugin_quick_start",
            resolveTutorial = { target ->
                when {
                    target.contains("build-project.md") -> buildTutorial
                    target.contains("plugin-quick-start.md") -> currentTutorial
                    else -> null
                }
            },
            resolveHelpDocument = { target ->
                if (target.contains("plugins-settings.md")) helpDocument else null
            },
        )

        assertThat(presentation.markdown).doesNotContain("## 相关文档")
        assertThat(presentation.markdown).contains("正文内容。")
        assertThat(presentation.relatedDestinations).hasSize(2)
        assertThat(presentation.relatedDestinations[0].type)
            .isEqualTo(TutorialRelatedDestinationType.TUTORIAL)
        assertThat(presentation.relatedDestinations[0].tutorial?.id)
            .isEqualTo("build_project")
        assertThat(presentation.relatedDestinations[1].type)
            .isEqualTo(TutorialRelatedDestinationType.HELP)
        assertThat(presentation.relatedDestinations[1].helpDocument?.id)
            .isEqualTo("plugins-settings")
    }

    @Test
    fun buildPresentation_shouldKeepExternalRelatedLinksAsSystemDestinations() {
        val markdown = """
# 快速上手

正文内容。

## 建议下一步

- [Kotlin 官方文档](https://kotlinlang.org/docs/home.html)
- [Kotlin 官方文档（重复）](https://kotlinlang.org/docs/home.html)
        """.trimIndent()

        val presentation = TutorialRelatedLearningSupport.buildPresentation(
            markdown = markdown,
            currentTutorialId = "getting_started",
            resolveTutorial = { null },
            resolveHelpDocument = { null },
        )

        assertThat(presentation.markdown).doesNotContain("## 建议下一步")
        assertThat(presentation.relatedDestinations).hasSize(1)
        assertThat(presentation.relatedDestinations.single().type)
            .isEqualTo(TutorialRelatedDestinationType.EXTERNAL)
        assertThat(presentation.relatedDestinations.single().label)
            .isEqualTo("Kotlin 官方文档")
        assertThat(presentation.relatedDestinations.single().linkTarget)
            .isEqualTo("https://kotlinlang.org/docs/home.html")
    }

    @Test
    fun buildPresentation_shouldTreatNextStepHeadingAsRelatedSection() {
        val buildTutorial = Tutorial(
            id = "build_project",
            titleRes = 1,
            descriptionRes = 2,
            category = TutorialCategory.BUILD,
            type = TutorialType.ARTICLE,
            estimatedMinutes = 20,
            order = 0,
            contentUrl = "help/build-project.md",
        )
        val markdown = """
# 编辑器基础

正文内容。

## 下一步

- [编译项目](build-project.md)
        """.trimIndent()

        val presentation = TutorialRelatedLearningSupport.buildPresentation(
            markdown = markdown,
            currentTutorialId = "editor_basics",
            resolveTutorial = { target ->
                if (target == "build-project.md") buildTutorial else null
            },
            resolveHelpDocument = { null },
        )

        assertThat(presentation.markdown).doesNotContain("## 下一步")
        assertThat(presentation.relatedDestinations).hasSize(1)
        assertThat(presentation.relatedDestinations.single().tutorial?.id)
            .isEqualTo("build_project")
    }
}
