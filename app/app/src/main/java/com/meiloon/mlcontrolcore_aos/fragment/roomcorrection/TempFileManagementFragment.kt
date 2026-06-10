package com.meiloon.mlcontrolcore_aos.fragment.roomcorrection

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.navigation.fragment.findNavController
import com.meiloon.controlcore.main.factory.ViewModelFactory
import com.meiloon.controlcore.widget.app.android.AppFragment
import com.meiloon.mlcontrolcore_aos.adapter.TempFileAdapter
import com.meiloon.mlcontrolcore_aos.databinding.FragmentTempFileManagementBinding

class TempFileManagementFragment : AppFragment<FragmentTempFileManagementBinding>() {
    private lateinit var viewModel: RoomCorrectionViewModel
    private lateinit var adapter: TempFileAdapter

    override fun initBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentTempFileManagementBinding {
        return FragmentTempFileManagementBinding.inflate(inflater, container, false)
    }

    override fun initArguments(arguments: Bundle) {}

    override fun oneTimeInit(context: Context) {}

    override fun initUI(context: Context) {
        val viewModelClasses = arrayOf(RoomCorrectionViewModel::class.java)
        val factory = ViewModelFactory(context, viewModelClasses) as androidx.lifecycle.ViewModelProvider.Factory
        viewModel = androidx.lifecycle.ViewModelProvider(requireActivity(), factory).get(RoomCorrectionViewModel::class.java)

        adapter = TempFileAdapter(
            onSetNF = { item -> 
                viewModel.setFileAsNF(item.file, requireContext().cacheDir)
            },
            onSetFF = { item -> 
                viewModel.setFileAsFF(item.file, requireContext().cacheDir)
            },
            onShare = { item -> shareFile(item.file) },
            onDelete = { item -> viewModel.deleteFile(item.file, requireContext().cacheDir) }
        )
        binding.rvFiles.adapter = adapter
        
        // 進入時主動刷一次
        viewModel.updateTempFileCount(requireContext().cacheDir)
    }

    override fun updateUI(context: Context) {}

    override fun initI18n(context: Context) {}

    override fun initListener(context: Context) {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    override fun initValue(savedInstanceState: Bundle?) {}

    override fun onVisibleChange(visible: Boolean) {}

    override fun initLiveData(context: Context) {
        observe(viewModel.tempFileList) { list ->
            adapter.submitList(list)
            if (list.isEmpty()) {
                findNavController().popBackStack()
            }
        }
    }

    private fun shareFile(file: java.io.File) {
        try {
            // 透過 FileProvider 獲取安全 URI
            val contentUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/wav"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // 授權對方讀取權限
            }

            // 啟動 Android Sharesheet
            startActivity(Intent.createChooser(shareIntent, "分享錄音檔: ${file.name}"))
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("分享失敗")
        }
    }

    override fun onBackPressed() {}
}
