package com.meiloon.mlcontrolcore_aos.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import com.meiloon.controlcore.global.database.entity.BluetoothEntity
import com.meiloon.controlcore.widget.app.adapter.ViewHolder
import com.meiloon.controlcore.widget.app.android.AppAdapter
import com.meiloon.mlcontrolcore_aos.databinding.ItemDeviceBinding
import com.meiloon.mlcontrolcore_aos.databinding.ItemLogBinding

class LogAdapter: AppAdapter<String, ViewHolder<ItemLogBinding>>() {

    override fun onBindViewHolder(
        holder: ViewHolder<ItemLogBinding>,
        position: Int,
        item: String?,
        selectedPosition: Int
    ) {
        holder.binding.tvLog.text = item ?: ""
    }

    override fun onRecycled(holder: ViewHolder<ItemLogBinding>) {

    }

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder<ItemLogBinding> {
        val binding = ItemLogBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder<ItemLogBinding>(binding.getRoot(), binding)
    }


}