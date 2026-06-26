package com.meiloon.mlcontrolcore_aos.fragment.control

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import com.google.android.material.slider.Slider
import com.meiloon.controlcore.main.api.enums.CommandType
import com.meiloon.controlcore.main.container.event.ReceiveCommandEvent
import com.meiloon.controlcore.main.factory.ViewModelFactory
import com.meiloon.mlcontrolcore_aos.base.BaseFragment
import com.meiloon.mlcontrolcore_aos.R
import com.meiloon.mlcontrolcore_aos.activity.MainActivity
import com.meiloon.mlcontrolcore_aos.data.BottomSheet
import com.meiloon.mlcontrolcore_aos.databinding.FragmentControlBinding
import com.meiloon.mlcontrolcore_aos.fragment.blescan.BleScanViewModel
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class ControlFragment : BaseFragment<FragmentControlBinding>() {
    private lateinit var viewModel: ControlViewModel

    override fun initBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentControlBinding {
        return FragmentControlBinding.inflate(getLayoutInflater())
    }

    override fun initArguments(arguments: Bundle) {}

    override fun oneTimeInit(context: Context) {}

    override fun initUI(context: Context) {
        val viewModelClasses = arrayOf(ControlViewModel::class.java)
        val factory = ViewModelFactory(context, viewModelClasses) as androidx.lifecycle.ViewModelProvider.Factory
        viewModel = getViewModel(ControlViewModel::class.java, factory)

        val mainActivity = activity as? MainActivity
        viewModel.initCDeviceInfo(mainActivity?.selectedResult?.value,
                                    mainActivity?.bottomSheet?.value)

        binding.layoutBottomSheetContent.rvLog.setAdapter(viewModel.logAdapter)

        if (viewModel.connectedDeviceInfo.selectedResult == null) {
            binding.layoutControlContent.isVisible = false
        }

        setupBottomSheet()
    }

    override fun updateUI(context: Context) {}

    override fun initI18n(context: Context) {}

    override fun initListener(context: Context) {
        binding.layoutControlContent.setupTitleAnimation(binding.tvLargeTitle)

        // 手指放開
        binding.slVolume.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}

            override fun onStopTrackingTouch(slider: Slider) {
                sendCommand(CommandType.SetLastVolume(slider.value.toInt()))
            }
        })

        binding.swMute.setOnCheckedChangeListener { view, isChecked ->
            if (view.isPressed) {
                sendCommand(CommandType.SetSPKMute(isChecked))
            }
        }

        binding.swBT.setOnCheckedChangeListener { view, isChecked ->
            if (view.isPressed && isChecked) {
                binding.swBT.isChecked = false
                sendCommand(CommandType.StartBTPairing)
            }
        }

        binding.swRoom.setOnCheckedChangeListener { view, isChecked ->
            if (view.isPressed) {
                sendCommand(CommandType.SetRoomCorrectionMode(if (isChecked) 1 else 0))
            }
        }

        binding.swLF.setOnCheckedChangeListener { view, isChecked ->
            if (view.isPressed) {
                sendCommand(CommandType.SetLFEQ(isChecked))
            }
        }

        binding.swHF.setOnCheckedChangeListener { view, isChecked ->
            if (view.isPressed) {
                sendCommand(CommandType.SetHFEQ(isChecked))
            }
        }

        binding.swDesk.setOnCheckedChangeListener { view, isChecked ->
            if (view.isPressed) {
                sendCommand(CommandType.SetDeskEQ(isChecked))
            }
        }

        // Manual Query Buttons
        binding.btnGetVolume.setOnClickListener { sendCommand(CommandType.GetVolume) }
        binding.btnGetVersion.setOnClickListener { sendCommand(CommandType.GetFirmwareVer) }
        binding.btnGetBattery.setOnClickListener { sendCommand(CommandType.GetBattery) }
        binding.btnGetStatus.setOnClickListener { sendCommand(CommandType.GetStatus) }

        binding.layoutBottomSheetContent.tvClear.setOnClickListener {
            BleScanViewModel.getInstance()?.clearLog()
            (activity as? MainActivity)?.logDataBridge?.postValue(emptyList())
            viewModel.logAdapter.replaceAllItems(emptyList())
            binding.layoutBottomSheetContent.tvLogCount.text = "0"
        }
    }

    private fun sendCommand(commandType: CommandType) {
        viewModel.connectedDeviceInfo.macAddress.value?.let {
            viewModel.addLogItem(listOf("發送指令: [${commandType.name}]"))
            CommandType.send(it, commandType)
        }
    }

    override fun initValue(savedInstanceState: Bundle?) {}

    override fun onVisibleChange(visible: Boolean) {}

    override fun initLiveData(context: Context) {
        (activity as? MainActivity)?.let { main ->
            observe(main.logDataBridge) { list ->
                val logList = list ?: emptyList()
                viewModel.logAdapter.replaceAllItems(logList)
                binding.layoutBottomSheetContent.tvLogCount.text = logList.size.toString()
            }
            observe(main.selectedResult) { 
                viewModel.connectedDeviceInfo.selectedResult = it 
                binding.layoutControlContent.visibility = if (it != null) android.view.View.VISIBLE else android.view.View.GONE
            }
        }

        observe(viewModel.connectedDeviceInfo.volume) {
            it?.let {
                binding.slVolume.value = it.toFloat()
                binding.tvVolumeValue.text = it.toString()
                updateGlobalBottomSheet(BottomSheet(volume = it.toString()))
            }
        }

        observe(viewModel.connectedDeviceInfo.isMuteOn) { isMuted ->
            val isMute = isMuted == true
            binding.swMute.isChecked = isMute
            binding.ivMute.setImageResource(if (isMute) R.drawable.volume_off_24px else R.drawable.volume_mute_24px)
            updateGlobalBottomSheet(BottomSheet(mute = if (isMute) "已靜音" else "正常"))
        }
        observe(viewModel.connectedDeviceInfo.roomCorrectionMode) {
            binding.swRoom.isChecked = it == 1
            updateGlobalBottomSheet(BottomSheet(roomCorrection = if (it == 1) "開啟" else "關閉"))
        }
        
        observe(viewModel.connectedDeviceInfo.chipID) {
            it?.let { binding.slVolume.valueTo = it.maxVolume().toFloat() }
        }

        observe(viewModel.connectedDeviceInfo.eqMode) { eQMode ->
            eQMode?.let {
                binding.swLF.isChecked = it.lfeq
                binding.swHF.isChecked = it.hfeq
                binding.swDesk.isChecked = it.deskEQ
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: ReceiveCommandEvent) {
        if (event.address != viewModel.connectedDeviceInfo.macAddress.value) return
        viewModel.parseReceiveCommand(event.command, context) { }
    }

    fun setupBottomSheet() {
        val bottomSheet: CardView = binding.persistentBottomSheet
        val screenHeight = resources.displayMetrics.heightPixels
        val layoutParams = bottomSheet.layoutParams
        layoutParams.height = screenHeight / 3
        bottomSheet.layoutParams = layoutParams
    }

    override fun onBackPressed() {}
}