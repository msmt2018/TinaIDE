package com.wuxianggujun.tinaide.ui.file

import com.wuxianggujun.tinaide.treeview.TreeNode
import com.wuxianggujun.tinaide.ui.file.model.TreeFile
import java.io.File

object TreeUtil {

    val FILE_FIRST_ORDER: Comparator<File> = Comparator { file1, file2 ->
        when {
            file1.isFile && file2.isDirectory -> 1
            file2.isFile && file1.isDirectory -> -1
            else -> String.CASE_INSENSITIVE_ORDER.compare(file1.name, file2.name)
        }
    }

    fun getRootNode(node: TreeNode<TreeFile>): TreeNode<TreeFile> {
        var parent = node.parent
        var root = node
        while (parent != null) {
            root = parent
            parent = parent.parent
        }
        return root
    }

    fun updateNode(node: TreeNode<TreeFile>) {
        val expandedNodes = getExpandedNodes(node)
        val newChildren = getNodes(node.value!!.file, node.level)[0].getChildren()
        setExpandedNodes(newChildren, expandedNodes)
        node.setChildren(newChildren)
    }

    private fun setExpandedNodes(nodeList: List<TreeNode<TreeFile>>, expandedNodes: Set<File>) {
        for (treeFileTreeNode in nodeList) {
            if (expandedNodes.contains(treeFileTreeNode.value!!.file)) {
                treeFileTreeNode.isExpanded = true
            }
            setExpandedNodes(treeFileTreeNode.getChildren(), expandedNodes)
        }
    }

    private fun getExpandedNodes(node: TreeNode<TreeFile>): Set<File> {
        val expandedNodes = mutableSetOf<File>()
        if (node.isExpanded) {
            expandedNodes.add(node.value!!.file)
        }
        for (child in node.getChildren()) {
            if (child.value!!.file.isDirectory) {
                expandedNodes.addAll(getExpandedNodes(child))
            }
        }
        return expandedNodes
    }

    fun getNodes(rootFile: File): List<TreeNode<TreeFile>> {
        return getNodes(rootFile, 0)
    }

    /**
     * Get all the tree nodes at the given root
     */
    fun getNodes(rootFile: File, initialLevel: Int): List<TreeNode<TreeFile>> {
        val nodes = mutableListOf<TreeNode<TreeFile>>()
        if (!rootFile.exists()) {
            return nodes
        }

        val root = TreeNode(TreeFile.fromFile(rootFile), initialLevel)
        root.isExpanded = true

        val children = rootFile.listFiles()
        if (children != null) {
            children.sortWith(FILE_FIRST_ORDER)
            for (file in children) {
                addNode(root, file, initialLevel + 1)
            }
        }
        nodes.add(root)
        return nodes
    }

    private fun addNode(node: TreeNode<TreeFile>, file: File, level: Int) {
        val childNode = TreeNode(TreeFile.fromFile(file), level)

        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                children.sortWith(FILE_FIRST_ORDER)
                for (child in children) {
                    addNode(childNode, child, level + 1)
                }
            }
        }

        node.addChild(childNode)
    }
}
