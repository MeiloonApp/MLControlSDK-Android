package com.meiloon.mlcontrolcore_aos.fragment.peq

import android.util.Log
import com.meiloon.mlcontrolcore_aos.adapter.BandAdapter
import com.meiloon.mlcontrolcore_aos.adapter.ChannelAdapter
import com.meiloon.mlcontrolcore_aos.data.ChipChannel
import com.meiloon.mlcontrolcore_aos.data.EQDataType
import com.meiloon.controlcore.global.activity.GlobalViewModel
import com.meiloon.controlcore.global.database.repository.DeviceRepository
import com.meiloon.controlcore.main.api.APIData
import com.meiloon.controlcore.main.api.CmdDone
import com.meiloon.controlcore.main.api.EQMode
import com.meiloon.controlcore.main.api.EQParas
import com.meiloon.controlcore.main.api.PreEQMode
import com.meiloon.controlcore.main.api.enums.APIMethod
import com.meiloon.controlcore.main.container.chart.data.EQData
import com.meiloon.controlcore.main.container.chart.widget.ChartStorage
import com.meiloon.controlcore.main.widget.ble.BleControlManager
import com.meiloon.controlcore.widget.app.android.AppViewModel

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

    fun parseReceiveCommand(command: ByteArray?, currentTime: String, notify: (APIMethod, String) -> Unit) {
        val apiData = APIData(command)
        val method: APIMethod = apiData.getMethod()
        Log.e("OtherViewModel", "收到指令: $method ,內容: ${String(apiData.getCustomData())}")

        when (method) {
            APIMethod.EQMode -> {
                val eqMode = apiData.getData(EQMode::class.java)
//                connectedDeviceInfo.isLFEQOn.value = eqMode.lfeq
//                connectedDeviceInfo.isHFEQOn.value = eqMode.hfeq
//                connectedDeviceInfo.isDeskEQOn.value = eqMode.deskEQ
            }

            APIMethod.PreEQMode -> {
                val preEQMode = apiData.getData(PreEQMode::class.java)
            }
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
                    "GetAllEQPara" -> { val text = "[$currentTime] 收到回應[CmdDone GetAllEQPara] [結果:成功]]"
                        notify(method, text)
                    }
                    "SetAllEQPara" -> {
                        val text = "[$currentTime] 收到回應[CmdDone SetAllEQPara] [結果:成功]"
                        //成功後同步
                        syncGlobalViewModelChartStorage()
                        notify(method, text)
                    }
                    "SetEQPara" -> {
                        val text = "[$currentTime] 收到回應[CmdDone SetEQPara] [結果:成功]]"
                        notify(method, text)
                    }
                    "SaveEQPara" -> {
                        val text = "[$currentTime] 收到回應[CmdDone SaveEQPara] [結果:成功]]"
                        notify(method, text)
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

    fun send(macAddress: String, command: String) {
        BleControlManager.getInstance().send(macAddress, command.toByteArray())
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
}
