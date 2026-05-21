package com.wuxianggujun.tinaide.ui

import android.app.Application
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wuxianggujun.tinaide.core.network.server.ServerAnnouncement
import com.wuxianggujun.tinaide.core.network.server.TinaServerConfig
import com.wuxianggujun.tinaide.core.compile.LanguageDetector
import com.wuxianggujun.tinaide.core.config.AppPreferences
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr
import com.wuxianggujun.tinaide.core.network.ApiResult
import com.wuxianggujun.tinaide.file.IProjectSession
import com.wuxianggujun.tinaide.project.ProjectListItem
import com.wuxianggujun.tinaide.project.ProjectMetadataStore
import com.wuxianggujun.tinaide.project.ProjectSourceLocation
import com.wuxianggujun.tinaide.storage.ProjectLocationManager
import com.wuxianggujun.tinaide.storage.ProjectPaths
import com.wuxianggujun.tinaide.storage.StorageManager
import com.wuxianggujun.tinaide.ui.projectlist.Announcement
import com.wuxianggujun.tinaide.ui.projectlist.AnnouncementReward
import com.wuxianggujun.tinaide.ui.projectlist.AnnouncementType
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

class ProjectManagerViewModel(
    application: Application,
    private val projectSession: IProjectSession,
    private val projectLocationManager: ProjectLocationManager,
    private val storageManager: StorageManager,
) : AndroidViewModel(application) {

    private companion object {
        private const val TAG = "ProjectManagerViewModel"
        private const val PREF_KEY_DISMISSED_ANNOUNCEMENT_IDS = "project_manager_dismissed_announcement_ids"
        private const val PREF_KEY_ANNOUNCEMENTS_JSON = "project_manager_announcements_json"
        private const val PREF_KEY_POPUP_SHOWN_ANNOUNCEMENT_IDS = "project_manager_popup_shown_announcement_ids"
        private const val MIN_REFRESH_VISIBLE_MS = 900L
    }

    private val _projects = MutableStateFlow<List<ProjectListItem>>(emptyList())
    val projects: StateFlow<List<ProjectListItem>> = _projects.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    // 通知中心状态
    private val _notifications = MutableStateFlow<List<Announcement>>(emptyList())
    val notifications: StateFlow<List<Announcement>> = _notifications.asStateFlow()

    private val _currentAnnouncement = MutableStateFlow<Announcement?>(null)
    val currentAnnouncement: StateFlow<Announcement?> = _currentAnnouncement.asStateFlow()

    private val _hasUnreadNotification = MutableStateFlow(false)
    val hasUnreadNotification: StateFlow<Boolean> = _hasUnreadNotification.asStateFlow()

    private val prefs = AppPreferences.get(getApplication())

    // 旧版“关闭公告”状态，用于迁移到“已读 + 已弹窗”
    private val dismissedAnnouncementIds: MutableSet<String> =
        prefs.getStringSet(PREF_KEY_DISMISSED_ANNOUNCEMENT_IDS, emptySet())
            ?.toMutableSet()
            ?: mutableSetOf()

    private val popupShownAnnouncementIds: MutableSet<String> =
        prefs.getStringSet(PREF_KEY_POPUP_SHOWN_ANNOUNCEMENT_IDS, emptySet())
            ?.toMutableSet()
            ?: mutableSetOf()

    private val announcementStore = LinkedHashMap<String, Announcement>()

    init {
        migrateLegacyAnnouncementState()
        loadPersistedAnnouncements()
        refreshAnnouncements()
    }

    private suspend fun updateAnnouncements() {
        try {
            val config = TinaServerConfig.getInstance(getApplication())
            val apiResult = config.getApi().getAnnouncements()

            when (apiResult) {
                is ApiResult.Success -> {
                    val existingAnnouncements = announcementStore.toMap()
                    val serverAnnouncements = apiResult.data.announcements
                        .map(::mapServerAnnouncementToUi)
                        .map { announcement ->
                            val existingAnnouncement = existingAnnouncements[announcement.id]
                            announcement.copy(
                                receivedAtMillis = existingAnnouncement?.receivedAtMillis
                                    ?: announcement.receivedAtMillis,
                                isRead = announcement.isRead ||
                                    existingAnnouncement?.isRead == true ||
                                    announcement.id in dismissedAnnouncementIds,
                                readAtMillis = announcement.readAtMillis
                                    ?: existingAnnouncement?.readAtMillis
                            )
                        }

                    announcementStore.clear()
                    serverAnnouncements.forEach { announcementStore[it.id] = it }

                    val activePopupId = serverAnnouncements.firstOrNull { announcement ->
                        val existingAnnouncement = existingAnnouncements[announcement.id]
                        existingAnnouncement == null &&
                            announcement.isPopup &&
                            !announcement.isRead &&
                            announcement.id !in popupShownAnnouncementIds &&
                            !isAnnouncementExpired(announcement)
                    }?.id ?: _currentAnnouncement.value?.id

                    if (serverAnnouncements.isEmpty()) {
                        syncAnnouncementState(activePopupId = null)
                    } else {
                        syncAnnouncementState(activePopupId)
                    }
                }

                is ApiResult.Error -> {
                    Timber.tag(TAG).w("Announcement API error: %s", apiResult.message)
                    syncAnnouncementState(_currentAnnouncement.value?.id)
                }

                is ApiResult.NetworkError -> {
                    Timber.tag(TAG).w("Announcement network error: %s", apiResult.message)
                    syncAnnouncementState(_currentAnnouncement.value?.id)
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load announcements")
            syncAnnouncementState(_currentAnnouncement.value?.id)
        }
    }

    fun refreshAnnouncements() {
        viewModelScope.launch {
            updateAnnouncements()
        }
    }

    private fun mapServerAnnouncementToUi(a: ServerAnnouncement): Announcement {
        val typ = when (a.type.trim().uppercase()) {
            "NEW_RELEASE", "UPDATE" -> AnnouncementType.NEW_RELEASE
            "IMPORTANT" -> AnnouncementType.IMPORTANT
            "WARNING", "MAINTENANCE" -> AnnouncementType.WARNING
            else -> AnnouncementType.INFO
        }

        val reward = a.reward?.let { reward ->
            AnnouncementReward(
                quotaAmount = reward.quotaAmount,
                quotaExpiresAtMillis = parseIsoMillis(reward.quotaExpiresAt),
                claimed = reward.claimed,
                canClaim = reward.canClaim,
                claimedAtMillis = parseIsoMillis(reward.claimedAt)
            )
        }

        return Announcement(
            id = a.id,
            type = typ,
            title = a.title,
            bodyContent = a.content,
            content = buildAnnouncementContent(a.content, reward),
            actionText = if (reward?.canClaim == true) {
                Strings.announcement_reward_claim_action.strOr(getApplication(), reward.quotaAmount)
            } else {
                null
            },
            isPopup = a.isPopup,
            dismissible = a.dismissible,
            timestamp = a.timestamp.takeIf { it > 0L } ?: System.currentTimeMillis(),
            expiresAtMillis = parseIsoMillis(a.expiresAt),
            receivedAtMillis = System.currentTimeMillis(),
            isRead = a.isRead,
            readAtMillis = parseIsoMillis(a.readAt),
            reward = reward
        )
    }

    fun markAnnouncementViewed(id: String, popupPresented: Boolean = false) {
        val existing = announcementStore[id] ?: return
        var shouldReportView = false
        var shouldSyncRead = false
        var changed = false
        val localReadAtMillis = System.currentTimeMillis()

        if (!existing.isRead) {
            announcementStore[id] = existing.copy(
                isRead = true,
                readAtMillis = existing.readAtMillis ?: localReadAtMillis
            )
            shouldReportView = true
            shouldSyncRead = true
            changed = true
        }

        if (popupPresented && popupShownAnnouncementIds.add(id)) {
            persistPopupShownAnnouncementIds()
            changed = true
        }

        if (shouldReportView) {
            viewModelScope.launch {
                val api = TinaServerConfig.getInstance(getApplication()).getApi()
                runCatching { api.reportAnnouncementView(id) }
                if (shouldSyncRead) {
                    when (val result = api.markAnnouncementRead(id)) {
                        is ApiResult.Success -> {
                            val syncedReadAtMillis = parseIsoMillis(result.data.readAt)
                                ?: localReadAtMillis
                            updateAnnouncementRecord(id) { current ->
                                current.copy(
                                    isRead = true,
                                    readAtMillis = syncedReadAtMillis
                                )
                            }
                        }

                        is ApiResult.Error -> Unit
                        is ApiResult.NetworkError -> Unit
                    }
                }
            }
        }

        if (changed || popupPresented) {
            syncAnnouncementState(activePopupId = if (popupPresented) id else _currentAnnouncement.value?.id)
        }
    }

    fun markAllAnnouncementsRead() {
        _notifications.value
            .asSequence()
            .filterNot(Announcement::isRead)
            .map(Announcement::id)
            .toList()
            .forEach { markAnnouncementViewed(it) }
    }

    fun dismissAnnouncementDialog(id: String) {
        syncAnnouncementState(
            activePopupId = _currentAnnouncement.value
                ?.takeIf { it.id != id }
                ?.id
        )
    }

    suspend fun handleAnnouncementAction(announcementId: String): Result<String?> {
        val announcement = announcementStore[announcementId] ?: return Result.success(null)
        return runCatching {
            val config = TinaServerConfig.getInstance(getApplication())
            val api = config.getApi()
            val reward = announcement.reward
            if (reward?.canClaim == true) {
                when (val result = api.claimAnnouncementReward(announcement.id)) {
                    is ApiResult.Success -> {
                        val updatedReward = reward.copy(
                            claimed = true,
                            canClaim = false,
                            claimedAtMillis = System.currentTimeMillis(),
                            quotaExpiresAtMillis = parseIsoMillis(result.data.quotaExpiresAt)
                                ?: reward.quotaExpiresAtMillis
                        )
                        updateAnnouncementRecord(announcement.id) {
                            it.copy(
                                reward = updatedReward,
                                actionText = null,
                                content = buildAnnouncementContent(it.bodyContent, updatedReward),
                                isRead = true
                            )
                        }
                        dismissAnnouncementDialog(announcement.id)
                        Strings.announcement_reward_claim_success.strOr(
                            getApplication(),
                            result.data.claimedQuota
                        )
                    }

                    is ApiResult.Error -> error(result.message)
                    is ApiResult.NetworkError -> error(result.message)
                }
            } else {
                runCatching { api.reportAnnouncementClick(announcement.id) }
                markAnnouncementViewed(announcement.id)
                dismissAnnouncementDialog(announcement.id)
                null
            }
        }
    }

    private fun buildAnnouncementContent(content: String?, reward: AnnouncementReward?): String? {
        val parts = mutableListOf<String>()
        content?.trim()?.takeIf { it.isNotEmpty() }?.let(parts::add)
        reward?.let { parts.add(buildRewardSummary(it)) }
        return parts.joinToString("\n\n").takeIf { it.isNotBlank() }
    }

    private fun buildRewardSummary(reward: AnnouncementReward): String {
        val context = getApplication<Application>()
        val lines = mutableListOf<String>()
        lines += if (reward.claimed) {
            Strings.announcement_reward_claimed_hint.strOr(context, reward.quotaAmount)
        } else {
            Strings.announcement_reward_available_hint.strOr(context, reward.quotaAmount)
        }
        reward.quotaExpiresAtMillis?.let { millis ->
            lines += Strings.announcement_reward_expire_hint.strOr(
                context,
                rewardFormatter.format(Instant.ofEpochMilli(millis))
            )
        }
        return lines.joinToString("\n")
    }

    private fun parseIsoMillis(value: String?): Long? {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { Instant.parse(normalized).toEpochMilli() }.getOrNull()
    }

    private fun migrateLegacyAnnouncementState() {
        if (dismissedAnnouncementIds.isEmpty()) return

        if (popupShownAnnouncementIds.addAll(dismissedAnnouncementIds)) {
            persistPopupShownAnnouncementIds()
        }
    }

    private fun loadPersistedAnnouncements() {
        announcementStore.clear()

        val rawJson = prefs.getString(PREF_KEY_ANNOUNCEMENTS_JSON, null)
        if (rawJson.isNullOrBlank()) {
            syncAnnouncementState()
            return
        }

        runCatching {
            val array = JSONArray(rawJson)
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                item.toAnnouncementOrNull()?.let { announcement ->
                    announcementStore[announcement.id] = announcement.copy(
                        isRead = announcement.isRead || announcement.id in dismissedAnnouncementIds
                    )
                }
            }
        }.onFailure { throwable ->
            Timber.tag(TAG).w(throwable, "Failed to parse cached announcements")
            prefs.edit().remove(PREF_KEY_ANNOUNCEMENTS_JSON).apply()
            announcementStore.clear()
        }

        syncAnnouncementState()
    }

    private fun updateAnnouncementRecord(id: String, transform: (Announcement) -> Announcement) {
        val existing = announcementStore[id] ?: return
        announcementStore[id] = transform(existing)
        syncAnnouncementState(activePopupId = _currentAnnouncement.value?.id)
    }

    private fun syncAnnouncementState(activePopupId: String? = _currentAnnouncement.value?.id) {
        pruneExpiredAnnouncements()

        val sortedAnnouncements = announcementStore.values
            .sortedByDescending(::announcementSortKey)

        _notifications.value = sortedAnnouncements
        _hasUnreadNotification.value = sortedAnnouncements.any { !it.isRead }
        _currentAnnouncement.value = activePopupId?.let { popupId ->
            sortedAnnouncements.firstOrNull { it.id == popupId }
        }

        persistAnnouncements()
    }

    private fun announcementSortKey(announcement: Announcement): Long = maxOf(announcement.timestamp, announcement.receivedAtMillis)

    private fun isAnnouncementExpired(announcement: Announcement): Boolean {
        val expiresAtMillis = announcement.expiresAtMillis ?: return false
        return System.currentTimeMillis() >= expiresAtMillis
    }

    private fun pruneExpiredAnnouncements() {
        val expiredIds = announcementStore.values
            .filter(::isAnnouncementExpired)
            .map(Announcement::id)

        if (expiredIds.isEmpty()) return

        expiredIds.forEach { id ->
            announcementStore.remove(id)
            popupShownAnnouncementIds.remove(id)
        }

        persistPopupShownAnnouncementIds()
    }

    private fun persistAnnouncements() {
        val payload = JSONArray().apply {
            announcementStore.values
                .sortedByDescending(::announcementSortKey)
                .forEach { put(it.toJson()) }
        }
        prefs.edit()
            .putString(PREF_KEY_ANNOUNCEMENTS_JSON, payload.toString())
            .apply()
    }

    private fun persistPopupShownAnnouncementIds() {
        prefs.edit()
            .putStringSet(PREF_KEY_POPUP_SHOWN_ANNOUNCEMENT_IDS, HashSet(popupShownAnnouncementIds))
            .apply()
    }

    private fun Announcement.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("title", title)
        put("bodyContent", bodyContent)
        put("content", content)
        put("actionText", actionText)
        put("actionUrl", actionUrl)
        put("isPopup", isPopup)
        put("dismissible", dismissible)
        put("timestamp", timestamp)
        put("expiresAtMillis", expiresAtMillis)
        put("receivedAtMillis", receivedAtMillis)
        put("isRead", isRead)
        put("readAtMillis", readAtMillis)
        put("reward", reward?.toJson())
    }

    private fun AnnouncementReward.toJson(): JSONObject = JSONObject().apply {
        put("quotaAmount", quotaAmount)
        put("quotaExpiresAtMillis", quotaExpiresAtMillis)
        put("claimed", claimed)
        put("canClaim", canClaim)
        put("claimedAtMillis", claimedAtMillis)
    }

    private fun JSONObject.toAnnouncementOrNull(): Announcement? {
        val id = optString("id").trim()
        val title = optString("title").trim()
        if (id.isEmpty() || title.isEmpty()) return null

        val type = runCatching {
            AnnouncementType.valueOf(optString("type").ifEmpty { AnnouncementType.INFO.name })
        }.getOrDefault(AnnouncementType.INFO)

        return Announcement(
            id = id,
            type = type,
            title = title,
            bodyContent = optNullableString("bodyContent"),
            content = optNullableString("content"),
            actionText = optNullableString("actionText"),
            actionUrl = optNullableString("actionUrl"),
            isPopup = optBoolean("isPopup"),
            dismissible = if (has("dismissible")) optBoolean("dismissible") else true,
            timestamp = optLong("timestamp").takeIf { it > 0L } ?: System.currentTimeMillis(),
            expiresAtMillis = optNullableLong("expiresAtMillis"),
            receivedAtMillis = optLong("receivedAtMillis").takeIf { it > 0L } ?: System.currentTimeMillis(),
            isRead = optBoolean("isRead"),
            readAtMillis = optNullableLong("readAtMillis"),
            reward = optJSONObject("reward")?.toAnnouncementReward()
        )
    }

    private fun JSONObject.toAnnouncementReward(): AnnouncementReward = AnnouncementReward(
        quotaAmount = optLong("quotaAmount"),
        quotaExpiresAtMillis = optNullableLong("quotaExpiresAtMillis"),
        claimed = optBoolean("claimed"),
        canClaim = optBoolean("canClaim"),
        claimedAtMillis = optNullableLong("claimedAtMillis")
    )

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optNullableLong(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        return optLong(key)
    }

    private val rewardFormatter: DateTimeFormatter by lazy {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
    }

    fun getProjectsRootDir(): File {
        val app = getApplication<Application>()
        return if (storageManager.hasExternalStoragePermission()) {
            ProjectPaths.getPublicProjectsRoot(app)
        } else {
            ProjectPaths.getPrivateProjectsRoot(app)
        }
    }

    fun reloadProjects(includeAnnouncements: Boolean = false) {
        if (_isRefreshing.value) return

        viewModelScope.launch {
            val startMs = SystemClock.elapsedRealtime()
            _isRefreshing.value = true
            try {
                val items = withContext(Dispatchers.IO) {
                    val knownDirs = LinkedHashMap<String, File>()
                    val appContext = getApplication<Application>()

                    cleanupLegacyExternalProjects(appContext)

                    fun addKnownDir(dir: File) {
                        if (!dir.isDirectory) return
                        if (!isManagedProject(appContext, dir)) return
                        if (!storageManager.canAccessProjectDir(dir)) return
                        if (isEmptyProjectShell(dir)) return
                        val key = runCatching { dir.canonicalPath }.getOrElse { dir.absolutePath }
                        knownDirs.putIfAbsent(key, dir)
                    }

                    fun scanRoot(root: File) {
                        runCatching {
                            if (!root.exists()) root.mkdirs()
                            root.listFiles()
                                ?.filter { it.isDirectory }
                                ?.forEach(::addKnownDir)
                        }
                    }

                    projectLocationManager.getAllProjects().forEach { location ->
                        addKnownDir(File(location.sourceRootPath))
                    }

                    scanRoot(ProjectPaths.getPrivateProjectsRoot(appContext))
                    if (storageManager.hasExternalStoragePermission()) {
                        scanRoot(ProjectPaths.getPublicProjectsRoot(appContext))
                    }

                    knownDirs.values.map { dir ->
                        val meta = ProjectMetadataStore.read(dir)
                        val language = LanguageDetector.detect(dir)
                        ProjectListItem(
                            dir = dir,
                            displayName = meta?.displayName ?: dir.name,
                            id = meta?.id,
                            lastOpenedAt = meta?.lastOpenedAt,
                            buildSystem = meta?.buildSystem,
                            primaryLanguage = language,
                            sourceLocation = if (ProjectPaths.isUnderPublicProjectsRoot(appContext, dir)) {
                                ProjectSourceLocation.PUBLIC
                            } else {
                                ProjectSourceLocation.PRIVATE
                            }
                        )
                    }
                        .sortedWith(compareBy<ProjectListItem>({ it.displayName.lowercase() }, { it.dir.name.lowercase() }))
                }
                _projects.value = items

                if (includeAnnouncements) {
                    updateAnnouncements()
                }
            } finally {
                withContext(NonCancellable) {
                    val elapsed = SystemClock.elapsedRealtime() - startMs
                    val remaining = MIN_REFRESH_VISIBLE_MS - elapsed
                    if (remaining > 0) delay(remaining)
                    _isRefreshing.value = false
                }
            }
        }
    }

    fun deleteProject(
        project: ProjectListItem,
        onResult: (Result<Int>) -> Unit,
    ) {
        if (!_isDeleting.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                val result = runCatching {
                    val app = getApplication<Application>()
                    withContext(NonCancellable + Dispatchers.IO) {
                        val dir = project.dir
                        ensureProjectFileAccess(dir)
                        val projectId = project.id ?: ProjectMetadataStore.read(dir)?.id
                        val ok = dir.deleteRecursively()
                        if (!ok) throw RuntimeException(Strings.error_delete_failed.strOr(app))
                        projectId?.let {
                            runCatching {
                                projectLocationManager.unregisterProject(it, deleteWorkspace = true)
                            }
                        }
                    }
                    reloadProjects()
                    Strings.toast_project_deleted
                }
                onResult(result)
            } finally {
                _isDeleting.value = false
            }
        }
    }

    fun openProject(dir: File): Result<Unit> = runCatching {
        ensureProjectFileAccess(dir)
        runCatching { projectLocationManager.registerProject(dir) }
        projectSession.openProject(dir.absolutePath)
    }

    fun renameProject(
        dir: File,
        newName: String,
        onResult: (Result<File>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = runCatching {
                val app = getApplication<Application>()
                val newDir = withContext(NonCancellable + Dispatchers.IO) {
                    ensureProjectFileAccess(dir)
                    val oldDirName = dir.name
                    val metadata = ProjectMetadataStore.ensure(dir, displayNameFallback = oldDirName)

                    val target = File(dir.parentFile, newName)
                    if (target.exists()) {
                        throw UiMessageException(Strings.error_project_name_exists)
                    }

                    val success = dir.renameTo(target)
                    if (!success) {
                        throw RuntimeException(Strings.toast_rename_failed.strOr(app))
                    }

                    // 更新元数据中的 displayName（并保留稳定 id）
                    runCatching {
                        val current = ProjectMetadataStore.read(target)
                        if (current != null) {
                            ProjectMetadataStore.write(target, current.copy(displayName = newName))
                        }
                    }

                    // 更新项目映射：保留稳定 projectId，只刷新源码目录位置
                    runCatching {
                        projectLocationManager.registerProject(target)
                    }

                    target
                }
                reloadProjects()
                newDir
            }
            onResult(result)
        }
    }

    private fun ensureProjectFileAccess(dir: File) {
        val access = storageManager.checkProjectDirAccess(dir)
        if (access.canAccess) {
            return
        }
        throw UiMessageException(access.failureMessageResId ?: Strings.toast_open_failed)
    }

    private fun cleanupLegacyExternalProjects(appContext: Application) {
        projectLocationManager.getAllProjects()
            .asSequence()
            .filterNot { isManagedProject(appContext, File(it.sourceRootPath)) }
            .forEach { location ->
                runCatching {
                    projectLocationManager.unregisterProject(location.projectId, deleteWorkspace = true)
                }.onSuccess {
                    Timber.tag(TAG).i(
                        "Removed legacy unmanaged project mapping: %s (%s)",
                        location.projectDirName,
                        location.sourceRootPath
                    )
                }.onFailure { throwable ->
                    Timber.tag(TAG).e(
                        throwable,
                        "Failed to remove legacy unmanaged project mapping: %s",
                        location.sourceRootPath
                    )
                }
            }
    }

    private fun isManagedProject(appContext: Application, dir: File): Boolean = ProjectPaths.isUnderPublicProjectsRoot(appContext, dir) ||
        ProjectPaths.isUnderPrivateProjectsRoot(appContext, dir)

    /**
     * 判断项目目录是否为"空壳"：除了 `.tinaide` 元数据目录外没有其它内容。
     *
     * 场景：Android 11+ 清除应用数据不会撤销 `MANAGE_EXTERNAL_STORAGE`，
     * 公有 `Documents/TinaIDE/` 下历史遗留的项目目录只剩元数据时，用户点进去
     * 看到空文件树容易困惑。直接过滤掉这种目录，避免展示。
     */
    private fun isEmptyProjectShell(dir: File): Boolean {
        val children = dir.listFiles() ?: return false
        return children.isNotEmpty() && children.all { it.name == ".tinaide" }
    }
}

class UiMessageException(
    @param:StringRes val messageResId: Int
) : RuntimeException()
