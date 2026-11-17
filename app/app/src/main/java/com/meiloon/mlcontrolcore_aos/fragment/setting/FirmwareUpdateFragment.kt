package com.meiloon.mlcontrolcore_aos.fragment.setting

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import autodispose2.androidx.lifecycle.scope
import com.meiloon.controlcore.extension.collectWithLifecycle
import com.meiloon.controlcore.main.factory.ViewModelFactory
import com.meiloon.controlcore.main.widget.ble.event.ConnectionResponse
import com.meiloon.mlcontrolcore_aos.base.BaseFragment
import com.meiloon.mlcontrolcore_aos.databinding.FragmentFirmwareUpdateBinding
import com.meiloon.controlcore.extension.getFileName
import com.meiloon.mlcontrolcore_aos.data.UpdateMode
import com.meiloon.controlcore.ota.util.DeviceConnectionManager
import com.meiloon.controlcore.ota.util.RetryConfig
import com.meiloon.controlcore.util.StatusAnimationHelper
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.text.toInt

class FirmwareUpdateFragment : BaseFragment<FragmentFirmwareUpdateBinding>() {
    private lateinit var viewModel: OTAViewModel
    private var connectionManager = DeviceConnectionManager(RetryConfig(6))
    private val statusAnimationHelper by lazy {
        StatusAnimationHelper { binding.tvProgressStatus.text = it }
    }
    private val selectFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { handleFileSelection(it) }
    }

    override fun initBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentFirmwareUpdateBinding {
        return FragmentFirmwareUpdateBinding.inflate(inflater, container, false)
    }

    override fun initArguments(arguments: Bundle) {
    }

    override fun oneTimeInit(context: Context) {
    }

    override fun initUI(context: Context) {
        val viewModelClasses = arrayOf(OTAViewModel::class.java)
        val factory = ViewModelFactory(context, viewModelClasses) as ViewModelProvider.Factory
        viewModel = getViewModel(OTAViewModel::class.java, factory)

        viewModel.initOTAManager(context)

        // 如果是從已連線狀態進入，自動觸發 OTA 連線流程
        mainActivity?.selectedResult?.value?.bleDevice?.macAddress?.let { macAddress ->
            viewModel.connectDeviceOTA(macAddress)
        }

        binding.btnCancelOTA.isEnabled = false
    }

    override fun updateUI(context: Context) {
    }

    override fun initI18n(context: Context) {
    }

    override fun initListener(context: Context) {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnSelectFile.setOnClickListener {
            if (!viewModel.isOTAing.value && viewModel.otaProgress.value.progress.toInt() != 100) {
                selectFileLauncher.launch(arrayOf("*/*"))
            }
        }

        binding.cvFileSelection.setOnClickListener {
            if (!viewModel.isOTAing.value && viewModel.otaProgress.value.progress.toInt() != 100) {
                selectFileLauncher.launch(arrayOf("*/*"))
            }
        }

        binding.btnStartOTA.setOnClickListener {
            viewModel.startOTA(requireContext())
        }

        binding.btnCancelOTA.setOnClickListener {
            viewModel.cancelOTA()
            stopStatusAnimation("取消升級")
        }
    }

    private fun handleFileSelection(uri: Uri) {
        val fileName = uri.getFileName(requireContext())
        viewModel.updateSelectedFile(requireContext(), uri, fileName)
    }

    override fun initValue(savedInstanceState: Bundle?) {
    }

    override fun initLiveData(context: Context) {
        combine(
            viewModel.fileName,
            viewModel.fileBankMode,
            viewModel.connectionStatus,
            viewModel.deviceBankMode,
            viewModel.isOTAing
        ) { _, _, _, _, _ -> }.collectWithLifecycle(viewLifecycleOwner) {
            updateStartButtonState()
        }

        viewModel.otaProgress.collectWithLifecycle(viewLifecycleOwner) { data ->
            val progress: Int = data.progress.toInt()
            val type = data.type
            binding.progressBar.progress = progress
            binding.tvProgressPercent.text = "${progress}%"

            if (progress in 1..<100) {
                if (type == 0) {
                    startStatusAnimation("準備中")
                } else if (type == 1) {
                    startStatusAnimation("升級中")
                }
            } else if (progress == 100 && type == 1) {
                val deviceBankMode = viewModel.deviceBankMode.value
                val msg = if (deviceBankMode == UpdateMode.SINGLE) "重啟中" else "升級中"
                startStatusAnimation(msg)

                // single bank要在 100％後才能重新連線
                if (deviceBankMode == UpdateMode.SINGLE) {
                    Log.d("tryReconnect", "100%")
                    viewModel.connectDeviceBLE()
                }
            } else {
//                stopStatusAnimation("Loader 100%")
            }
        }

        viewModel.isOTAing.collectWithLifecycle(viewLifecycleOwner) { isOTAing ->
            if (isOTAing) {
                if (viewModel.otaProgress.value.progress.toInt() == 0) {
                    startStatusAnimation("準備中")
                }
            } else {
                statusAnimationHelper.stop()
                if (viewModel.otaProgress.value.progress.toInt() == 0) {
                    binding.tvProgressStatus.text = "等待中"
                }
            }
        }

        viewModel.toastEvent.collectWithLifecycle(viewLifecycleOwner) { message ->
            showToast(message)
        }
    }

    private fun updateStartButtonState() {
        val name = viewModel.fileName.value
        val hasFile = name.isNotEmpty() && name != "尚未選擇檔案"
        val isConnected = viewModel.connectionStatus.value == "已連線"
        val isOTAing = viewModel.isOTAing.value
        val updateComplete = viewModel.otaProgress.value.progress.toInt() == 100

        binding.tvFileName.text = name
        binding.tvDeviceType.text = viewModel.deviceBankMode.value.text

        // 1. 開始升級按鈕：必須有檔案、已連線、目前沒在升級 且 不是更新完成
        binding.btnStartOTA.isEnabled = hasFile && isConnected && !isOTAing && !updateComplete
    }

    override fun onVisibleChange(visible: Boolean) {
        // No scan here, so no need to stop scan unless we add it
    }

    override fun onBackPressed() {
        findNavController().popBackStack()
    }

    private fun startStatusAnimation(baseText: String) {
        statusAnimationHelper.start(baseText, viewLifecycleOwner.lifecycleScope)
    }

    private fun stopStatusAnimation(text: String = "") {
        statusAnimationHelper.stop()
        binding.tvProgressStatus.text = text
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: ConnectionResponse) {
        val mac = event.bluetooth.address
        val updateComplete = viewModel.otaProgress.value.progress.toInt() == 100

        Log.d("onMessageEvent", "設備: ${event.bluetooth.address}, 連線狀態: ${event.connected}")

        if (event.connected) {
            connectionManager.reset(mac)
            viewModel.connectDeviceOTA(mac)

            if (updateComplete) {
                val msg = if (viewModel.deviceBankMode.value == UpdateMode.SINGLE) "重啟完成" else "更新完成"
                stopStatusAnimation(msg)
            }
        } else {
            if (updateComplete) {
                if (connectionManager.incrementRetry(mac)) {
                    Log.d("tryReconnect", "嘗試重連設備 [$mac]，第 ${connectionManager.getOrCreateState(mac).count} 次")
                    viewModel.connectDeviceBLE(mac)
                } else {
                    Log.w("tryReconnect", "設備 [$mac] 已達最大重試次數")
                    viewModel.connectDeviceOTA(mac)
                }
            } else {
                stopStatusAnimation("設備已斷線")
            }
        }
    }

    override fun onDestroyView() {
        stopStatusAnimation()
        super.onDestroyView()
    }
}
