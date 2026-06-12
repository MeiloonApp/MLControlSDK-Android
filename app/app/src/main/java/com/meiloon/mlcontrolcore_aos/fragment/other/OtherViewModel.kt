package com.meiloon.mlcontrolcore_aos.fragment.other

import android.content.Context
import android.util.Log
import com.meiloon.mlcontrolcore_aos.adapter.LogAdapter
import com.meiloon.mlcontrolcore_aos.data.Command
import com.meiloon.mlcontrolcore_aos.data.CommandItem
import com.meiloon.mlcontrolcore_aos.data.ConnectedDeviceInfo
import com.meiloon.controlcore.global.activity.GlobalViewModel
import com.meiloon.controlcore.global.database.entity.BluetoothEntity
import com.meiloon.controlcore.global.database.repository.DeviceRepository
import com.meiloon.controlcore.main.api.APIData
import com.meiloon.controlcore.main.api.AudioBand
import com.meiloon.controlcore.main.api.AudioChannel
import com.meiloon.controlcore.main.api.AudioChipEQRange
import com.meiloon.controlcore.main.api.AudioChipID
import com.meiloon.controlcore.main.api.AudioChipNumbers
import com.meiloon.controlcore.main.api.AudioSampleRate
import com.meiloon.controlcore.main.api.BTPairing
import com.meiloon.controlcore.main.api.Battery
import com.meiloon.controlcore.main.api.ChipID
import com.meiloon.controlcore.main.api.CmdDone
import com.meiloon.controlcore.main.api.EQEngine
import com.meiloon.controlcore.main.api.EQGroup
import com.meiloon.controlcore.main.api.EQMode
import com.meiloon.controlcore.main.api.EQPara
import com.meiloon.controlcore.main.api.EQParas
import com.meiloon.controlcore.main.api.EQRange
import com.meiloon.controlcore.main.api.FirmwareVer
import com.meiloon.controlcore.main.api.PreEQMode
import com.meiloon.controlcore.main.api.RoomCorrectionMode
import com.meiloon.controlcore.main.api.SPKMute
import com.meiloon.controlcore.main.api.Volume
import com.meiloon.controlcore.main.api.enums.APIMethod
import com.meiloon.controlcore.main.api.enums.CommandType
import com.meiloon.controlcore.main.api.enums.Mp3PlayerState
import com.meiloon.controlcore.main.api.enums.PairingStatus
import com.meiloon.controlcore.main.api.enums.SPKMuteStatus
import com.meiloon.controlcore.main.container.chart.data.EQData
import com.meiloon.controlcore.main.container.chart.widget.ChartStorage
import com.meiloon.controlcore.widget.app.android.AppViewModel
import com.meiloon.mlcontrolcore_aos.data.BottomSheet
import com.meiloon.mlcontrolcore_aos.util.LogManager
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single

class OtherViewModel(private val repository: DeviceRepository) : AppViewModel() {
    var chartStorage: ChartStorage = GlobalViewModel.chartStorage
    var selectedCommandItem: CommandItem? = null
    var connectedDeviceInfo: ConnectedDeviceInfo = ConnectedDeviceInfo()
        set(value) {
            field = value
            val address = field.macAddress.value
            if (address != "") return
            subscribeSingle<BluetoothEntity>(getControlDevice(address), { device ->
                connectedDeviceInfo = ConnectedDeviceInfo(device)
            }, { error -> Log.e("OtherViewModel init", error.localizedMessage ?: "") })
        }
    val items = Command.items
    val logAdapter: LogAdapter = LogAdapter()

    fun getControlDevice(macAddress: String): Single<BluetoothEntity> {
        return repository.getDevice(macAddress)
    }

    fun updateAutoConnect(macAddress: String?, isAuto: Boolean): Completable {
        return repository.updateAutoConnect(macAddress, isAuto)
    }

    fun initCDeviceInfo(selectedResult: com.polidea.rxandroidble3.scan.ScanResult?,
                        bottomSheet: BottomSheet?) {
        connectedDeviceInfo.updateFrom(selectedResult, bottomSheet)
    }

    fun addLogItem(data: List<String>, addTime: Boolean = true) {
        LogManager.addLogItem(data, addTime)
    }

    fun parseReceiveCommand(command: ByteArray?, context: Context?, cmdDoneNotify: (CmdDone) -> Unit) {
        val apiData = APIData(command)
        val method: APIMethod = apiData.method
        Log.e("OtherViewModel", "收到指令: $method ,內容: ${String(apiData.getCustomData())}")

        when (method) {
            APIMethod.FirmwareVer -> {
                val value = apiData.getData(FirmwareVer::class.java).get()
                connectedDeviceInfo.firmwareVer.value = value ?: ""
                addResponseLog("FirmwareVer", value ?: "")
            }
            APIMethod.ChipID -> {
                val chipID = apiData.getData(ChipID::class.java)
                addResponseLog("ChipID", "id:${chipID.id()}, name: ${chipID.title()}")
                connectedDeviceInfo.chipID.value = chipID
            }
            APIMethod.EQMode -> {
                val eqMode = apiData.getData(EQMode::class.java)
                if (eqMode != null) {
                    connectedDeviceInfo.eqMode.value = eqMode
                    addResponseLog("EQMode", "LFEQOn: ${eqMode.lfeq}, HFEQOn: ${eqMode.hfeq}, DeskEQOn: ${eqMode.deskEQ}")
                } else {
                    addResponseLog("EQMode", "null")
                }
            }

            APIMethod.PreEQMode -> {
                addResponseLog("PreEQMode")
            }

            APIMethod.RoomCorrectionMode -> {
                val mode = apiData.getData(RoomCorrectionMode::class.java)
                connectedDeviceInfo.roomCorrectionMode.value = mode.get()
                val value = if (mode.get() == 1) "開" else "關"
                addResponseLog("RoomCorrectionMode", value, "成功")
            }

            APIMethod.BTPairing -> {
                val btPairing = apiData.getData(BTPairing::class.java)
                val pairing = btPairing.get() == PairingStatus.PAIRING
                addResponseLog("BTPairing", pairing.toString(), "成功")
            }

            APIMethod.Volume -> {
                val volume = apiData.getData(Volume::class.java).get()
                connectedDeviceInfo.volume.value = volume
                addResponseLog("Volume", volume.toString(), "成功")
            }

            APIMethod.AudioChipID -> {
                val audioChipID = apiData.getData(AudioChipID::class.java)
                val value: String = audioChipID.audioChipIDs.joinToString(", ")
                connectedDeviceInfo.audioChipIDs.value = audioChipID.audioChipIDs
                addResponseLog("AudioChipID", value, "成功")
            }

            APIMethod.AudioChipNumbers -> {
                val chipNumbers = apiData.getData(AudioChipNumbers::class.java)
                connectedDeviceInfo.audioChipNumber.value = chipNumbers.audioChipNumber
                addResponseLog("AudioChipNumbers", chipNumbers.audioChipNumber.toString(), "成功")
            }

            APIMethod.AudioChannel -> {
                val data = apiData.getData(AudioChannel::class.java)
                val channels = data.allChannels
                connectedDeviceInfo.audioChannel.value = channels
                addResponseLog("AudioChannel", channels.toString(), "成功")
            }

            APIMethod.AudioSampleRate -> {
                val data = apiData.getData(AudioSampleRate::class.java)
                val audioSampleRates = data.audioSampleRates
                connectedDeviceInfo.audioSampleRates.value = audioSampleRates
                addResponseLog("AudioSampleRate", audioSampleRates.joinToString(", "), "成功")
            }

            APIMethod.AudioBand -> {
                val data = apiData.getData(AudioBand::class.java)
                val bands = data.audioBands
                connectedDeviceInfo.audioBands.value = bands
                addResponseLog("AudioBand", bands.joinToString(", "), "成功")
            }

            APIMethod.EQEngine -> {
                val data = apiData.getData(EQEngine::class.java)
                val isOn = data.isOn
                connectedDeviceInfo.eqEngine.value = isOn
                val value = if (isOn) "開" else "關"
                addResponseLog("EQEngine", value, "成功")
            }

            APIMethod.EQGroup -> {
                val data = apiData.getData(EQGroup::class.java)
                val isOn = data.isOn
                connectedDeviceInfo.eqEngine.value = isOn
                val value = if (isOn) "開" else "關"
                addResponseLog("EQGroup", value, "成功")
            }

            APIMethod.EQRange -> {
                val data = apiData.getData(EQRange::class.java)
                val eqRanges = data.eqRanges
                connectedDeviceInfo.eqRanges.value = eqRanges

                eqRanges.lastOrNull()?.let {
                    val rangeText = formatEQRange(it)
                    val textList = rangeText.split(",").reversed()
                    for (i in textList.indices) {
                        if (i == 0) addResponseLog("EQRange", textList[i])
                        else addLogItem(listOf("[EQRange: ${textList[i]}}]"))
                    }
                }
            }

            APIMethod.EQPara -> {
                val eqPara: EQPara = apiData.getData(EQPara::class.java)
                addResponseLog("EQPara", "chipIndex=${eqPara.chipIndex}, channel=${eqPara.channel}, band=${eqPara.band}")
                addLogItem(listOf("[EQPara: freq=${eqPara.freq}, gain=${eqPara.gain}, q=${eqPara.q}, type=${eqPara.type}]"))
            }

            APIMethod.EQParas -> {
                val eqParas: EQParas = apiData.getData(EQParas::class.java)
                for (eqPara in eqParas.get()) {
                    val eqPoint = EQData(eqPara.band, eqPara.freq, eqPara.gain, eqPara.q, eqPara.type)
                    chartStorage.saveData(eqPara.chipIndex, eqPara.channel, eqPara.band, eqPoint)
                }
                connectedDeviceInfo.eqParas.value = eqParas.get()
                addResponseLog("EQParas", "數量: ${eqParas.get().size}")
            }
            APIMethod.SPKMute -> {
                val status = apiData.getData(SPKMute::class.java).get()
                connectedDeviceInfo.isMuteOn.value = (status == SPKMuteStatus.MUTE)
                addResponseLog("SPKMute", "狀態: ${status.name}")
            }
            APIMethod.CmdDone -> {
                val cmdDone = apiData.getData(CmdDone::class.java)

                context?.let { conetext ->
                    val result = cmdDone.getCmdMessage(conetext)

                    when (cmdDone.cmd) {
                        "SetLFEQOn" -> {
                            addCMDLog("SetLFEQOn", result)
                        }
                        "SetLFEQOff" -> {
                            addCMDLog("SetLFEQOff", result)
                        }
                        "SetHFEQOn" -> {
                            addCMDLog("SetHFEQOn", result)
                        }
                        "SetHFEQOff" -> {
                            addCMDLog("SetHFEQOff", result)
                        }
                        "SetDeskEQOn" -> {
                            addCMDLog("SetDeskEQOn", result)
                        }
                        "SetDeskEQOff" -> {
                            addCMDLog("SetDeskEQOff", result)
                        }
                        "SetMuteOff" -> {
                            addCMDLog("SetMuteOff", result)
                        }
                        "SetMuteOn" -> {
                            addCMDLog("SetMuteOn", result)
                        }
                        "StartBTPairing" -> addCMDLog("StartBTPairing", result)
                        "SetBTDeviceName" -> addCMDLog("SetBTDeviceName", result)
                        "GetMqttInfo" -> {
                            if (cmdDone.cmdResult == 5) {
                                val message = "設備不支援MQTT"
                                Log.w("CmdDone GetMqttInfo", "設備不支援MQTT")
                                addCMDLog("SetLastVolume", message)
                            }
                        }
                        "SetLastVolume" -> addCMDLog("SetLastVolume", result)
                        "SetRoomCorrectionMode" -> addCMDLog("SetRoomCorrectionMode", result)
                        "GetAudioSampleRate" -> addCMDLog("GetAudioSampleRate", result)
                        "GetEQRange" -> addCMDLog("GetEQRange", result)
                        "GetAllEQPara" -> addCMDLog("GetAllEQPara", result)
                        "GetMute" -> {
                            if (cmdDone.cmdResult == 5)  {
                                val message = "設備不支援靜音查詢"
                                Log.w("GetMute", message)
                                addCMDLog("GetMute", message)
                            }
                        }
                        "GetBattery" -> {
                            if (cmdDone.cmdResult == 5) {
                                val message = "設備不支援電量查詢"
                                Log.w("GetBattery", message)
                                addCMDLog("GetBattery", message)
                            }
                        }
                        "SetAllEQPara" -> addCMDLog("SetAllEQPara", result)
                        "SetEQPara" -> addCMDLog("SetEQPara", result)
                        "GetEQPara" -> addCMDLog("GetEQPara", result)
                        "GetStatus" -> addCMDLog("GetStatus", result)
                        else -> {
                            cmdDone?.let { cmdDone -> cmdDoneNotify(cmdDone) }
                        }
                    }
                }
            }
            else -> {
                Log.d("parseReceiveCommand", "找不到對應的APIMethod")
            }
        }
    }

    fun formatEQRange(range: AudioChipEQRange): String {
        return "Frequency: ${String.format("%.1f", range.minFreq)} Hz ~ ${String.format("%.1f", range.maxFreq)} Hz," +
                "Gain: ${String.format("%.1f", range.minGain)} dB ~ ${String.format("%.1f", range.maxGain)} dBQ值範圍," +
                "Quality: ${String.format("%.2f", range.minQ)} ~ ${String.format("%.2f", range.maxQ)}"
    }

    private fun addResponseLog(method: String, data: String = "", result: String? = null) {
        LogManager.addResponseLog(method, data, result)
    }

    private fun addCMDLog(type: String, result: String) {
        LogManager.addCMDLog(type, result)
    }

}
