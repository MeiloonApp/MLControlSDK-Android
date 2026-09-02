package com.meiloon.mlcontrolcore_aos.fragment.blescan

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.meiloon.controlcore.ota.ScanUiState
import com.meiloon.controlcore.widget.app.android.AppViewModel
import com.meiloon.controlcore.widget.app.ble.BleManager
import com.meiloon.mlcontrolcore_aos.util.LogManager
import com.polidea.rxandroidble3.scan.ScanResult
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

abstract class BaseScanViewModel : AppViewModel() {
    val bleManager: BleManager = BleManager.getInstance()
    val scanState: MutableLiveData<Boolean?> = MutableLiveData<Boolean?>()
    val onScanDevicesChange: MutableLiveData<MutableList<ScanResult?>?> =
        MutableLiveData<MutableList<ScanResult?>?>(java.util.ArrayList<ScanResult?>())
    protected val onScanDevicesMap =
        MutableLiveData<MutableMap<String?, ScanResult?>?>(java.util.HashMap<String?, ScanResult?>())
    
    protected val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState = _uiState.asStateFlow()
    
    private var scanJob: Job? = null
    private var isStopRequested = false
    
    val logData = LogManager.logs

    private fun scanResultsFlow(): Flow<ScanResult> = callbackFlow {
        val disposable = bleManager.startScanUntilStopped { isScan ->
            scanState.postValue(isScan)
            if (!isScan) channel.close()
        }.subscribe({ result ->
            trySend(result)
        }, { error ->
            close(error)
        })

        awaitClose {
            disposable.dispose()
            bleManager.stopScan()
        }
    }

    fun startScanLoop(onResult: (ScanResult) -> Unit) {
        isStopRequested = false
        if (scanJob?.isActive == true) return

        scanJob = viewModelScope.launch {
            try {
                _uiState.value = ScanUiState.Scanning
                withTimeout(15000L) {
                    while (isActive && !isStopRequested) {
                        try {
                            scanResultsFlow().collect { result ->
                                onResult(result)
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            val msg = "掃描過程發生錯誤: ${e.message}"
                            Log.e("ScanViewModel", msg)
                            addLog(msg)
                        }

                        if (!isStopRequested) {
                            delay(1000)
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                val msg = "已達到 15 秒掃描時限，自動停止"
                Log.d("ScanViewModel", msg)
                addLog(msg)
            } catch (e: CancellationException) {
                Log.d("ScanViewModel", "掃描已取消")
                addLog("掃描已取消")
            } catch (e: Exception) {
                val msg = "掃描發生異常: ${e.message}"
                Log.e("ScanViewModel", msg)
                addLog(msg)
            } finally {
                stopScanAction()
            }
        }
    }

    fun stopScanAction() {
        isStopRequested = true
        scanJob?.cancel()
        bleManager.stopScan()
        _uiState.value = ScanUiState.Idle
        scanState.postValue(false)
    }

    fun isScanningNow(): Boolean = scanJob?.isActive == true

    @Synchronized
    fun updateScanDevices(macAddress: String?, scanResult: ScanResult?) {
        val deviceMap: MutableMap<String?, ScanResult?> =
            LinkedHashMap<String?, ScanResult?>(onScanDevicesMap.value ?: emptyMap())
        deviceMap[macAddress] = scanResult
        onScanDevicesMap.postValue(deviceMap)
        onScanDevicesChange.postValue(ArrayList<ScanResult?>(deviceMap.values))
    }

    fun addLog(text: String) {
        LogManager.addLogItem(listOf(text))
    }

    fun clearLog() {
        LogManager.clearLogs()
    }

    fun merge(a: Array<String?>, b: Array<String?>): Array<String?> {
        val result = arrayOfNulls<String>(a.size + b.size)
        System.arraycopy(a, 0, result, 0, a.size)
        System.arraycopy(b, 0, result, a.size, b.size)
        return result
    }
}
