package com.meiloon.mlcontrolcore_aos.fragment.blescan

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.meiloon.controlcore.global.database.entity.BluetoothEntity
import com.meiloon.controlcore.global.database.repository.DeviceRepository
import com.meiloon.controlcore.main.widget.ble.BleControlManager
import com.meiloon.controlcore.retrofit.request.RegisterMobileDevice
import com.meiloon.controlcore.widget.app.action.Action
import com.meiloon.controlcore.widget.app.android.AppViewModel
import com.meiloon.controlcore.widget.app.ble.BleManager
import com.meiloon.controlcore.widget.app.method.Method
import com.meiloon.controlcore.widget.app.shared.SharedMethod
import com.meiloon.controlcore.widget.app.widget.blufi.AppBlufiClient
import com.meiloon.controlcore.widget.app.widget.blufi.BlufiClientManager
import com.meiloon.mlcontrolcore_aos.adapter.DeviceAdapter
import com.meiloon.mlcontrolcore_aos.util.LogManager
import com.meiloon.controlcore.main.api.enums.CommandType
import com.meiloon.controlcore.main.api.enums.CommandType.GetAllEQPara
import com.meiloon.controlcore.main.api.enums.CommandType.GetChipID
import com.meiloon.controlcore.main.api.enums.CommandType.GetVolume
import com.polidea.rxandroidble3.scan.ScanResult
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import okhttp3.ResponseBody

class BleScanViewModel(private val repository: DeviceRepository) : AppViewModel() {

    companion object {
        @Volatile
        private var instance: BleScanViewModel? = null

        fun setInstance(viewModel: BleScanViewModel) {
            instance = viewModel
        }

        fun getInstance(): BleScanViewModel? {
            return instance
        }
    }
    private val bleControlManager: BleControlManager = BleControlManager.getInstance()
    val bleManager: BleManager = BleManager.getInstance()
    val scanState: MutableLiveData<Boolean?> = MutableLiveData<Boolean?>()
    val onScanDevice: MutableLiveData<ScanResult?> = MutableLiveData<ScanResult?>()
    val scanResultMap: MutableMap<String?, ScanResult?> = HashMap<String?, ScanResult?>()
    val onScanDevicesChange: MutableLiveData<MutableList<ScanResult?>?> =
        MutableLiveData<MutableList<ScanResult?>?>(java.util.ArrayList<ScanResult?>())
    private val onScanDevicesMap =
        MutableLiveData<MutableMap<String?, ScanResult?>?>(java.util.HashMap<String?, ScanResult?>())
    val refreshNear: MutableLiveData<Long?> = MutableLiveData<Long?>()
    val onConnectBluFi: MutableLiveData<AppBlufiClient?> = MutableLiveData<AppBlufiClient?>()
    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState = _uiState.asStateFlow()
    private var scanJob: Job? = null
    private var isStopRequested = false
    val deviceAdapter: DeviceAdapter = DeviceAdapter()
    var isEnd: Boolean = false
    val logData = LogManager.logs

    init {
        setInstance(this)

        viewModelScope.launch {
            while (isActive) {
                refreshNear.postValue(System.currentTimeMillis())
                delay(2000)
            }
        }
    }

    private fun scanResultsFlow(): Flow<ScanResult> = callbackFlow {
        val disposable = bleManager.startScanUntilStopped { isScan ->
            scanState.postValue(isScan)
            // 當 Rx 層級掃描停止時，關閉 Flow 通道以通知外層 collect 結束
            if (!isScan) channel.close()
        }.subscribe({ result ->
            trySend(result)
        }, { error ->
            close(error)
        })

        awaitClose {
            disposable.dispose()
            bleManager.stopScan()
        }
    }

    fun startScanLoop(onResult: (ScanResult) -> Unit) {
        isStopRequested = false
        if (scanJob?.isActive == true) return

        scanJob = viewModelScope.launch {
            try {
                _uiState.value = ScanUiState.Scanning
                // 使用 withTimeout 限制這整個掃描區塊最多執行 15 秒
                withTimeout(15000L) {
                    while (isActive && !isStopRequested) {
                        try {
                            scanResultsFlow().collect { result ->
                                onResult(result)
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            val msg = "掃描過程發生錯誤: ${e.message}"
                            Log.e("BleScan", msg)
                            addLog(msg)
                        }

                        if (!isStopRequested) {
                            delay(1000)
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                val msg = "已達到 15 秒掃描時限，自動停止"
                Log.d("BleScan", msg)
                addLog(msg)
            } catch (e: CancellationException) {
                val msg = "掃描已取消"
                Log.d("BleScan", "掃描已取消")
                addLog(msg)
            } catch (e: Exception) {
                val msg = "掃描發生異常: ${e.message}"
                Log.e("BleScan", msg)
                addLog(msg)
            } finally {
                stopScanAction()
            }
        }
    }

    fun stopScanAction() {
        isStopRequested = true
        scanJob?.cancel()
        bleManager.stopScan()
        _uiState.value = ScanUiState.Idle
        scanState.postValue(false)
    }

    fun updateState(state: ScanUiState) {
        _uiState.value = state
    }

    fun isScanningNow(): Boolean = scanJob?.isActive == true

    fun resetAllNearby(): Completable {
        return repository.resetAllNearby()
    }

    fun getAllDevices(): Single<MutableList<BluetoothEntity>> {
        return repository.allDevices
    }

    fun updateLastConnectedTime(address: String?): Completable {
        return repository.updateLastConnectedTime(address)
    }

    fun updateNear(address: String?, isNear: Boolean): Completable {
        return repository.updateNear(address, isNear)
    }

    fun insertDevice(device: BluetoothEntity): Completable {
        return repository.insertDevice(
            device.getAddress(),
            device.getName(),
            device.getScanRecord(),
            device.getManufacturerSpecificData(),
            device.getManufacturerId(),
            device.getAliasName(),
            device.getDeviceType(),
            device.getAddTime(),
            device.getLastConnectedTime(),
            device.getServiceUuids(),
            device.getReadUuid(),
            device.getWriteUuid(),
            device.getNotifyUuid(),
            device.getIndicateUuid(),
            device.getCustomUuid(),
            device.isAutoConnect(),
            device.isNear()
        )
    }

    @Synchronized
    fun updateScanDevices(macAddress: String?, scanResult: ScanResult?) {
        val deviceMap: MutableMap<String?, ScanResult?> =
            LinkedHashMap<String?, ScanResult?>(onScanDevicesMap.getValue())
        deviceMap[macAddress] = scanResult
        onScanDevicesMap.postValue(deviceMap)
        onScanDevicesChange.postValue(ArrayList<ScanResult?>(deviceMap.values))
    }

    fun isMeiLoonDevice(scanResult: ScanResult): Boolean {
        var isMeiLoonDevice = false
        val scanRecord = scanResult.getScanRecord()
        if (scanRecord != null) {
            if (isESP32(scanResult)) return true
            val manufacturerData = scanRecord.getManufacturerSpecificData()
            for (i in 0..<manufacturerData.size()) {
                if (manufacturerData.keyAt(i) == 19533 || manufacturerData.keyAt(i) == 1494) isMeiLoonDevice =
                    true
            }
        }
        return isMeiLoonDevice
    }

    fun isESP32(scanResult: ScanResult): Boolean {
        var isESP32 = false
        val scanRecord = scanResult.getScanRecord()
        if (scanRecord != null) {
            val data = scanRecord.getManufacturerSpecificData(19533) ?: return false
            val hexData: String = Method.encode.bytes2HexStr(data)
            if (!Method.data.isEmpty(hexData)) {
                val vid = hexData.substring(0, 4)
                val pid = hexData.substring(4, 8)
                if (vid == "0003" && pid == "0001") isESP32 = true
            }
        }
        return isESP32
    }

    fun isJieLi(scanResult: ScanResult): Boolean {
        var isJieLi = false
        val scanRecord = scanResult.getScanRecord()
        if (scanRecord != null) {
            val data = scanRecord.getManufacturerSpecificData(19533)
            val hexData = Method.encode.bytes2HexStr(data)
            if (!Method.data.isEmpty(hexData)) {
                val vid = hexData.substring(0, 4)
                if (vid == "0002") isJieLi = true
            }
        }
        return isJieLi
    }

    fun isConnected(address: String): Boolean {
        val isConnected = false
        for (device in bleControlManager.connectedDevices) {
            if (device.device.address.equals(address)) return true
        }

        return isConnected
    }

    fun initWhenConnected(address: String) {
        subscribeSingle<BluetoothEntity>(getControlDevice(address), { device ->
            performInit(address, device)
        }, { error ->
            Log.w("initWhenConnected", "Device not in DB yet: ${error.localizedMessage}")
        })
    }

    private fun performInit(address: String, device: BluetoothEntity) {
        if (device.isESP32) {
            BleControlManager.getInstance().getMqttInfo(address)
        } else if (device.isHubSpeaker) {
            BleControlManager.getInstance().getSPKMute(address)
            BleControlManager.getInstance().getMicMute(address)
        } else {
            BleControlManager.getInstance().getFirmwareVer(address)
            BleControlManager.getInstance().getVolume(address)
            BleControlManager.getInstance().getBattery(address)
            BleControlManager.getInstance().getChipID(address)
            BleControlManager.getInstance().getAudioChipNumbers(address)
            BleControlManager.getInstance().getEQMode(address)
            BleControlManager.getInstance().getMute(address)
            BleControlManager.getInstance().getRoomCorrectionMode(address)
            CommandType.send(address, GetChipID)
            CommandType.send(address, GetVolume)
            CommandType.send(address, GetAllEQPara)
        }
    }

    fun updateDeviceUid(macAddress: String?, deviceUid: String?): Completable {
        return repository.updateDeviceUid(macAddress, deviceUid)
    }

    fun updateAutoConnect(macAddress: String?, isAuto: Boolean): Completable {
        return repository.updateAutoConnect(macAddress, isAuto)
    }

    fun connectBluFi(device: BluetoothEntity?) {
        BlufiClientManager.getInstance().connect(device, onConnectBluFi::postValue)
    }

    fun getControlDevice(macAddress: String): Single<BluetoothEntity> {
        return repository.getDevice(macAddress)
    }

    fun stopScan() {
        stopScanAction()
    }

    fun registerMobileDevice(
        vid: String,
        pid: String,
        deviceUID: String,
        macAddress: String,
        response: Action<String>,
        error: Action<Throwable>
    ) {
        val androidId = Method.security.getAndroidId()
        val fcmToken: String = SharedMethod.getFCMToken()
        val registerMobileDevice: RegisterMobileDevice =
            RegisterMobileDevice(vid, pid, deviceUID, macAddress, androidId, fcmToken)
        val call: retrofit2.Call<ResponseBody?>? = retrofit.getApiStores()
            .registerMobileDevice(registerMobileDevice)
        subscribeSingle(
            getSingleResponse(call),
            response::execute,
            { throwable ->
                Log.e("error", "registerMobileDevice: " + throwable.message)
                error.execute(throwable)
            })
    }

    fun removeMobileDevice(
        vid: String,
        pid: String,
        deviceUID: String,
        macAddress: String,
        response: Action<String>,
        error: Action<Throwable>
    ) {
        val androidId = Method.security.getAndroidId()
        val fcmToken: String = SharedMethod.getFCMToken()
        val registerMobileDevice: RegisterMobileDevice =
            RegisterMobileDevice(vid, pid, deviceUID, macAddress, androidId, fcmToken)
        val call: retrofit2.Call<ResponseBody?>? = retrofit.apiStores
            .removeMobileDevice(registerMobileDevice)
        subscribeSingle(
            getSingleResponse(call),
            response::execute,
            { throwable ->
                Log.e("error","removeMobileDevice: " + throwable.message)
                error.execute(throwable)
            })
    }

    fun merge(a: Array<String?>, b: Array<String?>): Array<String?> {
        val result = arrayOfNulls<String>(a.size + b.size)
        System.arraycopy(a, 0, result, 0, a.size)
        System.arraycopy(b, 0, result, a.size, b.size)
        return result
    }

    fun addLog(text: String) {
        LogManager.addLogItem(listOf(text))
    }

    fun clearLog() {
        LogManager.clearLogs()
    }
}
