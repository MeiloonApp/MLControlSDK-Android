package com.meiloon.mlcontrolcore_aos.util

import android.Manifest
import androidx.fragment.app.Fragment
import com.meiloon.controlcore.widget.app.android.AppFragment
import com.meiloon.mlcontrolcore_aos.R
import com.permissionx.guolindev.PermissionX

/**
 * 請求麥克風權限的封裝方法
 * @param rationaleResId 當權限被拒絕時顯示的提示文字資源 ID
 * @param onGranted 權限取得後的執行邏輯
 */
fun AppFragment<*>.requestMicPermission(
    rationaleResId: Int = R.string.permission_mic_rationale_general,
    onGranted: () -> Unit
) {
    PermissionX.init(this)
        .permissions(Manifest.permission.RECORD_AUDIO)
        .request { allGranted, _, _ ->
            if (allGranted) {
                onGranted()
            } else {
                showToast(getString(rationaleResId))
            }
        }
}
