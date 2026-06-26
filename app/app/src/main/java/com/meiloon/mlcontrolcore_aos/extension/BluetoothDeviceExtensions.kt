package com.meiloon.mlcontrolcore_aos.extension

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.meiloon.controlcore.widget.app.method.Method

/**
 * 取得藍牙裝置名稱的 Extension
 * 內部處理了權限檢查與空值判斷
 */
@SuppressLint("MissingPermission")
fun BluetoothDevice?.getSafeName(context: Context): String {
    if (this == null) return "N/A"

    return if (Method.permission.checkConnectPermission(context)) {
        this.name ?: "N/A"
    } else {
        "N/A"
    }
}
