package com.meiloon.mlcontrolcore_aos.ota

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Context
import android.util.Log
import com.jieli.jl_bt_ota.constant.BluetoothConstant
import com.jieli.jl_bt_ota.constant.StateCode
import com.jieli.jl_bt_ota.impl.BluetoothOTAManager
import com.jieli.jl_bt_ota.model.BluetoothOTAConfigure
import com.jieli.jl_bt_ota.model.base.BaseError
import com.meiloon.controlcore.main.api.bluetooth.BTConfig
import com.meiloon.controlcore.widget.app.ble.BleManager
import com.meiloon.controlcore.widget.library.jieli.JieliManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.rx3.await

/**
 * 杰里 OTA 管理器實作 (整合現有的BleManager)
 */
class JieliOTAManager(context: Context) : BluetoothOTAManager(context) {

    private val bleManager = BleManager.getInstance()
    private val jieliManager = JieliManager.getInstance()
    
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationJobs = mutableMapOf<String, Job>()
    
    private val writeChannel = kotlinx.coroutines.channels.Channel<ByteArray>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private var writeJob: Job? = null
    private var connectionJob: Job? = null
    private var currentMtu = BluetoothConstant.BLE_MTU_MIN
    private var activeDevice: BluetoothDevice? = null

    init {
        val config = BluetoothOTAConfigure().apply {
            priority = BluetoothOTAConfigure.PREFER_BLE
            mtu = 509
            isNeedChangeMtu = false 
            isUseAuthDevice = true 
            isUseReconnect = true 
            timeoutMs = 20000 
        }
        configure(config)
        startWriteWorker()
    }

    private fun startWriteWorker() {
        writeJob?.cancel()
        writeJob = managerScope.launch {
            for (data in writeChannel) {
                val device = activeDevice ?: continue
                try {
                    // 增加超時與異常捕捉，避免寫入失敗導致協程中斷
                    withTimeoutOrNull(1500) {
                        bleManager.writeCharacteristic(device.address, BTConfig.jieLiWriteUUID, data).await()
                    }
                    delay(5) 
                } catch (e: Exception) {
                    Log.e("JieliOTA", "數據寫入失敗 (${device.address}): ${e.message}")
                }
            }
        }
    }

    override fun connectBluetoothDevice(device: BluetoothDevice?) {
        if (device == null) return
        val address = device.address

        // Toggle 邏輯：如果點擊的是目前已連線設備，則斷線
        if (activeDevice?.address == address) {
            Log.w("JieliOTA", "=> 偵測到重複點擊，執行斷線: $address")
            disconnectBluetoothDevice(device)
            return
        }

        Log.d("JieliOTA", "=> 開始連線流程: $address")
        
        // 確保先清理舊連線
        connectionJob?.cancel()
        activeDevice = device
        onBtDeviceConnection(device, StateCode.CONNECTION_CONNECTING)
        
        connectionJob = managerScope.launch {
            try {
                // 1. 物理連接
                bleManager.connect(address, 23).asFlow().collect { connection ->
                    Log.d("JieliOTA", "1. 鏈路物理連接成功: $address")
                    
                    // 2. 請求 MTU
                    val mtu = try {
                        connection.requestMtu(509).await()
                    } catch (e: Exception) {
                        BluetoothConstant.BLE_MTU_MIN
                    }
                    currentMtu = mtu
                    withContext(Dispatchers.Main) {
                        onMtuChanged(null, mtu, BluetoothGatt.GATT_SUCCESS)
                    }

                    // 3. 關鍵：檢查服務是否存在 (避免非杰里設備導致崩潰)
                    val services = connection.discoverServices().await()
                    val hasJieliService = services.bluetoothGattServices.any { s -> 
                        s.characteristics.any { c -> c.uuid.toString().equals(BTConfig.jieLiNotifyUUID, ignoreCase = true) }
                    }

                    if (!hasJieliService) {
                        Log.e("JieliOTA", "錯誤: 該設備不具備杰里服務特徵值")
                        withContext(Dispatchers.Main) {
                            onBtDeviceConnection(device, StateCode.CONNECTION_FAILED)
                        }
                        return@collect
                    }

                    // 4. 開啟通知
                    startNotificationListen(device)

                    // 5. 通道就緒，觸發 SDK 交握
                    withContext(Dispatchers.Main) {
                        Log.d("JieliOTA", "3. 通道完全就緒，啟動 SDK 握手")
                        onBtDeviceConnection(device, StateCode.CONNECTION_OK)
                    }
                    
                    suspendCancellableCoroutine<Unit> { }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e("JieliOTA", "連線異常中斷: ${e.message}")
                    withContext(Dispatchers.Main) {
                        onBtDeviceConnection(device, StateCode.CONNECTION_DISCONNECT)
                    }
                }
            } finally {
                if (activeDevice?.address == address) {
                    activeDevice = null
                }
            }
        }
    }

    private fun startNotificationListen(device: BluetoothDevice) {
        val address = device.address
        notificationJobs[address]?.cancel()
        
        notificationJobs[address] = bleManager.setupNotification(address, BTConfig.jieLiNotifyUUID)
            .asFlow()
            .catch { e -> 
                Log.e("JieliOTA", "通知監聽啟動失敗: ${e.message}")
            }
            .onEach { data -> 
                onReceiveDeviceData(device, data) 
            }
            .launchIn(managerScope)
    }

    fun currentMtu(): Int {
        return currentMtu
    }

    override fun sendDataToDevice(device: BluetoothDevice?, data: ByteArray?): Boolean {
        if (device == null || data == null) return false
        // 檢查是否為當前活躍設備，避免發送數據到已斷線的設備
        if (device.address != activeDevice?.address) return false
        return writeChannel.trySend(data).isSuccess
    }

    override fun getConnectedDevice(): BluetoothDevice? = activeDevice ?: jieliManager.usingDevice
    override fun getConnectedBluetoothGatt(): BluetoothGatt? = null
    override fun getCommunicationMtu(device: BluetoothDevice?): Int = currentMtu
    override fun getReceiveMtu(device: BluetoothDevice?): Int = currentMtu
    
    override fun errorEventCallback(error: BaseError?) {
        Log.e("JieliOTA", "SDK 內部錯誤: $error")
    }

    override fun disconnectBluetoothDevice(device: BluetoothDevice?) {
        val address = device?.address ?: activeDevice?.address ?: return
        Log.w("JieliOTA", "執行斷線流程: $address")
        
        connectionJob?.cancel()
        notificationJobs[address]?.cancel()
        notificationJobs.remove(address)
        
        bleManager.disconnect(address)

        onBtDeviceConnection(device, StateCode.CONNECTION_DISCONNECT)
        activeDevice = null
    }

    override fun release() {
        super.release()
        connectionJob?.cancel()
        writeJob?.cancel()
        managerScope.cancel()
        activeDevice = null
    }
}
