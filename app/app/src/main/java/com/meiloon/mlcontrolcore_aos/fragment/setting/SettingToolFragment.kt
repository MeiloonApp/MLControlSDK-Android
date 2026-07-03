package com.meiloon.mlcontrolcore_aos.fragment.setting

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.meiloon.controlcore.MLControlCore
import com.meiloon.controlcore.main.widget.ble.BleControlManager
import com.meiloon.mlcontrolcore_aos.R
import com.meiloon.controlcore.widget.app.android.AppFragment
import com.meiloon.mlcontrolcore_aos.databinding.FragmentSettingsAndToolsBinding

class SettingToolFragment : AppFragment<FragmentSettingsAndToolsBinding>() {

    override fun initBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentSettingsAndToolsBinding {
        return FragmentSettingsAndToolsBinding.inflate(inflater, container, false)
    }

    override fun initArguments(arguments: Bundle) {
    }

    override fun oneTimeInit(context: Context) {
    }

    override fun initUI(context: Context) {
        val version = MLControlCore.getInstance().getSDKVersion()
        binding.tvSDKVersion.text = version
        binding.tips.text = "MLControl SDK Example V$version"
        updateUI(context)
    }

    override fun updateUI(context: Context) {
        val isConnected = BleControlManager.getInstance().getConnectedDevices().isNotEmpty()
        binding.cvOTAUpgrade.isEnabled = isConnected
        binding.cvOTAUpgrade.alpha = if (isConnected) 1.0f else 0.5f
    }

    override fun initI18n(context: Context) {
    }

    override fun initListener(context: Context) {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.cvOTA.setOnClickListener {
            findNavController().navigate(R.id.action_settingToolFragment_to_OTAFragment)
        }

        binding.cvOTAUpgrade.setOnClickListener {
            val connectedDevice = BleControlManager.getInstance().getConnectedDevices().firstOrNull()
            val bundle = Bundle().apply {
                putString("device_address", connectedDevice?.device?.address)
            }
            findNavController().navigate(R.id.action_settingToolFragment_to_firmwareUpdateFragment, bundle)
        }

        binding.swLowLevelLog.setOnCheckedChangeListener { _, isChecked ->
            // TODO: Save preference
        }

        binding.swToastLog.setOnCheckedChangeListener { _, isChecked ->
            // TODO: Save preference
        }
    }

    override fun initValue(savedInstanceState: Bundle?) {
    }

    override fun onVisibleChange(visible: Boolean) {
        updateUI(requireContext())
    }

    override fun initLiveData(context: Context) {
    }

    override fun onBackPressed() {
        findNavController().popBackStack()
    }
}
