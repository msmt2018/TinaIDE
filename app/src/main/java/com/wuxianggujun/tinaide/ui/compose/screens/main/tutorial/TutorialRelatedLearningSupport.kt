package com.wuxianggujun.tinaide.ui.compose.screens.main.tutorial

import com.wuxianggujun.tinaide.core.help.HelpDocument
import com.wuxianggujun.tinaide.tutorial.data.Tutorial

internal enum class TutorialRelatedDestinationType {
    TUTORIAL,
    HELP,
    EXTERNAL,
}

internal data class TutorialRelatedDestination(
    val linkTarget: String,
    val type: TutorialRelatedDestinationType,
    val label: String? = null,
    val tutorial: Tutorial? = null,
    val helpDocument: HelpDocument? = null,
)

internal data class TutorialArticlePresentation(
    val markdown: String,
    val relatedDestinations: List<TutorialRelatedDestination>,
)

internal object TutorialRelatedLearningSupport {

    private val relatedSectionTitles = setOf(
        "相关文档",
        "建议下一步",
        "相关阅读",
        "相关教程",
        "继续学习",
        "下一步",
        "related docs",
        "related documents",
        "related tutorials",
        "next steps",
        "suggested next steps",
        "continue learning",
    )

    private val linkRegex = Regex("""(?<!!)\[([^\]]+)]\(([^)]+)\)""")
    private val headingRegex = Regex("""^(#{1,6})\s*(.+?)\s*$""")

    fun buildPresentation(
        markdown: String,
        currentTutorialId: String,
        resolveTutorial: (String) -> Tutorial?,
        resolveHelpDocument: (String) -> HelpDocument?,
    ): TutorialArticlePresentation {
        val extraction = extractRelatedSections(markdown)
        val seenKeys = mutableSetOf<String>()
        val relatedDestinations = extraction.links.mapNotNull { link ->
            val target = link.target
            val tutorial = resolveTutorial(target)
            if (tutorial != null) {
                if (tutorial.id == currentTutorialId) {
                    return@mapNotNull null
                }
                val key = "tutorial:${tutorial.id}"
                if (!seenKeys.add(key)) {
                    return@mapNotNull null
                }
                return@mapNotNull TutorialRelatedDestination(
                    linkTarget = target,
                    type = TutorialRelatedDestinationType.TUTORIAL,
                    label = link.label,
                    tutorial = tutorial,
                )
            }

            val helpDocument = resolveHelpDocument(target)
            if (helpDocument != null) {
                val key = "help:${helpDocument.id}"
                if (!seenKeys.add(key)) {
                    return@mapNotNull null
                }
                return@mapNotNull TutorialRelatedDestination(
                    linkTarget = target,
                    type = TutorialRelatedDestinationType.HELP,
                    label = link.label,
                    helpDocument = helpDocument,
                )
            }

            if (!isExternalLinkTarget(target)) {
                return@mapNotNull null
            }

            val key = "external:${target.trim()}"
            if (!seenKeys.add(key)) {
                return@mapNotNull null
            }
            return@mapNotNull TutorialRelatedDestination(
                linkTarget = target,
                type = TutorialRelatedDestinationType.EXTERNAL,
                label = link.label,
            )
        }

        return TutorialArticlePresentation(
            markdown = extraction.markdown,
            relatedDestinations = relatedDestinations,
        )
    }

    private fun extractRelatedSections(markdown: String): ExtractedRelatedSections {
        val lines = markdown.lines()
        val remainingLines = mutableListOf<String>()
        val links = mutableListOf<RelatedLink>()
        var index = 0

        while (index < lines.size) {
            val heading = parseHeading(lines[index])
            if (heading != null && isRelatedSectionTitle(heading.title)) {
                while (remainingLines.isNotEmpty() && remainingLines.last().isBlank()) {
                    remainingLines.removeAt(remainingLines.lastIndex)
                }

                val sectionLines = mutableListOf<String>()
                index++
                while (index < lines.size) {
                    val nextHeading = parseHeading(lines[index])
                    if (nextHeading != null && nextHeading.level <= heading.level) {
                        break
                    }
                    sectionLines += lines[index]
                    index++
                }

                links += extractLinks(sectionLines.joinToString("\n"))
                continue
            }

            remainingLines += lines[index]
            index++
        }

        return ExtractedRelatedSections(
            markdown = remainingLines.joinToString("\n").trimEnd(),
            links = links,
        )
    }

    private fun extractLinks(markdown: String): List<RelatedLink> = linkRegex.findAll(markdown)
        .map { match ->
            RelatedLink(
                label = match.groupValues[1].trim(),
                target = match.groupValues[2].trim(),
            )
        }
        .filter { it.target.isNotBlank() }
        .toList()

    private fun isExternalLinkTarget(linkTarget: String): Boolean = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(linkTarget.trim())

    private fun parseHeading(line: String): ParsedHeading? {
        val match = headingRegex.matchEntire(line.trim()) ?: return null
        return ParsedHeading(
            level = match.groupValues[1].length,
            title = match.groupValues[2],
        )
    }

    private fun isRelatedSectionTitle(title: String): Boolean = normalizeSectionTitle(title) in relatedSectionTitles

    private fun normalizeSectionTitle(title: String): String = title
        .trim()
        .removeSuffix(":")
        .removeSuffix("：")
        .lowercase()

    private data class ParsedHeading(
        val level: Int,
        val title: String,
    )

    private data class ExtractedRelatedSections(
        val markdown: String,
        val links: List<RelatedLink>,
    )

    private data class RelatedLink(
        val label: String,
        val target: String,
    )
}
