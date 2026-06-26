package com.meiloon.mlcontrolcore_aos.base

import com.meiloon.controlcore.widget.app.android.AppViewModel
import com.meiloon.mlcontrolcore_aos.util.LogManager

abstract class BaseViewModel : AppViewModel() {
    val logData = LogManager.logs

    fun addLog(text: String) {
        LogManager.addLogItem(listOf(text))
    }

    fun addLogItem(data: List<String>, addTime: Boolean = true) {
        LogManager.addLogItem(data, addTime)
    }

    fun clearLog() {
        LogManager.clearLogs()
    }
}
