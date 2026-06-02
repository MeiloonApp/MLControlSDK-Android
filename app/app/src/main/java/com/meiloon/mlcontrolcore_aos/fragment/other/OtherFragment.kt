package com.meiloon.mlcontrolcore_aos.fragment.other

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import com.meiloon.mlcontrolcore_aos.activity.MainActivity
import com.meiloon.mlcontrolcore_aos.data.Command
import com.meiloon.mlcontrolcore_aos.data.CommandDesc
import com.meiloon.mlcontrolcore_aos.data.CommandItem
import com.meiloon.mlcontrolcore_aos.databinding.FragmentOthersBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.meiloon.controlcore.main.api.APIData
import com.meiloon.controlcore.main.api.enums.APIMethod
import com.meiloon.controlcore.main.api.enums.CommandType
import com.meiloon.controlcore.main.api.enums.CommandType.*
import com.meiloon.controlcore.main.container.event.ReceiveCommandEvent
import com.meiloon.controlcore.main.factory.ViewModelFactory
import com.meiloon.controlcore.main.widget.ble.event.ConnectionResponse
import com.meiloon.controlcore.widget.app.android.AppFragment
import com.meiloon.controlcore.widget.app.method.Method
import com.meiloon.controlcore.widget.app.method.Method.date.getDateFormat
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class OtherFragment : AppFragment<FragmentOthersBinding>() {
    private lateinit var viewModel: OtherViewModel

    override fun initBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentOthersBinding {
        return FragmentOthersBinding.inflate(getLayoutInflater())
    }

    override fun initArguments(arguments: Bundle) {

    }

    override fun oneTimeInit(context: Context) {

    }

    override fun initUI(context: Context) {
        val viewModelClasses = arrayOf(OtherViewModel::class.java)
        val factory = ViewModelFactory(context, viewModelClasses) as androidx.lifecycle.ViewModelProvider.Factory
        viewModel = getViewModel(OtherViewModel::class.java, factory)

        viewModel.connectedDeviceInfo.selectedResult = (activity as? MainActivity)?.selectedResult?.value

        binding.layoutBottomSheetContent.rvLog.setAdapter(viewModel.logAdapter)
    }

    override fun updateUI(context: Context) {
        setupBottomSheet()
        setupDescView()
    }

    override fun initI18n(context: Context) {

    }

    override fun initListener(context: Context) {
        binding.actvSelectCommand.setOnClickListener {
            getContext()?.let {
                MaterialAlertDialogBuilder(it)
                    .setTitle("請選擇")
                    .setItems(Command.getDisplayNames()) { dialog, index ->
                        val item = viewModel.items[index]
                        viewModel.selectedCommandItem = item
                        binding.actvSelectCommand.setText(item.name, false)
                        setupDescView(true, item)
                    }
                    .show()
            }
        }

        binding.btSendCommand.setOnClickListener { view ->
            val selectedResult = viewModel.connectedDeviceInfo.selectedResult

            if (selectedResult != null) {
                val address = selectedResult.bleDevice.macAddress

                viewModel.selectedCommandItem?.let { selectedCommandItem->
                    val selectedCommandType = selectedCommandItem.commandType
                    val commandType = CommandType.setValue(selectedCommandType, getSettingValue(selectedCommandType))
                    if (commandType != null) {
                        CommandType.send(address, commandType)
                    } else {
                        showToast("請設定CommandType send:TODO")
                    }
                }
            } else {
                showToast("請連接設備")
            }
        }

        binding.slSlider.addOnChangeListener { slider, value, fromUser ->
            binding.tvSliderTitle.text = "數值: ${value.toInt()}"
        }

        binding.swBtn.setOnCheckedChangeListener { view, isChecked ->
            val statusText = if (isChecked) "ON" else "OFF"
            binding.tvSwitchStatus.text = "開關狀態: ${statusText}"
        }
    }

    override fun initValue(savedInstanceState: Bundle?) {

    }

    override fun onVisibleChange(visible: Boolean) {

    }

    override fun initLiveData(context: Context) {
        (activity as? MainActivity)?.let {
            observe(it.logDataBridge, { list ->
                if (viewModel.logAdapter.items.size == 0) {
                    viewModel.addLogItem(list)
                } else {
                    viewModel.logAdapter.replaceAllItems(emptyList())
                    viewModel.addLogItem(list)
                }
            })
        }

        observe(viewModel.connectedDeviceInfo.volume, { volume ->
            binding.slSlider.value = volume.toFloat()
            binding.tvSliderTitle.text = "數值: ${volume}"
        })

        observe((activity as? MainActivity)?.selectedResult, { selectedResult ->
            viewModel.connectedDeviceInfo.selectedResult = selectedResult
        })
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: ConnectionResponse) {
        dismissProgressDialog()
        if (event.connected) {
            val address: String = event.bluetooth.getAddress()

            Log.e("ConnectionResponse", "onMessageEvent連線時間更新: " + address)
        } else {
            val address: String = event.bluetooth.getAddress()
            subscribeCompleteAuto(viewModel.updateAutoConnect(address, false))
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: ReceiveCommandEvent) {
        val selectedDevceAddress = viewModel.connectedDeviceInfo.macAddress.value ?: ""
        if (!event.address.equals(selectedDevceAddress)) return

        val apiData = APIData(event.command)
        val method: APIMethod = apiData.getMethod()
        Log.e("","收到指令: " + method + " ,內容: " + String(apiData.getCustomData()))

        val now = System.currentTimeMillis()
        val currentTime = getDateFormat(now, "HH:mm:ss")

        viewModel.parseReceiveCommand(event.command, currentTime) { cmdDone ->
            // 未判斷時跳出提示
            Method.control.run(getAppActivity()) { context ->
                val message: String? = cmdDone.getCmdMessage(context)
                if (cmdDone.cmdResult != 0) {
                    showToast("${cmdDone.cmd} $message")
                }
            }
        }
    }

    override fun onBackPressed() {

    }

    fun setupBottomSheet() {
        val bottomSheet: CardView = binding.persistentBottomSheet
        val behavior = BottomSheetBehavior.from(bottomSheet)

        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels

        // 將最大高度設定為螢幕的一半
        behavior.maxHeight = screenHeight / 2
    }

    fun setupDescView(show: Boolean = false, item: CommandItem? = null) {
        binding.tvParameterSetting.isVisible = show
        binding.cvtvParameterSettingBorder.isVisible = show

        binding.tvAPISpec.isVisible = show
        binding.cvAPISpecBorder.isVisible = show

        // 重置View
        binding.cvSwitchBorder.isVisible = false
        binding.cvSliderBorder.isVisible = false
        binding.edText.isVisible = false

        binding.tvSliderTitle.text = "數值: 0"
        binding.slSlider.value = 0.toFloat()

        if (item == null) return

        setAPIDesc(CommandDesc.getDescData(item.commandType))

        when (item.commandType) {
            GetChipID,
            GetAudioChipID,
            GetFirmwareVer,
            GetVolume,
            GetRoomCorrectionMode,
            GetBTPairing,
            StartBTPairing,
            GetAudioChipNumbers,
            GetAudioSampleRate,
            GetAudioChannel,
            GetAudioBand,
            GetEQRange,
            GetAllEQPara,
            GetEQGroup,
            GetEQEngine,
            GetEQMode,
            GetBattery -> {
                return
            }
            is SetRoomCorrectionMode -> {
                binding.cvSliderBorder.isVisible = true
            }
            is SetVolume, is SetLastVolume -> {
                binding.cvSliderBorder.isVisible = true

                val info = viewModel.connectedDeviceInfo
                val volume = (info.volume.value ?: 0)
                val maxVolume = info.maxValue
                binding.slSlider.valueFrom = 0.toFloat()
                binding.slSlider.value = volume.toFloat()
                binding.slSlider.valueTo = maxVolume.toFloat()

                binding.tvSliderTitle.text = "數值: ${volume}"
                binding.tvRange.text = "範圍:0~${maxVolume}"
            }
            is SetBTDeviceName -> {
                binding.edText.isVisible = true
            }
            is SetHFEQ, is SetLFEQ, is SetDeskEQ -> {
                binding.cvSwitchBorder.isVisible = true
            }
            else -> {
                Log.d("", item.commandType.toString())
            }
        }
    }

    fun setAPIDesc(commandDesc: CommandDesc) {
        binding.tvAPISpecType.text = commandDesc.type
        binding.tvAPISpecDesc.text = commandDesc.desc
        binding.tvDemoValue.text = commandDesc.demo
    }

    fun getSettingValue(commandType: CommandType? = null): Any? {
        var value: Any? = null

        if (binding.cvSwitchBorder.isVisible) {
            value = binding.swBtn.isChecked
        } else if (binding.cvSliderBorder.isVisible) {
            value = binding.slSlider.value.toInt()
        } else if (binding.edText.isVisible) {
            value = binding.edText.text.toString()
        } else if (commandType is SetAllEQPara) {
            value = viewModel.connectedDeviceInfo.eqParas.value
        } else if (commandType is SetEQPara) {
            val eqParas = viewModel.connectedDeviceInfo.eqParas.value?.first() ?: return null
            value = SetEQPara(eqParas.chipIndex, eqParas.channel, eqParas.toEQData())
        }

        return value
    }

}