package com.example.practica3.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.practica3.R

data class AudioEntry(val title: String, val uriString: String)

class AudioAdapter(
    private val items: MutableList<AudioEntry>,
    private val onClick: (AudioEntry) -> Unit
) : RecyclerView.Adapter<AudioAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.txtTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_audio, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun replaceAll(newItems: List<AudioEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
