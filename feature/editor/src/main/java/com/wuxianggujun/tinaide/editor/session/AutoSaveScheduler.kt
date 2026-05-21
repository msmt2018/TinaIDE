package com.wuxianggujun.tinaide.editor.session

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
class AutoSaveScheduler(
    private val scope: CoroutineScope,
    private val intervalProvider: () -> Long,
    private val action: suspend (DocumentSession) -> Unit
) {

    private val jobs = ConcurrentHashMap<String, Job>()
    fun schedule(session: DocumentSession) {
        val interval = intervalProvider()
        if (interval <= 0L) {
            cancel(session.tabId)
            return
        }
        jobs[session.tabId]?.cancel()
        jobs[session.tabId] = scope.launch {
            delay(interval)
            action(session)
            jobs.remove(session.tabId)
        }
    }
    fun cancel(sessionId: String) {
        jobs.remove(sessionId)?.cancel()
    }

    fun cancel(session: DocumentSession) {
        cancel(session.tabId)
    }

    fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }
}
