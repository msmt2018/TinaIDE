package com.wuxianggujun.tinaide.ui.compose.screens.main

import android.content.Context
import androidx.annotation.StringRes
import com.wuxianggujun.tinaide.core.commands.HostCommandCategory
import com.wuxianggujun.tinaide.core.config.ShortcutAction
import com.wuxianggujun.tinaide.core.i18n.Strings

internal sealed interface MainActivityCommandText {
    fun resolve(context: Context): String

    data class Resource(
        @param:StringRes @get:StringRes val resId: Int
    ) : MainActivityCommandText {
        override fun resolve(context: Context): String = context.getString(resId)
    }

    data class Literal(
        val value: String
    ) : MainActivityCommandText {
        override fun resolve(context: Context): String = value
    }
}

internal enum class MainActivityCommandCategory(
    @param:StringRes @get:StringRes val titleRes: Int,
    val order: Int
) {
    FILE(Strings.menu_section_file, 10),
    CODE(Strings.menu_section_code, 20),
    BUILD(Strings.menu_section_build, 30),
    VIEW(Strings.menu_section_view, 40),
    TERMINAL(Strings.menu_terminal, 50),
    PLUGIN(Strings.menu_section_plugin, 90)
}

internal fun HostCommandCategory.toMainActivityCommandCategory(): MainActivityCommandCategory {
    return when (this) {
        HostCommandCategory.FILE -> MainActivityCommandCategory.FILE
        HostCommandCategory.CODE -> MainActivityCommandCategory.CODE
        HostCommandCategory.BUILD -> MainActivityCommandCategory.BUILD
        HostCommandCategory.VIEW -> MainActivityCommandCategory.VIEW
        HostCommandCategory.TERMINAL -> MainActivityCommandCategory.TERMINAL
    }
}

internal enum class MainActivityCommandSource {
    BUILT_IN,
    PLUGIN
}

internal data class MainActivityCommand(
    val id: String,
    val title: MainActivityCommandText,
    val category: MainActivityCommandCategory,
    val enabled: Boolean = true,
    val shortcutAction: ShortcutAction? = null,
    val keywords: List<String> = emptyList(),
    val source: MainActivityCommandSource = MainActivityCommandSource.BUILT_IN,
    val sourceName: String? = null,
    val execute: () -> Unit,
) {
    fun matches(context: Context, query: String): Boolean {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return true

        val titleText = title.resolve(context)
        val categoryText = context.getString(category.titleRes)
        return titleText.contains(normalizedQuery, ignoreCase = true) ||
            categoryText.contains(normalizedQuery, ignoreCase = true) ||
            sourceName?.contains(normalizedQuery, ignoreCase = true) == true ||
            keywords.any { keyword -> keyword.contains(normalizedQuery, ignoreCase = true) }
    }
}

internal fun orderMainActivityCommands(
    commands: List<MainActivityCommand>,
    context: Context,
    pinnedCommandIds: List<String>,
    recentCommandIds: List<String>,
    query: String
): List<MainActivityCommand> {
    val pinnedRank = pinnedCommandIds.rankByCommandId()
    val recentRank = recentCommandIds.rankByCommandId()
    return commands
        .asSequence()
        .filter { command -> command.enabled && command.matches(context, query) }
        .sortedWith(
            compareBy<MainActivityCommand> { command -> pinnedRank[command.id] ?: Int.MAX_VALUE }
                .thenBy { command -> recentRank[command.id] ?: Int.MAX_VALUE }
                .thenBy { command -> command.category.order }
                .thenBy { command -> command.title.resolve(context).lowercase() }
                .thenBy { command -> command.id }
        )
        .toList()
}

private fun List<String>.rankByCommandId(): Map<String, Int> {
    return distinct().mapIndexed { index, commandId -> commandId to index }.toMap()
}
