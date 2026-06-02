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
//    object SavePEQ: CommandType()
    data class SetBTDeviceName(val deviceName: String? = null) : CommandType()
    data class SetVolume(val volume: Int? = null, val device: BluetoothEntity? = null, val deviceStatus: DeviceStatus? = null) : CommandType()
    data class SetLastVolume(val volume: Int? = null, val device: BluetoothEntity? = null, val deviceStatus: DeviceStatus? = null) : CommandType()
    data class SetSPKMute(val isOn: Boolean? = null) : CommandType()
    data class SetRoomCorrectionMode(val mode: Int? = null) : CommandType()
    data class SetAllEQPara(val chipIndex: Int? = null, val channelIndex: Int? = null, val eqData: List<EQData>? = null) : CommandType()
    data class SetEQPara(val chipIndex: Int? = null, val channelIndex: Int? = null, val eqData: EQData? = null) : CommandType()
    data class SetEQGroup(val isOn: Boolean? = null) : CommandType()
    data class SetEQEngine(val isOn: Boolean? = null) : CommandType()
    data class SetHFEQ(val isOn: Boolean? = null) : CommandType()
    data class SetDeskEQ(val isOn: Boolean? = null) : CommandType()
    data class SetLFEQ(val isOn: Boolean? = null) : CommandType()

    companion object {
        fun send(address: String, command: CommandType) {
            val ble = BleControlManager.getInstance()

            when (command) {
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
//                SavePEQ-> ble.getEQGroup(address)

                is SetBTDeviceName -> ble.setBtDeviceName(address, command.deviceName)
                is SetSPKMute -> ble.setSPKMute(address, command.isOn)
                is SetRoomCorrectionMode -> ble.setRoomCorrectionMode(address, command.mode ?: 0)
                is SetVolume-> {
                    if (command.device?.isSPA101 ?: false) {
                        when (command.deviceStatus ?: DeviceStatus.UNKNOWN) {
                            DeviceStatus.BT_SOURCE -> ble.setBTVolume(address, command.volume ?: 0)
                            DeviceStatus.UAC_SOURCE -> ble.setUACVolume(address, command.volume ?: 0)
                            else -> { Log.d("CommandType", "Unknown source") }
                        }
                    } else {
                        ble.setVolume(address, command.volume ?: 0)
                    }
                }
                is SetLastVolume -> {
                    if (command.device?.isSPA101 ?: false) {
                        when (command.deviceStatus ?: DeviceStatus.UNKNOWN) {
                            DeviceStatus.BT_SOURCE -> ble.setBTVolume(address, command.volume ?: 0)
                            DeviceStatus.UAC_SOURCE -> ble.setUACVolume(address, command.volume ?: 0)
                            else -> { Log.d("CommandType", "Unknown source") }
                        }
                    } else {
                        ble.setLastVolume(address, command.volume ?: 0)
                    }
                }
                is SetDeskEQ -> ble.setDeskEQ(address, command.isOn ?: false)
                is SetLFEQ -> ble.setLFEQ(address, command.isOn ?: false)
                is SetHFEQ -> ble.setHFEQ(address, command.isOn ?: false)
                is SetAllEQPara -> ble.sendAllEQPara(address, command.chipIndex ?: 1, command.channelIndex ?: 1, command.eqData)
                is SetEQEngine -> ble.setEQEngine(address, command.isOn ?: false)
                is SetEQGroup -> ble.setEQGroup(address, command.isOn ?: false)
                is SetEQPara -> ble.sendEQPara(address,
                                                command.chipIndex ?: 0,
                                                command.channelIndex ?: 0,
                                                command.eqData)
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
                is SetLastVolume -> return SetLastVolume((value as Int))
                is SetRoomCorrectionMode -> return SetRoomCorrectionMode((value as Int))
                is SetSPKMute -> return SetSPKMute((value as Boolean))
                is SetVolume -> return SetVolume((value as Int))
                else -> {
                    Log.d("CommandType setValue", "setValue未設定")
                    return null
                }
            }
        }
    }
}