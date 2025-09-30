package com.example.lingo.ui.main.translation

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.lingo.R

class TranslationPhotoAdapter(
    private val items: MutableList<Uri>,
    private val onDelete: (position: Int) -> Unit
) : RecyclerView.Adapter<TranslationPhotoAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.imgPhoto)
        val order: TextView = v.findViewById(R.id.tvOrder)
        val btnDelete: TextView = v.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_translation_photo, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val uri = items[position]
        holder.img.setImageURI(uri) // 썸네일 간단 표시
        holder.order.text = (position + 1).toString()
        holder.btnDelete.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onDelete(pos)
            }
        }
    }

    override fun getItemCount(): Int = items.size
}
