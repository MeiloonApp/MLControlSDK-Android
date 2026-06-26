package com.meiloon.mlcontrolcore_aos.fragment.setting

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import com.meiloon.controlcore.main.factory.ViewModelFactory
import com.meiloon.mlcontrolcore_aos.R
import com.meiloon.mlcontrolcore_aos.base.BaseFragment
import com.meiloon.mlcontrolcore_aos.databinding.FragmentFirmwareUpdateBinding
import com.meiloon.mlcontrolcore_aos.extension.collectIn
import com.meiloon.mlcontrolcore_aos.extension.getFileName

class FirmwareUpdateFragment : BaseFragment<FragmentFirmwareUpdateBinding>() {
    private lateinit var viewModel: OTAViewModel
    private val selectFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { handleFileSelection(it) }
    }

    override fun initBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentFirmwareUpdateBinding {
        return FragmentFirmwareUpdateBinding.inflate(inflater, container, false)
    }

    override fun initArguments(arguments: Bundle) {
    }

    override fun oneTimeInit(context: Context) {
    }

    override fun initUI(context: Context) {
        val viewModelClasses = arrayOf(OTAViewModel::class.java)
        val factory = ViewModelFactory(context, viewModelClasses) as androidx.lifecycle.ViewModelProvider.Factory
        viewModel = getViewModel(OTAViewModel::class.java, factory)

        viewModel.initOTAManager(context)
    }

    override fun updateUI(context: Context) {
    }

    override fun initI18n(context: Context) {
    }

    override fun initListener(context: Context) {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnSelectFile.setOnClickListener {
            selectFileLauncher.launch(arrayOf("*/*"))
        }

        binding.cvFileSelection.setOnClickListener {
            selectFileLauncher.launch(arrayOf("*/*"))
        }

        binding.btnStartOTA.setOnClickListener {
            viewModel.startOTA(requireContext())
        }

        binding.btnCancelOTA.setOnClickListener {
            viewModel.otaManager?.cancelOTA()
        }
    }

    private fun handleFileSelection(uri: Uri) {
        val fileName = uri.getFileName(requireContext())
        viewModel.updateSelectedFile(requireContext(), uri, fileName)
    }

    override fun initValue(savedInstanceState: Bundle?) {
    }

    override fun initLiveData(context: Context) {
        viewModel.fileName.collectWithLifecycle { name ->
            binding.tvFileName.text = name
            updateStartButtonState()
        }

        viewModel.otaProgress.collectWithLifecycle { progress ->
            binding.progressBar.progress = progress
            binding.tvProgressPercent.text = "$progress%"
            
            if (progress > 0 && progress < 100) {
                binding.tvProgressStatus.text = "升級中..."
            } else if (progress == 100) {
                binding.tvProgressStatus.text = "升級完成"
            }
        }

        viewModel.deviceBankMode.collectWithLifecycle { mode ->
            binding.tvDeviceType.text = mode.text
            updateStartButtonState()
        }

        viewModel.isOTAing.collectWithLifecycle { isOTAing ->
            if (isOTAing) {
                binding.tvProgressStatus.text = "準備中..."
                binding.btnStartOTA.isEnabled = false
                binding.btnCancelOTA.isEnabled = true
            } else {
                if (viewModel.otaProgress.value == 0) {
                    binding.tvProgressStatus.text = "等待中"
                }
                binding.btnCancelOTA.isEnabled = false
                updateStartButtonState()
            }
        }
        
        viewModel.toastEvent.collectWithLifecycle { message ->
            showToast(message)
        }
    }

    private fun updateStartButtonState() {
        val hasFile = viewModel.fileName.value.isNotEmpty()
        val canUpdate = viewModel.checkCanUpdate() && viewModel.isOTAing.value == false && hasFile
        binding.btnStartOTA.isEnabled = canUpdate
    }

    override fun onVisibleChange(visible: Boolean) {
        // No scan here, so no need to stop scan unless we add it
    }

    override fun onBackPressed() {
        findNavController().popBackStack()
    }
}
