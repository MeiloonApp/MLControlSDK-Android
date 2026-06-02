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
import com.meiloon.controlcore.global.activity.GlobalViewModel.bleManager
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
import com.meiloon.controlcore.widget.app.android.AppFragment
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
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.Locale

class BleScanFragment : AppFragment<FragmentBleScanBinding>(), View.OnClickListener {
    companion object {
        const val MAC_ADDRESS = "deviceAddress"
    }
    private val jieliManager: JieliManager = JieliManager.getInstance()
    private lateinit var viewModel: BleScanViewModel
    private var isNewConnect = false
    private var isConnecting = false
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<CardView>
    private var clickAddress: String? = ""

    private var lastClickTime = 0L

    override fun onClick(v: View?) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime >= 500L) {
            lastClickTime = currentTime
            avoidFastClick(v)
        }
    }

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
        bottomSheetBehavior = BottomSheetBehavior.from(binding.persistentBottomSheet)
        bottomSheetBehavior.isHideable = true
        setupBottomSheet(false, true)
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
            setGlobalBottomSheetData(bottomSheet)

            if (viewModel.isConnected(address)) {
                showToast("裝置已經連線")
            } else {
                if (viewModel.deviceAdapter.isNearby(address)) {
                    clickAddress = address
                    if (!Method.data.isEmpty(device.deviceUid)) {
                        setupBottomSheet(true, false)
                    } else {
                        scanResult?.let {
                            setupConnectStatus(BleConnectStatus.CONNECTING)
                            connectDevice(it, false)
                        }
                    }
                } else if (!Method.data.isEmpty(device.deviceUid)) {
                    clickAddress = address
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
//                for (BluetoothEntity bluetoothEntity : deviceAdapter.getItems()) {
//                    Log.e("目前列表裝置: " + bluetoothEntity.getBluetoothDevice().getAddress());
//                }
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
            viewModel.isScanning = isScan!!
            if (isScan) {
                viewModel.isScanScheduled = false
                return@observe
            }

            if (!viewModel.isScanScheduled && !viewModel.isStopScan) {
                viewModel.isScanScheduled = true

                post({
                    viewModel.isScanScheduled = false
                    if (!isFragmentVisible()) return@post
                    scan()
                }, 1000)
            }
        })

        observe(viewModel.refreshNear, viewModel.deviceAdapter::refreshNear)

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
            if (!viewModel.isBonding) dismissProgressDialog()
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
            val bottomSheet = getGlobalBottomSheetData() ?: BottomSheet()
            setupConnectionStatus(scanResult, bottomSheet)
        })
    }

    override fun onBackPressed() {

    }

    override fun reloadData() {
        super.reloadData()
        subscribeSingleAuto(viewModel.getAllDevices(), SingleObserver({ devices ->
            for (device in devices) if (!Method.data.isEmpty(device.getDeviceUid()) && !device.isNear()) subscribeCompleteAuto(
                viewModel.updateNear(device.getAddress(), true)
            )
        }))
    }

    private fun initValue() {
        isNewConnect = false
    }

    override fun onVisibleChange(visible: Boolean) {
        if (visible) {
            val scanResult = getGlobalSelectedResult() ?: return
            val bottomSheet = getGlobalBottomSheetData() ?: BottomSheet()
            setupConnectionStatus(scanResult, bottomSheet)
            setupScanBtn(false)
        } else {
            addLog("停止掃描")
            viewModel.stopScan()
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
        device.setNotifyUuid(notifyUUID!!)
        device.setWriteUuid(writeUUID!!)
        device.setNear(true)
        saveDeviceToDatabase(device, notifyUUID, writeUUID)
    }

    private fun saveDeviceToDatabase(
        device: BluetoothEntity,
        notifyUUID: String?,
        writeUUID: String?
    ) {
        subscribeCompleteAuto(viewModel.insertDevice(device), CompletableObserver({
            initValue()
            dismissProgressDialog()
            viewModel.initWhenConnected(device.address)
            setPreviousStackArgument(MAC_ADDRESS, device.address)
        }))
    }

    private fun scan() {
        if (viewModel.isScanning || viewModel.isStopScan) return

        if (Method.permission.checkBluetoothPermission(getContext())) {
            viewModel.isScanning = true
            addLog("開始掃描...")
            subscribeObservableAuto(viewModel.startScanUntilStopped(), { scanResult ->
                val name: kotlin.String? = scanResult.getScanRecord().getDeviceName()
                if (!Method.data.isEmpty(name)) {
//                    Log.d("Device ScanResult: " + name + "\n" + scanResult.getBleDevice().getMacAddress());
                    setupScanBtn(true)
                    viewModel.onScanDevice.postValue(scanResult)
                }
            })
        } else {
            val permissions: Array<kotlin.String?> = viewModel.merge(
                Method.permission.getNotificationsPermissions(),
                Method.permission.getBluetoothPermissions()
            )
            requestPermissions(permissions, RequestCallback { allGranted, grantedList, deniedList ->
                    if (Method.permission.checkBluetoothPermission(getContext())) scan()
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
        isConnecting = true
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
                isNewConnect = true
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

    private fun setupScanBtn(scanning: Boolean) {
        if (getGlobalSelectedResult() != null)  {
            binding.btScan.text = "中斷連線"
            binding.btScan.setTextColor(Method.resource.getColor(R.color.system_red))
            binding.btScan.setBackgroundColor(
                ContextCompat.getColor(
                    binding.root.context,
                    R.color.system_pink
                )
            )
        } else {
            binding.btScan.setTextColor(Method.resource.getColor(R.color.white))

            if (scanning) {
                binding.btScan.text = "停止掃描"
                binding.btScan.setBackgroundColor(Method.resource.getColor(R.color.system_red))
            } else {
                binding.btScan.text = "開始掃描"
                binding.btScan.setBackgroundColor(Method.resource.getColor(R.color.system_blue))
            }
        }
    }

    private fun setupConnectStatus(connectStatus: BleConnectStatus) {
        binding.tvBleStatus.text = connectStatus.text
        binding.vwBleStatusLight.setCardBackgroundColor(ContextCompat.getColor(binding.root.context, connectStatus.colorResId))
    }

    private fun setupBottomSheet(show: Boolean, isInit: Boolean = false) {
        bottomSheetBehavior.state = if (show) BottomSheetBehavior.STATE_EXPANDED else BottomSheetBehavior.STATE_HIDDEN

        // 初始化設定
        if (!isInit) return

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

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: ConnectionResponse) {
        isConnecting = false
        dismissProgressDialog()
        val address = event.bluetooth.address
        if (event.connected) {
            addLog("連線成功")

            val address: String = event.bluetooth.getAddress()

            Log.e("ConnectionResponse", "onMessageEvent連線時間更新: " + address)
            if (address == clickAddress) {
                clickAddress = ""
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
        val method: APIMethod = apiData.getMethod()
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
            apiData.getData(ChipID::class.java)?.id()?.let {
                bottomSheet.chipID = it.toString()
            }
        } else if (method.equals(APIMethod.AudioChipNumbers)) {
            apiData.getData(AudioChipNumbers::class.java)?.audioChipNumber?.let {
                bottomSheet.chipNumbers = it.toString()
            }
        } else if (method.equals(APIMethod.RoomCorrectionMode)) {
            apiData.getData(RoomCorrectionMode::class.java)?.get()?.let {
                bottomSheet.roomCorrection = if (it == 1) "正常" else "不支援"
            }
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
        } else if (apiData.getMethod().equals(APIMethod.CmdDone)) {
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
                    if (cmdDone.cmdResult == 5) showToast("設備不支援電量查詢")
                    bottomSheet.mute = "不支援"
                } else {
                    Method.control.run(getAppActivity()) { context ->
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

        setGlobalBottomSheetData(bottomSheet)
        setBottomSheetValue(bottomSheet)
    }

    fun setBottomSheetValue(data: BottomSheet) {
        val layoutBinding = binding.layoutBottomSheetContent
        layoutBinding.tvDemoDeviceNameValue.text = data.name
        layoutBinding.tvDemoFirmwareValue.text = data.firmwareVer
        layoutBinding.tvVolumeValue.text = data.volume
        layoutBinding.tvPowerValue.text = data.battery
        layoutBinding.tvChipIDValue.text = data.chipID
        layoutBinding.tvChipCountValue.text = data.chipNumbers
        layoutBinding.tvProductIDValue.text = data.pID
        layoutBinding.tvMuteValue.text = data.mute
        layoutBinding.tvRoomCorrectionValue.text = data.roomCorrection
    }

    private fun registerMobileDevice(device: BluetoothEntity) {
        viewModel.registerMobileDevice(
            device.getVID(), device.getPID(), device.getDeviceUid(), device.getAddress(),
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
        (getAppActivity() as? MainActivity)?.let {
            viewModel.addLog(text, it)
        }
    }

    fun avoidFastClick(view: View?) {
        if (view == binding.btScan) {
            if (!Method.bluetooth.isEnable()) {
                showToast("請確認藍牙狀態")
                return
            }

            // 如果目前有連線裝置，點擊按鈕一律執行「中斷連線」
            if (getGlobalSelectedResult() != null) {
                val address = getGlobalSelectedResult()!!.bleDevice.macAddress
                viewModel.stopScan()
                setGlobalBottomSheetData(BottomSheet())
                BleControlManager.getInstance().disconnect(address)
                setGlobalSelectedResult(null)
                view.isSelected = false
                setupScanBtn(false)
            } else {
                // 沒有連線裝置時，切換「掃描/停止」狀態
                view.isSelected = !view.isSelected
                val isScanning = view.isSelected
                setupScanBtn(isScanning)

                if (isScanning) {
                    viewModel.isStopScan = false
                    viewModel.deviceAdapter.replaceAllItems(ArrayList())
                    viewModel.scanState.value = false
                } else {
                    viewModel.stopScan()
                    addLog("停止掃描")
                }
            }
        } else {
            showToast("請確認藍牙狀態")
        }
    }

    private fun setGlobalSelectedResult(selectedResult: ScanResult?) {
        val mainActivity = (activity as? MainActivity) ?: return
        mainActivity.selectedResult.value = selectedResult
        //沒有設備就清除
        if (selectedResult == null) {
            GlobalViewModel.chartStorage.clear()
        }
    }

    private fun getGlobalSelectedResult(): ScanResult? {
        return (activity as? MainActivity)?.selectedResult?.value
    }

    private fun setGlobalBottomSheetData(bottomSheet: BottomSheet) {
        (activity as? MainActivity)?.bottomSheet?.value = bottomSheet
    }

    private fun getGlobalBottomSheetData(): BottomSheet? {
        return (activity as? MainActivity)?.bottomSheet?.value
    }

    fun setupConnectionStatus(scanResult: ScanResult?, bottomSheet: BottomSheet) {
        val status = if (scanResult != null) BleConnectStatus.CONNECTED else BleConnectStatus.DISCONNECT

        setupScanBtn(!bottomSheet.isEmpty())
        setupConnectStatus(status)
        setupBottomSheet(!bottomSheet.isEmpty())

        if (status == BleConnectStatus.CONNECTED) else return
        setBottomSheetValue(bottomSheet)
    }

}