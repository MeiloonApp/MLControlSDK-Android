package com.meiloon.mlcontrolcore_aos.data

import android.content.Context
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.viewbinding.ViewBinding
import com.meiloon.mlcontrolcore_aos.R

// DevieceFragment顯示連線狀態
enum class BleConnectStatus(val text: String,
                            @ColorRes val colorResId: Int) {
    CONNECTING("連線中...", R.color.ble_connecting),
    DISCONNECT("已斷線", R.color.ble_disconnect),
    CONNECTED("已連線", R.color.system_geeen);

    fun getColor(context: Context): Int {
        return ContextCompat.getColor(context, colorResId)
    }
}