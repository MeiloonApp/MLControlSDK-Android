package com.meiloon.mlcontrolcore_aos.widget

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.core.view.isVisible
import androidx.core.content.ContextCompat
import com.meiloon.mlcontrolcore_aos.R

/**
 * 具備標題伸縮效果的自定義 NestedScrollView
 * 會自動在父容器中生成並管理頂部的小標題 Bar
 */
class DynamicTitleScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : NestedScrollView(context, attrs, defStyleAttr) {

    private var largeTitleView: View? = null
    private var topBarView: View? = null

    /**
     * 設定標題動畫聯動（自動生成 TopBar）
     * @param largeTitle 內容區塊的大標題 TextView
     */
    fun setupTitleAnimation(largeTitle: TextView) {
        this.largeTitleView = largeTitle
        val titleText = largeTitle.text.toString()

        // 如果已經有 TopBar 了就先移除
        topBarView?.let { (it.parent as? ViewGroup)?.removeView(it) }

        // 1. 動態建立 TopBar 容器
        val topBar = FrameLayout(context).apply {
            val height = (context.resources.displayMetrics.density * 56).toInt() // 標準 ActionBar 高度
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, height)
            setBackgroundColor(ContextCompat.getColor(context, R.color.translucent_gray))
            visibility = INVISIBLE
            elevation = 8f // 確保在最上層
        }

        // 2. 建立小標題文字
        val smallTitle = TextView(context).apply {
            text = titleText
            textSize = 18f
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        topBar.addView(smallTitle)

        // 3. 尋找父容器並注入 TopBar
        post {
            (parent as? ViewGroup)?.let { parentGroup ->
                parentGroup.addView(topBar)
                this.topBarView = topBar
                setupScrollListener()
            }
        }
    }

    private fun setupScrollListener() {
        setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val largeTitleRef = largeTitleView ?: return@setOnScrollChangeListener
            val topBarRef = topBarView ?: return@setOnScrollChangeListener

            // 當大標題完全滑出視線時 (scrollY 超過大標題底部)
            val threshold = largeTitleRef.top + largeTitleRef.height

            if (scrollY > threshold) {
                if (topBarRef.visibility != VISIBLE) {
                    topBarRef.visibility = VISIBLE
                    topBarRef.alpha = 0f
                    topBarRef.animate().alpha(1f).setDuration(200).start()
                }
            } else {
                if (topBarRef.isVisible) {
                    topBarRef.animate().alpha(0f).setDuration(200).withEndAction {
                        topBarRef.visibility = View.INVISIBLE
                    }.start()
                }
            }
        }
    }
}
