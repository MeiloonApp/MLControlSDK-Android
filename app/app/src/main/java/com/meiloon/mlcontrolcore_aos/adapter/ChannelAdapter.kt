package com.meiloon.mlcontrolcore_aos.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import com.meiloon.mlcontrolcore_aos.databinding.ItemChannelBinding
import com.meiloon.controlcore.widget.app.adapter.ViewHolder
import com.meiloon.controlcore.widget.app.android.AppAdapter

class ChannelAdapter: AppAdapter<String, ViewHolder<ItemChannelBinding>>() {
    var onChannelClickListener: ((position: Int, item: String) -> Unit)? = null

    override fun onBindViewHolder(
        holder: ViewHolder<ItemChannelBinding>,
        position: Int,
        item: String?,
        selectedPosition: Int
    ) {
        val isSelected = position == selectedPosition
        holder.binding.tvChannelName.text = item ?: ""
        holder.binding.tvChannelName.isSelected = isSelected

        holder.binding.tvChannelName.setOnClickListener {
            item?.let { onChannelClickListener?.invoke(position, it) }
        }
    }

    override fun onRecycled(holder: ViewHolder<ItemChannelBinding>) {

    }

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder<ItemChannelBinding>  {
        val binding = ItemChannelBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder<ItemChannelBinding>(binding.getRoot(), binding)
    }

}