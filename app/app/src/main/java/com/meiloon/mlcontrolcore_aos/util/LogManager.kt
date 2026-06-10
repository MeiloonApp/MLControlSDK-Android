package com.meiloon.mlcontrolcore_aos.util

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.meiloon.controlcore.widget.app.method.Method.date.getDateFormat

/**
 * Log 管理類別，負責處理日誌的格式化與顯示
 * 改為單例模式 (object)，使其生命週期不隨 ViewModel 銷毀
 */
object LogManager {

    private val _logs = MutableLiveData<List<String>>(emptyList())
    val logs: LiveData<List<String>> = _logs
    private val logsList = mutableListOf<String>()

    /**
     * 加入命令回應的日誌 (CmdDone)
     */
    fun addCMDLog(type: String, result: String) {
        val now = System.currentTimeMillis()
        val currentTime = getDateFormat(now, "HH:mm:ss")
        addLogItem(listOf("[$currentTime] 收到回應 [CmdDone $type] [結果: $result]"), false)
    }

    /**
     * 加入一般回應的日誌
     */
    fun addResponseLog(method: String, data: String = "", result: String? = null) {
        val now = System.currentTimeMillis()
        val currentTime = getDateFormat(now, "HH:mm:ss")
        val dataPart = if (data.isNotEmpty()) " [$data]" else ""
        val resultPart = if (result != null) " [結果: $result]" else ""
        addLogItem(listOf("[$currentTime] 收到回應 [$method]$dataPart$resultPart"), false)
    }

    /**
     * 加入通用日誌
     * @param data 日誌清單
     * @param addTime 是否自動加上時間戳
     */
    fun addLogItem(data: List<String>, addTime: Boolean = true) {
        synchronized(logsList) {
            val now = System.currentTimeMillis()
            val currentTime = getDateFormat(now, "HH:mm:ss")

            val temp = mutableListOf<String>()
            // 由新到舊紀錄排序
            for (i in data.indices.reversed()) {
                val d = data[i]
                var text = d
                if (addTime) {
                    text = "[$currentTime] $d"
                }
                temp.add(text)
            }

            // 將新日誌放在最前面 (最新在最上)
            logsList.addAll(0, temp)
            _logs.postValue(ArrayList(logsList))
        }
    }

    /**
     * 清空所有日誌
     */
    fun clearLogs() {
        synchronized(logsList) {
            logsList.clear()
            _logs.postValue(emptyList())
        }
    }
}
