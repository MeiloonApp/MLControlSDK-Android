package com.meiloon.mlcontrolcore_aos.fragment.peq

import android.util.Log
import com.meiloon.controlcore.auth.MLNativeManager
import com.meiloon.controlcore.global.activity.GlobalViewModel
import com.meiloon.controlcore.global.database.repository.DeviceRepository
import com.meiloon.controlcore.main.api.APIData
import com.meiloon.controlcore.main.api.CmdDone
import com.meiloon.controlcore.main.api.EQParas
import com.meiloon.controlcore.main.api.enums.APIMethod
import com.meiloon.controlcore.main.container.chart.data.EQData
import com.meiloon.controlcore.main.container.chart.data.toPEQString
import com.meiloon.controlcore.main.container.chart.widget.ChartStorage
import com.meiloon.controlcore.widget.app.android.AppViewModel
import com.meiloon.mlcontrolcore_aos.adapter.BandAdapter
import com.meiloon.mlcontrolcore_aos.adapter.ChannelAdapter
import com.meiloon.mlcontrolcore_aos.adapter.LogAdapter
import com.meiloon.mlcontrolcore_aos.data.ChipChannel
import com.meiloon.mlcontrolcore_aos.data.EQDataType
import com.meiloon.mlcontrolcore_aos.util.LogManager
import kotlin.math.log10


class PeqViewModel(private val repository: DeviceRepository) : AppViewModel() {
    var channelAdapter: ChannelAdapter = ChannelAdapter()
    var bandAdapter: BandAdapter = BandAdapter()
    var chartStorage: ChartStorage = GlobalViewModel.chartStorage
        set(value) {
            field = value
            updateBandData()
        }
    var showingChipChannel: ChipChannel = ChipChannel(1, 1)

    init {
        updateBandData()
    }

    private fun updateBandData() {
        val data = chartStorage.getData(
            showingChipChannel.chip,
            showingChipChannel.channel
        ) ?: emptyList()

        if (data.isNotEmpty()) {
            this.bandAdapter.items = data
        }
    }

    fun parseReceiveCommand(command: ByteArray?, notify: (APIMethod, String) -> Unit) {
        val apiData = APIData(command)
        val method: APIMethod = apiData.method
        Log.e("OtherViewModel", "收到指令: $method ,內容: ${String(apiData.getCustomData())}")

        when (method) {
            APIMethod.EQParas -> {
                val eqParas: EQParas = apiData.getData(EQParas::class.java)
                val tempChannels = mutableSetOf<ChipChannel>()

                for (eqPara in eqParas.get()) {
                    tempChannels.add(ChipChannel(eqPara.chipIndex, eqPara.channel))

                    val eqData = EQData(eqPara.band, eqPara.freq, eqPara.gain, eqPara.q, eqPara.type)
                    chartStorage.saveData(eqPara.chipIndex, eqPara.channel, eqPara.band, eqData)
                    syncGlobalViewModelChartStorage(eqPara.chipIndex, eqPara.channel, eqPara.band, eqData)
                }

                showingChipChannel = getAllChannels().first()
                notify(method, "")
            }
            APIMethod.CmdDone -> {
                val cmdDone = apiData.getData(CmdDone::class.java)
                when (cmdDone.cmd) {
                    "GetAllEQPara" -> {
                        addCMDLog("GetAllEQPara", "成功")
                        notify(method, LogManager.logs.value?.firstOrNull() ?: "")
                    }
                    "SetAllEQPara" -> {
                        addCMDLog("SetAllEQPara", "成功")
                        //成功後同步
                        syncGlobalViewModelChartStorage()
                        notify(method, LogManager.logs.value?.firstOrNull() ?: "")
                    }
                    "SetEQPara" -> {
                        addCMDLog("SetEQPara", "成功")
                        notify(method, LogManager.logs.value?.firstOrNull() ?: "")
                    }
                    "SaveEQPara" -> {
                        addCMDLog("SaveEQPara", "成功")
                        notify(method, LogManager.logs.value?.firstOrNull() ?: "")
                    }
                    else -> {
                        notify(APIMethod.UNKNOWN ,"未定義CmdDone類別")
                    }
                }
            }
            else -> {
                Log.d("parseReceiveCommand", "找不到對應的APIMethod")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun getAllChannels(): List<ChipChannel> {
        val field = chartStorage.javaClass.getDeclaredField("dataMap").apply { isAccessible = true }
        val allData = field.get(chartStorage) as Map<Triple<Int, Int, Int>, EQData>
        val chipChannelList = allData.keys
            .map { ChipChannel(it.first, it.second) }
            .distinct()
            .sortedWith(compareBy({ it.chip }, { it.channel }))

        return chipChannelList
    }

    @Suppress("UNCHECKED_CAST")
    fun syncGlobalViewModelChartStorage() {
        val field = chartStorage.javaClass.getDeclaredField("dataMap").apply { isAccessible = true }
        val allData = field.get(chartStorage) as? Map<Triple<Int, Int, Int>, EQData> ?: return
        
        for ((triple, eqData) in allData) {
            GlobalViewModel.chartStorage.saveData(
                triple.first,
                triple.second,
                triple.third,
                eqData
            )
        }
    }

    fun syncGlobalViewModelChartStorage(chip: Int, channel: Int, band: Int, data: EQData) {
        GlobalViewModel.chartStorage.saveData(chip, channel, band, data)
    }

    fun getResponseData(eqDataList: List<EQData>, fs: Double, numPoints: Int = 100): String {
        val frequencies = DoubleArray(numPoints) { i ->
            i * 200.0
        }

        val params = DoubleArray(eqDataList.size * 4)

        eqDataList.forEachIndexed { i, eqData ->
            val offset = i * 4
            val typeEnum = EQDataType.fromId(eqData.type)

            params[offset + 0] = if (typeEnum == EQDataType.OFF) 0.0 else eqData.type.toDouble()
            params[offset + 1] = if (eqData.freq <= 0) 1.0 else eqData.freq.toDouble()
            params[offset + 2] = if (typeEnum == EQDataType.OFF) 0.0 else eqData.gain.toDouble()
            params[offset + 3] = if (eqData.q <= 0f) 0.707 else eqData.q.toDouble()
        }

        val magnitudes = MLNativeManager.getInstance().calculateCombinedResponse(
            frequencies,
            params,
            fs,
            0.0
        )

        val sb = StringBuilder()
        for (i in frequencies.indices) {
            val mag = magnitudes[i]
            // 確保 log10 的輸入大於 0 且非 NaN
            val safeMag = if (mag.isNaN() || mag <= 0.0) 0.0 else mag
            val dB = 20 * log10(safeMag)
            sb.append(String.format(java.util.Locale.US, "%.1fHz: %.2f dB\n", frequencies[i], dB))
        }
        return sb.toString()
    }

    fun formateEQDataList() : StringBuilder {
        val allChannels = getAllChannels()
        var string = StringBuilder()

        allChannels.forEach { chipChannel ->
            val eqDatas = chartStorage.getData(chipChannel.chip, chipChannel.channel)
            if (eqDatas.isNotEmpty()) {
                if (string.isNotEmpty()) {
                    string.append("\n")
                }
                string.append(eqDatas.toPEQString())
            }
        }

        return  string
    }

    private fun addCMDLog(type: String, result: String) {
        LogManager.addCMDLog(type, result)
    }

}
