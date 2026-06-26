package com.meiloon.mlcontrolcore_aos.fragment.blescan

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.meiloon.controlcore.global.database.entity.BluetoothEntity
import com.meiloon.controlcore.global.database.repository.DeviceRepository
import com.meiloon.controlcore.main.widget.ble.BleControlManager
import com.meiloon.controlcore.retrofit.request.RegisterMobileDevice
import com.meiloon.controlcore.widget.app.action.Action
import com.meiloon.controlcore.widget.app.method.Method
import com.meiloon.controlcore.widget.app.shared.SharedMethod
import com.meiloon.controlcore.widget.app.widget.blufi.AppBlufiClient
import com.meiloon.controlcore.widget.app.widget.blufi.BlufiClientManager
import com.meiloon.mlcontrolcore_aos.adapter.DeviceAdapter
import com.meiloon.controlcore.main.api.enums.CommandType
import com.meiloon.controlcore.main.api.enums.CommandType.*
import com.polidea.rxandroidble3.scan.ScanResult
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.*
import okhttp3.ResponseBody

class BleScanViewModel(private val repository: DeviceRepository) : BaseScanViewModel() {

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
    val onScanDevice: MutableLiveData<ScanResult?> = MutableLiveData<ScanResult?>()
    val scanResultMap: MutableMap<String?, ScanResult?> = HashMap<String?, ScanResult?>()
    val refreshNear: MutableLiveData<Long?> = MutableLiveData<Long?>()
    val onConnectBluFi: MutableLiveData<AppBlufiClient?> = MutableLiveData<AppBlufiClient?>()
    val deviceAdapter: DeviceAdapter = DeviceAdapter()
    var isEnd: Boolean = false

    init {
        setInstance(this)

        viewModelScope.launch {
            while (isActive) {
                refreshNear.postValue(System.currentTimeMillis())
                delay(2000)
            }
        }
    }

    fun stopScan() {
        stopScanAction()
    }

    fun updateState(state: ScanUiState) {
        _uiState.value = state
    }

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
            CommandType.send(address, GetMute)
            BleControlManager.getInstance().getMicMute(address)
        } else {
            CommandType.send(address, GetFirmwareVer)
            CommandType.send(address, GetVolume)
            CommandType.send(address, GetBattery)
            CommandType.send(address, GetChipID)
            CommandType.send(address, GetAudioChipNumbers)
            CommandType.send(address, GetEQMode)
            CommandType.send(address, GetMute)
            CommandType.send(address, GetRoomCorrectionMode)
            CommandType.send(address, GetAllEQPara)
        }
    }

    fun updateDeviceUid(macAddress: String?, deviceUid: String?): Completable {
        return repository.updateDeviceUid(macAddress, deviceUid)
    }

    fun connectBluFi(device: BluetoothEntity?) {
        BlufiClientManager.getInstance().connect(device, onConnectBluFi::postValue)
    }

    fun getControlDevice(macAddress: String): Single<BluetoothEntity> {
        return repository.getDevice(macAddress)
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
}
