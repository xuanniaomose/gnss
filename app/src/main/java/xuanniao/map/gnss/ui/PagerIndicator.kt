package xuanniao.map.gnss.ui

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import xuanniao.map.gnss.R

class PagerIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    private val tag = "指示器"
    // 指示点默认大小（8dp）
    private val dotSize = 8
    // 指示点之间的间距（5dp）
    private val dotMargin = 5
    // 当前选中的位置
    private var currentPosition = 0

    init {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER
        // 关键：设置控件本身的默认宽高为wrap_content（避免占满屏幕）
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    }

    /**
     * 初始化指示点（核心修复：严格设置指示点宽高）
     */
    fun setIndicatorCount(count: Int) {
        removeAllViews()
        if (count <= 0) return

        // 1. 转换dp为px（确保尺寸单位正确）
        val sizePx = dp2px(dotSize)
        val marginPx = dp2px(dotMargin)

        // 2. 为每个指示点设置固定宽高的布局参数（关键！）
        val dotLayoutParams = LayoutParams(sizePx, sizePx).apply {
            setMargins(marginPx, 0, marginPx, 0)
        }

        // 3. 创建指示点并强制设置固定宽高
        for (i in 0 until count) {
            val dot = View(context).apply {
                // 设置圆点样式
                setBackgroundResource(R.drawable.indicator_dot)
                // 强制绑定固定宽高的布局参数
                this.layoutParams = dotLayoutParams
                // 默认选中第一个
                isSelected = (i == 0)
            }
            addView(dot)
        }
    }

    /**
     * 更新选中的指示点位置
     */
    fun updateSelectedPosition(position: Int) {
        Log.d(tag, "position = $position")
        if (position < 0 || position >= childCount || position == currentPosition) return
        getChildAt(currentPosition)?.isSelected = false
        getChildAt(position)?.isSelected = true
        currentPosition = position
    }

    /**
     * 重置指示点
     */
    fun reset() {
        currentPosition = 0
        for (i in 0 until childCount) {
            getChildAt(i)?.isSelected = (i == 0)
        }
    }

    /**
     * 可靠的dp转px工具方法（修复可能的单位转换错误）
     */
    private fun dp2px(dp: Int): Int {
        return try {
            val density = context.resources.displayMetrics.density
            (dp * density + 0.5f).toInt()
        } catch (e: Exception) {
            dp // 异常时降级返回原始值
        }
    }
}