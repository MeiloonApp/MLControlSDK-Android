package com.meiloon.mlcontrolcore_aos.adapter

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import com.meiloon.controlcore.widget.app.adapter.ViewHolder
import com.meiloon.controlcore.widget.app.android.AppAdapter
import com.meiloon.mlcontrolcore_aos.databinding.ItemBleDeviceJlBinding
import com.meiloon.mlcontrolcore_aos.extension.getSafeName

data class BleDeviceItem(val device: BluetoothDevice, var isConnected: Boolean = false)

class BleDeviceAdapter : AppAdapter<BleDeviceItem, ViewHolder<ItemBleDeviceJlBinding>>() {
    var onConnectClickListener: ((BleDeviceItem) -> Unit)? = null

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder<ItemBleDeviceJlBinding> {
        val binding = ItemBleDeviceJlBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder(binding.root, binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder<ItemBleDeviceJlBinding>,
        position: Int,
        item: BleDeviceItem?,
        selectedPosition: Int
    ) {
        item?.let { bleItem ->
            holder.binding.tvDeviceName.text = bleItem.device.getSafeName(context)
            holder.binding.tvDeviceAddress.text = bleItem.device.address
            
            if (bleItem.isConnected) {
                holder.binding.btnConnect.visibility = View.GONE
                holder.binding.ivConnected.visibility = View.VISIBLE
            } else {
                holder.binding.btnConnect.visibility = View.VISIBLE
                holder.binding.ivConnected.visibility = View.GONE
                holder.binding.btnConnect.setOnClickListener {
                    onConnectClickListener?.invoke(bleItem)
                }
            }
        }
    }

    override fun onRecycled(holder: ViewHolder<ItemBleDeviceJlBinding>) {
    }
}
