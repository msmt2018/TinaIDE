package com.wuxianggujun.tinaide.ui.fragment

import android.os.Bundle
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.wuxianggujun.tinaide.base.BaseBindingFragment
import com.wuxianggujun.tinaide.databinding.FragmentFileTreeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.wuxianggujun.tinaide.R
import com.wuxianggujun.tinaide.core.ServiceLocator
import com.wuxianggujun.tinaide.core.get
import com.wuxianggujun.tinaide.file.IFileManager
import com.wuxianggujun.tinaide.extensions.toastError
import com.wuxianggujun.tinaide.treeview.TreeNode
import com.wuxianggujun.tinaide.treeview.TreeView
import com.wuxianggujun.tinaide.ui.adapter.FileNodeViewFactory
import com.wuxianggujun.tinaide.ui.adapter.FileNodeViewBinder
import com.wuxianggujun.tinaide.ui.file.TreeUtil
import com.wuxianggujun.tinaide.ui.file.model.TreeFile
import java.io.File

/**
 * 文件树 Fragment
 * 显示项目文件结构
 */
class FileTreeFragment : BaseBindingFragment<FragmentFileTreeBinding>(
    FragmentFileTreeBinding::inflate
) {
    private var treeView: TreeView<TreeFile>? = null
    private lateinit var refreshLayout: SwipeRefreshLayout
    private lateinit var horizontalScrollView: HorizontalScrollView

    private fun fileManagerOrNull(): IFileManager? = try {
        ServiceLocator.get(IFileManager::class.java)
    } catch (_: IllegalStateException) { null }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        refreshLayout = binding.refreshLayout
        horizontalScrollView = binding.horizontalScrollView

        // 设置下拉刷新
        refreshLayout.setOnRefreshListener {
            partialRefresh {
                refreshLayout.isRefreshing = false
                treeView?.refreshTreeView()
            }
        }

        // 推迟到首帧后加载，避免进入页面首帧阻塞导致黑屏
        view.post { loadProject() }
    }

    private fun loadProject() {
        val fm = fileManagerOrNull()
        if (fm == null) {
            try { requireContext().toastError("Service IFileManager 未注册") } catch (_: Throwable) {}
            return
        }
        val project = fm.getCurrentProject()

        if (project != null) {
            loadProjectFiles(project.rootPath)
        }
    }

    private fun loadProjectFiles(rootPath: String) {
        val rootDir = File(rootPath)
        if (rootDir.exists() && rootDir.isDirectory) {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val root = TreeNode.root(TreeUtil.getNodes(rootDir))

                withContext(Dispatchers.Main) {
                    setupTreeView(root)
                }
            }
        }
    }

    /**
     * 设置 TreeView
     */
    private fun setupTreeView(root: TreeNode<TreeFile>) {
        // 创建 TreeView
        val tv = TreeView(requireContext(), root)

        // 创建 ViewFactory
        val factory = FileNodeViewFactory(object : FileNodeViewBinder.TreeFileNodeListener {
            override fun onNodeToggled(treeNode: TreeNode<TreeFile>?, expanded: Boolean) {
                if (treeNode?.isLeaf() == true) {
                    val file = treeNode.value?.file
                    if (file?.isFile == true) {
                        openFileInEditor(file)
                    }
                }
            }

            override fun onNodeLongClicked(
                view: View?,
                treeNode: TreeNode<TreeFile>?,
                expanded: Boolean
            ): Boolean {
                if (view != null && treeNode != null) {
                    showFileContextMenu(view, treeNode)
                }
                return true
            }
        })

        tv.setAdapter(factory)

        // 添加到容器
        horizontalScrollView.removeAllViews()
        horizontalScrollView.addView(
            tv.getView(),
            android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        this.treeView = tv
    }

    private fun openFileInEditor(file: File) {
        // 获取 EditorContainerFragment 并打开文件
        val activity = requireActivity()
        val editorContainer = activity.supportFragmentManager.findFragmentById(R.id.editor_container)
            as? EditorContainerFragment

        if (editorContainer != null) {
            editorContainer.openFile(file)
        } else {
            android.util.Log.e("FileTreeFragment", "EditorContainerFragment not found!")
        }
    }

    private fun showFileContextMenu(view: View, treeNode: TreeNode<TreeFile>) {
        val file = treeNode.value?.file ?: return
        val popupMenu = PopupMenu(requireContext(), view)
        
        // TODO: 添加菜单项
        // 这里可以根据需要添加文件操作菜单
        
        popupMenu.show()
    }

    private fun partialRefresh(callback: () -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val allNodes = treeView?.getAllNodes() ?: emptyList()
            if (allNodes.isNotEmpty()) {
                val node = allNodes[0]
                TreeUtil.updateNode(node)
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        callback()
                    }
                }
            }
        }
    }

    /**
     * 刷新文件树
     */
    fun refresh() {
        if (!isAdded) return
        loadProject()
    }
}
