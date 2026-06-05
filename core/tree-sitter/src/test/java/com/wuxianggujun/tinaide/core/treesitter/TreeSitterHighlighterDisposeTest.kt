package com.wuxianggujun.tinaide.core.treesitter

import com.google.common.truth.Truth.assertThat
import com.itsaky.androidide.treesitter.TSLanguage
import com.itsaky.androidide.treesitter.TSParser
import com.itsaky.androidide.treesitter.TSQuery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TreeSitterHighlighterDisposeTest {

    @Test
    fun dispose_shouldReturnWithoutWaitingForActiveLifecycleReaders() {
        val parser = mockk<TSParser>()
        val query = mockk<TSQuery>()
        val nativeClosed = CountDownLatch(2)

        every { parser.close() } answers {
            nativeClosed.countDown()
            Unit
        }
        every { query.close() } answers {
            nativeClosed.countDown()
            Unit
        }

        val highlighter = createHighlighter(parser = parser, query = query)
        val lifecycleLock = highlighter.lifecycleLockForTest()
        val readLockAcquired = CountDownLatch(1)
        val releaseReadLock = CountDownLatch(1)
        val reader = Thread({
            lifecycleLock.readLock().lock()
            try {
                readLockAcquired.countDown()
                releaseReadLock.await(5, TimeUnit.SECONDS)
            } finally {
                lifecycleLock.readLock().unlock()
            }
        }, "TreeSitterHighlighterDisposeTestReader")

        reader.start()
        assertThat(readLockAcquired.await(5, TimeUnit.SECONDS)).isTrue()

        val disposeReturned = CountDownLatch(1)
        Thread({
            highlighter.dispose()
            disposeReturned.countDown()
        }, "TreeSitterHighlighterDisposeTestCaller").start()

        assertThat(disposeReturned.await(250, TimeUnit.MILLISECONDS)).isTrue()
        assertThat(nativeClosed.await(150, TimeUnit.MILLISECONDS)).isFalse()

        releaseReadLock.countDown()

        assertThat(nativeClosed.await(5, TimeUnit.SECONDS)).isTrue()
        reader.join(5_000)
        assertThat(reader.isAlive).isFalse()
    }

    private fun createHighlighter(
        parser: TSParser = mockk {
            every { close() } just runs
        },
        query: TSQuery = mockk {
            every { close() } just runs
        }
    ): TreeSitterHighlighter {
        val constructor = TreeSitterHighlighter::class.java.getDeclaredConstructor(
            TSLanguage::class.java,
            TSParser::class.java,
            TSQuery::class.java,
            Array<HighlightType>::class.java
        )
        constructor.isAccessible = true
        return constructor.newInstance(
            mockk<TSLanguage>(),
            parser,
            query,
            emptyArray<HighlightType>()
        )
    }

    private fun TreeSitterHighlighter.lifecycleLockForTest(): ReentrantReadWriteLock {
        val field = TreeSitterHighlighter::class.java.getDeclaredField("lifecycleLock")
        field.isAccessible = true
        return field.get(this) as ReentrantReadWriteLock
    }
}
