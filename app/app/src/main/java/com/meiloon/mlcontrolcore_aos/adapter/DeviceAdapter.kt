package com.meiloon.mlcontrolcore_aos.adapter

import android.content.Context
import android.net.wifi.ScanResult
import android.util.Log
import android.util.Pair
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.meiloon.controlcore.global.database.entity.BluetoothEntity
import com.meiloon.controlcore.main.widget.ble.BleControlManager
import com.meiloon.controlcore.widget.app.action.Action
import com.meiloon.controlcore.widget.app.adapter.ViewHolder
import com.meiloon.controlcore.widget.app.android.AppAdapter
import com.meiloon.controlcore.widget.app.listener.click.OnAppClickListener
import com.meiloon.controlcore.widget.app.listener.click.OnAppItemClickListener
import com.meiloon.controlcore.widget.app.shared.SharedMethod
import com.meiloon.mlcontrolcore_aos.databinding.ItemDeviceBinding

class DeviceAdapter : AppAdapter<BluetoothEntity, ViewHolder<ItemDeviceBinding>>() {
    private val bleControlManager: BleControlManager = BleControlManager.getInstance()
    private val nearbyMap: MutableMap<String?, Boolean?> = HashMap<String?, Boolean?>()
    private val lastNotifyTimeMap: MutableMap<String?, Long?> = HashMap<String?, Long?>()
    private var onNearChangeListener: Action<Pair<String?, Boolean?>?>? = null
    private var onItemClickListener: OnAppItemClickListener<DeviceAdapter?>? = null

    override fun onBindViewHolder(
        holder: ViewHolder<ItemDeviceBinding>,
        position: Int,
        item: BluetoothEntity?,
        selectedPosition: Int
    ) {
        if (item == null) return

        holder.binding.btScan.setOnClickListener { view: View? ->
            onItemClickListener?.onItemClick(this, view, position, null)
        }

        val pid = item.pid
        val cid = item.customUuid
        item.serviceUuids
        holder.binding.tvName.text = item.name
        holder.binding.tvIDs.text = "PID:" + pid
        holder.binding.tvAddress.text = item.address
    }

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder<ItemDeviceBinding> {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder<ItemDeviceBinding>(binding.getRoot(), binding)
    }

    override fun onRecycled(holder: ViewHolder<ItemDeviceBinding>) {
    }

    private fun isConnected(bluetoothEntity: BluetoothEntity): Boolean {
        val address = bluetoothEntity.getBluetoothDevice().getAddress()
        for (bluetoothDevice in bleControlManager.getConnectedDevices()) {
//            Log.d("目前連線設備: " + bluetoothDevice.getDevice().getAddress());
            if (bluetoothDevice.device.address.equals(address)) return true
        }
        return false
    }

    private fun isConnected(address: String?): Boolean {
        for (bluetoothDevice in bleControlManager.getConnectedDevices()) {
//            Log.d("目前連線設備: " + bluetoothDevice.getDevice().getAddress() + " , 來源: " + bluetoothDevice.getType());
            if (bluetoothDevice.device.address.equals(address)) return true
        }
        return false
    }

    fun markNearby(macAddress: String?) {
        val now = System.currentTimeMillis()
        nearbyMap.put(macAddress, true)
        lastNotifyTimeMap.put(macAddress, now)
    }

    fun refreshNear(now: Long) {
        val iterator = lastNotifyTimeMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val isConnected = isConnected(entry.key)
            if (isConnected) lastNotifyTimeMap.put(entry.key, now)

            //            Log.e(entry.getKey() + " 設備等待移除時間: " + (now - entry.getValue()) / 1000 + " 秒");
            val expiredMac = entry.key
            val isExpired = now - entry.value!! >= NEARBY_TIMEOUT
            if (isExpired) {
                iterator.remove()
                nearbyMap.remove(expiredMac)
            }
            for (bluetoothEntity in items) {
                if (bluetoothEntity!!.getAddress() != expiredMac) continue
                if (onNearChangeListener != null) onNearChangeListener!!.execute(
                    Pair<String?, Boolean?>(
                        expiredMac,
                        !isExpired
                    )
                )
                break
            }
        }
    }

    fun isNearby(macAddress: String?): Boolean {
        var isNearby = nearbyMap.get(macAddress)
        if (isNearby == null) isNearby = false
        return isNearby
    }

    fun setOnNearChangeListener(onNearChangeListener: Action<Pair<String?, Boolean?>?>?) {
        this.onNearChangeListener = onNearChangeListener
    }

    private fun refreshItem(macAddress: String?) {

        val address = macAddress ?: return

        val index = items.indexOfFirst { it?.getAddress() == address }

        // 3. 如果找到了 (index != -1)，則更新 UI
        if (index != -1) {
            notifyItemChanged(index)
        }
    }

    fun setDeviceItemClickListener(onItemClickListener: OnAppItemClickListener<DeviceAdapter?>?) {
        this.onItemClickListener = onItemClickListener
    }

    companion object {
        private const val NEARBY_TIMEOUT: Long = 30000
    }
}