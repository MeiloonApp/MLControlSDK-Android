package com.meiloon.mlcontrolcore_aos.fragment.control

import android.content.Context
import android.util.Log
import com.meiloon.controlcore.global.database.repository.DeviceRepository
import com.meiloon.controlcore.main.api.APIData
import com.meiloon.controlcore.main.api.BTPairing
import com.meiloon.controlcore.main.api.ChipID
import com.meiloon.controlcore.main.api.CmdDone
import com.meiloon.controlcore.main.api.EQMode
import com.meiloon.controlcore.main.api.FirmwareVer
import com.meiloon.controlcore.main.api.RoomCorrectionMode
import com.meiloon.controlcore.main.api.SPKMute
import com.meiloon.controlcore.main.api.Status
import com.meiloon.controlcore.main.api.Volume
import com.meiloon.controlcore.main.api.enums.APIMethod
import com.meiloon.controlcore.main.api.enums.DeviceStatus
import com.meiloon.controlcore.main.api.enums.PairingStatus
import com.meiloon.controlcore.main.api.enums.SPKMuteStatus
import com.meiloon.mlcontrolcore_aos.adapter.LogAdapter
import com.meiloon.mlcontrolcore_aos.base.BaseViewModel
import com.meiloon.mlcontrolcore_aos.data.BottomSheet
import com.meiloon.mlcontrolcore_aos.data.ConnectedDeviceInfo
import com.meiloon.mlcontrolcore_aos.util.LogManager

class ControlViewModel(private val repository: DeviceRepository) : BaseViewModel() {
    var connectedDeviceInfo: ConnectedDeviceInfo = ConnectedDeviceInfo()
    val logAdapter: LogAdapter = LogAdapter()

    fun initCDeviceInfo(selectedResult: com.polidea.rxandroidble3.scan.ScanResult?,
                        bottomSheet: BottomSheet?) {
        connectedDeviceInfo.updateFrom(selectedResult, bottomSheet)
    }

    fun parseReceiveCommand(command: ByteArray?, context: Context?, cmdDoneNotify: (CmdDone) -> Unit) {
        val apiData = APIData(command)
        val method: APIMethod = apiData.method
        Log.e("ControlViewModel", "收到指令: $method")

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
            APIMethod.SPKMute -> {
                val status = apiData.getData(SPKMute::class.java).get()
                connectedDeviceInfo.isMuteOn.value = (status == SPKMuteStatus.MUTE)
                addResponseLog("SPKMute", "狀態: ${status.name}")
            }
            APIMethod.Status -> {
                val status: Status = apiData.getData(Status::class.java)
                if (connectedDeviceInfo.isSPA101) {
                    val deviceStatus: DeviceStatus = status.get()
                    connectedDeviceInfo.deviceStatus.value = deviceStatus
                    Log.d("設備狀態: ",  deviceStatus.name)
                    if (deviceStatus == DeviceStatus.BT_SOURCE) {
                        addResponseLog("Status", "BT狀態: ${deviceStatus.name}")
//                        binding.tvVolumeSource.setText(getString(R.string.control_volume_source_bt))
//                        binding.numberPicker.setMinMax(0, 15)
//                        viewModel.send("GetBTVolume")
                    } else if (deviceStatus == DeviceStatus.UAC_SOURCE) {
                        addResponseLog("Status", "UAC狀態: ${deviceStatus.name}")
//                        binding.tvVolumeSource.setText(getString(R.string.control_volume_source_uac))
//                        binding.numberPicker.setMinMax(0, 100)
//                        viewModel.send("GetUACVolume")
//                        viewModel.send("GetUACMute")
                    }
                }
            }
            APIMethod.CmdDone -> {
                val cmdDone = apiData.getData(CmdDone::class.java)
                context?.let { conetext ->
                    val result = cmdDone.getCmdMessage(conetext)
                    when (cmdDone.cmd) {
                        "SetLFEQOn" -> { addCMDLog("SetLFEQOn", result) }
                        "SetLFEQOff" -> { addCMDLog("SetLFEQOff", result) }
                        "SetHFEQOn" -> { addCMDLog("SetHFEQOn", result) }
                        "SetHFEQOff" -> { addCMDLog("SetHFEQOff", result) }
                        "SetDeskEQOn" -> { addCMDLog("SetDeskEQOn", result) }
                        "SetDeskEQOff" -> { addCMDLog("SetDeskEQOff", result) }
                        "SetMuteOff" -> { addCMDLog("SetMuteOff", result) }
                        "SetMuteOn" -> { addCMDLog("SetMuteOn", result) }
                        "SetVolume" -> addCMDLog("SetVolume", result)
                        "SetRoomCorrectionMode" -> addCMDLog("SetRoomCorrectionMode", result)
                        "StartBTPairing" -> addCMDLog("StartBTPairing", result)
                        else -> {
                            addCMDLog(cmdDone.cmd, result)
                            cmdDoneNotify(cmdDone)
                        }
                    }
                }
            }
            else -> {}
        }
    }

    private fun addResponseLog(method: String, data: String = "", result: String? = null) {
        LogManager.addResponseLog(method, data, result)
    }

    private fun addCMDLog(type: String, result: String) {
        LogManager.addCMDLog(type, result)
    }

}
