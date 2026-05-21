package com.wuxianggujun.tinaide.ui.compose.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wuxianggujun.tinaide.storage.CleanupCategory
import com.wuxianggujun.tinaide.storage.StorageCleanupManager
import com.wuxianggujun.tinaide.storage.StorageCleanupManager.CleanupNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StorageCleanupItem(
    val category: CleanupCategory,
    val bytes: Long = 0L,
    val isCleaning: Boolean = false,
)

data class StorageCleanupUiState(
    val items: List<StorageCleanupItem> =
        CleanupCategory.entries.map { StorageCleanupItem(it) },
    val totalBytes: Long = 0L,
    val isScanning: Boolean = true,
    val isCleaningAll: Boolean = false,
    val feedback: Feedback? = null,
) {
    sealed class Feedback {
        data class CleanupDone(val freedBytes: Long) : Feedback()
        data class CleanupFailed(val failedCount: Int, val freedBytes: Long) : Feedback()
    }
}

enum class CheckState { UNCHECKED, CHECKED, PARTIAL }

data class StorageCleanupDetailState(
    val category: CleanupCategory? = null,
    val items: List<CleanupNode> = emptyList(),
    val expandedPaths: Set<String> = emptySet(),
    val loadingChildrenPaths: Set<String> = emptySet(),
    val childrenByParent: Map<String, List<CleanupNode>> = emptyMap(),
    val selectedPaths: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val isDeleting: Boolean = false,
)

class StorageCleanupViewModel(
    private val manager: StorageCleanupManager
) : ViewModel() {

    private val _state = MutableStateFlow(StorageCleanupUiState())
    val state: StateFlow<StorageCleanupUiState> = _state.asStateFlow()

    private val _detail = MutableStateFlow(StorageCleanupDetailState())
    val detail: StateFlow<StorageCleanupDetailState> = _detail.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isScanning = true) }
            val sizes = manager.scanAll()
            _state.update { prev ->
                val updated = sizes.map { sized ->
                    val existing = prev.items.firstOrNull { it.category == sized.category }
                    existing?.copy(bytes = sized.bytes)
                        ?: StorageCleanupItem(sized.category, sized.bytes)
                }
                prev.copy(
                    items = updated,
                    totalBytes = updated.sumOf { it.bytes },
                    isScanning = false
                )
            }
        }
    }

    fun cleanOne(category: CleanupCategory) {
        viewModelScope.launch {
            _state.update { s ->
                s.copy(
                    items = s.items.map {
                        if (it.category == category) it.copy(isCleaning = true) else it
                    }
                )
            }
            val result = manager.clean(category)
            val newSize = manager.scanSize(category)
            _state.update { s ->
                val updated = s.items.map {
                    if (it.category == category) {
                        it.copy(bytes = newSize, isCleaning = false)
                    } else {
                        it
                    }
                }
                s.copy(
                    items = updated,
                    totalBytes = updated.sumOf { it.bytes },
                    feedback = if (result.success) {
                        StorageCleanupUiState.Feedback.CleanupDone(result.deletedBytes)
                    } else {
                        StorageCleanupUiState.Feedback.CleanupFailed(
                            failedCount = result.failedPaths.size,
                            freedBytes = result.deletedBytes
                        )
                    }
                )
            }
        }
    }

    fun cleanAll() {
        viewModelScope.launch {
            _state.update { it.copy(isCleaningAll = true) }
            val results = manager.cleanAll()
            val sizes = manager.scanAll()
            _state.update { prev ->
                val updated = sizes.map { sized ->
                    val existing = prev.items.firstOrNull { it.category == sized.category }
                    existing?.copy(bytes = sized.bytes, isCleaning = false)
                        ?: StorageCleanupItem(sized.category, sized.bytes)
                }
                val freed = results.sumOf { it.deletedBytes }
                val failed = results.sumOf { it.failedPaths.size }
                prev.copy(
                    items = updated,
                    totalBytes = updated.sumOf { it.bytes },
                    isCleaningAll = false,
                    feedback = if (failed == 0) {
                        StorageCleanupUiState.Feedback.CleanupDone(freed)
                    } else {
                        StorageCleanupUiState.Feedback.CleanupFailed(failed, freed)
                    }
                )
            }
        }
    }

    fun consumeFeedback() {
        _state.update { it.copy(feedback = null) }
    }

    // ============ 详情子屏 ============

    fun openDetail(category: CleanupCategory) {
        _detail.value = StorageCleanupDetailState(category = category, isLoading = true)
        viewModelScope.launch {
            val items = manager.scanItems(category)
            _detail.update { it.copy(items = items, isLoading = false) }
        }
    }

    fun closeDetail() {
        _detail.value = StorageCleanupDetailState()
    }

    fun refreshDetail() {
        val current = _detail.value
        val category = current.category ?: return
        viewModelScope.launch {
            _detail.update { it.copy(isLoading = true) }
            val items = manager.scanItems(category)
            val keepExpanded = current.expandedPaths
                .filter { path -> items.any { it.absolutePath == path && it.isDirectory } }
                .toSet()
            val newChildren = mutableMapOf<String, List<CleanupNode>>()
            keepExpanded.forEach { path ->
                items.firstOrNull { it.absolutePath == path }?.let { parent ->
                    newChildren[path] = manager.scanChildren(parent)
                }
            }
            _detail.update {
                it.copy(
                    items = items,
                    expandedPaths = keepExpanded,
                    childrenByParent = newChildren,
                    selectedPaths = emptySet(),
                    isLoading = false,
                )
            }
        }
    }

    fun toggleExpand(item: CleanupNode) {
        if (!item.isDirectory) return
        val path = item.absolutePath
        val current = _detail.value
        if (path in current.expandedPaths) {
            _detail.update { it.copy(expandedPaths = it.expandedPaths - path) }
            return
        }
        _detail.update {
            it.copy(
                expandedPaths = it.expandedPaths + path,
                loadingChildrenPaths = it.loadingChildrenPaths + path,
            )
        }
        if (current.childrenByParent.containsKey(path)) {
            _detail.update { it.copy(loadingChildrenPaths = it.loadingChildrenPaths - path) }
            return
        }
        viewModelScope.launch {
            val children = manager.scanChildren(item)
            _detail.update {
                it.copy(
                    childrenByParent = it.childrenByParent + (path to children),
                    loadingChildrenPaths = it.loadingChildrenPaths - path,
                )
            }
        }
    }

    fun toggleTopLevelSelection(item: CleanupNode) {
        _detail.update { s ->
            val path = item.absolutePath
            val isChecked = path in s.selectedPaths
            val newSelected = if (isChecked) {
                s.selectedPaths - path
            } else {
                val childrenPaths = s.childrenByParent[path]
                    ?.map { it.absolutePath }
                    ?.toSet()
                    ?: emptySet()
                (s.selectedPaths - childrenPaths) + path
            }
            s.copy(selectedPaths = newSelected)
        }
    }

    fun toggleChildSelection(child: CleanupNode, parent: CleanupNode) {
        _detail.update { s ->
            val parentPath = parent.absolutePath
            val childPath = child.absolutePath
            val newSelected: Set<String> = when {
                parentPath in s.selectedPaths -> {
                    val others = s.childrenByParent[parentPath]
                        ?.filter { it.absolutePath != childPath }
                        ?.map { it.absolutePath }
                        ?.toSet()
                        ?: emptySet()
                    (s.selectedPaths - parentPath) + others
                }
                childPath in s.selectedPaths -> s.selectedPaths - childPath
                else -> s.selectedPaths + childPath
            }
            s.copy(selectedPaths = newSelected)
        }
    }

    fun selectAllTopLevel() {
        _detail.update { s ->
            val allPaths = s.items.map { it.absolutePath }.toSet()
            s.copy(selectedPaths = allPaths)
        }
    }

    fun clearSelection() {
        _detail.update { it.copy(selectedPaths = emptySet()) }
    }

    fun cleanSelectedInDetail() {
        val current = _detail.value
        val category = current.category ?: return
        val toClean = current.selectedPaths
        if (toClean.isEmpty()) return
        viewModelScope.launch {
            _detail.update { it.copy(isDeleting = true) }
            val result = manager.cleanPaths(category, toClean)
            // refresh detail items + expanded children
            val items = manager.scanItems(category)
            val keepExpanded = current.expandedPaths
                .filter { path -> items.any { it.absolutePath == path && it.isDirectory } }
                .toSet()
            val newChildren = mutableMapOf<String, List<CleanupNode>>()
            keepExpanded.forEach { path ->
                items.firstOrNull { it.absolutePath == path }?.let { parent ->
                    newChildren[path] = manager.scanChildren(parent)
                }
            }
            _detail.update {
                it.copy(
                    items = items,
                    expandedPaths = keepExpanded,
                    childrenByParent = newChildren,
                    selectedPaths = emptySet(),
                    isDeleting = false,
                )
            }
            // refresh overview totals + emit feedback
            val sizes = manager.scanAll()
            _state.update { prev ->
                val updated = prev.items.map { item ->
                    val sized = sizes.firstOrNull { it.category == item.category }
                    if (sized != null) item.copy(bytes = sized.bytes) else item
                }
                prev.copy(
                    items = updated,
                    totalBytes = updated.sumOf { it.bytes },
                    feedback = if (result.success) {
                        StorageCleanupUiState.Feedback.CleanupDone(result.deletedBytes)
                    } else {
                        StorageCleanupUiState.Feedback.CleanupFailed(
                            failedCount = result.failedPaths.size,
                            freedBytes = result.deletedBytes,
                        )
                    }
                )
            }
        }
    }
}

object StorageCleanupSelectionResolver {
    /** 计算顶层项的复选状态。需要传入它的(已加载的)子项列表;未加载视为空。 */
    fun topLevelCheckState(
        item: CleanupNode,
        loadedChildren: List<CleanupNode>?,
        selectedPaths: Set<String>,
    ): CheckState {
        if (item.absolutePath in selectedPaths) return CheckState.CHECKED
        val children = loadedChildren ?: return CheckState.UNCHECKED
        val anyChildSelected = children.any { it.absolutePath in selectedPaths }
        return if (anyChildSelected) CheckState.PARTIAL else CheckState.UNCHECKED
    }

    /** 子项的复选状态。父在选中集中视为父级覆盖,该子项视为已选。 */
    fun childCheckState(
        child: CleanupNode,
        parent: CleanupNode,
        selectedPaths: Set<String>,
    ): CheckState {
        if (parent.absolutePath in selectedPaths) return CheckState.CHECKED
        if (child.absolutePath in selectedPaths) return CheckState.CHECKED
        return CheckState.UNCHECKED
    }

    /** 计算"已选项"数量(顶层选中 +1;父未选中时,逐个子项 +1)。 */
    fun selectedCount(
        items: List<CleanupNode>,
        childrenByParent: Map<String, List<CleanupNode>>,
        selectedPaths: Set<String>,
    ): Int {
        var count = 0
        items.forEach { item ->
            if (item.absolutePath in selectedPaths) {
                count++
            } else {
                val children = childrenByParent[item.absolutePath] ?: emptyList()
                count += children.count { it.absolutePath in selectedPaths }
            }
        }
        return count
    }

    /** 计算"已选总字节数"(避免父+子重复计数)。 */
    fun selectedBytes(
        items: List<CleanupNode>,
        childrenByParent: Map<String, List<CleanupNode>>,
        selectedPaths: Set<String>,
    ): Long {
        var sum = 0L
        items.forEach { item ->
            if (item.absolutePath in selectedPaths) {
                sum += item.bytes
            } else {
                val children = childrenByParent[item.absolutePath] ?: emptyList()
                sum += children.filter { it.absolutePath in selectedPaths }.sumOf { it.bytes }
            }
        }
        return sum
    }
}
