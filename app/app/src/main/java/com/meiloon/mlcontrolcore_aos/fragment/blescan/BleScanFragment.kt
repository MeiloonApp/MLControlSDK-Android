package com.meiloon.mlcontrolcore_aos.fragment.blescan

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.meiloon.mlcontrolcore_aos.R
import com.meiloon.mlcontrolcore_aos.activity.MainActivity
import com.meiloon.mlcontrolcore_aos.data.BleConnectStatus
import com.meiloon.mlcontrolcore_aos.data.BottomSheet
import com.meiloon.mlcontrolcore_aos.databinding.FragmentBleScanBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.meiloon.controlcore.broadcast.BluetoothStateReceiver
import com.meiloon.controlcore.global.activity.GlobalViewModel
import com.meiloon.controlcore.global.database.entity.BluetoothEntity
import com.meiloon.controlcore.main.api.APIData
import com.meiloon.controlcore.main.api.AudioChipNumbers
import com.meiloon.controlcore.main.api.Battery
import com.meiloon.controlcore.main.api.ChipID
import com.meiloon.controlcore.main.api.CmdDone
import com.meiloon.controlcore.main.api.EQParas
import com.meiloon.controlcore.main.api.FirmwareVer
import com.meiloon.controlcore.main.api.MqttInfo
import com.meiloon.controlcore.main.api.RoomCorrectionMode
import com.meiloon.controlcore.main.api.Volume
import com.meiloon.controlcore.main.api.bluetooth.BTConfig
import com.meiloon.controlcore.main.api.enums.APIMethod
import com.meiloon.controlcore.main.container.chart.data.EQData
import com.meiloon.controlcore.main.container.chart.widget.ChartStorage
import com.meiloon.controlcore.main.container.event.ReceiveCommandEvent
import com.meiloon.controlcore.main.factory.ViewModelFactory
import com.meiloon.controlcore.main.widget.ble.BleControlManager
import com.meiloon.controlcore.main.widget.ble.event.ConnectionResponse
import com.meiloon.mlcontrolcore_aos.fragment.BaseFragment
import com.meiloon.controlcore.widget.app.ble.BleManager
import com.meiloon.controlcore.widget.app.listener.click.OnAppClickListener
import com.meiloon.controlcore.widget.app.method.Method
import com.meiloon.controlcore.widget.app.rxjava.observer.CompletableObserver
import com.meiloon.controlcore.widget.app.rxjava.observer.SingleObserver
import com.meiloon.controlcore.widget.app.shared.SharedMethod
import com.meiloon.controlcore.widget.library.blufi.params.BlufiParameter
import com.meiloon.controlcore.widget.library.jieli.JieliManager
import com.permissionx.guolindev.callback.RequestCallback
import com.polidea.rxandroidble3.scan.ScanResult
import com.meiloon.controlcore.main.api.EQMode
import com.meiloon.controlcore.main.api.SPKMute
import com.meiloon.controlcore.main.api.enums.SPKMuteStatus
import com.meiloon.mlcontrolcore_aos.util.LogManager
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.Locale

class BleScanFragment : BaseFragment<FragmentBleScanBinding>(), OnAppClickListener {
    companion object {
        const val MAC_ADDRESS = "deviceAddress"
    }
    private val jieliManager: JieliManager = JieliManager.getInstance()
    private lateinit var viewModel: BleScanViewModel
    private var isNewConnect = false
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<CardView>

    override fun initBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentBleScanBinding {
        return FragmentBleScanBinding.inflate(getLayoutInflater())
    }

    override fun initArguments(arguments: Bundle) {

    }

    override fun oneTimeInit(context: Context) {
        resetAllNearby()
    }

    override fun initUI(context: Context) {
        val viewModelClasses = arrayOf(BleScanViewModel::class.java)
        val factory = ViewModelFactory(context, viewModelClasses) as androidx.lifecycle.ViewModelProvider.Factory
        viewModel = getViewModel(BleScanViewModel::class.java, factory)

        binding.rvDevice.setAdapter(viewModel.deviceAdapter)
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter != null) checkBleStatus(adapter.state)

        // BottomSheet
        initBottomSheet()
    }

    override fun updateUI(context: Context) {

    }

    override fun initI18n(context: Context) {

    }

    override fun initListener(context: Context) {
        BluetoothStateReceiver.setListener { status ->
            checkBleStatus(status)
        }

        binding.btScan.setOnClickListener(this)

        viewModel.deviceAdapter.setOnNearChangeListener ({ device ->
            updateNear(
                device?.first,
                device?.second as Boolean
            )
        })

        viewModel.deviceAdapter.setDeviceItemClickListener { adapter, view, i, any ->
            val device = adapter?.getItem(i) ?: return@setDeviceItemClickListener
            val address = device.address
            val scanResult = viewModel.scanResultMap[address]

            val bottomSheet = BottomSheet()
            bottomSheet.name = device.name
            bottomSheet.pID = device.pid
            updateGlobalBottomSheet(bottomSheet)

            if (viewModel.isConnected(address)) {
                showToast("裝置已經連線")
            } else {
                if (viewModel.deviceAdapter.isNearby(address)) {
                    isNewConnect = true
                    if (!Method.data.isEmpty(device.deviceUid)) {
                        showBottomSheet(true)
                    } else {
                        scanResult?.let {
                            setupConnectStatus(BleConnectStatus.CONNECTING)
                            connectDevice(it, false)
                        }
                    }
                } else if (!Method.data.isEmpty(device.deviceUid)) {
                    isNewConnect = true
                    scanResult?.let {
                        setupConnectStatus(BleConnectStatus.CONNECTING)
                        connectDevice(it, true)
                    }
                } else {
                    showToast("裝置不在附近")
                }
            }
        }
    }

    override fun initValue(savedInstanceState: Bundle?) {

    }

    override fun initLiveData(context: Context) {

        observe(GlobalViewModel.isSplashEnd(), { isEnd ->
            viewModel.isEnd = isEnd
        })

        observe(viewModel.onScanDevicesChange, { data ->
            val entitys = data ?: return@observe
            val temp = mutableListOf<BluetoothEntity>()

            for (entity in entitys) {
                val en = entity ?: return@observe
                temp.add(BluetoothEntity(en))
            }

            viewModel.deviceAdapter.replaceAllItems(temp)
        })

        observe(viewModel.onScanDevice, { scanResult ->
            if (scanResult != null) {
                if (viewModel.isMeiLoonDevice(scanResult)) {
                    val macAddress = scanResult.bleDevice.bluetoothDevice.address

                    for (old in viewModel.deviceAdapter.items) {
                        if (old?.bluetoothDevice?.address == macAddress) {
                            return@observe
                        }
                    }

                    viewModel.scanResultMap[macAddress] = scanResult
                    viewModel.deviceAdapter.markNearby(macAddress)
                    viewModel.updateScanDevices(macAddress, scanResult)
                }
            }
        })

        observe(viewModel.scanState, { isScan ->
            val scanning = isScan ?: false
            if (scanning) {
                viewModel.isEnd.let { if (it) {} }
                return@observe
            }
        })

        observe(viewModel.refreshNear, viewModel.deviceAdapter::refreshNear)

        observe(viewModel.logData) { data ->
            (activity as? MainActivity)?.logDataBridge?.value = data
        }

        viewModel.uiState.collectWithLifecycle { state -> updateScanBtnUI(state) }

        observeCurrentArgument<Any>(
            MAC_ADDRESS,
            { address ->
                val newAddress = address.toString()
                viewModel.initWhenConnected(newAddress)
            })

        observe(jieliManager.onBondStatusChange, { bondStatus ->
            Log.d("jieliManager", "onBondStatusChange")
        })

        observe(jieliManager.onConnectionChange, { response ->
            if (!Method.permission.checkConnectPermission(getAppActivity())) return@observe
            if (isNotSelectedDevice(response.bluetooth) || !isNewConnect) return@observe
            dismissProgressDialog()
            viewModel.scanResultMap.get(response.bluetooth.address)?.let {
                if (response.connected) {
                    saveMQTTDatabase(
                        BTConfig.jieLiNotifyUUID,
                        BTConfig.jieLiWriteUUID
                    )
                }
            }

        })

        observe(viewModel.onConnectBluFi, { client ->
            if (client?.isConnected == true) {
                saveMQTTDatabase(
                    BlufiParameter.UUID_NOTIFICATION_CHARACTERISTIC.toString(),
                    BlufiParameter.UUID_WRITE_CHARACTERISTIC.toString()
                )
            } else dismissProgressDialog()
        })

        observe((activity as? MainActivity)?.bottomSheet, { bottomSheet ->
            val scanResult = getGlobalSelectedResult() ?: return@observe
            val bottomSheet = bottomSheet ?: BottomSheet()
            setupConnectionStatus(scanResult, bottomSheet)
        })

        observe((activity as? MainActivity)?.selectedResult, { scanResult ->
            if (scanResult != null) {
                viewModel.updateState(ScanUiState.Connected(scanResult.bleDevice.macAddress))
            } else if (!viewModel.isScanningNow()) {
                viewModel.updateState(ScanUiState.Idle)
            }
            val bottomSheet = getGlobalBottomSheetData() ?: BottomSheet()
            setupConnectionStatus(scanResult, bottomSheet)
        })
    }

    override fun onBackPressed() {

    }

    /**
     * 重新載入裝置資料。從資料庫獲取所有已儲存的裝置，並更新其「在附近」的狀態。
     *
     * 此功能會檢索裝置列表，檢查裝置是否具有有效的 UID 但尚未標記為「在附近」，
     * 並觸發狀態更新，以確保 UI 呈現最新的裝置資訊。
     */
    override fun reloadData() {
        super.reloadData()
        subscribeSingleAuto(viewModel.getAllDevices(), SingleObserver({ devices ->
            for (device in devices) if (!Method.data.isEmpty(device.deviceUid) && !device.isNear) subscribeCompleteAuto(
                viewModel.updateNear(device.address, true)
            )
        }))
    }

    private fun initValue() {
    }

    override fun onVisibleChange(visible: Boolean) {
        if (visible) {
            val scanResult = getGlobalSelectedResult()
            val bottomSheet = getGlobalBottomSheetData() ?: BottomSheet()
            setupConnectionStatus(scanResult, bottomSheet)
        } else {
            addLog("停止掃描")
            viewModel.stopScanAction()
        }
    }

    private fun resetAllNearby() {
        Log.d("BleScanFragment", "執行清空所有裝置位置")
        subscribeCompleteAuto(viewModel.resetAllNearby())
    }

    private fun updateNear(address: String?, isNear: Boolean) {
        subscribeCompleteAuto(
            viewModel.updateNear(address, isNear),
            CompletableObserver({ this.reloadData() })
        )
    }

    private fun saveMQTTDatabase(notifyUUID: String?, writeUUID: String?) {
        val selectedResult = getGlobalSelectedResult() ?: return
        val device = BluetoothEntity(selectedResult)
        device.notifyUuid = notifyUUID!!
        device.writeUuid = writeUUID!!
        device.isNear = true
        saveDeviceToDatabase(device)
    }

    private fun saveDeviceToDatabase(device: BluetoothEntity) {
        subscribeCompleteAuto(viewModel.insertDevice(device), CompletableObserver({
            dismissProgressDialog()
            viewModel.initWhenConnected(device.address)
            setPreviousStackArgument(MAC_ADDRESS, device.address)
        }))
    }

    private fun scan() {
        if (viewModel.isScanningNow()) return

        if (Method.permission.checkBluetoothPermission(context)) {
            val text = "開始掃描(15秒 後自動停止)..."
            addLog(text)
            showToast(text)
            viewModel.startScanLoop { scanResult ->
                val name: String? = scanResult.scanRecord.deviceName
                if (!Method.data.isEmpty(name)) {
                    viewModel.onScanDevice.postValue(scanResult)
                }
            }
        } else {
            val permissions: Array<String?> = viewModel.merge(
                Method.permission.getNotificationsPermissions(),
                Method.permission.getBluetoothPermissions()
            )
            requestPermissions(permissions, RequestCallback { allGranted, grantedList, deniedList ->
                    if (Method.permission.checkBluetoothPermission(context)) scan()
                })
        }
    }

    private fun isNotSelectedDevice(device: BluetoothDevice): Boolean {
        val scanResult = getGlobalSelectedResult() ?: return false
        var edrAddress = ""
        val scanRecord = scanResult.scanRecord
        if (scanRecord != null) {
            val data = scanRecord.getManufacturerSpecificData(19533)
            val hexData = Method.encode.bytes2HexStr(data)
            if (hexData != null) {
                val edrHex = hexData.substring(10, 22)
                val formattedAddress = StringBuilder()
                var i = 0
                while (i < edrHex.length) {
                    if (i > 0) formattedAddress.append(":")
                    formattedAddress.append(edrHex, i, i + 2)
                    i += 2
                }
                edrAddress = formattedAddress.toString().uppercase(Locale.getDefault())
            }
        }
        return !(getGlobalSelectedResult()!!.bleDevice.macAddress
            .equals(device.address) ||
                edrAddress == device.address)
    }

    private fun updateLastConnectedTime(address: String?) {
        subscribeCompleteAuto(
            viewModel.updateLastConnectedTime(address),
            CompletableObserver({ Log.d("updateLastConnectedTime", address + " 最後連線時間更新成功") })
        )
    }

    private fun connectDevice(scanResult: ScanResult, isMQTT: Boolean) {
        addLog("停止掃描")
        viewModel.stopScan()
        //更新選擇的裝置
        setGlobalSelectedResult(scanResult)
        SharedMethod.isMQTT(scanResult.bleDevice.macAddress, isMQTT)

        if (viewModel.isJieLi(scanResult)) {
            // 使用 JieLi lib 連線
            if (jieliManager.connect(
                    getGlobalSelectedResult()!!.bleDevice.bluetoothDevice
                )
            ) {
                showProgressDialog()
            }
        } else if (viewModel.isESP32(scanResult)) {
            showProgressDialog()
            viewModel.connectBluFi(BluetoothEntity(scanResult))
        } else {
            // 使用原生藍芽連線
            GlobalViewModel.connect(
                scanResult.getBleDevice().getMacAddress(),
                { connection ->
                    subscribeSingleAuto(
                        viewModel.bleManager.discoverServices(connection),
                        SingleObserver({ services ->
                            dismissProgressDialog()
                            val btConfig = BTConfig(services)
                            saveMQTTDatabase(btConfig.getNotifyUUID(), btConfig.getWriteUUID())
                        })
                    )
                },
                { error -> showToast(error.message) },
                { progress ->
                    if (progress) showProgressDialog()
                    else dismissProgressDialog()
                })
        }
    }

    fun checkBleStatus(status: Int) {
        if (status == BluetoothAdapter.STATE_OFF) {
            binding.tvBleStatus.text = "藍牙未就緒"
        } else if (status == BluetoothAdapter.STATE_ON) {
            binding.tvBleStatus.text = "藍牙已開啟"
        } else if (status == BluetoothAdapter.STATE_CONNECTED ) {
            binding.tvBleStatus.text = "現在已經連線了"
        } else if (status == BluetoothAdapter.STATE_DISCONNECTED) {
            binding.tvBleStatus.text = "現在是斷線狀態"
        }
    }

    private fun updateScanBtnUI(state: ScanUiState) {
        when (state) {
            is ScanUiState.Connected -> {
                binding.btScan.text = "中斷連線"
                binding.btScan.isSelected = false
                binding.btScan.setTextColor(Method.resource.getColor(R.color.system_red))
                binding.btScan.setBackgroundColor(
                    ContextCompat.getColor(
                        binding.root.context,
                        R.color.system_pink
                    )
                )
            }
            is ScanUiState.Scanning -> {
                binding.btScan.isSelected = true
                binding.btScan.text = "停止掃描"
                binding.btScan.setTextColor(Method.resource.getColor(R.color.white))
                binding.btScan.setBackgroundColor(Method.resource.getColor(R.color.system_red))
            }
            is ScanUiState.Idle -> {
                binding.btScan.isSelected = false
                binding.btScan.text = "開始掃描"
                binding.btScan.setTextColor(Method.resource.getColor(R.color.white))
                binding.btScan.setBackgroundColor(Method.resource.getColor(R.color.system_blue))
            }
        }
    }

    private fun setupConnectStatus(connectStatus: BleConnectStatus) {
        binding.tvBleStatus.text = connectStatus.text
        binding.vwBleStatusLight.setCardBackgroundColor(ContextCompat.getColor(binding.root.context, connectStatus.colorResId))
    }

    private fun initBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.persistentBottomSheet)
        bottomSheetBehavior.isHideable = true
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior.isFitToContents = false

        binding.root.post {
            val titleHeight = binding.cvTitle.height
            bottomSheetBehavior.expandedOffset = titleHeight
        }

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> { }
                    BottomSheetBehavior.STATE_COLLAPSED -> { }
                    BottomSheetBehavior.STATE_HIDDEN -> { }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {

            }
        })
    }

    private fun showBottomSheet(show: Boolean) {
        bottomSheetBehavior.state = if (show) BottomSheetBehavior.STATE_EXPANDED else BottomSheetBehavior.STATE_HIDDEN
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: ConnectionResponse) {
        dismissProgressDialog()
        val address = event.bluetooth.address
        if (event.connected) {
            addLog("連線成功")

            val address: String = event.bluetooth.getAddress()

            Log.e("ConnectionResponse", "onMessageEvent連線時間更新: " + address)
            val selectedAddress = (viewModel.uiState.value as? ScanUiState.Connected)?.address
            if (address == selectedAddress && isNewConnect) {
                isNewConnect = false
                updateLastConnectedTime(address)
            }
            
            for (i in 0..<viewModel.deviceAdapter.items.size) {
                val itemAddress: String? =
                    viewModel.deviceAdapter.items.get(i).bluetoothDevice.address
                if (address == itemAddress) {
                    viewModel.deviceAdapter.notifyItemChanged(i)
                    break
                }
            }
        } else {
            addLog("結束連線")
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: ReceiveCommandEvent) {
        val apiData = APIData(event.command)
        val method: APIMethod = apiData.method
        val bottomSheet = getGlobalBottomSheetData() ?: BottomSheet()
        Log.e("BleScanFragment", "收到指令: " + method + " ,內容: " + String(apiData.getCustomData()))

        if (method == APIMethod.Status) {
            Log.d("" ,"")
        } else if (method.equals(APIMethod.FirmwareVer)) {
            apiData.getData(FirmwareVer::class.java)?.get()?.let {
                bottomSheet.firmwareVer = it
            }
        } else if (method.equals(APIMethod.Volume)) {
            apiData.getData(Volume::class.java)?.get()?.let {
                bottomSheet.volume = it.toString()
            }
        } else if (method.equals(APIMethod.Battery)) {
            apiData.getData(Battery::class.java)?.battery?.let {
                bottomSheet.battery = it
            }
        } else if (method.equals(APIMethod.ChipID)) {
            bottomSheet.chipID = apiData.getData(ChipID::class.java)
        } else if (method.equals(APIMethod.AudioChipNumbers)) {
            apiData.getData(AudioChipNumbers::class.java)?.audioChipNumber?.let {
                bottomSheet.chipNumbers = it.toString()
            }
        } else if (method.equals(APIMethod.RoomCorrectionMode)) {
            apiData.getData(RoomCorrectionMode::class.java)?.get()?.let {
                bottomSheet.roomCorrection = if (it == 1) "開啟" else "關閉"
            }
        } else if (method.equals(APIMethod.SPKMute)) {
            val status = apiData.getData(SPKMute::class.java).get()
            bottomSheet.mute = if (status == SPKMuteStatus.MUTE) "已靜音" else "正常"
        } else if (method.equals(APIMethod.EQParas)) {
            val eqParas: EQParas = apiData.getData(EQParas::class.java)
            for (eqPara in eqParas.get()) {
                val eqPoint = EQData(eqPara.band, eqPara.freq, eqPara.gain, eqPara.q, eqPara.type)
                GlobalViewModel.chartStorage.saveData(
                    eqPara.chipIndex,
                    eqPara.channel,
                    eqPara.band,
                    eqPoint
                )
            }
        } else if (method.equals(APIMethod.MQTTInfo)) {
            subscribeSingleAuto(
                viewModel.getControlDevice(event.getAddress()),
                SingleObserver({ device ->
                    if (device.deviceUid.isEmpty()) {
                        showProgressDialog()
                        val mqttInfo: MqttInfo = apiData.getData(MqttInfo::class.java)
                        subscribeCompleteAuto(
                            viewModel.updateDeviceUid(
                                event.getAddress(),
                                mqttInfo.getDeviceUid()
                            ),
                            CompletableObserver({
                                subscribeSingleAuto(
                                    viewModel.getControlDevice(event.address),
                                    SingleObserver({ device: BluetoothEntity ->
                                        this.registerMobileDevice(device)
                                    })
                                )
                            })
                        )
                    }
                })
            )
        } else if (apiData.method.equals(APIMethod.EQMode)) {
            val eqMode = apiData.getData(EQMode::class.java)
            bottomSheet.eqMode = eqMode
        } else if (apiData.method.equals(APIMethod.CmdDone)) {
            apiData.getData(CmdDone::class.java)?.let { cmdDone ->
                if (cmdDone.cmd.equals("GetEQGroup")) {
                    if (cmdDone.cmdResult == 5) showToast("設備不支援EQGroup")
                } else if (cmdDone.cmd.equals("GetEQEngine")) {
                    if (cmdDone.cmdResult == 5) showToast("設備不支援EQEngine")
                } else if (cmdDone.cmd.equals("GetMqttInfo")) {
                    if (cmdDone.cmdResult == 5) showToast("設備不支援MQTT")
                } else if (cmdDone.cmd.equals("GetBattery")) {
                    if (cmdDone.cmdResult == 5) showToast("設備不支援電量查詢")
                    bottomSheet.battery = "不支援"
                } else if (cmdDone.cmd.equals("GetMute")) {
                    if (cmdDone.cmdResult == 5) {
                        showToast("設備不支援靜音查詢")
                        bottomSheet.mute = "不支援"
                    }
                } else {
                    Method.control.run(activity) { context ->
                        val message = cmdDone.getCmdMessage(context)
                        if (message != "success") {
                            Log.d("", "指令: " + cmdDone.cmd + " 結果: " + message)
                            if (cmdDone.cmdResult != 0) {
                                showToast(cmdDone.cmd + " " + message)
                            }
                        }
                    }
                }
            }
        }

        updateGlobalBottomSheet(bottomSheet)
        setBottomSheetData(bottomSheet)
    }

    fun setBottomSheetData(data: BottomSheet) {
        val layoutBinding = binding.layoutBottomSheetContent
        val maxVolume = data.chipID?.maxVolume() ?: 0
        layoutBinding.tvDemoDeviceNameValue.text = data.name
        layoutBinding.tvDemoFirmwareValue.text = data.firmwareVer

        if (maxVolume > 0) {
            layoutBinding.tvVolumeValue.text = "${data.volume} / ${maxVolume}"
        } else {
            layoutBinding.tvVolumeValue.text = data.volume
        }

        layoutBinding.tvPowerValue.text = data.battery
        layoutBinding.tvChipIDValue.text = data.chipID?.idString() ?: ""
        layoutBinding.tvChipCountValue.text = data.chipNumbers
        layoutBinding.tvProductIDValue.text = data.pID
        layoutBinding.tvMuteValue.text = data.mute
        layoutBinding.tvRoomCorrectionValue.text = data.roomCorrection
        
        val muteColor = when (data.mute) {
            "已靜音" -> R.color.system_red
            "正常" -> R.color.system_geeen
            else -> R.color.black
        }
        layoutBinding.tvMuteValue.setTextColor(Method.resource.getColor(muteColor))
    }

    private fun registerMobileDevice(device: BluetoothEntity) {
        viewModel.registerMobileDevice(
            device.vid, device.pid, device.deviceUid, device.address,
            { response ->
                Log.d("response", "registerMobileDevice: $response")
                dismissProgressDialog()
            },
            { error ->
                Log.e("error", "registerMobileDevice: " + (error?.message ?: ""))
                dismissProgressDialog()
            })
    }

    private fun addLog(text: String) {
        viewModel.addLog(text)
    }

    override fun avoidFastClick(view: View?) {
        if (view == binding.btScan) {
            if (!Method.bluetooth.isEnable()) {
                showToast("請確認藍牙狀態")
                return
            }

            val selectedResult = getGlobalSelectedResult()
            if (selectedResult != null) {
                // 如果目前有連線裝置，點擊按鈕執行「中斷連線」
                val address = selectedResult.bleDevice.macAddress
                viewModel.stopScanAction()
                BleControlManager.getInstance().disconnect(address)
                removeGlobalBottomSheet()
                setGlobalSelectedResult(null)
            } else {
                // 沒有連線裝置時，切換「掃描/停止」狀態
                if (viewModel.isScanningNow()) {
                    viewModel.stopScanAction()
                    addLog("停止掃描")
                } else {
                    viewModel.deviceAdapter.replaceAllItems(ArrayList())
                    scan()
                }
            }
        } else {
            showToast("請確認藍牙狀態")
        }
    }

    fun setupConnectionStatus(scanResult: ScanResult?, bottomSheet: BottomSheet) {
        val status = if (scanResult != null) BleConnectStatus.CONNECTED else BleConnectStatus.DISCONNECT

        updateScanBtnUI(viewModel.uiState.value)
        setupConnectStatus(status)
        showBottomSheet(status == BleConnectStatus.CONNECTED)

        if (status == BleConnectStatus.CONNECTED) {
            setBottomSheetData(bottomSheet)
        }
    }

}