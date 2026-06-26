package com.meiloon.mlcontrolcore_aos.fragment.setting

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanResult
import android.content.Context
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.jieli.jl_bt_ota.constant.StateCode
import com.jieli.jl_bt_ota.interfaces.IBluetoothCallback
import com.jieli.jl_bt_ota.interfaces.IUpgradeCallback
import com.jieli.jl_bt_ota.model.BleScanMessage
import com.jieli.jl_bt_ota.model.base.*
import com.meiloon.controlcore.global.database.repository.DeviceRepository
import com.meiloon.mlcontrolcore_aos.adapter.BleDeviceAdapter
import com.meiloon.mlcontrolcore_aos.adapter.LogAdapter
import com.meiloon.mlcontrolcore_aos.base.BaseViewModel
import com.meiloon.mlcontrolcore_aos.data.UpdateMode
import com.meiloon.mlcontrolcore_aos.extension.collectIn
import com.meiloon.mlcontrolcore_aos.fragment.blescan.ScanUiState
import com.meiloon.mlcontrolcore_aos.ota.OTAManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class OTAViewModel(private val repository: DeviceRepository) : BaseViewModel() {
    var otaManager: OTAManager? = null
    val deviceAdapter = BleDeviceAdapter()
    val logAdapter = LogAdapter()
    private var selectedFileUri: Uri? = null
    val fileName = MutableStateFlow("尚未選擇檔案")
    val connectionStatus = MutableStateFlow("未連線")
    val otaProgress = MutableStateFlow(0)
    val isOTAing = MutableStateFlow(false)
    val deviceBankMode = MutableStateFlow(UpdateMode.UNKNOW)
    val fileBankMode = MutableStateFlow(UpdateMode.UNKNOW)
    val toastEvent = MutableSharedFlow<String>()
    val isLoading = MutableStateFlow(false)

    // Standalone Scan properties
    val onScanDevicesChange = MutableLiveData<List<ScanResult>>(emptyList())
    private val onScanDevicesMap = mutableMapOf<String, ScanResult>()
    val uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)

    private val otaBluetoothCallback = object : IBluetoothCallback {
        override fun onAdapterStatus(b: Boolean, b1: Boolean) {}
        override fun onDiscoveryStatus(b: Boolean, b1: Boolean) {}
        override fun onDiscovery(bluetoothDevice: android.bluetooth.BluetoothDevice, bleScanMessage: BleScanMessage) {}
        override fun onBleDataBlockChanged(bluetoothDevice: android.bluetooth.BluetoothDevice, i: Int, i1: Int) {}

        override fun onConnection(device: android.bluetooth.BluetoothDevice?, status: Int) {
            updateConnectionStatus(status, device?.address, "onConnection")
        }

        override fun onBtDeviceConnection(device: android.bluetooth.BluetoothDevice?, status: Int) {
            updateConnectionStatus(status, device?.address, "onBtDeviceConnection")
        }

        override fun onReceiveCommand(bluetoothDevice: android.bluetooth.BluetoothDevice?, commandBase: CommandBase<out BaseParameter, out CommonResponse>?) {}
        override fun onA2dpStatus(bluetoothDevice: android.bluetooth.BluetoothDevice, i: Int) {}
        override fun onHfpStatus(bluetoothDevice: android.bluetooth.BluetoothDevice, i: Int) {}

        override fun onMandatoryUpgrade(bluetoothDevice: android.bluetooth.BluetoothDevice) {
            addLog("提示：設備強制升級")
        }

        override fun onError(error: BaseError?) {
            addLog("SDK 錯誤: ${error?.message}")
        }
    }

    fun updateSelectedFile(context: Context, uri: Uri, name: String) {
        selectedFileUri = uri
        addLog("已選擇檔案: $name")

        fileBankMode.value = UpdateMode.UNKNOW
        fileName.value = ""

        // 檢查是否為 UFW 檔案，如果不是則發送 Toast 事件
        if (!name.endsWith(".ufw", ignoreCase = true)) {
            viewModelScope.launch {
                toastEvent.emit("請選擇有效的 .ufw 檔案")
            }
            return
        }

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()

                if (bytes.size < 32) return

                val off4 = bytes[4].toInt() and 0xFF
                val isDoubleBank = (off4 and 0x20) == 0

                fileName.value = name
                fileBankMode.value = if (isDoubleBank) UpdateMode.DOUBLE else UpdateMode.SINGLE
            }
        } catch (e: Exception) {
            addLog("檔案解析失敗: ${e.message}")
            e.printStackTrace()
        }
    }

    fun updateConnectionStatus(status: Int, address: String? = null, type: String) {
        val stateText = when (status) {
            StateCode.CONNECTION_OK -> "已連線"
            StateCode.CONNECTION_CONNECTING -> "連線中"
            StateCode.CONNECTION_DISCONNECT -> "已中斷"
            StateCode.CONNECTION_FAILED -> "連接失敗"
            else -> "未知狀態($status)"
        }

        connectionStatus.value = stateText
        addLog("狀態回報: $type $stateText")

        if (status == StateCode.CONNECTION_OK) {
            val addr = address ?: otaManager?.connectedDevice?.address

            val info = otaManager?.deviceInfo
            if (info != null) {
                deviceBankMode.value = if (info.isSupportDoubleBackup) UpdateMode.DOUBLE else UpdateMode.SINGLE
                addLog("獲得設備資訊：needBootLoader: ${info.isNeedBootLoader} address: ${info.bleAddr}")
            }

            if (addr != null) {
                viewModelScope.launch {
                    val newItems = deviceAdapter.items.map {
                        it.copy(isConnected = it.device.address == addr)
                    }
                    deviceAdapter.replaceAllItems(newItems)
                }
            }
        } else if (status == StateCode.CONNECTION_DISCONNECT || status == StateCode.CONNECTION_FAILED) {
            viewModelScope.launch {
                val newItems = deviceAdapter.items.map {
                    it.copy(isConnected = false)
                }
                deviceAdapter.replaceAllItems(newItems)
            }
            deviceBankMode.value = UpdateMode.UNKNOW
        }
    }

    fun connectDevice(address: String) {
        val bluetoothDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address)
        otaManager?.connectBluetoothDevice(bluetoothDevice)
    }

    fun disconnectDevice() {
        val device = otaManager?.connectedDevice
        otaManager?.disconnectBluetoothDevice(device)
    }

    fun startOTA(context: Context) {
        val uri = selectedFileUri ?: run {
            addLog("錯誤: 尚未選擇檔案")
            return
        }

        val device = otaManager?.connectedDevice ?: run {
            addLog("錯誤: 設備未連線")
            return
        }

        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null) {
                addLog("錯誤: 無法讀取檔案內容")
                return
            }

            addLog("準備開始 OTA (${device.address}), 檔案大小: ${bytes.size} bytes")

            otaManager?.bluetoothOption?.apply {
                firmwareFileData = bytes
            }

            otaManager?.startOTA(object : IUpgradeCallback {
                override fun onStartOTA() {
                    isOTAing.value = true
                    otaProgress.value = 0
                    addLog("OTA 已啟動")
                }

                override fun onNeedReconnect(address: String?, isPaired: Boolean) {
                    otaManager?.handleLoaderReconnect(address)
                }

                override fun onProgress(type: Int, progress: Float) {
                    val p = progress.toInt()
                    val currentProgress = otaProgress.value

                    val name = if (type == 0) "正在下載引導程序: " else "OTA升級: "
                    // 進度有更新才顯示Log
                    if (p > currentProgress) {
                        addLog("${name} 進度: $p%")
                    }

                    otaProgress.value = p
                }

                override fun onStopOTA() {
                    isOTAing.value = false
                    otaProgress.value = 100
                    addLog("OTA 成功完成")
                }

                override fun onCancelOTA() {
                    isOTAing.value = false
                    addLog("OTA 已取消")
                }

                override fun onError(error: BaseError?) {
                    isOTAing.value = false
                    addLog("OTA 發生錯誤: ${error?.message ?: "未知錯誤"}")
                }
            })

        } catch (e: Exception) {
            addLog("啟動 OTA 失敗: ${e.message}")
            e.printStackTrace()
        }
    }

    fun initOTAManager(context: Context) {
        if (otaManager != null) return

        val manager = OTAManager(context)
        otaManager = manager

        manager.scanUiState.collectIn(viewModelScope) {
            uiState.value = it
        }

        manager.isBusy.collectIn(viewModelScope) { busy ->
            isLoading.value = busy
        }

        manager.unregisterBluetoothCallback(otaBluetoothCallback)
        manager.registerBluetoothCallback(otaBluetoothCallback)
    }

    fun checkCanUpdate(): Boolean {
        if (deviceBankMode.value == UpdateMode.UNKNOW || fileBankMode.value == UpdateMode.UNKNOW) {
            return false
        } else {
            return (deviceBankMode.value == fileBankMode.value)
        }
    }

    // Standalone Scan methods
    fun isScanningNow(): Boolean = otaManager?.isScanningNow() ?: false

    fun startScanLoop(onResult: (ScanResult) -> Unit) {
        onScanDevicesMap.clear()
        otaManager?.startScan(onResult) {
            addLog("已達到 15 秒獨立掃描時限，自動停止")
        }
    }

    fun stopScanAction() {
        otaManager?.stopScan()
    }

    fun updateScanDevices(macAddress: String, scanResult: ScanResult) {
        onScanDevicesMap[macAddress] = scanResult
        onScanDevicesChange.postValue(onScanDevicesMap.values.toList())
    }

    fun merge(a: Array<String?>, b: Array<String?>): Array<String?> {
        val result = arrayOfNulls<String>(a.size + b.size)
        System.arraycopy(a, 0, result, 0, a.size)
        System.arraycopy(b, 0, result, a.size, b.size)
        return result
    }

    override fun onCleared() {
        super.onCleared()
        stopScanAction()
        otaManager?.unregisterBluetoothCallback(otaBluetoothCallback)
        otaManager?.release()
    }
}
