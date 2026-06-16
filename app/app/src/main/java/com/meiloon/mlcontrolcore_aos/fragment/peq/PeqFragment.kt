package com.meiloon.mlcontrolcore_aos.fragment.peq

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.meiloon.mlcontrolcore_aos.activity.MainActivity
import com.meiloon.mlcontrolcore_aos.data.ChipChannel
import com.meiloon.mlcontrolcore_aos.data.EQDataType
import com.meiloon.mlcontrolcore_aos.databinding.FragmentPeqBinding
import com.meiloon.mlcontrolcore_aos.databinding.DialogPeqConfigBinding
import com.meiloon.mlcontrolcore_aos.databinding.DialogPeqLoadBinding
import com.meiloon.controlcore.main.container.chart.data.toEQDataList
import com.meiloon.controlcore.global.activity.GlobalViewModel
import com.meiloon.controlcore.main.api.APIData
import com.meiloon.controlcore.main.api.CmdDone
import com.meiloon.controlcore.main.api.enums.APIMethod
import com.meiloon.controlcore.main.api.enums.CommandType
import com.meiloon.controlcore.main.api.enums.CommandType.GetAllEQPara
import com.meiloon.controlcore.main.container.chart.data.EQData
import com.meiloon.controlcore.main.container.chart.data.toPEQString
import com.meiloon.controlcore.main.container.event.ReceiveCommandEvent
import com.meiloon.controlcore.main.factory.ViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.meiloon.controlcore.main.widget.ble.BleControlManager
import com.meiloon.controlcore.main.widget.ble.event.ConnectionResponse
import com.meiloon.controlcore.widget.app.android.AppFragment
import com.meiloon.controlcore.widget.app.method.Method
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
            CommandType.send(address, CommandType.SaveEQPara)
        }

        binding.btnCopySteps.setOnClickListener {
            val chip = viewModel.showingChipChannel.chip
            val channel = viewModel.showingChipChannel.channel
            val eqDatas = viewModel.chartStorage.getData(chip, channel)

            if (eqDatas.isNotEmpty()) {
                val stepsString = viewModel.getResponseData(eqDatas, 48000.0)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("PEQ Steps", stepsString)
                clipboard.setPrimaryClip(clip)
                showToast("已複製當前通道響應步數")
            } else {
                showToast("請先讀取數據")
            }
        }

        viewModel.bandAdapter.onSendClickListener = { position, data ->
            val address = (activity as? MainActivity)?.selectedResult?.value?.bleDevice?.macAddress
            if (address != null) {
                val chipIndex = viewModel.showingChipChannel.chip
                val channelIndex = viewModel.showingChipChannel.channel
                
                Log.d("PeqFragment", "Sending EQPara: Address=$address, Chip=$chipIndex, Channel=$channelIndex, Band=${data.index}")
                // SetEQPara && SetLastEQPara 相同
                CommandType.send(address, CommandType.SetEQPara(chipIndex, channelIndex, data))
//                CommandType.send(address, CommandType.SetLastEQPara(chipIndex, channelIndex, data))
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

        binding.btnSaveText.setOnClickListener {
            // 先將 Adapter 中的最新資料同步到 storage，避免正在編輯中的內容遺失
            viewModel.bandAdapter.items.forEach { data ->
                viewModel.chartStorage.saveData(
                    viewModel.showingChipChannel.chip,
                    viewModel.showingChipChannel.channel,
                    data.index,
                    data
                )
            }

            // 只導出當前選擇的通道
            val currentPeqString = viewModel.formateEQDataList()

            if (currentPeqString.isEmpty()) {
                showToast("目前無 PEQ 數據")
            } else {
                showPeqConfigDialog(currentPeqString.toString())
            }
        }

        binding.btnLoadText.setOnClickListener {
            showPeqLoadDialog()
        }
    }

    private fun showPeqLoadDialog() {
        val dialogBinding = DialogPeqLoadBinding.inflate(layoutInflater)

        viewModel.bandAdapter.items.forEach { data ->
            viewModel.chartStorage.saveData(
                viewModel.showingChipChannel.chip,
                viewModel.showingChipChannel.channel,
                data.index,
                data
            )
        }

        val currentPeqString = viewModel.formateEQDataList()

        dialogBinding.etPeqInput.setText(currentPeqString)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.decorView?.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val rect = Rect()
                dialogBinding.root.getGlobalVisibleRect(rect)
                if (!rect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    if (isKeyboardVisible()) {
                        hideKeyboard(dialogBinding.root)
                    } else {
                        dialog.dismiss()
                    }
                    v.performClick()
                    return@setOnTouchListener true
                }
            }
            false
        }

        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                if (isKeyboardVisible()) {
                    hideKeyboard(dialogBinding.root)
                    return@setOnKeyListener true
                }
            }
            false
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnLoad.setOnClickListener {
            val input = dialogBinding.etPeqInput.text.toString()
            if (input.isBlank()) {
                showToast("請輸入內容")
                return@setOnClickListener
            }

            try {
                val eqList = input.toEQDataList()
                if (eqList.isNotEmpty()) {
                    eqList.forEach { eqData ->
                        viewModel.chartStorage.saveData(
                            viewModel.showingChipChannel.chip,
                            viewModel.showingChipChannel.channel,
                            eqData.index,
                            eqData
                        )
                    }
                    setupChannelView(viewModel.showingChipChannel.chip, viewModel.showingChipChannel.channel)
                    showToast("載入成功")
                    dialog.dismiss()
                } else {
                    showToast("無效的 PEQ 格式")
                }
            } catch (e: Exception) {
                showToast("解析失敗")
            }
        }

        dialog.show()
    }

    private fun showPeqConfigDialog(peqString: String) {
        val dialogBinding = DialogPeqConfigBinding.inflate(layoutInflater)
        dialogBinding.etPeqString.setText(peqString)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.decorView?.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val rect = Rect()
                dialogBinding.root.getGlobalVisibleRect(rect)
                if (!rect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    if (isKeyboardVisible()) {
                        hideKeyboard(dialogBinding.root)
                    } else {
                        dialog.dismiss()
                    }
                    v.performClick()
                    return@setOnTouchListener true
                }
            }
            false
        }

        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                if (isKeyboardVisible()) {
                    hideKeyboard(dialogBinding.root)
                    return@setOnKeyListener true
                }
            }
            false
        }

        dialogBinding.btnCopy.setOnClickListener {
            val editedString = dialogBinding.etPeqString.text.toString()
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("PEQ Config", editedString)
            clipboard.setPrimaryClip(clip)
            showToast("已複製到剪貼簿")
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun isKeyboardVisible(): Boolean {
        val insets = ViewCompat.getRootWindowInsets(requireActivity().window.decorView)
        return insets?.isVisible(WindowInsetsCompat.Type.ime()) ?: false
    }

    private fun hideKeyboard(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
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
                data.index,
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
        binding.bottomBar.isVisible = eqDatas.isNotEmpty()

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

        viewModel.parseReceiveCommand(event.command) { apiMethod, toastText, cmdDone ->
            Method.control.run(activity) { context ->
                
                when (apiMethod) {
                    APIMethod.EQPara -> {
                        setupChannelView(viewModel.showingChipChannel.chip,
                                        viewModel.showingChipChannel.channel)
                    }
                    APIMethod.EQParas -> {
                        setupChannelView(viewModel.showingChipChannel.chip,
                                        viewModel.showingChipChannel.channel)
                    }
                    else -> {
                        if (cmdDone != null && toastText != "") {
                            showToast("$toastText ${cmdDone.getCmdMessage(context)}")
                        } else if (cmdDone != null && toastText == "") {
                            showToast(cmdDone.getCmdMessage(context))
                        } else {
                            showToast(toastText)
                        }
                    }
                }
            }
        }
    }
}
