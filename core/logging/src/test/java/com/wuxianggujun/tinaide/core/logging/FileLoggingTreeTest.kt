package com.wuxianggujun.tinaide.core.logging

import android.util.Log
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class FileLoggingTreeTest {
    private lateinit var logDir: File

    @Before
    fun setUp() {
        logDir = createTempDir(prefix = "tina-logs-")
    }

    @After
    fun tearDown() {
        logDir.deleteRecursively()
    }

    @Test
    fun isLoggable_shouldRespectMinimumPriority() {
        val tree = FileLoggingTree(logDir = logDir, minPriority = Log.INFO)
        try {
            assertThat(tree.callIsLoggable("Tina", Log.DEBUG)).isFalse()
            assertThat(tree.callIsLoggable("Tina", Log.INFO)).isTrue()
            assertThat(tree.callIsLoggable("Tina", Log.ERROR)).isTrue()
        } finally {
            tree.shutdown()
        }
    }

    @Test
    fun getAllLogFiles_shouldOnlyReturnTinaLogFilesSortedNewestFirst() {
        File(logDir, "tinaide_2026-01-01.log").writeText("old")
        File(logDir, "tinaide_2026-01-03.log").writeText("new")
        File(logDir, "other.log").writeText("ignored")

        val tree = FileLoggingTree(logDir = logDir)
        try {
            assertThat(tree.getAllLogFiles().map { it.name })
                .containsExactly("tinaide_2026-01-03.log", "tinaide_2026-01-01.log")
                .inOrder()
        } finally {
            tree.shutdown()
        }
    }

    private fun FileLoggingTree.callIsLoggable(tag: String?, priority: Int): Boolean {
        val method = FileLoggingTree::class.java.getDeclaredMethod(
            "isLoggable",
            String::class.java,
            Int::class.javaPrimitiveType!!
        )
        method.isAccessible = true
        return method.invoke(this, tag, priority) as Boolean
    }
}
