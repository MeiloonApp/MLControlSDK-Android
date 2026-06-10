package com.meiloon.mlcontrolcore_aos.fragment.roomcorrection

import android.annotation.SuppressLint
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.meiloon.controlcore.global.database.repository.DeviceRepository
import com.meiloon.controlcore.main.container.chart.data.EQData
import com.meiloon.controlcore.main.container.room.MLRoomCorrectionEngine
import com.meiloon.controlcore.main.container.room.MLRoomCorrectionOptions
import com.meiloon.controlcore.main.container.room.MLRoomMeasurementType
import com.meiloon.controlcore.widget.app.android.AppViewModel
import com.meiloon.mlcontrolcore_aos.adapter.LogAdapter
import com.meiloon.mlcontrolcore_aos.data.BottomSheet
import com.meiloon.mlcontrolcore_aos.data.ConnectedDeviceInfo
import com.meiloon.mlcontrolcore_aos.data.TempFileItem
import com.meiloon.mlcontrolcore_aos.util.AppCoroutine
import com.meiloon.mlcontrolcore_aos.util.LogManager
import com.meiloon.mlcontrolcore_aos.util.toIntOrZero
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.stream.Collectors

class RoomCorrectionViewModel(private val repository: DeviceRepository) : AppViewModel() {
    var connectedDeviceInfo: ConnectedDeviceInfo = ConnectedDeviceInfo()
    val logAdapter: LogAdapter = LogAdapter()
    val isMonitoring = MutableLiveData<Boolean>(false)
    val micLevel = MutableLiveData<Double>(-100.0)
    val progressText = MutableLiveData<String>("閒置")
    val isRecording = MutableLiveData<Boolean>(false)
    
    val nfStatus = MutableLiveData<String>("未錄製")
    val ffStatus = MutableLiveData<String>("未錄製")
    val tempFileList = MutableLiveData<List<TempFileItem>>(emptyList())
    val isAnalysisReady = MutableLiveData<Boolean>(false)
    val analysisResult = MutableLiveData<List<EQData>>(emptyList())

    private var nfFile: File? = null
    private var ffFile: File? = null
    val roomOptions = MLRoomCorrectionOptions().apply {
        numBands = 15
        maxFreq = 600.0
        minFreq = 50.0
        minGain = -12.0
        maxGain = 12.0
        enableAutoGainMatching = false
        enableHouseCurve = false
        enableDeepNullProtection = false
    }

    val numBands = MutableLiveData<Int>(roomOptions.numBands)
    val bandRange = 1..15
    private var monitorJob: Job? = null

    fun addBand() {
        val nextValue = ((numBands.value ?: bandRange.last) + 1).coerceIn(bandRange)
        numBands.value = nextValue
        roomOptions.numBands = nextValue
    }

    fun removeBand() {
        val nextValue = ((numBands.value ?: bandRange.last) - 1).coerceIn(bandRange)
        numBands.value = nextValue
        roomOptions.numBands = nextValue
    }

    fun initCDeviceInfo(selectedResult: com.polidea.rxandroidble3.scan.ScanResult?,
                        bottomSheet: BottomSheet?) {
        connectedDeviceInfo.selectedResult = selectedResult
        connectedDeviceInfo.chipID.value = bottomSheet?.chipID ?: "0"
        connectedDeviceInfo.volume.value = bottomSheet?.volume?.toIntOrZero()
    }

    fun toggleLevelMonitoring() {
        if (isMonitoring.value == true) stopMonitoring() else startMonitoring()
    }

    @SuppressLint("MissingPermission")
    private fun startMonitoring() {
        isMonitoring.value = true
        progressText.value = "校正音量中"

        addLogItem(listOf("開始電平監測..."))

        val engine = MLRoomCorrectionEngine.getInstance()
        engine.startCheckLevelMode()
        
        monitorJob?.cancel()
        val startTime = System.currentTimeMillis()
        monitorJob = AppCoroutine.launchMain(viewModelScope) {
            try {
                while (isMonitoring.value == true) {
                    val db = engine.inputLevelDb
                    micLevel.value = db

                    // 監測滿 3 秒後，若電平達到 -45dB 則自動停止
                    val elapsedTime = System.currentTimeMillis() - startTime
                    if (elapsedTime >= 2000 && db >= -45.0) {
                        addLogItem(listOf("電平已達標 (-45dB) 停止監測"))
                        stopMonitoring()
                        break
                    }
                    delay(100) // 輪詢頻率
                }
            } catch (e: Exception) {
                e.printStackTrace()
                stopMonitoring()
            }
        }
    }

    fun stopMonitoring() {
        isMonitoring.value = false
        progressText.value = "閒置"
        MLRoomCorrectionEngine.getInstance().stopCheckLevelMode()
        monitorJob?.cancel()
    }

    fun startRecord(type: MLRoomMeasurementType, cacheDir: File) {
        if (isMonitoring.value == true) stopMonitoring()
        // 如果正在錄製就不要觸發
        if (isRecording.value == true) return

        val statusLiveData = if (type == MLRoomMeasurementType.NEAR_FIELD) nfStatus else ffStatus

        isRecording.value = true
        progressText.value = "量測中 (Sweep)"
        statusLiveData.value = "錄製中..."

        AppCoroutine.launchDefault(viewModelScope) {
            MLRoomCorrectionEngine.getInstance().recordMeasurement(type) { file ->
                AppCoroutine.launchMain(viewModelScope) {
                    isRecording.value = false
                    if (file != null) {
                        if (type == MLRoomMeasurementType.NEAR_FIELD) {
                            nfFile = file
                            statusLiveData.value = "NF 已就緒"

                            addLogItem(listOf("NF 錄製成功: ${file.name}"))
                        } else {
                            ffFile = file
                            statusLiveData.value = "FF 已就緒"

                            addLogItem(listOf("FF 錄製成功: ${file.name}"))
                        }

                        updateTempFileCount(cacheDir)
                    } else {
                        statusLiveData.value = "失敗"
                    }
                    progressText.value = "閒置"
                }
            }
        }
    }

    fun analyze(
        smartGain: Boolean,
        houseCurve: Boolean,
        deepNull: Boolean,
        onResult: (Boolean, String?) -> Unit,
        showAlert: (String) -> Unit
    ) {
        val nf = nfFile
        val ff = ffFile

        if (nf == null || ff == null) {
            showAlert("請先完成近場與遠場錄製")
            return
        }
        
        // 更新當前 UI 選擇的參數到 roomOptions
        roomOptions.enableAutoGainMatching = smartGain
        roomOptions.enableHouseCurve = houseCurve
        roomOptions.enableDeepNullProtection = deepNull

        progressText.value = "計算 FFT 中"
        addLogItem(listOf("正在開始 FFT 分析與合成"))
        
        MLRoomCorrectionEngine.getInstance().analyzeFiles(nf, ff, roomOptions) { result, error ->
            AppCoroutine.launchMain(viewModelScope) {
                progressText.value = "閒置"
                if (result != null) {
                    // 修正EQDataIndex
                    analysisResult.value = setupEQData(result.peqBands)
                    isAnalysisReady.value = true
                    onResult(true, "計算完成")
                } else {
                    analysisResult.value = emptyList()
                    isAnalysisReady.value = false
                    onResult(false, "計算失敗: $error")
                }
            }
        }
    }

    /**
     * 檢查 EQData 列表中的 gain 是否超過範圍 (-12 到 12)，若超過則設為最大或最小值
     * 硬體端的index是從1開始
     */
    private fun setupEQData(eqList: List<EQData>): List<EQData> {
        return eqList.map { item ->
            val safeGain = item.gain.coerceIn(-12.0f, 12.0f)
            EQData(item.index + 1, item.freq, safeGain, item.q, item.type)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopMonitoring()
        //清除所有暫存錄音檔
        MLRoomCorrectionEngine.getInstance().clearAllMeasurements()
    }

    fun addCMDLog(type: String, result: String) {
        LogManager.addCMDLog(type, result)
    }

    fun addLogItem(data: List<String>, addTime: Boolean = true) {
        LogManager.addLogItem(data, addTime)
    }

    fun updateTempFileCount(cacheDir: File) {
        val files = cacheDir.listFiles { dir: File, name: String -> 
            name.endsWith(".wav") 
        } ?: emptyArray<File>()
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val items = files.map { file: File ->
            val tag = when {
                nfFile?.name == file.name -> "NF"
                ffFile?.name == file.name -> "FF"
                else -> null
            }
            TempFileItem(
                file = file,
                name = file.name,
                date = dateFormat.format(Date(file.lastModified())),
                tag = tag
            )
        }.sortedByDescending { it.file.lastModified() }

        tempFileList.value = items
    }

    fun setFileAsNF(file: File, cacheDir: File) {
        nfFile = file
        nfStatus.value = "NF 已就緒"
        if (ffFile?.name == file.name) {
            ffFile = null
            ffStatus.value = "未錄製"
        }
        updateTempFileCount(cacheDir)
    }

    fun setFileAsFF(file: File, cacheDir: File) {
        ffFile = file
        ffStatus.value = "FF 已就緒"
        if (nfFile?.name == file.name) {
            nfFile = null
            nfStatus.value = "未錄製"
        }
        updateTempFileCount(cacheDir)
    }

    fun deleteFile(file: File, cacheDir: File) {
        if (file.exists()) {
            file.delete()
        }
        if (nfFile?.name == file.name) {
            nfFile = null
            nfStatus.value = "未錄製"
        }
        if (ffFile?.name == file.name) {
            ffFile = null
            ffStatus.value = "未錄製"
        }
        updateTempFileCount(cacheDir)
    }

    fun clearTempFiles(cacheDir: File) {
        nfFile = null
        ffFile = null
        nfStatus.value = "未錄製"
        ffStatus.value = "未錄製"
        isAnalysisReady.value = false
        analysisResult.value = emptyList()
        updateTempFileCount(cacheDir)
    }
}
