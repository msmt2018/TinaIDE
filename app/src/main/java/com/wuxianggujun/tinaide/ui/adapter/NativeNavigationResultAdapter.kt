package com.wuxianggujun.tinaide.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wuxianggujun.tinaide.R
import com.wuxianggujun.tinaide.lsp.model.Location
import java.io.File
import java.util.Locale

class NativeNavigationResultAdapter(
    private val onItemClick: (Location) -> Unit
) : ListAdapter<Location, NativeNavigationResultAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_native_navigation_result, parent, false)
        return ViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        itemView: View,
        private val onClick: (Location) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val fileName: TextView = itemView.findViewById(R.id.location_file)
        private val position: TextView = itemView.findViewById(R.id.location_position)
        private val path: TextView = itemView.findViewById(R.id.location_path)

        fun bind(location: Location) {
            val file = File(location.filePath)
            fileName.text = file.name.ifBlank { location.filePath }
            position.text = String.format(
                Locale.getDefault(),
                "L%d : C%d",
                location.startLine + 1,
                location.startCharacter + 1
            )
            path.text = location.filePath

            itemView.setOnClickListener { onClick(location) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Location>() {
        override fun areItemsTheSame(oldItem: Location, newItem: Location): Boolean {
            return oldItem.filePath == newItem.filePath &&
                oldItem.startLine == newItem.startLine &&
                oldItem.startCharacter == newItem.startCharacter &&
                oldItem.endLine == newItem.endLine &&
                oldItem.endCharacter == newItem.endCharacter
        }

        override fun areContentsTheSame(oldItem: Location, newItem: Location): Boolean {
            return oldItem == newItem
        }
    }
}
