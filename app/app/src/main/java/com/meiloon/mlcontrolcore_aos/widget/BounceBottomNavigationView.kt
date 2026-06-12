package com.meiloon.mlcontrolcore_aos.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView

/**
 * 具備點擊縮放動畫效果，且自動根據元件高度優化佈局比例的 BottomNavigationView
 */
class BounceBottomNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BottomNavigationView(context, attrs, defStyleAttr) {

    private var externalListener: OnItemSelectedListener? = null

    init {
        // 初始樣式設定
        isItemActiveIndicatorEnabled = false
        
        // 監聽選取事件
        super.setOnItemSelectedListener { item ->
            animateItem(item.itemId)
            externalListener?.onNavigationItemSelected(item) ?: true
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        val density = context.resources.displayMetrics.density
        val totalHeightDp = h / density

        // 精細化間距分配
        when {
            totalHeightDp <= 60 -> {
                // 緊湊模式 (60dp)
                itemIconSize = (20 * density).toInt()
                itemPaddingTop = (10 * density).toInt()
                itemPaddingBottom = (8 * density).toInt()
            }
            totalHeightDp <= 70 -> {
                // 黃金比例模式 (66dp - 70dp) - 推薦值
                itemIconSize = (24 * density).toInt()
                itemPaddingTop = (12 * density).toInt()
                itemPaddingBottom = (10 * density).toInt()
            }
            else -> {
                // 寬鬆模式 (80dp) - 增加 Padding 來「擠壓」中間間距，避免分太開
                itemIconSize = (24 * density).toInt()
                itemPaddingTop = (18 * density).toInt()
                itemPaddingBottom = (16 * density).toInt()
            }
        }
    }

    override fun setOnItemSelectedListener(listener: NavigationBarView.OnItemSelectedListener?) {
        this.externalListener = listener
    }

    private fun animateItem(itemId: Int) {
        val itemView = findViewById<View>(itemId) ?: return
        itemView.animate()
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(100)
            .withEndAction {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
            }
            .start()
    }
}
