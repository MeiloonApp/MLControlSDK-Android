package com.meiloon.mlcontrolcore_aos.fragment.peq

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import android.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.meiloon.mlcontrolcore_aos.activity.MainActivity
import com.meiloon.mlcontrolcore_aos.data.ChipChannel
import com.meiloon.mlcontrolcore_aos.data.Command
import com.meiloon.mlcontrolcore_aos.data.EQDataType
import com.meiloon.mlcontrolcore_aos.databinding.FragmentPeqBinding
import com.meiloon.controlcore.global.activity.GlobalViewModel
import com.meiloon.controlcore.main.api.APIData
import com.meiloon.controlcore.main.api.CmdDone
import com.meiloon.controlcore.main.api.enums.APIMethod
import com.meiloon.controlcore.main.api.enums.CommandType
import com.meiloon.controlcore.main.api.enums.CommandType.GetAllEQPara
import com.meiloon.controlcore.main.container.chart.data.EQData
import com.meiloon.controlcore.main.container.event.ReceiveCommandEvent
import com.meiloon.controlcore.main.factory.ViewModelFactory
import com.meiloon.controlcore.main.widget.ble.BleControlManager
import com.meiloon.controlcore.main.widget.ble.event.ConnectionResponse
import com.meiloon.controlcore.widget.app.android.AppFragment
import com.meiloon.controlcore.widget.app.method.Method
import com.meiloon.controlcore.widget.app.method.Method.date.getDateFormat
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class PeqFragment : AppFragment<FragmentPeqBinding>() {
    private lateinit var viewModel: PeqViewModel

    override fun initBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentPeqBinding {
        return FragmentPeqBinding.inflate(getLayoutInflater())
    }

    override fun initArguments(arguments: Bundle) {

    }

    override fun oneTimeInit(context: Context) {
        // 測試用假資料 - 建立 6 個 Channel 以驗證畫面與邏輯
//        val storage = GlobalViewModel.chartStorage
//
//        // Channel 1-1
//        storage.saveData(1, 1, 0, EQData(0, 100, 0f, 1f, 1))
//        storage.saveData(1, 1, 1, EQData(1, 1000, 0f, 1f, 1))
//        storage.saveData(1, 1, 2, EQData(2, 10000, 0f, 1f, 1))
//
//        // Channel 1-2
//        storage.saveData(1, 2, 0, EQData(0, 80, 5f, 0.7f, 1))
//        storage.saveData(1, 2, 1, EQData(1, 500, -3f, 1.2f, 1))
//        storage.saveData(1, 2, 2, EQData(2, 5000, 2f, 1f, 1))
//
//        // Channel 1-3
//        storage.saveData(1, 3, 0, EQData(0, 60, -2f, 1.0f, 1))
//        storage.saveData(1, 3, 1, EQData(1, 400, 4f, 0.5f, 1))
//
//        // Channel 1-4
//        storage.saveData(1, 4, 0, EQData(0, 120, 1f, 1.5f, 1))
//        storage.saveData(1, 4, 1, EQData(1, 2000, -5f, 2.0f, 1))
//
//        // Channel 1-5
//        storage.saveData(1, 5, 0, EQData(0, 250, 0f, 1f, 1))
//
//        // Channel 1-6
//        storage.saveData(1, 6, 0, EQData(0, 4000, 3f, 0.8f, 1))
//
//        setupChannelView()
    }

    override fun initUI(context: Context) {
        val viewModelClasses = arrayOf(PeqViewModel::class.java)
        val factory = ViewModelFactory(context, viewModelClasses) as ViewModelProvider.Factory
        viewModel = getViewModel(PeqViewModel::class.java, factory)

        // 初始化顯示第一個 Channel
        setupChannelView(1, 1)
    }

    override fun updateUI(context: Context) {

    }

    override fun initI18n(context: Context) {

    }

    override fun initListener(context: Context) {
        binding.btnReadAll.setOnClickListener { view ->
            val address = (activity as? MainActivity)?.selectedResult?.value?.bleDevice?.macAddress ?: return@setOnClickListener
            CommandType.send(address, GetAllEQPara)
        }

        binding.btnWriteAll.setOnClickListener { view ->
            val address = (activity as? MainActivity)?.selectedResult?.value?.bleDevice?.macAddress ?: return@setOnClickListener
            
            val chip = viewModel.showingChipChannel.chip
            val channel = viewModel.showingChipChannel.channel
            val eqDatas = viewModel.chartStorage.getData(chip, channel)

            val allEQPara = CommandType.SetAllEQPara(chip, channel, eqDatas)
            CommandType.send(address, allEQPara)
        }

        binding.btnSaveToDevice.setOnClickListener { view ->
            val address = (activity as? MainActivity)?.selectedResult?.value?.bleDevice?.macAddress ?: return@setOnClickListener
            viewModel.send(address, "SaveEQPara")
        }

        viewModel.bandAdapter.onSendClickListener = { position, data ->
            val address = (activity as? MainActivity)?.selectedResult?.value?.bleDevice?.macAddress
            if (address != null) {
                val chipIndex = viewModel.showingChipChannel.chip
                val channelIndex = viewModel.showingChipChannel.channel
                
                Log.d("PeqFragment", "Sending EQPara: Address=$address, Chip=$chipIndex, Channel=$channelIndex, Band=${data.index}")
                
                BleControlManager.getInstance().sendEQPara(
                    address,
                    chipIndex,
                    channelIndex,
                    data
                )
            } else {
                Log.e("PeqFragment", "Cannot send EQPara: Address is null")
            }
        }

        viewModel.bandAdapter.onDropdownClickListener = { view, position, data ->
            showFilterTypeMenu(view, position, data)
        }

        viewModel.bandAdapter.onSliderStopChangeListener = { view, position, data ->
            viewModel.chartStorage.saveData(viewModel.showingChipChannel.chip,
                                            viewModel.showingChipChannel.channel,
                                            position,
                                            data)
        }

        viewModel.bandAdapter.onDataUpdateListener = { position, data ->
            viewModel.chartStorage.saveData(viewModel.showingChipChannel.chip,
                                            viewModel.showingChipChannel.channel,
                                            position,
                                            data)
        }

        viewModel.channelAdapter.onChannelClickListener = { position, item ->
            viewModel.channelAdapter.selectedPosition = position
            val parts = item.split("-")
            if (parts.size == 2) {
                val chip = parts[0].toIntOrNull() ?: 1
                val channel = parts[1].toIntOrNull() ?: 1
                setupChannelView(chip, channel)
            }
        }
    }

    private fun showFilterTypeMenu(view: View, position: Int, data: EQData) {
        val popup = PopupMenu(view.context, view)
        
        // 使用 EQDataType Enum 來建立選單項目
        EQDataType.entries.forEach { type ->
            popup.menu.add(0, type.id, type.id, type.typeName)
        }
        
        popup.setOnMenuItemClickListener { menuItem ->
            // 使用者點選的 itemId 即為對應的 EQ 類型 ID
            val selectedId = menuItem.itemId
            data.type = selectedId
            viewModel.bandAdapter.notifyItemChanged(position)
            
            // 更新並存檔
            viewModel.chartStorage.saveData(
                viewModel.showingChipChannel.chip,
                viewModel.showingChipChannel.channel,
                data.index + 1,
                data
            )
            true
        }
        popup.show()
    }

    override fun initValue(bundle: Bundle?) {

    }

    override fun initLiveData(context: Context) {
        observe((activity as? MainActivity)?.selectedResult , { result ->
            viewModel.chartStorage = GlobalViewModel.chartStorage
        })
    }

    override fun onBackPressed() {

    }

    fun setupChannelView(chip: Int = 1, channel: Int = 1) {
        // 更新 ViewModel 中的當前通道資訊
        viewModel.showingChipChannel = ChipChannel(chip, channel)

        val eqDatas = viewModel.chartStorage.getData(chip, channel)
        binding.clDataContent.isVisible = eqDatas.isNotEmpty()
        binding.tvChipAndChannel.isVisible = eqDatas.isNotEmpty()

        viewModel.bandAdapter.items = eqDatas
        viewModel.channelAdapter.items = viewModel.getAllChannels().map { it.toFormattedString() }

        binding.rvChannel.setAdapter(viewModel.channelAdapter)
        binding.rvPeqList.setAdapter(viewModel.bandAdapter)
        binding.tvChipAndChannel.text = "當前:Chip${chip} CH${channel}"
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: ConnectionResponse) {
        if (!event.connected) else return
        showToast("設備已斷線")
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: ReceiveCommandEvent) {

        val apiData = APIData(event.command)
        val method: APIMethod = apiData.method
        Log.e("","收到指令: " + method + " ,內容: " + String(apiData.getCustomData()))

        val now = System.currentTimeMillis()
        val currentTime = getDateFormat(now, "HH:mm:ss")

        viewModel.parseReceiveCommand(event.command, currentTime) { apiMethod, toastText ->
            Method.control.run(activity) { context ->
                
                when (apiMethod) {
                    APIMethod.EQParas -> {
                        setupChannelView(viewModel.showingChipChannel.chip,
                                        viewModel.showingChipChannel.channel)
                    }
                    else -> {}
                }
                
                showToast(toastText)
            }
        }
    }
}
