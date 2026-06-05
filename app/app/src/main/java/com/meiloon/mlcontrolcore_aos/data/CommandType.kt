package com.meiloon.controlcore.main.api.enums

import com.meiloon.controlcore.global.database.entity.BluetoothEntity
import com.meiloon.controlcore.main.api.EQPara
import com.meiloon.controlcore.main.container.chart.data.EQData
import com.meiloon.controlcore.main.widget.ble.BleControlManager
import com.meiloon.controlcore.widget.app.developer.Log

sealed class CommandType {
    object GetChipID : CommandType()
    object GetAudioChipID : CommandType()
    object GetFirmwareVer : CommandType()
    object GetVolume : CommandType()
    object GetRoomCorrectionMode : CommandType()
    object GetBTPairing : CommandType()
    object StartBTPairing : CommandType()
    object GetAudioChipNumbers : CommandType()
    object GetAudioSampleRate : CommandType()
    object GetAudioChannel : CommandType()
    object GetAudioBand : CommandType()
    object GetEQRange : CommandType()
    object GetAllEQPara : CommandType()
    object GetEQGroup : CommandType()
    object GetEQEngine : CommandType()
    object GetEQMode: CommandType()
    object GetBattery: CommandType()
    object GetMute: CommandType()
    object SaveEQPara: CommandType()
    data class GetEQPara(val chipIndex: Int? = null,
                         val channel: Int? = null,
                         val band: Int? = null): CommandType()
    data class GetChannelEQPara(val chipIndex: Int? = null,
                                val channel: Int? = null) : CommandType()
    data class SetBTDeviceName(val deviceName: String? = null) : CommandType()
    data class SetVolume(val volume: Int? = null,
                         val device: BluetoothEntity? = null,
                         val deviceStatus: DeviceStatus? = null) : CommandType()
    data class SetLastVolume(val volume: Int? = null,
                             val device: BluetoothEntity? = null,
                             val deviceStatus: DeviceStatus? = null) : CommandType()
    data class SetSPKMute(val isOn: Boolean? = null) : CommandType()
    data class SetRoomCorrectionMode(val mode: Int? = null) : CommandType()
    data class SetAllEQPara(val chipIndex: Int? = null,
                            val channelIndex: Int? = null,
                            val eqData: List<EQData>? = null) : CommandType()
    data class SetEQPara(val chipIndex: Int? = null,
                         val channelIndex: Int? = null,
                         val eqData: EQData? = null) : CommandType()
    data class SetEQGroup(val isOn: Boolean? = null) : CommandType()
    data class SetEQEngine(val isOn: Boolean? = null) : CommandType()
    data class SetHFEQ(val isOn: Boolean? = null) : CommandType()
    data class SetDeskEQ(val isOn: Boolean? = null) : CommandType()
    data class SetLFEQ(val isOn: Boolean? = null) : CommandType()

    companion object {
        fun send(address: String, commandType: CommandType) {
            val ble = BleControlManager.getInstance()

            when (commandType) {
                GetChipID -> ble.getChipID(address)
                GetAudioChipID -> ble.getAudioChipID(address)
                GetFirmwareVer -> ble.getFirmwareVer(address)
                GetBattery -> ble.getBattery(address)
                GetVolume -> ble.getVolume(address)
                GetEQMode -> ble.getEQMode(address)
                GetBTPairing -> ble.getBTPairing(address)
                StartBTPairing -> ble.startBTPairing(address)
                GetRoomCorrectionMode -> ble.getRoomCorrectionMode(address)
                GetMute -> ble.getSPKMute(address)
                GetAudioChipNumbers -> ble.getAudioChipNumbers(address)
                GetAudioSampleRate -> ble.getAudioSampleRate(address)
                GetAudioChannel -> ble.getAudioChannel(address)
                GetAudioBand -> ble.getAudioBand(address)
                GetEQRange -> ble.getEQRange(address)
                GetAllEQPara -> ble.getAllEQPara(address)
                GetEQEngine -> ble.getEQEngine(address)
                GetEQGroup -> ble.getEQGroup(address)
                SaveEQPara -> ble.saveEQPara(address)

                is GetEQPara -> ble.getEQPara(address, commandType.chipIndex ?: 0, commandType.channel ?: 0, commandType.band ?:0)
                is GetChannelEQPara -> ble.getChannelEQPara(address, commandType.chipIndex ?: 0, commandType.channel ?: 0)
                is SetBTDeviceName -> ble.setBtDeviceName(address, commandType.deviceName)
                is SetSPKMute -> ble.setSPKMute(address, commandType.isOn)
                is SetRoomCorrectionMode -> ble.setRoomCorrectionMode(address, commandType.mode ?: 0)
                is SetVolume-> {
                    if (commandType.device?.isSPA101 ?: false) {
                        when (commandType.deviceStatus ?: DeviceStatus.UNKNOWN) {
                            DeviceStatus.BT_SOURCE -> ble.setBTVolume(address, commandType.volume ?: 0)
                            DeviceStatus.UAC_SOURCE -> ble.setUACVolume(address, commandType.volume ?: 0)
                            else -> { Log.d("CommandType", "Unknown source") }
                        }
                    } else {
                        ble.setVolume(address, commandType.volume ?: 0)
                    }
                }
                is SetLastVolume -> {
                    if (commandType.device?.isSPA101 ?: false) {
                        when (commandType.deviceStatus ?: DeviceStatus.UNKNOWN) {
                            DeviceStatus.BT_SOURCE -> ble.setBTVolume(address, commandType.volume ?: 0)
                            DeviceStatus.UAC_SOURCE -> ble.setUACVolume(address, commandType.volume ?: 0)
                            else -> { Log.d("CommandType", "Unknown source") }
                        }
                    } else {
                        ble.setLastVolume(address, commandType.volume ?: 0)
                    }
                }
                is SetDeskEQ -> ble.setDeskEQ(address, commandType.isOn ?: false)
                is SetLFEQ -> ble.setLFEQ(address, commandType.isOn ?: false)
                is SetHFEQ -> ble.setHFEQ(address, commandType.isOn ?: false)
                is SetAllEQPara -> ble.sendAllEQPara(address,
                                                        commandType.chipIndex ?: 1,
                                                        commandType.channelIndex ?: 1,
                                                        commandType.eqData)
                is SetEQEngine -> ble.setEQEngine(address, commandType.isOn ?: false)
                is SetEQGroup -> ble.setEQGroup(address, commandType.isOn ?: false)
                is SetEQPara -> ble.sendEQPara(address,
                                                commandType.chipIndex ?: 0,
                                                commandType.channelIndex ?: 0,
                                                commandType.eqData)
            }
        }

        fun setValue(commandType: CommandType, value: Any? = null): CommandType? {
            if (value == null) return commandType

            when (commandType) {
                is SetAllEQPara -> {
                    val list = value as? List<*>
                    val eqParas = list?.filterIsInstance<EQPara>() ?: return null

                    var temp = emptyList<EQData>()

                    for (eqPara in eqParas) {
                        temp = temp + eqPara.toEQData()
                    }

                    return SetAllEQPara(eqData = temp)
                }
                is SetBTDeviceName -> return SetBTDeviceName((value as String))
                is SetDeskEQ -> return SetDeskEQ((value as Boolean))
                is SetEQEngine -> return SetEQEngine((value as Boolean))
                is SetEQGroup -> return SetEQGroup((value as Boolean))
                is SetEQPara -> return value as? SetEQPara
                is SetHFEQ -> return SetHFEQ((value as Boolean))
                is SetLFEQ -> return SetLFEQ((value as Boolean))
                is SetLastVolume -> return value as? SetLastVolume
                is SetRoomCorrectionMode -> return SetRoomCorrectionMode((value as Int))
                is SetSPKMute -> return SetSPKMute((value as Boolean))
                is SetVolume -> return SetVolume((value as Int))
                is GetEQPara -> return value as? GetEQPara
                is GetChannelEQPara -> return value as? GetChannelEQPara
                else -> {
                    Log.d("CommandType setValue", "setValue未設定")
                    return null
                }
            }
        }
    }
}