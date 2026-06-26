package com.meiloon.mlcontrolcore_aos.ota

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.jieli.bluetooth.utils.ParseDataUtil
import com.jieli.jl_bt_ota.constant.BluetoothConstant
import com.jieli.jl_bt_ota.constant.JL_Constant
import com.jieli.jl_bt_ota.constant.StateCode
import com.jieli.jl_bt_ota.impl.BluetoothOTAManager
import com.jieli.jl_bt_ota.model.BluetoothOTAConfigure
import com.meiloon.mlcontrolcore_aos.fragment.blescan.ScanUiState
import com.meiloon.mlcontrolcore_aos.ota.data.OTAUUIDs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * OTA獨立流程
 */
@SuppressLint("MissingPermission")
class OTAManager(val context: Context) : BluetoothOTAManager(context) {
    private var mBluetoothGatt: BluetoothGatt? = null
    private var mTargetDevice: BluetoothDevice? = null
    private var currentMtu = BluetoothConstant.BLE_MTU_MIN

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val mainHandler = Handler(Looper.getMainLooper())

    private val bleDataSender = BleDataSender(
        scope = scope,
        getMtu = { currentMtu },
        getGatt = { mBluetoothGatt },
        getWriteChar = {
            mBluetoothGatt?.getService(OTAUUIDs.SERVICE)?.getCharacteristic(OTAUUIDs.WRITE)
        }
    )

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager?.adapter
    }

    // 掃描相關
    private val _scanUiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanUiState = _scanUiState.asStateFlow()
    private var scanJob: Job? = null
    private var scanCallback: ScanCallback? = null

    // 狀態相關
    private val _isBusy = MutableStateFlow(false)
    val isBusy = _isBusy.asStateFlow()

    private var isHandshakeDone = false

    val errorReport = MutableStateFlow("")

    init {
        var config = BluetoothOTAConfigure().apply {
            priority = BluetoothOTAConfigure.PREFER_BLE
            isNeedChangeMtu = false
            isUseAuthDevice = true
            isUseReconnect = true
            timeoutMs = 20000
        }

        configure(config)
    }

    // --- Scan Implementation ---
    fun isScanningNow(): Boolean = _scanUiState.value == ScanUiState.Scanning

    @SuppressLint("MissingPermission")
    fun startScan(onResult: (ScanResult) -> Unit, onStop: () -> Unit = {}) {
        if (isScanningNow()) return

        _scanUiState.value = ScanUiState.Scanning

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                onResult(result)
            }
            override fun onScanFailed(errorCode: Int) {
                stopScan()
            }
        }
        scanCallback = callback

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        scanner?.startScan(
            null,
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            callback
        )

        // 掃描15秒自動關閉
        scanJob = scope.launch {
            delay(15000)
            if (isActive) {
                stopScan()
                onStop()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanningNow()) return

        _scanUiState.value = ScanUiState.Idle
        scanJob?.cancel()
        scanJob = null

        scanCallback?.let {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(it)
            scanCallback = null
        }
    }

    override fun connectBluetoothDevice(device: BluetoothDevice?) {
        if (device == null) return
        performConnect(device)
    }

    private fun performConnect(device: BluetoothDevice) {
        if (_isBusy.value) return
        _isBusy.value = true

        scope.launch {
            Log.d("OTAManager", "開始連線預處理邏輯: ${device.address}")

            if (isScanningNow()) {
                stopScan()
            }

            mTargetDevice = device

            closeGatt()
            delay(1000)

            onBtDeviceConnection(device, StateCode.CONNECTION_CONNECTING)

            Log.d("OTAManager", "正式向系統發起 connectGatt 調用")

            val transport: Int = BluetoothDevice.TRANSPORT_LE
            val phyMask = 1

            mBluetoothGatt = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    // Android 8.0+：傳入 6 個參數，包含 mainHandler
                    device.connectGatt(context, false, mGattCallback, transport, phyMask, mainHandler)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    // Android 6.0 - 7.1
                    device.connectGatt(context, false, mGattCallback, transport)
                }
                else -> {
                    // Android 6.0 以下
                    device.connectGatt(context, false, mGattCallback)
                }
            }
        }
    }

    /**
     * 處理 OTA 過程中的 Loader 回連
     */
    fun handleLoaderReconnect(targetAddress: String?) {
        if (targetAddress == null) return

        startScan(onResult = { result ->
            val foundAddr = result.device.address
            
            if (isLoaderAddressMatch(targetAddress, foundAddr)) {
                Log.i("OTAManager", "找到目標設備 (完整邏輯驗證通過): $foundAddr")
                stopScan()

                // 如果是地址變了，必須通知 SDK
                setReconnectAddress(foundAddr)

                scope.launch {
                    delay(500)
                    connectBluetoothDevice(result.device)
                }
            }
        })
    }

    /**
     * 輔助判斷：比對掃描到的地址是否為目標或其 Loader 偏移地址
     */
    private fun isLoaderAddressMatch(target: String, found: String): Boolean {
        if (target.equals(found, ignoreCase = true)) return true

        // 判斷 MAC 最後一位是否 +1 (十六進位)
        return try {
            val targetPrefix = target.substring(0, target.length - 2)
            val foundPrefix = found.substring(0, found.length - 2)

            if (targetPrefix.equals(foundPrefix, ignoreCase = true)) {
                val targetLast = target.substring(target.length - 2).toInt(16)
                val foundLast = found.substring(found.length - 2).toInt(16)
                // 匹配 target+1 或 target (處理環狀 0xFF->0x00 的情況可用 (targetLast + 1) and 0xFF)
                foundLast == (targetLast + 1) and 0xFF
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private fun closeGatt() {
        val gatt = mBluetoothGatt ?: return
        Log.i("OTAManager", "closeGatt: 執行斷線與關閉")
        gatt.disconnect()
        gatt.close()
        clearAllConnectionState()
        bleDataSender.clearQueue()
    }

    override fun disconnectBluetoothDevice(device: BluetoothDevice?) {
        Log.w("OTAManager", "主動發起斷線流程")
        _isBusy.value = true

        if (isScanningNow()) {
            stopScan()
        }

        // 恢復初始的 MTU 設定
        currentMtu = BluetoothConstant.BLE_MTU_MIN
        bluetoothOption.mtu = BluetoothConstant.BLE_MTU_MIN

        closeGatt()
    }

    override fun onBtDeviceConnection(device: BluetoothDevice?, status: Int) {
        super.onBtDeviceConnection(device, status)
        when (status) {
            StateCode.CONNECTION_OK,
            StateCode.CONNECTION_DISCONNECT,
            StateCode.CONNECTION_FAILED -> {
                _isBusy.value = false
            }
        }
    }

    private fun clearAllConnectionState() {
        Log.i("OTAManager", "清理設備變數並通知杰里 SDK 斷線狀態")
        isHandshakeDone = false
        onBtDeviceConnection(mTargetDevice, StateCode.CONNECTION_DISCONNECT)
        mTargetDevice = null
        mBluetoothGatt = null
    }

    override fun sendDataToDevice(device: BluetoothDevice?, data: ByteArray?): Boolean {
        if (data == null || mBluetoothGatt == null) return false
        bleDataSender.enqueue(data)
        return true
    }

    override fun getConnectedDevice(): BluetoothDevice? = mTargetDevice
    override fun getConnectedBluetoothGatt(): BluetoothGatt? = mBluetoothGatt



    private val mGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device.address
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("onConnectionStateChange", "GATT 狀態異常 (status=$status), 執行斷線")
                isHandshakeDone = false
                closeGatt()
                scope.launch {
                    onBtDeviceConnection(gatt.device, StateCode.CONNECTION_DISCONNECT)
                }
                return
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("onConnectionStateChange", "物理連線成功: $address, 開始請求 MTU")
                isHandshakeDone = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    gatt.requestMtu(509)
                } else {
                    gatt.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w("onConnectionStateChange", "物理鏈路已斷開: $address")
                isHandshakeDone = false
                closeGatt()
                scope.launch {
                    onBtDeviceConnection(gatt.device, StateCode.CONNECTION_DISCONNECT)
                    mTargetDevice = null
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d("OTAManager", "物理 MTU 變更成功: $mtu")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val maxMtu = mtu - 6
                currentMtu = maxMtu
                bluetoothOption.mtu = maxMtu
            }

            // 只有在握手尚未完成時才發起服務搜尋，避免 SDK 觸發 MTU 變更導致無限迴圈
            if (!isHandshakeDone) {
                Log.d("OTAManager", "尚未完成握手，執行服務搜尋 (discoverServices)")
                gatt.discoverServices()
            } else {
                Log.d("OTAManager", "已完成握手，僅更新 MTU 資訊")
            }
            super@OTAManager.onMtuChanged(gatt, mtu, status)

        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("OTAManager", "服務搜尋完成，正在驗證服務...")
                val service = gatt.getService(OTAUUIDs.SERVICE)
                val notifyChar = service?.getCharacteristic(OTAUUIDs.NOTIFY)

//                val writeChar = service.getCharacteristic(OTAUUIDs.WRITE)
//                // 檢查寫入特徵值
//                if (writeChar == null) {
//                    Log.e("OTAManager", "錯誤: 寫入特徵值無效！(UUID: ${OTAUUIDs.WRITE})")
//                    // 這裡可以報錯，因為沒有寫入通道無法進行 OTA
//                } else {
//                    Log.i("OTAManager", "寫入特徵值確認有效")
//                }

                if (notifyChar == null) {
                    val msg = "錯誤: 該設備不是認證的設備"
                    Log.e("OTAManager", msg)
                    errorReport.value = msg
                    disconnectBluetoothDevice(gatt.device)
                    return
                } else {
                    // 開啟通知
                    enableNotification(gatt, notifyChar)
                }
            } else {
                Log.e("OTAManager", "服務搜尋失敗: $status")
                disconnectBluetoothDevice(gatt.device)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.characteristic.uuid == OTAUUIDs.NOTIFY && status == BluetoothGatt.GATT_SUCCESS) {
                if (isHandshakeDone) return
                Log.d("OTAManager", "通知開啟成功，通道完全就緒")
                isHandshakeDone = true
                // 只有到這一步，才通知 SDK 連線完成 (CONNECTION_OK)
                onBtDeviceConnection(gatt.device, StateCode.CONNECTION_OK)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            bleDataSender.onWriteResult(status)
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("onCharacteristicWrite", "寫入失敗 status: $status")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == OTAUUIDs.NOTIFY) {
                onReceiveDeviceData(gatt.device, characteristic.value)
            }
        }
    }

    private fun enableNotification(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(char, true)
        val desc = char.getDescriptor(OTAUUIDs.DESC)
        if (desc != null) {
            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(desc)
        }
    }

    override fun startOTA(callback: com.jieli.jl_bt_ota.interfaces.IUpgradeCallback?) {
        _isBusy.value = true
        super.startOTA(object : com.jieli.jl_bt_ota.interfaces.IUpgradeCallback {
            override fun onStartOTA() {
                callback?.onStartOTA()
            }

            override fun onNeedReconnect(address: String?, isPaired: Boolean) {
                callback?.onNeedReconnect(address, isPaired)
            }

            override fun onProgress(type: Int, progress: Float) {
                callback?.onProgress(type, progress)
            }

            override fun onStopOTA() {
                _isBusy.value = false
                callback?.onStopOTA()
            }

            override fun onCancelOTA() {
                _isBusy.value = false
                callback?.onCancelOTA()
            }

            override fun onError(error: com.jieli.jl_bt_ota.model.base.BaseError?) {
                _isBusy.value = false
                callback?.onError(error)
            }
        })
    }

    override fun release() {
        Log.i("OTAManager", "release: 開始釋放資源")
        bleDataSender.release()

        if (mBluetoothGatt != null) {
            disconnectBluetoothDevice(mTargetDevice)
        } else {
            closeGatt()
        }

        super.release()

        scope.cancel()
    }
}
