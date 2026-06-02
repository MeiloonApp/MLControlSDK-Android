package com.meiloon.mlcontrolcore_aos.fragment.other

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
import com.meiloon.controlcore.main.api.EQParas
import com.meiloon.controlcore.main.api.EQRange
import com.meiloon.controlcore.main.api.FirmwareVer
import com.meiloon.controlcore.main.api.PreEQMode
import com.meiloon.controlcore.main.api.RoomCorrectionMode
import com.meiloon.controlcore.main.api.SPKMute
import com.meiloon.controlcore.main.api.Volume
import com.meiloon.controlcore.main.api.enums.APIMethod
import com.meiloon.controlcore.main.api.enums.Mp3PlayerState
import com.meiloon.controlcore.main.api.enums.PairingStatus
import com.meiloon.controlcore.main.api.enums.SPKMuteStatus
import com.meiloon.controlcore.main.container.chart.data.EQData
import com.meiloon.controlcore.main.container.chart.widget.ChartStorage
import com.meiloon.controlcore.widget.app.android.AppViewModel
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

    fun getMp3PlayerState(playerStatus: Int): Mp3PlayerState {
        val status: Mp3PlayerState
        if (playerStatus == Mp3PlayerState.INIT.value) {
            status = Mp3PlayerState.INIT
        } else if (playerStatus == Mp3PlayerState.INITIALIZING.value) {
            status = Mp3PlayerState.INITIALIZING
        } else if (playerStatus == Mp3PlayerState.RUNNING.value) {
            status = Mp3PlayerState.RUNNING
        } else if (playerStatus == Mp3PlayerState.PAUSED.value) {
            status = Mp3PlayerState.PAUSED
        } else if (playerStatus == Mp3PlayerState.STOPPED.value) {
            status = Mp3PlayerState.STOPPED
        } else if (playerStatus == Mp3PlayerState.FINISHED.value) {
            status = Mp3PlayerState.FINISHED
        } else if (playerStatus == Mp3PlayerState.ERROR.value) {
            status = Mp3PlayerState.ERROR
        } else status = Mp3PlayerState.NONE
        return status
    }

    fun addLogItem(data: List<String>) {
        val items = logAdapter.items
        val temp = mutableListOf<String>()

        for (d in data) {
            temp.add(d)
        }

        for (item in items) {
            temp.add(item)
        }

        logAdapter.replaceAllItems(temp)
    }

    fun parseReceiveCommand(command: ByteArray?, currentTime: String, cmdDoneNotify: (CmdDone) -> Unit) {
        val apiData = APIData(command)
        val method: APIMethod = apiData.getMethod()
        Log.e("OtherViewModel", "收到指令: $method ,內容: ${String(apiData.getCustomData())}")

        when (method) {
            APIMethod.FirmwareVer -> {
                val value = apiData.getData(FirmwareVer::class.java).get()
                connectedDeviceInfo.firmwareVer.value = value ?: ""
                addLogItem(listOf("[$currentTime] 收到回應[getFirmwareVer] [$value]"))
            }
            APIMethod.ChipID -> {
                val chipID = apiData.getData(ChipID::class.java)
                addLogItem(listOf("[$currentTime] 收到回應[chipID] [id:${chipID.id()}, name: ${chipID.title()}]"))
                connectedDeviceInfo.chipID.value = chipID.idString()
                connectedDeviceInfo.maxValue = chipID.maxVolume()
            }
            APIMethod.EQMode -> {
                val eqMode = apiData.getData(EQMode::class.java)
                connectedDeviceInfo.isLFEQOn.value = eqMode.lfeq
                connectedDeviceInfo.isHFEQOn.value = eqMode.hfeq
                connectedDeviceInfo.isDeskEQOn.value = eqMode.deskEQ
                addLogItem(listOf("[$currentTime] 收到回應[EQMode] [LFEQOn:${eqMode.lfeq}, HFEQOn:${eqMode.hfeq}, DeskEQOn:${eqMode.deskEQ}]"))
            }

            APIMethod.PreEQMode -> {
                val preEQMode = apiData.getData(PreEQMode::class.java)
                addLogItem(listOf("[$currentTime] 收到回應[PreEQMode]"))
            }

            APIMethod.RoomCorrectionMode -> {
                val mode = apiData.getData(RoomCorrectionMode::class.java)
                connectedDeviceInfo.roomCorrectionMode.value = mode.get()
                val value = if (mode.get() == 1) "開" else "關"
                addLogItem(listOf("[$currentTime] 收到回應[RoomCorrectionMode: $value] [結果:成功]]"))
            }

            APIMethod.BTPairing -> {
                val btPairing = apiData.getData(BTPairing::class.java)
                val pairing = btPairing.get() == PairingStatus.PAIRING
                addLogItem(listOf("[$currentTime] 收到回應[BTPairing: $pairing] [結果:成功]]"))
            }

            APIMethod.Volume -> {
                val volume = apiData.getData(Volume::class.java).get()
                connectedDeviceInfo.volume.value = volume
                addLogItem(listOf("[$currentTime] 收到回應 [Volume: $volume] [結果:成功]]"))
            }

            APIMethod.AudioChipID -> {
                val audioChipID = apiData.getData(AudioChipID::class.java)
                val value: String = audioChipID.audioChipIDs.joinToString(", ")
                connectedDeviceInfo.audioChipIDs.value = audioChipID.audioChipIDs
                addLogItem(listOf("[$currentTime] 收到回應 [AudioChipID: ${value}] [結果:成功]]"))
            }

            APIMethod.AudioChipNumbers -> {
                val chipNumbers = apiData.getData(AudioChipNumbers::class.java)
                connectedDeviceInfo.audioChipNumber.value = chipNumbers.audioChipNumber
                addLogItem(listOf("[$currentTime] 收到回應 [AudioChipNumbers: ${chipNumbers.audioChipNumber}] [結果:成功]]"))
            }

            APIMethod.AudioChannel -> {
                val data = apiData.getData(AudioChannel::class.java)
                val channels = data.allChannels
                connectedDeviceInfo.audioChannel.value = channels
                addLogItem(listOf("[$currentTime] 收到回應 [AudioChannel: ${channels}] [結果:成功]]"))
            }

            APIMethod.AudioSampleRate -> {
                val data = apiData.getData(AudioSampleRate::class.java)
                val audioSampleRates = data.audioSampleRates
                connectedDeviceInfo.audioSampleRates.value = audioSampleRates
                addLogItem(listOf("[$currentTime] 收到回應 [AudioSampleRate: ${audioSampleRates.joinToString(", ") }}] [結果:成功]]"))
            }

            APIMethod.AudioBand -> {
                val data = apiData.getData(AudioBand::class.java)
                val bands = data.audioBands
                connectedDeviceInfo.audioBands.value = bands
                addLogItem(listOf("[$currentTime] 收到回應 [AudioBand: ${bands.joinToString(", ") }}] [結果:成功]]"))
            }

            APIMethod.EQEngine -> {
                val data = apiData.getData(EQEngine::class.java)
                val isOn = data.isOn
                connectedDeviceInfo.eqEngine.value = isOn
                val value = if (isOn) "開" else "關"
                addLogItem(listOf("[$currentTime] 收到回應 [EQEngine: ${value}}] [結果:成功]]"))
            }

            APIMethod.EQGroup -> {
                val data = apiData.getData(EQGroup::class.java)
                val isOn = data.isOn
                connectedDeviceInfo.eqEngine.value = isOn
                val value = if (isOn) "開" else "關"
                addLogItem(listOf("[$currentTime] 收到回應 [EQGroup: ${value}}] [結果:成功]]"))
            }

            APIMethod.EQRange -> {
                val data = apiData.getData(EQRange::class.java)
                val eqRanges = data.eqRanges
                connectedDeviceInfo.eqRanges.value = eqRanges

                var rangeText = ""

                eqRanges.lastOrNull()?.let {
                    rangeText = formatEQRange(it)
                }

                val textList = rangeText.split(",").reversed()

                // 資料過長分段顯示
                for (i in textList.indices) {
                    val text = textList[i]
                    if (i == 0) {
                        addLogItem(listOf("[$currentTime] 收到回應[EQRange: ${text}}]"))
                    } else {
                        addLogItem(listOf("[$currentTime] [EQRange: ${text}}]"))
                    }
                }
            }
            APIMethod.EQParas -> {
                val eqParas: EQParas = apiData.getData(EQParas::class.java)
                for (eqPara in eqParas.get()) {
                    val eqPoint = EQData(eqPara.band, eqPara.freq, eqPara.gain, eqPara.q, eqPara.type)
                    chartStorage.saveData(eqPara.chipIndex, eqPara.channel, eqPara.band, eqPoint)
                }
                connectedDeviceInfo.eqParas.value = eqParas.get()
            }
            APIMethod.SPKMute -> {
                val status = apiData.getData(SPKMute::class.java).get()
                addLogItem(listOf("[$currentTime] [SPKMute: ${status.name}}]"))
                connectedDeviceInfo.isMuteOn.value = (status == SPKMuteStatus.MUTE)
            }
            APIMethod.CmdDone -> {
                val cmdDone = apiData.getData(CmdDone::class.java)
                when (cmdDone.cmd) {
                    "SetLFEQOn" -> {
                        connectedDeviceInfo.isLFEQOn.value = true
                        addLogItem(listOf("[$currentTime] 收到回應[CmdDone SetLFEQOn] [結果:成功]]"))
                    }
                    "SetLFEQOff" -> {
                        connectedDeviceInfo.isLFEQOn.value = false
                        addLogItem(listOf("[$currentTime] 收到回應[CmdDone SetLFEQOff] [結果:成功]]"))
                    }
                    "SetHFEQOn" -> {
                        connectedDeviceInfo.isHFEQOn.value = true
                        addLogItem(listOf("[$currentTime] 收到回應[CmdDone SetHFEQOn] [結果:成功]]"))
                    }
                    "SetHFEQOff" -> {
                        connectedDeviceInfo.isHFEQOn.value = false
                        addLogItem(listOf("[$currentTime] 收到回應[CmdDone SetHFEQOff] [結果:成功]]"))
                    }
                    "SetDeskEQOn" -> {
                        connectedDeviceInfo.isDeskEQOn.value = true
                        addLogItem(listOf("[$currentTime] 收到回應[CmdDone SetDeskEQOn] [結果:成功]]"))
                    }
                    "SetDeskEQOff" -> {
                        connectedDeviceInfo.isDeskEQOn.value = false
                        addLogItem(listOf("[$currentTime] 收到回應[CmdDone SetDeskEQOff] [結果:成功]]"))
                    }
                    "SetMuteOff" -> {
                        connectedDeviceInfo.isMuteOn.value = false
                        addLogItem(listOf("[$currentTime] 收到回應[CmdDone SetMuteOff] [結果:成功]]"))
                    }
                    "SetMuteOn" -> {
                        connectedDeviceInfo.isMuteOn.value = true
                        addLogItem(listOf("[$currentTime] 收到回應[CmdDone SetMuteOn] [結果:成功]]"))
                    }
                    "StartBTPairing" -> addLogItem(listOf("[$currentTime] 收到回應[CmdDone StartBTPairing] [結果:成功]]"))
                    "SetBTDeviceName" -> addLogItem(listOf("[$currentTime] 收到回應[CmdDone SetBTDeviceName] [結果:成功]]"))
                    "GetMqttInfo" -> if (cmdDone.cmdResult == 5) Log.w("CmdDone GetMqttInfo", "設備不支援MQTT")
                    "SetLastVolume" -> addLogItem(listOf("[$currentTime] 收到回應[CmdDone SetLastVolume] [結果:成功]]"))
                    "SetRoomCorrectionMode" -> addLogItem(listOf("[$currentTime] 收到回應[CmdDone SetRoomCorrectionMode] [結果:成功]]"))
                    "GetAudioSampleRate" -> addLogItem(listOf("[$currentTime] 收到回應[CmdDone GetAudioSampleRate] [結果:成功]]"))
                    "GetEQRange" -> addLogItem(listOf("[$currentTime] 收到回應[CmdDone GetEQRange] [結果:成功]]"))
                    "GetAllEQPara" -> addLogItem(listOf("[$currentTime] 收到回應[CmdDone GetAllEQPara] [結果:成功]]"))
                    "GetMute" -> {
                        if (cmdDone.cmdResult == 5)  {
                            Log.w("GetMute", "設備不支援靜音查詢")
                            addLogItem(listOf("[$currentTime] 收到回應[CmdDone GetMute] 設備不支援靜音查詢]"))
                        }
                    }
                    "GetBattery" -> {
                        if (cmdDone.cmdResult == 5) {
                            Log.w("GetBattery", "設備不支援電量查詢")
                            addLogItem(listOf("[$currentTime] 收到回應[CmdDone GetBattery] 設備不支援電量查詢]"))
                        }
                    }
                    "SetAllEQPara" -> {
                        addLogItem(listOf("[$currentTime] 收到回應[CmdDone SetAllEQPara] [結果:成功]"))
                    }
                    "SetEQPara" -> {
                        addLogItem(listOf("[$currentTime] 收到回應[CmdDone SetEQPara] [結果:成功]]"))
                    }
                    else -> {
                        cmdDone?.let {
                            cmdDoneNotify(it)
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
}
