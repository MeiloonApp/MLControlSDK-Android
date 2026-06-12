package com.meiloon.mlcontrolcore_aos.data

import androidx.lifecycle.MutableLiveData
import com.meiloon.controlcore.main.api.ChipID
import com.meiloon.controlcore.main.api.EQMode
import com.meiloon.controlcore.main.api.PreEQMode
import com.meiloon.controlcore.main.api.enums.SPKMuteStatus
import com.meiloon.controlcore.main.widget.ble.BleControlManager

data class BottomSheet(
    var name: String = "",
    var firmwareVer: String = "",
    var volume: String = "",
    var battery: String = "",
    var chipNumbers: String = "",
    var chipID: ChipID? = null,
    var pID: String = "",
    var eqMode: EQMode? = null,
    var mute: String = "",
    var roomCorrection: String = ""
) {
    fun isEmpty() : Boolean {
        return name.isEmpty() && firmwareVer.isEmpty() && volume.isEmpty() && battery.isEmpty() &&
                chipNumbers.isEmpty() && chipID == null && pID.isEmpty() && eqMode == null &&
                mute.isEmpty() && roomCorrection.isEmpty()
    }
}