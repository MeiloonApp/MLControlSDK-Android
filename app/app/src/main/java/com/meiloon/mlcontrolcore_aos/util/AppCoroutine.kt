package com.meiloon.mlcontrolcore_aos.util

import kotlinx.coroutines.*

/**
 * 協程封裝類別，用於統一管理不同線程的啟動
 */
object AppCoroutine {
    
    /**
     * 在主線程 (Main) 執行
     */
    fun launchMain(scope: CoroutineScope, block: suspend CoroutineScope.() -> Unit): Job {
        return scope.launch(Dispatchers.Main, block = block)
    }

    /**
     * 在背景線程 (Default, 適合 CPU 密集型工作) 執行
     */
    fun launchDefault(scope: CoroutineScope, block: suspend CoroutineScope.() -> Unit): Job {
        return scope.launch(Dispatchers.Default, block = block)
    }

    /**
     * 在背景線程 (IO, 適合網路或檔案讀寫) 執行
     */
    fun launchIO(scope: CoroutineScope, block: suspend CoroutineScope.() -> Unit): Job {
        return scope.launch(Dispatchers.IO, block = block)
    }
}
