package com.meiloon.mlcontrolcore_aos.util

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.meiloon.controlcore.widget.app.method.Method.date.getDateFormat
import kotlinx.coroutines.*

/**
 * Log 管理類別，負責處理日誌的格式化與顯示
 * 優化：加入節流機制，每 500ms 才更新一次 UI，並限制最大日誌量
 */
object LogManager {

    private val _logs = MutableLiveData<List<String>>(emptyList())
    val logs: LiveData<List<String>> = _logs
    private val logsList = mutableListOf<String>()
    
    // --- 優化：節流相關設定 ---
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var needsUpdate = false
    private const val UPDATE_INTERVAL_MS = 500L
    private const val MAX_LOG_SIZE = 100 // 限制最大條數

    init {
        // 啟動背景循環，定期刷新 UI
        scope.launch {
            while (isActive) {
                if (needsUpdate) {
                    val snapshot = synchronized(logsList) {
                        needsUpdate = false
                        ArrayList(logsList) // 只有在這裡才執行昂貴的複製操作
                    }
                    _logs.postValue(snapshot)
                }
                delay(UPDATE_INTERVAL_MS)
            }
        }
    }

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
            
            // --- 優化：使用 removeAt 進行高效截斷，避免昂貴的 take/addAll 操作 ---
            while (logsList.size > MAX_LOG_SIZE) {
                logsList.removeAt(logsList.size - 1)
            }
            
            // 標記需要更新，不立刻觸發 postValue
            needsUpdate = true
        }
    }

    /**
     * 清空所有日誌
     */
    fun clearLogs() {
        synchronized(logsList) {
            logsList.clear()
            needsUpdate = true
        }
    }
}
