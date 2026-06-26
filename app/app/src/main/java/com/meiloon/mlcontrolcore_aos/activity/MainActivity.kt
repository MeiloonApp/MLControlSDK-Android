package com.meiloon.mlcontrolcore_aos.activity

import android.bluetooth.BluetoothAdapter
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat.setTint
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.MutableLiveData
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.meiloon.controlcore.broadcast.BluetoothStateReceiver
import com.meiloon.controlcore.global.activity.GlobalViewModel
import com.meiloon.controlcore.widget.app.android.AppActivity
import com.meiloon.controlcore.widget.app.ble.BleManager
import com.meiloon.controlcore.widget.app.method.Method
import com.meiloon.mlcontrolcore_aos.R
import com.meiloon.mlcontrolcore_aos.data.BottomSheet
import com.meiloon.mlcontrolcore_aos.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearanceModel
import com.meiloon.controlcore.MLControlCore
import com.meiloon.controlcore.main.container.room.MLRoomCorrectionEngine
import com.meiloon.mlcontrolcore_aos.util.LogManager
import com.polidea.rxandroidble3.scan.ScanResult


class MainActivity : AppActivity<ActivityMainBinding>() {
    private var keepSplash = true
    private lateinit var bluetoothReceiver: BluetoothStateReceiver
    val logDataBridge = MutableLiveData<List<String>>()
    var selectedResult = MutableLiveData<ScanResult>()
    var bottomSheet = MutableLiveData<BottomSheet>()
    private var loadingDialog: android.app.Dialog? = null

    fun setLoading(isLoading: Boolean) {
        if (isLoading) {
            if (loadingDialog == null) {
                loadingDialog = android.app.Dialog(this, R.style.FullScreenLoadingDialog).apply {
                    setContentView(R.layout.dialog_loading)
                    setCancelable(false)
                    window?.setLayout(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }
            if (loadingDialog?.isShowing == false) {
                loadingDialog?.show()
            }
        } else {
            loadingDialog?.dismiss()
        }
    }

    override fun initBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getViewModel(GlobalViewModel::class.java)
        setContentView(binding.root)

        LogManager.logs.observe(this) {
            logDataBridge.value = it
        }

        BleManager.getInstance().init(this)
        MLRoomCorrectionEngine.init(this)
        keepSplash()

        setupBottomNavigationView()

        bluetoothReceiver = BluetoothStateReceiver()
        registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))

        // api授權
        MLControlCore.getInstance().configure(context = this) { successs, error ->
            if (successs) {
                showToast("SDK 授權成功！")
            } else {
                showToast("授權失敗: $error")
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(bluetoothReceiver)
        super.onDestroy()
    }

    private fun setupBottomNavigationView() {
        // remove padding
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigationView) { view, insets ->
            view.setPadding(view.paddingLeft, 0, view.paddingRight, 0)
            WindowInsetsCompat.CONSUMED
        }

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val navController = navHostFragment?.navController

        if (navController != null) {
            binding.bottomNavigationView.setupWithNavController(navController)

            // 修正子頁面選取狀態不同步與顯示控制問題
            navController.addOnDestinationChangedListener { _, destination, _ ->
                val menu = binding.bottomNavigationView.menu
                
                // 1. 控制顯示/隱藏：進入深層功能頁面時隱藏 BottomNavigationView
                val shouldHideBottomNav = when (destination.id) {
                    R.id.peqResultFragment,
                    R.id.tempFileManagementFragment -> true
                    else -> false
                }
                binding.bottomNavigationView.visibility = if (shouldHideBottomNav) android.view.View.GONE else android.view.View.VISIBLE

                // 2. 修正二級頁面選取狀態同步問題
                when (destination.id) {
                    R.id.settingToolFragment, R.id.OTAFragment, R.id.firmwareUpdateFragment -> {
                        menu.findItem(R.id.bleScanFragment)?.isChecked = true
                    }
                    R.id.peqResultFragment, R.id.tempFileManagementFragment -> {
                        menu.findItem(R.id.otherRoomCollection)?.isChecked = true
                    }
                }
            }
        }

        binding.bottomNavigationView.doOnLayout { view ->
            val perfectRadius = view.height / 2f

            val shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setAllCorners(CornerFamily.ROUNDED, perfectRadius)
                .build()

            val shapeDrawable = MaterialShapeDrawable(shapeAppearanceModel).apply {
                setTint(ContextCompat.getColor(this@MainActivity, R.color.white))
            }

            view.background = shapeDrawable
        }
    }

    private fun keepSplash() {
        post({
            keepSplash = false
            GlobalViewModel.isSplashEnd(true)
        }, 1000)
    }
}