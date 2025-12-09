package com.wuxianggujun.tinaide.ui.adapter

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.wuxianggujun.tinaide.R
import com.wuxianggujun.tinaide.treeview.TreeNode
import com.wuxianggujun.tinaide.treeview.base.BaseNodeViewBinder
import com.wuxianggujun.tinaide.ui.file.model.TreeFile

/**
 * 文件节点 ViewBinder
 */
class FileNodeViewBinder(
    itemView: View,
    private val level: Int,
    private val nodeListener: TreeFileNodeListener
) : BaseNodeViewBinder<TreeFile>(itemView) {

    private lateinit var viewHolder: ViewHolder

    override fun bindView(treeNode: TreeNode<TreeFile>) {
        viewHolder = ViewHolder(itemView)

        // 设置左边距以显示层级
        val leftMargin = level * 15 * itemView.resources.displayMetrics.density.toInt()
        val layoutParams = itemView.layoutParams as? android.view.ViewGroup.MarginLayoutParams
        layoutParams?.leftMargin = leftMargin
        itemView.layoutParams = layoutParams

        // 设置箭头
        with(viewHolder.arrow) {
            rotation = if (treeNode.isExpanded) 90F else 0F
            visibility = if (treeNode.isLeaf()) View.INVISIBLE else View.VISIBLE
        }

        val file = treeNode.value?.file ?: return

        // 设置文件名
        viewHolder.dirName.text = file.name

        // 设置图标
        with(viewHolder.icon) {
            setImageDrawable(treeNode.value?.getIcon(context))
        }
    }

    override fun onNodeToggled(treeNode: TreeNode<TreeFile>, expand: Boolean) {
        viewHolder.arrow.animate()
            .rotation(if (expand) 90F else 0F)
            .setDuration(150)
            .start()

        nodeListener.onNodeToggled(treeNode, expand)
    }

    override fun onNodeLongClicked(view: View, treeNode: TreeNode<TreeFile>, expanded: Boolean): Boolean {
        return nodeListener.onNodeLongClicked(view, treeNode, expanded)
    }

    class ViewHolder(val rootView: View) {
        val arrow: ImageView = rootView.findViewById(R.id.arrow)
        val icon: ImageView = rootView.findViewById(R.id.icon)
        val dirName: TextView = rootView.findViewById(R.id.name)
    }

    interface TreeFileNodeListener {
        fun onNodeToggled(treeNode: TreeNode<TreeFile>?, expanded: Boolean)
        fun onNodeLongClicked(view: View?, treeNode: TreeNode<TreeFile>?, expanded: Boolean): Boolean
    }
}
