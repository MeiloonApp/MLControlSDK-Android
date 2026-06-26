package com.meiloon.mlcontrolcore_aos.fragment.setting

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.meiloon.controlcore.main.factory.ViewModelFactory
import com.meiloon.controlcore.widget.app.method.Method
import com.meiloon.mlcontrolcore_aos.R
import com.meiloon.mlcontrolcore_aos.adapter.BleDeviceItem
import com.meiloon.mlcontrolcore_aos.data.UpdateMode
import com.meiloon.mlcontrolcore_aos.databinding.FragmentOtaBinding
import com.meiloon.mlcontrolcore_aos.base.BaseFragment
import com.meiloon.mlcontrolcore_aos.extension.collectIn
import com.meiloon.mlcontrolcore_aos.fragment.blescan.ScanUiState
import com.meiloon.mlcontrolcore_aos.extension.getFileName
import com.meiloon.mlcontrolcore_aos.extension.getSafeName
import com.permissionx.guolindev.callback.RequestCallback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OTAFragment : BaseFragment<FragmentOtaBinding>() {
    private lateinit var viewModel: OTAViewModel
    private val selectFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { handleFileSelection(it) }
    }

    override fun initBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentOtaBinding {
        return FragmentOtaBinding.inflate(inflater, container, false)
    }

    override fun initArguments(arguments: Bundle) {
    }

    override fun oneTimeInit(context: Context) {
    }

    override fun initUI(context: Context) {
        val viewModelClasses = arrayOf(OTAViewModel::class.java)
        val factory = ViewModelFactory(context, viewModelClasses) as androidx.lifecycle.ViewModelProvider.Factory
        viewModel = getViewModel(OTAViewModel::class.java, factory)

        viewModel.initOTAManager(context)

        binding.rvDevices.layoutManager = LinearLayoutManager(context)
        binding.rvDevices.adapter = viewModel.deviceAdapter

        // Log setup using standard BottomSheet layout
        binding.layoutBottomSheetContent.rvLog.layoutManager = LinearLayoutManager(context)
        binding.layoutBottomSheetContent.rvLog.adapter = viewModel.logAdapter
    }

    override fun updateUI(context: Context) {
    }

    override fun initI18n(context: Context) {
    }

    override fun initListener(context: Context) {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnStartScan.setOnClickListener {
            scan()
        }

        binding.btnSelectFile.setOnClickListener {
            selectFileLauncher.launch(arrayOf("*/*"))
        }

        binding.layoutBottomSheetContent.tvClear.setOnClickListener {
            viewModel.clearLog()
        }

        binding.btnStartOTA.setOnClickListener {
            viewModel.startOTA(requireContext())
        }

        viewModel.deviceAdapter.onConnectClickListener = label@{ bleItem ->
            val device = bleItem.device
            val connectedAddress = viewModel.otaManager?.connectedDevice?.address

            if (device.address == connectedAddress) {
                viewModel.addLog("中斷與 ${device.getSafeName(requireContext()).replace("\n", " ")} 的連線")
                viewModel.stopScanAction()
                viewModel.disconnectDevice()
            } else {
                viewModel.stopScanAction()
                viewLifecycleOwner.lifecycleScope.launch {
                    if (connectedAddress != null) {
                        viewModel.addLog("更換連線，先中斷目前連線: $connectedAddress")
                        viewModel.disconnectDevice()
                    }

                    delay(500)
                    viewModel.addLog("開始連線到 ${device.getSafeName(requireContext()).replace("\n", " ")}")
                    viewModel.connectDevice(device.address)
                }
            }
        }

        viewModel.otaManager?.errorReport?.collectIn(lifecycleScope) { msg ->
            if (msg != "") {
                showToast(msg)
                // 重新刷新
                viewModel.otaManager?.errorReport?.value = ""
            }
        }
    }

    private fun scan() {
        if (viewModel.isScanningNow()) return

        if (Method.permission.checkBluetoothPermission(requireContext())) {
            val text = "開始掃描(15秒 後自動停止)"
            viewModel.addLog("$text...")
            showToast(text)

            viewModel.onScanDevicesChange.postValue(mutableListOf())
            
            viewModel.startScanLoop { scanResult ->
                val name: String? = scanResult.scanRecord?.deviceName
                if (!Method.data.isEmpty(name)) {
                    // 必須調用 updateScanDevices 才會觸發 onScanDevicesChange 更新列表
                    val macAddress = scanResult.device.address
                    viewModel.updateScanDevices(macAddress, scanResult)
                }
            }
        } else {
            val permissions: Array<String?> = viewModel.merge(
                Method.permission.getNotificationsPermissions(),
                Method.permission.getBluetoothPermissions()
            )
            requestPermissions(permissions, RequestCallback { allGranted, grantedList, deniedList ->
                if (Method.permission.checkBluetoothPermission(requireContext())) scan()
            })
        }
    }

    private fun handleFileSelection(uri: Uri) {
        val fileName = uri.getFileName(requireContext())
        viewModel.updateSelectedFile(requireContext(), uri, fileName)
    }

    override fun initValue(savedInstanceState: Bundle?) {
    }

    override fun onVisibleChange(visible: Boolean) {
        if (!visible) {
            viewModel.stopScanAction()
        }
    }

    override fun initLiveData(context: Context) {
        observe(viewModel.onScanDevicesChange, { results ->
            // 改用 ViewModel 維護的位址作為唯一勾勾判斷標準
            val connectedAddress = viewModel.otaManager?.connectedDevice?.address
            val devices = results.map { result ->
                val address = result.device.address
                BleDeviceItem(result.device, isConnected = address == connectedAddress)
            }
            viewModel.deviceAdapter.replaceAllItems(devices)
            
            // Auto show the list if devices found
            if (devices.isNotEmpty()) {
                binding.rvDevices.visibility = View.VISIBLE
                binding.dividerScan.visibility = View.VISIBLE
            }
        })

        observe(viewModel.logData) { data ->
            viewModel.logAdapter.replaceAllItems(data)
            binding.layoutBottomSheetContent.tvLogCount.text = data.size.toString()
            
            // 在主執行緒 (post) 執行滾動，確保 RecyclerView 完成佈局後才跳轉到最新一筆
            if (data.isNotEmpty()) {
                binding.layoutBottomSheetContent.rvLog.post {
                    binding.layoutBottomSheetContent.rvLog.scrollToPosition(0)
                }
            }
        }

        viewModel.uiState.collectWithLifecycle { state ->
            updateScanUI(state)
        }

        viewModel.connectionStatus.collectWithLifecycle { status ->
            // 解析連線狀態文字與顏色
            binding.tvStatus.text = status.substringAfter(": ").trim()
            
            val color = when {
                status.contains("已連線") -> R.color.system_green
                status.contains("連線中") -> R.color.ble_connecting
                status.contains("已中斷") || status.contains("失敗") -> R.color.system_red
                else -> R.color.tv_text_gray
            }
            binding.tvStatus.setTextColor(requireContext().getColor(color))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.fileName.collect { name ->
                binding.tvSelectedFile.text = name
            }
        }

        viewModel.toastEvent.collectWithLifecycle { message ->
            showToast(message)
        }

        viewModel.isLoading.collectWithLifecycle { isLoading ->
            mainActivity?.setLoading(isLoading)
        }

        viewModel.deviceBankMode.collectWithLifecycle { mode ->
            binding.tvDeviceInfo.text = "設備: ${mode.text}"
            reloadUpdateBtn(viewModel.checkCanUpdate())
        }

        viewModel.fileBankMode.collectWithLifecycle { mode ->
            binding.tvFileInfo.text = "檔案: ${mode.text}"
            if (mode == UpdateMode.DOUBLE) {
                binding.tvFileInfo.setTextColor(requireContext().getColor(R.color.system_red))
            } else {
                binding.tvFileInfo.setTextColor(requireContext().getColor(R.color.tv_text_gray))
            }
            reloadUpdateBtn(viewModel.checkCanUpdate())
        }

        viewModel.otaProgress.collectWithLifecycle { progress ->
            binding.tvProgressPercent.text = "$progress%"
            if (progress > 0 && progress < 100) {
                binding.tvProgressState.text = "升級中..."
            } else if (progress == 100) {
                binding.tvProgressState.text = "升級完成"
            }
        }

        viewModel.isOTAing.collectWithLifecycle { isOTAing ->
            binding.btnSelectFile.isEnabled = !isOTAing
            binding.btnStartScan.isEnabled = !isOTAing
            
            if (isOTAing) {
                binding.btnStartOTA.text = "正在升級..."
                binding.tvProgressState.text = "準備中..."
                reloadUpdateBtn(false)
            } else {
                binding.btnStartOTA.text = "開始升級"
                reloadUpdateBtn(viewModel.checkCanUpdate())
            }
        }
    }

    private fun updateScanUI(state: ScanUiState) {
        val isScanning = state is ScanUiState.Scanning
        
        binding.btnStartScan.isEnabled = !isScanning
        binding.btnStartScan.alpha = if (isScanning) 0.5f else 1.0f
    }

    private fun reloadUpdateBtn(canUpdate: Boolean) {
        if (canUpdate) {
            binding.btnStartOTA.isEnabled = true
            binding.btnStartOTA.backgroundTintList = requireContext().getColorStateList(R.color.system_blue)
            binding.btnStartOTA.setTextColor(requireContext().getColor(R.color.white))
        } else {
            binding.btnStartOTA.isEnabled = false
            binding.btnStartOTA.backgroundTintList =
                requireContext().getColorStateList(R.color.tv_background_gray)
            binding.btnStartOTA.setTextColor(requireContext().getColor(R.color.tv_text_gray))
        }
    }

    override fun onBackPressed() {
        findNavController().popBackStack()
    }
}
