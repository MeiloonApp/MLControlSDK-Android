package com.meiloon.mlcontrolcore_aos.data

import androidx.lifecycle.MutableLiveData
import com.meiloon.controlcore.main.api.PreEQMode
import com.meiloon.controlcore.main.api.enums.SPKMuteStatus
import com.meiloon.controlcore.main.widget.ble.BleControlManager

data class BottomSheet(
    var name: String = "",
    var firmwareVer: String = "",
    var volume: String = "",
    var battery: String = "",
    var chipNumbers: String = "",
    var chipID: String = "",
    var pID: String = "",
    var eqMode: String = "",
    var mute: String = "",
    var roomCorrection: String = ""
) {
    fun isEmpty() : Boolean {
        return name.isEmpty() && firmwareVer.isEmpty() && volume.isEmpty() && battery.isEmpty() &&
                chipNumbers.isEmpty() && chipID.isEmpty() && pID.isEmpty() && eqMode.isEmpty() &&
                mute.isEmpty() && roomCorrection.isEmpty()
    }
}