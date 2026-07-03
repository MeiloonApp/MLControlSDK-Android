package com.meiloon.mlcontrolcore_aos.util

import kotlinx.coroutines.*

/**
 * 狀態文字動畫輔助類別，自動在文字後方加上循環動態點 "..."
 *
 * @param onUpdate 當文字更新時的回呼，通常用來設定 TextView.text
 */
class StatusAnimationHelper(private val onUpdate: (String) -> Unit) {
    private var job: Job? = null
    private var currentBaseText: String? = null

    /**
     * 開始動畫
     * @param baseText 基礎文字，例如 "準備中"
     * @param scope 協程作用域，通常傳入 lifecycleScope
     * @param intervalMillis 點閃爍的間隔時間，預設 1000ms
     */
    fun start(baseText: String, scope: CoroutineScope, intervalMillis: Long = 1000) {
        if (currentBaseText == baseText && job?.isActive == true) return
        currentBaseText = baseText

        job?.cancel()
        job = scope.launch {
            var count = 0
            while (isActive) {
                val dots = ".".repeat(count % 3 + 1)
                onUpdate("$baseText$dots")
                count++
                delay(intervalMillis)
            }
        }
    }

    /**
     * 停止動畫
     * @param finalText 停止後要顯示的最終文字（可選）
     */
    fun stop(finalText: String? = null) {
        job?.cancel()
        job = null
        currentBaseText = null
        finalText?.let { onUpdate(it) }
    }

    /**
     * 檢查動畫是否正在運行
     */
    fun isRunning(): Boolean = job?.isActive == true
}
