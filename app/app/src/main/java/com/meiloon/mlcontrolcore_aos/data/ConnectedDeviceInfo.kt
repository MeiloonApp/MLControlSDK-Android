package com.meiloon.mlcontrolcore_aos.data

import androidx.lifecycle.MutableLiveData
import com.meiloon.controlcore.global.database.entity.BluetoothEntity
import com.meiloon.controlcore.main.api.AudioChipEQRange
import com.meiloon.controlcore.main.api.ChipID
import com.meiloon.controlcore.main.api.EQMode
import com.meiloon.controlcore.main.api.EQPara
import com.meiloon.controlcore.main.api.enums.DeviceStatus
import com.meiloon.controlcore.main.api.enums.PairingStatus
import com.meiloon.controlcore.main.api.enums.SPKMuteStatus
import com.meiloon.controlcore.main.api.enums.SampleRate
import com.meiloon.controlcore.main.api.enums.UACMuteStatus
import com.meiloon.controlcore.extension.toIntOrZero
import com.polidea.rxandroidble3.scan.ScanResult
import kotlin.collections.emptyList

class ConnectedDeviceInfo(private val device: BluetoothEntity? = null) {
    var bluetoothEntity: BluetoothEntity? = device
        private set
    var selectedResult: ScanResult? = null
        set(value) {
            field = value
            macAddress.value = field?.bleDevice?.macAddress
        }
    val macAddress: MutableLiveData<String> = MutableLiveData<String>("")
    val firmwareVer: MutableLiveData<String> = MutableLiveData<String>("")
    val chipID: MutableLiveData<ChipID> = MutableLiveData<ChipID>()
    val audioChipIDs: MutableLiveData<List<String>> = MutableLiveData<List<String>>(emptyList())
    var audioChipNumber: MutableLiveData<Int> = MutableLiveData<Int>(0)
    var audioChannel: MutableLiveData<Int> = MutableLiveData<Int>(0)
    var audioSampleRates: MutableLiveData<List<SampleRate>> = MutableLiveData<List<SampleRate>>(emptyList())
    var audioBands: MutableLiveData<List<Int>> = MutableLiveData<List<Int>>(emptyList())
    var eqRanges: MutableLiveData<List<AudioChipEQRange>> =MutableLiveData<List<AudioChipEQRange>>(emptyList())
    var eqMode: MutableLiveData<EQMode> =MutableLiveData<EQMode>()
    var isMuteOn: MutableLiveData<Boolean> = MutableLiveData<Boolean>(false)
    val roomCorrectionMode: MutableLiveData<Int> = MutableLiveData<Int>(0)
    val eqParas: MutableLiveData<List<EQPara>> = MutableLiveData<List<EQPara>>(emptyList())
    val bTDeviceName: MutableLiveData<String> = MutableLiveData<String>("")
    val volume: MutableLiveData<Int> = MutableLiveData<Int>(0)
    var eqEngine: MutableLiveData<Boolean> = MutableLiveData<Boolean>(false)
    var eqGroup: MutableLiveData<Boolean> = MutableLiveData<Boolean>(false)
    val spkMuteStatus: MutableLiveData<SPKMuteStatus> = MutableLiveData<SPKMuteStatus>(SPKMuteStatus.UNKNOWN)
    val btPairingStatus: MutableLiveData<PairingStatus> = MutableLiveData<PairingStatus>(PairingStatus.UNKNOWN)
    val deviceStatus: MutableLiveData<DeviceStatus> = MutableLiveData<DeviceStatus>(DeviceStatus.UNKNOWN)
    val uacMuteStatus: MutableLiveData<UACMuteStatus> = MutableLiveData<UACMuteStatus>(UACMuteStatus.UNKNOWN)
    val phaseStatus: MutableLiveData<Boolean> = MutableLiveData<Boolean>(false)
    val preMode: MutableLiveData<Int> = MutableLiveData<Int>(0)
    val battery: MutableLiveData<Int> = MutableLiveData<Int>(0)
    val isESP32: Boolean
        get() {
            return bluetoothEntity?.isESP32 ?: false
        }
    val isAMPLY: Boolean
        get() {
            return bluetoothEntity?.isAMPLY ?: false
        }
    val isSPA101: Boolean
        get() {
            return bluetoothEntity?.isSPA101 ?: false
        }

    fun updateFrom(selectedResult: ScanResult?, bottomSheet: BottomSheet?) {
        this.selectedResult = selectedResult

        if (bottomSheet == null) return

        bottomSheet.chipID?.let {
            chipID.value = it
        }

        volume.value = bottomSheet.volume.toIntOrZero()

        bottomSheet.eqMode?.let {
            eqMode.value = it
        }

        isMuteOn.value = bottomSheet.mute == "MUTE"
        roomCorrectionMode.value = if (bottomSheet.roomCorrection == "正常") 1 else 0
        firmwareVer.value = bottomSheet.firmwareVer
    }

}