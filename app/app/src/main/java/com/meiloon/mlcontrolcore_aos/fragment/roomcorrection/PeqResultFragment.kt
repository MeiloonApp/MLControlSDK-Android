package com.meiloon.mlcontrolcore_aos.fragment.roomcorrection

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.meiloon.controlcore.main.factory.ViewModelFactory
import com.meiloon.controlcore.widget.app.android.AppFragment
import com.meiloon.mlcontrolcore_aos.adapter.PeqResultAdapter
import com.meiloon.mlcontrolcore_aos.databinding.FragmentPeqResultBinding

class PeqResultFragment : AppFragment<FragmentPeqResultBinding>() {
    private lateinit var viewModel: RoomCorrectionViewModel
    private val adapter = PeqResultAdapter()

    override fun initBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentPeqResultBinding {
        return FragmentPeqResultBinding.inflate(inflater, container, false)
    }

    override fun initArguments(arguments: Bundle) {}

    override fun oneTimeInit(context: Context) {}

    override fun initUI(context: Context) {
        val viewModelClasses = arrayOf(RoomCorrectionViewModel::class.java)
        val factory = ViewModelFactory(context, viewModelClasses) as androidx.lifecycle.ViewModelProvider.Factory
        viewModel = androidx.lifecycle.ViewModelProvider(requireActivity(), factory)[RoomCorrectionViewModel::class.java]

        binding.rvPeqResults.adapter = adapter
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
        observe(viewModel.analysisResult) { list ->
            adapter.submitList(list ?: emptyList())
        }
    }

    override fun onBackPressed() {}
}