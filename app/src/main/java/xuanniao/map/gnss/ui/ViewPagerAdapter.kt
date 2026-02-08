package xuanniao.map.gnss.ui

import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import java.lang.ref.WeakReference

class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    private val tag = "ViewPager适配器"
    // 用 WeakReference 保存 Fragment 引用（避免内存泄漏）
    var displayFragmentRef: WeakReference<DisplayFragment>? = null
    var mapFragmentRef: WeakReference<MapFragment>? = null
    var location: Location? = null
    // 获取Fragment数量
    override fun getItemCount(): Int = 2

    // 创建对应位置的Fragment
    override fun createFragment(position: Int): Fragment {
        Log.d(tag, "创建fragment")
        when (position) {
            0 -> {
                val fragment = DisplayFragment.newInstance("display")
                // 用 WeakReference 包装，保存弱引用
                displayFragmentRef = WeakReference(fragment)
                return fragment
            }
            1 -> {
                val fragment = MapFragment.newInstance("webMap")
                mapFragmentRef = WeakReference(fragment)
                return fragment
            }
            else -> throw IllegalArgumentException("无效位置：$position")
        }
    }

    // 类型安全的获取方法：直接返回具体类型，无需强转
    fun getDisplayFragment(): DisplayFragment? = displayFragmentRef?.get()
    fun getMapFragment(): MapFragment? = mapFragmentRef?.get()

    fun setFirstLocation(location: Location) {
        this.location = location
    }
}