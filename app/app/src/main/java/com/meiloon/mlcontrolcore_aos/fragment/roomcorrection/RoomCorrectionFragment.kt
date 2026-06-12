package com.meiloon.mlcontrolcore_aos.fragment.roomcorrection

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import java.util.Locale
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.meiloon.controlcore.main.factory.ViewModelFactory
import com.meiloon.controlcore.main.container.room.MLRoomMeasurementType
import com.meiloon.controlcore.widget.app.android.AppFragment
import com.meiloon.mlcontrolcore_aos.R
import com.meiloon.mlcontrolcore_aos.activity.MainActivity
import com.meiloon.mlcontrolcore_aos.databinding.FragmentRoomCorrectionBinding
import com.meiloon.mlcontrolcore_aos.util.requestMicPermission
import com.meiloon.controlcore.main.api.APIData
import com.meiloon.controlcore.main.api.CmdDone
import com.meiloon.controlcore.main.api.enums.APIMethod
import com.meiloon.controlcore.main.container.event.ReceiveCommandEvent
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import androidx.navigation.fragment.findNavController
import com.meiloon.controlcore.main.api.enums.CommandType

class RoomCorrectionFragment : AppFragment<FragmentRoomCorrectionBinding>() {
    private lateinit var viewModel: RoomCorrectionViewModel

    override fun initBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentRoomCorrectionBinding {
        return FragmentRoomCorrectionBinding.inflate(inflater, container, false)
    }

    override fun initArguments(arguments: Bundle) {}

    override fun oneTimeInit(context: Context) {
        viewModel.clearTempFiles(context.cacheDir)
    }

    override fun initUI(context: Context) {
        val viewModelClasses = arrayOf(RoomCorrectionViewModel::class.java)
        val factory = ViewModelFactory(context, viewModelClasses) as androidx.lifecycle.ViewModelProvider.Factory
        viewModel = androidx.lifecycle.ViewModelProvider(requireActivity(), factory)[RoomCorrectionViewModel::class.java]

        val mainActivity = activity as? MainActivity
        viewModel.initCDeviceInfo(mainActivity?.selectedResult?.value,
                                    mainActivity?.bottomSheet?.value)

        binding.layoutBottomSheetContent.rvLog.adapter = viewModel.logAdapter
        
        viewModel.updateTempFileCount(requireContext().cacheDir)
    }

    override fun updateUI(context: Context) {
        setupBottomSheet()
    }

    override fun initI18n(context: Context) {}

    override fun initListener(context: Context) {
        binding.scrollView.setupTitleAnimation(binding.tvLargeTitle)

        binding.btnStartLevelMonitor.setOnClickListener {
            requestMicPermission(R.string.permission_mic_rationale_monitoring) {
                viewModel.toggleLevelMonitoring()
            }
        }

        binding.btnRecordNF.setOnClickListener {
            requestMicPermission(R.string.permission_mic_rationale_recording) {
                viewModel.startRecord(MLRoomMeasurementType.NEAR_FIELD, requireContext().cacheDir)
            }
        }

        binding.btnRecordFF.setOnClickListener {
            requestMicPermission(R.string.permission_mic_rationale_recording) {
                viewModel.startRecord(MLRoomMeasurementType.FAR_FIELD, requireContext().cacheDir)
            }
        }

        binding.btnRemoveBand.setOnClickListener {
            viewModel.removeBand()
        }

        binding.btnAddBand.setOnClickListener {
            viewModel.addBand()
        }

        binding.layoutBottomSheetContent.tvClear.setOnClickListener {
            com.meiloon.mlcontrolcore_aos.fragment.blescan.BleScanViewModel.getInstance()
                ?.clearLog()
            (activity as? MainActivity)?.logDataBridge?.postValue(emptyList())
            viewModel.logAdapter.replaceAllItems(emptyList())
            binding.layoutBottomSheetContent.tvLogCount.text = "0"
        }

        binding.btnClearTempFiles.setOnClickListener {
            viewModel.clearTempFiles(requireContext().cacheDir)
        }

        binding.btnAnalyze.setOnClickListener {
            viewModel.analyze(
                binding.swSmartGain.isChecked,
                binding.swHouseCurve.isChecked,
                binding.swDeepNull.isChecked,
                onResult = { _, message ->
                    viewModel.addLogItem(listOf(message ?: ""), addTime = true)
                },
                showAlert = { message ->
                    showToast(message)
                })
        }

        binding.btnViewDetails.setOnClickListener {
            findNavController().navigate(R.id.action_otherRoomCollection_to_peqResultFragment)
        }

        binding.btnWriteToDevice.setOnClickListener {
            val address = viewModel.connectedDeviceInfo.macAddress.value
            if (address.isNullOrEmpty() || viewModel.connectedDeviceInfo.selectedResult == null) {
                showToast("請先連接設備")
                return@setOnClickListener
            }

            val eqData = viewModel.analysisResult.value
            if (eqData.isNullOrEmpty()) {
                showToast("請先完成分析")
                return@setOnClickListener
            }

            showProgressDialog()
            // 先將 EQData 存入設備記憶體 (通常使用 chipIndex=1, channelIndex=1)
            viewModel.addLogItem(listOf("發送指令: [SetAllEQPara] (chipIndex = 1, channel = 1, eqData數量 = ${eqData.size})"))
            CommandType.send(address, CommandType.SetAllEQPara(chipIndex = 1, channel = 1, eqData = eqData))
        }

        binding.layoutManageTempFiles.setOnClickListener {
            val count = viewModel.tempFileList.value?.size ?: 0
            if (count > 0) {
                findNavController().navigate(R.id.action_otherRoomCollection_to_tempFileManagementFragment)
            }
        }
    }

    override fun initValue(savedInstanceState: Bundle?) {}

    override fun onVisibleChange(visible: Boolean) {}

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: ReceiveCommandEvent) {
        val selectedDeviceAddress = viewModel.connectedDeviceInfo.macAddress.value ?: ""
        if (!event.address.equals(selectedDeviceAddress)) return

        val apiData = APIData(event.command)
        if (apiData.method == APIMethod.CmdDone) {
            val cmdDone = apiData.getData(CmdDone::class.java)
            when (cmdDone.cmd) {
                "SetAllEQPara" -> {
                    if (cmdDone.cmdResult == 0) {
                        // 成功寫入記憶體後，發送儲存指令將記憶體內的資料寫入硬體
                        viewModel.addCMDLog("SetAllEQPara", "寫入成功")
                        viewModel.addLogItem(listOf("發送指令: SaveEQPara"))
                        CommandType.send(event.address, CommandType.SaveEQPara)
                    } else {
                        dismissProgressDialog()
                        viewModel.addCMDLog("SetAllEQPara", "寫入失敗")
                    }
                }
                "SaveEQPara" -> {
                    dismissProgressDialog()
                    if (cmdDone.cmdResult == 0) {
                        viewModel.addCMDLog("SaveEQPara", "寫入成功並已儲存至設備")
                    } else {
                        showToast("儲存失敗: ${cmdDone.cmdResult}")
                        viewModel.addCMDLog("SaveEQPara", "儲存失敗")
                    }
                }
            }
        }
    }

    override fun initLiveData(context: Context) {
        observe(viewModel.isRecording) { isRecording ->
            val alpha = if (isRecording) 0.5f else 1.0f
            binding.btnRecordNF.isEnabled = !isRecording
            binding.btnRecordNF.alpha = alpha
            binding.btnRecordFF.isEnabled = !isRecording
            binding.btnRecordFF.alpha = alpha
            binding.btnAnalyze.isEnabled = !isRecording
            binding.btnAnalyze.alpha = alpha
            binding.btnClearTempFiles.isEnabled = !isRecording && (viewModel.tempFileList.value?.size ?: 0) > 0

            // 如果正在錄製，也禁用頻段調整
            binding.btnAddBand.isEnabled = !isRecording && (viewModel.numBands.value ?: 0) < viewModel.bandRange.last
            binding.btnRemoveBand.isEnabled = !isRecording && (viewModel.numBands.value ?: 0) > viewModel.bandRange.first
        }

        observe(viewModel.progressText) { text ->
            binding.tvCurrentProgress.text = text
            when (text) {
                "校正音量中" -> {
                    binding.tvCurrentProgress.setBackgroundResource(R.drawable.bg_rounded_orange)
                    binding.tvCurrentProgress.setTextColor(getColor(R.color.system_orange))
                }
                "量測中 (Sweep)" -> {
                    binding.tvCurrentProgress.setBackgroundResource(R.drawable.bg_rounded_blue)
                    binding.tvCurrentProgress.setTextColor(getColor(R.color.status_blue_text))
                }
                "計算 FFT 中" -> {
                    binding.tvCurrentProgress.setBackgroundResource(R.drawable.bg_rounded_green)
                    binding.tvCurrentProgress.setTextColor(getColor(R.color.status_green_text))
                }
                else -> {
                    binding.tvCurrentProgress.setBackgroundResource(R.drawable.bg_rounded_gray)
                    binding.tvCurrentProgress.setTextColor(getColor(R.color.system_dark_gray))
                }
            }
        }

        observe(viewModel.micLevel) { level ->
            binding.tvMicLevel.text = String.format(Locale.getDefault(), "%.1f dBFS", level)
            val progress = (100 + level).toInt().coerceIn(0, 100)
            binding.pbMicLevel.progress = progress
        }

        observe(viewModel.isMonitoring) { isMonitoring ->
            binding.btnStartLevelMonitor.text = if (isMonitoring) "停止電平監測" else "開始電平監測 (達 -45dB 停)"
        }

        observe(viewModel.nfStatus) { status ->
            binding.tvStatusNF.text = status
            if (status == "錄製中...") {
                viewModel.addLogItem(listOf("NF 錄製中..."))
                binding.tvStatusNF.setTextColor(getColor(R.color.system_orange))
                binding.tvStatusNF.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            } else if (status == "NF 已就緒") {
                binding.tvStatusNF.setTextColor(getColor(R.color.system_geeen))
                val drawable = ContextCompat.getDrawable(context, R.drawable.check_circle_24px)?.apply {
                    setTint(getColor(R.color.system_geeen))
                    // 縮小一點圖示
                    setBounds(0, 0, 48, 48)
                }
                binding.tvStatusNF.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, drawable, null)
                binding.tvStatusNF.compoundDrawablePadding = 12
            } else {
                binding.tvStatusNF.setTextColor(getColor(R.color.system_dark_gray))
                binding.tvStatusNF.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            }
        }

        observe(viewModel.ffStatus) { status ->
            binding.tvStatusFF.text = status
            if (status == "錄製中...") {
                viewModel.addLogItem(listOf("FF 錄製中..."))
                binding.tvStatusFF.setTextColor(getColor(R.color.system_orange))
                binding.tvStatusFF.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            } else if (status == "FF 已就緒") {
                binding.tvStatusFF.setTextColor(getColor(R.color.system_geeen))
                val drawable = ContextCompat.getDrawable(context, R.drawable.check_circle_24px)?.apply {
                    setTint(getColor(R.color.system_geeen))
                    // 縮小一點圖示
                    setBounds(0, 0, 48, 48)
                }
                binding.tvStatusFF.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, drawable, null)
                binding.tvStatusFF.compoundDrawablePadding = 12
            } else {
                binding.tvStatusFF.setTextColor(getColor(R.color.system_dark_gray))
                binding.tvStatusFF.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            }
        }

        observe(viewModel.numBands) { count ->
            binding.tvBandsCount.text = String.format(Locale.getDefault(), "頻段數量 %d 段", count)
            
            val isAtMin = count <= viewModel.bandRange.first
            val isAtMax = count >= viewModel.bandRange.last
            
            binding.btnRemoveBand.isEnabled = !isAtMin
            binding.btnRemoveBand.alpha = if (isAtMin) 0.3f else 1.0f
            
            binding.btnAddBand.isEnabled = !isAtMax
            binding.btnAddBand.alpha = if (isAtMax) 0.3f else 1.0f
        }

        observe(viewModel.isAnalysisReady) { isReady ->
            binding.layoutSectionWrite.visibility = if (isReady) android.view.View.VISIBLE else android.view.View.GONE
            if (isReady) {
                binding.tvAnalyzeResultTitle.text = String.format(Locale.getDefault(), "已計算完成: %d 段 PEQ", viewModel.numBands.value ?: 15)
            }
        }

        observe(viewModel.tempFileList) { list ->
            val count = list.size
            binding.tvTempFileCount.text = String.format(Locale.getDefault(), "%d 個", count)
            if (count == 0) {
                binding.btnClearTempFiles.setTextColor(getColor(R.color.unSelected_gray))
                binding.btnClearTempFiles.isEnabled = false
            } else {
                binding.btnClearTempFiles.setTextColor(getColor(R.color.system_red))
                binding.btnClearTempFiles.isEnabled = true
            }
        }

        (activity as? MainActivity)?.let {
            observe(it.logDataBridge, { list ->
                val logList = list ?: emptyList()
                viewModel.logAdapter.replaceAllItems(logList)
                binding.layoutBottomSheetContent.tvLogCount.text = logList.size.toString()
            })
        }
    }

    override fun onBackPressed() {}

    private fun setupBottomSheet() {
        val bottomSheet: CardView = binding.persistentBottomSheet
        val screenHeight = resources.displayMetrics.heightPixels
        val layoutParams = bottomSheet.layoutParams
        layoutParams.height = screenHeight / 3
        bottomSheet.layoutParams = layoutParams
    }

}
