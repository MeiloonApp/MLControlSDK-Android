package com.meiloon.mlcontrolcore_aos.base

import androidx.lifecycle.Lifecycle
import androidx.viewbinding.ViewBinding
import com.meiloon.controlcore.global.activity.GlobalViewModel
import com.meiloon.controlcore.widget.app.android.AppFragment
import com.meiloon.mlcontrolcore_aos.activity.MainActivity
import com.meiloon.mlcontrolcore_aos.data.BottomSheet
import com.meiloon.mlcontrolcore_aos.extension.collectWithLifecycle
import com.polidea.rxandroidble3.scan.ScanResult
import kotlinx.coroutines.flow.Flow

abstract class BaseFragment<B : ViewBinding> : AppFragment<B>() {

    val mainActivity: MainActivity?
        get() = activity as? MainActivity

    fun updateGlobalBottomSheet(newSheet: BottomSheet) {
        val currentSheet = getGlobalBottomSheetData() ?: BottomSheet()

        currentSheet.apply {
            if (newSheet.name.isNotEmpty()) name = newSheet.name
            if (newSheet.firmwareVer.isNotEmpty()) firmwareVer = newSheet.firmwareVer
            if (newSheet.volume.isNotEmpty()) volume = newSheet.volume
            if (newSheet.battery.isNotEmpty()) battery = newSheet.battery
            if (newSheet.chipNumbers.isNotEmpty()) chipNumbers = newSheet.chipNumbers
            if (newSheet.chipID != null) chipID = newSheet.chipID
            if (newSheet.pID.isNotEmpty()) pID = newSheet.pID
            if (newSheet.eqMode != null) eqMode = newSheet.eqMode
            if (newSheet.mute.isNotEmpty()) mute = newSheet.mute
            if (newSheet.roomCorrection.isNotEmpty()) roomCorrection = newSheet.roomCorrection
        }

        setGlobalBottomSheetData(currentSheet)
    }

    fun removeGlobalBottomSheet() {
        setGlobalBottomSheetData(BottomSheet())
    }

    private fun setGlobalBottomSheetData(bottomSheet: BottomSheet) {
        mainActivity?.bottomSheet?.value = bottomSheet
    }

    protected fun getGlobalBottomSheetData(): BottomSheet? {
        return mainActivity?.bottomSheet?.value
    }

    protected fun setGlobalSelectedResult(selectedResult: ScanResult?) {
        mainActivity?.let {
            it.selectedResult.value = selectedResult
            if (selectedResult == null) {
                GlobalViewModel.chartStorage.clear()
            }
        }
    }

    protected fun getGlobalSelectedResult(): ScanResult? {
        return mainActivity?.selectedResult?.value
    }

    /**
     * Fragment 專用的簡化版，自動傳入 viewLifecycleOwner
     */
    protected fun <T> Flow<T>.collectWithLifecycle(
        minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
        action: suspend (T) -> Unit
    ) {
        collectWithLifecycle(viewLifecycleOwner, minActiveState, action)
    }
}