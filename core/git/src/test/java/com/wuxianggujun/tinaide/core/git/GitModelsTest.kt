package com.wuxianggujun.tinaide.core.git

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GitModelsTest {

    @Test
    fun notARepository_shouldRepresentCleanNonRepositoryState() {
        val status = GitStatus.NOT_A_REPOSITORY

        assertThat(status.isRepository).isFalse()
        assertThat(status.branch).isNull()
        assertThat(status.hasChanges).isFalse()
        assertThat(status.staged).isEmpty()
        assertThat(status.untracked).isEmpty()
    }

    @Test
    fun fileStatusSymbols_shouldRemainStableForUiBadges() {
        assertThat(FileStatus.MODIFIED.symbol).isEqualTo("M")
        assertThat(FileStatus.ADDED.symbol).isEqualTo("A")
        assertThat(FileStatus.DELETED.symbol).isEqualTo("D")
        assertThat(FileStatus.UNTRACKED.symbol).isEqualTo("U")
        assertThat(FileStatus.IGNORED.symbol).isEqualTo("!")
    }

    @Test
    fun gitDiff_shouldPreserveLineNumbersAndLineTypes() {
        val diff = GitDiff(
            filePath = "main.cpp",
            hunks = listOf(
                DiffHunk(
                    oldStart = 1,
                    oldCount = 1,
                    newStart = 1,
                    newCount = 2,
                    lines = listOf(
                        DiffLine(DiffLineType.CONTEXT, "int main() {", 1, 1),
                        DiffLine(DiffLineType.ADDED, "  return 0;", null, 2)
                    )
                )
            )
        )

        assertThat(diff.filePath).isEqualTo("main.cpp")
        assertThat(diff.hunks.single().lines.map { it.type })
            .containsExactly(DiffLineType.CONTEXT, DiffLineType.ADDED)
            .inOrder()
        assertThat(diff.hunks.single().lines.last().oldLineNumber).isNull()
        assertThat(diff.hunks.single().lines.last().newLineNumber).isEqualTo(2)
    }
}
