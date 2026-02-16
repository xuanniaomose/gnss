package xuanniao.map.gnss.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import xuanniao.map.gnss.R
import java.util.function.IntConsumer
import java.util.stream.IntStream


class SlideButtonGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
    private val tag = "SlideButtonGroup"
    private var rootButtonContainer: LinearLayout
    private val animationDuration = 200L
//    private var with = 0
//    private var height = dp2px(80)
//    private var withWeight = 1f
//    private var heightWeight = 0f
    // 状态记录
    private var selectedMainButton: Button? = null
    private var isExpanded = false
    var originalMainButtons: MutableList<Button> = mutableListOf()
    private var expandedButtons: MutableList<Button> = mutableListOf()
    // 新增：缓存每个主按钮的初始位置（布局完成后记录）
    private val originalButtonXMap = mutableMapOf<Button, Float>()
    private var subButtonMap = mutableMapOf<Int, List<Button>>()
    private var mainSlidedNum = 0
    private var subSlidedNum = 0
    // 控件是否初始化
    private var isInitiated = false
    private var buttonColorList: ArrayList<Int>? = null
    private var buttonTextColorList: ColorStateList? = null

    // 保存接口实例
    private var clickListener: OnMainButtonClickListener? = null
    fun setOnMainButtonClickListener(listener: OnMainButtonClickListener) {
        this.clickListener = listener
    }
    private var longClickListener: OnMainButtonLongClickListener? = null
    fun setOnMainButtonLongClickListener(listener: OnMainButtonLongClickListener) {
        this.longClickListener = listener
    }


    init {
        inflate(context, R.layout.slide_button_group, this)
        buttonColorList = arrayListOf<Int>(
            ContextCompat.getColor(context, R.color.green_500),
            ContextCompat.getColor(context,R.color.red_700),
            ContextCompat.getColor(context,R.color.yellow_700),
            ContextCompat.getColor(context,R.color.blue_700),
            ContextCompat.getColor(context,R.color.purple_500)
        )
        buttonTextColorList = ContextCompat.getColorStateList(context, R.color.button_text_color)
        rootButtonContainer = findViewById(R.id.rootButtonContainer)
        // 监听布局完成事件，缓存初始位置
        viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                // 只有集合为空时才缓存
                if (originalButtonXMap.isEmpty() && originalMainButtons.isNotEmpty()) {
                    // 缓存所有主按钮的初始X坐标
                    cacheOriginalButtonPositions()
                }
                // 移除监听，避免重复执行
                viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
    }

    /**
     * 缓存所有主按钮的初始X坐标（布局完成后执行）
     */
    private fun cacheOriginalButtonPositions() {
        for (button in originalMainButtons) {
            originalButtonXMap[button] = button.x
        }
    }

    // 对外提供初始化方法，替代直接调用addMainButton
    fun initMainButtons(buttonConfig: List<Pair<String, List<Button>>>) {
        if (isInitiated) return // 已初始化则直接返回

        // 清空容器和集合（防止重建后残留）
        rootButtonContainer.removeAllViews()
        originalMainButtons.clear()
        originalButtonXMap.clear()

        // 批量添加主按钮
        buttonConfig.forEach { (text, childButtons) ->
            addMainButton(text, childButtons)
        }
        isInitiated = true
    }

    // 控件内部重写状态保存
    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        val bundle = Bundle()
        // 保存核心状态（如是否展开、选中的按钮文本、是否已初始化）
        bundle.putBoolean("isExpanded", isExpanded)
        bundle.putBoolean("isInitiated", isInitiated)
        (selectedMainButton?.tag as Int?)?.let {
            bundle.putInt("selectedButtonTag", it)
        }
        // 保存父类状态
        bundle.putParcelable("superState", superState)
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        var superState = state
        if (state is Bundle) {
            // 恢复状态
            isExpanded = state.getBoolean("isExpanded")
            isInitiated = state.getBoolean("isInitiated")
            val selectedTag = state.getInt("selectedButtonTag")
            // 恢复选中的按钮（需根据文本匹配）
            selectedMainButton = originalMainButtons.find { it.tag == selectedTag }
            // 恢复父类状态
            superState = state.getParcelable("superState")
        }
        super.onRestoreInstanceState(superState)
    }

    /**
     * 添加主按钮（初始化调用）
     */
    fun addMainButton(buttonText: String, childButtons: List<Button>?) {
        val mainButton = Button(context).apply {
            text = buttonText
            setTextColor(buttonTextColorList)
            layoutParams = LinearLayout.LayoutParams(
                0, dp2px(70), 1f
            ).apply {
                setMargins(dp2px(1),0,dp2px(1),0)
                setPadding(0,0,0,0)
            }
            setOnClickListener {
                if (this == selectedMainButton && isExpanded) {
                    // 点击已选中的主按钮：重置
                    resetState()
                } else if (this != selectedMainButton && !isExpanded) {
                    // 确保初始位置已缓存，否则先缓存
                    if (originalButtonXMap.isEmpty()) {
                        cacheOriginalButtonPositions()
                    }
                    // 点击未选中的主按钮：滑动+展开
                    onMainButtonSelect(this)
                }
            }
            setOnLongClickListener {
                longClickListener?.onMainButtonLongClick(
                    this)?: false
            }
            if (buttonColorList != null) {
                val targetColor = buttonColorList!![originalMainButtons.size]
                backgroundTintList = ColorStateList.valueOf(targetColor)
            }
            tag = originalMainButtons.size
        }
        if (childButtons == null) {
            subButtonMap[mainButton.tag as Int] = mutableListOf()
        } else {
            // 设置子按钮颜色
            IntStream.range(0, childButtons.size)
                .forEach(IntConsumer { index: Int ->
                    val childButton: Button = childButtons[index]
                    val targetColor = buttonColorList!![index]
                    childButton.backgroundTintList =
                        ColorStateList.valueOf(targetColor)
                    childButton.setTextColor(buttonTextColorList)
                })
            subButtonMap[mainButton.tag as Int] = childButtons
        }
//        Log.d(tag, "添加了主按钮 ${mainButton.tag}")
        rootButtonContainer.addView(mainButton)
        originalMainButtons.add(mainButton)
    }

    /**
     * 主按钮选中逻辑
     */
    private fun onMainButtonSelect(clickedButton: Button) {
        if (clickListener?.onMainButtonSelected(
                clickedButton) == false) return
        if (subButtonMap[clickedButton.tag] == null ||
            subButtonMap[clickedButton.tag]?.isEmpty() == true
        ) return
        // 为所有主按钮执行滑动动画
        for (button in originalMainButtons) {
            if (button == clickedButton) {
                // 选中的主按钮：滑到最左侧，强制保持可见
                button.visibility = View.VISIBLE
                mainButtonSlideAnimate(button, button.x,
                    true, true)
            } else {
                // 其他主按钮：滑出容器右侧并隐藏
                mainButtonSlideAnimate(button, button.x,
                    false, true)
            }
        }
    }

    /**
     * 按钮滑动动画
     */
    private fun mainButtonSlideAnimate(
        button: Button,
        targetX: Float,
        isKeepVisible: Boolean,
        isToSub: Boolean
    ) {
        // 动画执行前强制设置按钮可见，避免消失
        button.visibility = View.VISIBLE
        button.animate()
            .x(targetX)
            .alpha(1f)
            .setDuration(animationDuration)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (isToSub) toSub(button, isKeepVisible)
                    else toMain()
                }
            })
            .start()
    }

    private fun toSub(button: Button, isKeepVisible: Boolean) {
        if (!isKeepVisible) {
            // 非选中按钮动画结束后才隐藏
            button.visibility = View.GONE
//            Log.d(tag, "按钮的tag:${button.tag}")
        } else {
            // 选中按钮始终保持可见
            button.visibility = View.VISIBLE
            selectedMainButton = button
//            Log.d(tag, "按钮的tag:${button.tag}")
            subButtonMap[button.tag as Int]?.let {
                addExpandedChildButtons(button, it)
            }
        }
    }

    private fun toMain() {
        // 判断是否全部滑动完成了
        mainSlidedNum += 1
//        Log.d(tag, "主按钮滑动完成个数：$mainSlidedNum")
        if (mainSlidedNum == originalMainButtons.size) {
            mainSlidedNum = 0
            isExpanded = false
        }
    }

    /**
     * 在选中主按钮右侧添加子按钮
     */
    private fun addExpandedChildButtons(mainButton: Button, childButtons: List<Button>) {
        expandedButtons.forEach { rootButtonContainer.removeView(it) }
        expandedButtons.clear()

        childButtons.forEachIndexed { index, button ->
            button.layoutParams = LinearLayout.LayoutParams(
//                with, height, withWeight
                0, dp2px(70), 1f
            ).apply {
                setMargins(dp2px(2),0,dp2px(2),0)
                setPadding(0,0,0,0)
            }
            button.alpha = 0f
            button.scaleX = 0.5f
            button.scaleY = 0.5f
            // 添加到主按钮右侧
            rootButtonContainer.addView(button,
                rootButtonContainer.indexOfChild(mainButton) + 1 + index)
            expandedButtons.add(button)

            // 子按钮展开动画
            button.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(animationDuration)
                .setStartDelay(index * 50L)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        button.visibility = View.VISIBLE
                        subSlidedNum ++
//                        Log.d(tag, "子按钮滑动完成个数：$subSlidedNum")
                        if (subSlidedNum == subButtonMap[mainButton.tag]?.size) {
                            subSlidedNum = 0
                            isExpanded = true
                            Log.d(tag, "全部子按钮滑动完成")
                        }
                    }
                })
                .start()
        }
    }

    /**
     * 返回主按钮界面
     */
    fun resetState() {
        isExpanded = false
        if (expandedButtons.isEmpty()) {
            originalMainButtons.forEach { button ->
                val originalX = originalButtonXMap[button] ?: 0f
                mainButtonSlideAnimate(
                    button, originalX,
                    true, false
                )
            }
        }
        expandedButtons.forEach { button ->
            button.animate()
                .alpha(0f)
                .scaleX(0.5f)
                .scaleY(0.5f)
                .setDuration(animationDuration)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        reset(button)
                    }
                })
                .start()
        }
        selectedMainButton?.let {
            clickListener?.onMainButtonReset(it)
        }
    }

    private fun reset(button: Button) {
        rootButtonContainer.removeView(button)
        subSlidedNum++
        // 都移除完了再添加主按钮（因为移除完之前子按钮还占着父视图容器）
        if (subSlidedNum == expandedButtons.size) {
            // 恢复所有主按钮的位置和可见性（使用缓存的初始位置）
            Log.d(tag, "开始恢复主按钮")
            for (i in 0..<originalMainButtons.size) {
                val button = originalMainButtons[i]
                val originalX = originalButtonXMap[button] ?: 0f
                mainButtonSlideAnimate(button, originalX,
                    true, false)
            }
            subSlidedNum = 0
            // 清空选中状态
            selectedMainButton = null
            // 清空子按钮列表
            expandedButtons.clear()
        }
    }

    /**
     * 根据Tag在按钮组中查找子按钮
     */
    fun findSubButtonByTag(tag: String): Button? {
        // 获取按钮组的根容器
        val rootContainer = findViewById<LinearLayout>(R.id.rootButtonContainer)
        // 遍历容器内所有子View，找到匹配Tag的按钮
        for (i in 0 until rootContainer.childCount) {
            val view = rootContainer.getChildAt(i)
            if (view is Button && view.tag == tag) {
                return view
            }
        }
        return null
    }

    /**
     * 设置主按钮点击监听器
     * @param mainIndex 所属的主按钮序号
     * @param onClick 监听器
     */
    fun setMainButtonClickListener(
        mainIndex: Int,
        onClick: (View) -> Unit
    ) {
        if (mainIndex in 0 until originalMainButtons.size) {
            originalMainButtons[mainIndex].setOnClickListener(onClick)
        } else {
            Log.e(tag, "主按钮序号错误")
        }
    }

    /** 设置主按钮点击监听器
     * @param mainIndex 所属的主按钮序号
     * @param onClick 监听器
     */
    fun setMainButtonLongClickListener(
        mainIndex: Int,
        onClick: (View) -> Boolean
    ) {
        if (mainIndex in 0 until originalMainButtons.size) {
            originalMainButtons[mainIndex]
                .setOnLongClickListener(onClick)
        } else {
            Log.e(tag, "主按钮序号错误")
        }
    }

    /**
     * 设置子按钮点击监听器
     * @param mainIndex 所属的主按钮序号
     * @param subIndex 子按钮序号
     * @param onClick 监听器
     */
    fun setSubButtonClickListener(
        mainIndex: Int,
        subIndex: Int,
        onClick: (View) -> Unit
    ) {
        if (mainIndex in 0 until originalMainButtons.size) {
            val subButtons = subButtonMap[mainIndex]
            if (subIndex in 0 until
                (subButtonMap[mainIndex]?.size ?: 0)) {
                subButtons?.get(subIndex)?.setOnClickListener {
                    onClick(it)
                }
            } else {
                Log.e(tag, "子按钮序号错误")
            }
        } else {
            Log.e(tag, "主按钮序号错误")
        }
    }

    /**
     * 设置按钮的宽高
     * @param with 宽度(dp)
     * @param height 高度(dp)
     * @param withWeight 横向比例
     * @param heightWeight 纵向比例
     */
//    fun setWightHeight(
//        with: Int,
//        height: Int,
//        withWeight: Float?,
//        heightWeight: Float?
//    ) {
//        this.with = dp2px(with)
//        this.height = dp2px(height)
//        withWeight?.let { this.withWeight = withWeight }
//        heightWeight?.let { this.heightWeight = heightWeight }
//    }

    private fun dp2px(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
}


// 回调接口
interface OnMainButtonClickListener {
    // 主按钮选中时的回调
    fun onMainButtonSelected(mainButton: Button): Boolean
    // 主按钮触发重置时的回调（可选）
    fun onMainButtonReset(mainButton: Button)
}

// 回调接口
interface OnMainButtonLongClickListener {
    // 主按钮选中时的回调
    fun onMainButtonLongClick(mainButton: Button): Boolean
}