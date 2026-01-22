package xuanniao.map.gnss

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 基于安卓原生 LocationManager 的 GNSS 位置服务模块，与FusedLocation版本接口兼容
 */
class GNSSLocationManagerService(private val context: Context) {
    var tag : String? = "定位管理"
    // 原生定位管理器
    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    // 定位提供者（优先GNSS，其次网络定位）
    private val GNSS_PROVIDER = LocationManager.GPS_PROVIDER
    private val NETWORK_PROVIDER = LocationManager.NETWORK_PROVIDER

    // 定位更新配置参数
    private val MIN_TIME_BETWEEN_UPDATES: Long = 500 // 最小更新间隔（0.5秒）
    private val MIN_DISTANCE_BETWEEN_UPDATES: Float = 0f // 最小位移变化（0米）

    // 上层回调接口
    private var gnssLocationCallback: GnssLocationCallback? = null

    // 协程作用域（用于异步处理数据）
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // 速度平滑处理：滑动平均算法
    private val speedBuffer = mutableListOf<Float>()


    // 标记是否正在定位
    var isLocating = false
        private set

    // 原生定位监听器
    private lateinit var locationListener: LocationListener

    /**
     * 初始化定位监听器
     */
    init {
        initLocationListener()
    }

    /**
     * 设置上层回调（供UI层接收数据）
     */
    fun setGNSSLocationCallback(callback: GnssLocationCallback) {
        this.gnssLocationCallback = callback
    }

    /**
     * 开始GNSS定位（优先GNSS，GNSS不可用时切换到网络定位）
     */
    fun startLocationUpdates() {
        if (isLocating) {
            Log.i(tag, "正在定位")
            return
        }
        // 检查GNSS是否开启
        if (!locationManager.isProviderEnabled(GNSS_PROVIDER)) {
            Log.i(tag, "GNSS未开启")
            gnssLocationCallback?.onLocationStatusChanged(0)
            return
        }
        Log.i(tag, "开启定位")
        try {
            // 1. 注册GNSS定位监听器
            locationManager.requestLocationUpdates(
                GNSS_PROVIDER,
                MIN_TIME_BETWEEN_UPDATES,
                MIN_DISTANCE_BETWEEN_UPDATES,
                locationListener
            )
            // 2. （可选）注册网络定位监听器作为备用（当GNSS信号弱时补充）
            if (locationManager.isProviderEnabled(NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    NETWORK_PROVIDER,
                    MIN_TIME_BETWEEN_UPDATES * 2, // 网络定位更新间隔稍长
                    MIN_DISTANCE_BETWEEN_UPDATES,
                    locationListener
                )
            }
            isLocating = true
            gnssLocationCallback?.onLocationStatusChanged(1)
        } catch (e: SecurityException) {
            // 无定位权限，通知上层
            gnssLocationCallback?.onLocationStatusChanged(-1)
            e.printStackTrace()
        }
    }

    /**
     * 停止GNSS定位，注销监听器
     */
    fun stopLocationUpdates() {
        if (!isLocating) return

        // 注销所有定位监听器
        locationManager.removeUpdates(locationListener)
        isLocating = false
        speedBuffer.clear() // 清空速度缓存
        coroutineScope.cancel() // 取消协程
        gnssLocationCallback?.onLocationStatusChanged(0)
    }

    /**
     * 初始化原生LocationListener，处理原始定位数据
     */
    private fun initLocationListener() {
        locationListener = object : LocationListener {
            // 定位数据更新时回调（核心方法）
            override fun onLocationChanged(location: Location) {
                // 异步处理定位数据，避免阻塞主线程
                coroutineScope.launch {
                    gnssLocationCallback?.let { processLocationData(
                        location, it, speedBuffer) }
                }
            }

            // 定位提供者状态变化时回调（如GNSS从关闭到开启）
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                // TODO 这里的逻辑需要整合到实时定位数据回调中
//                val statusMsg = when (status) {
//                    android.location.LocationProvider.AVAILABLE -> "GNSS信号可用"
//                    android.location.LocationProvider.OUT_OF_SERVICE -> "GNSS信号超出服务范围"
//                    android.location.LocationProvider.TEMPORARILY_UNAVAILABLE -> "GNSS信号暂时不可用"
//                    else -> "GNSS状态未知"
//                }
//
//                val signalStrength = when (status) {
//                    android.location.LocationProvider.AVAILABLE -> GnssSignalStrength.STRONG
//                    android.location.LocationProvider.TEMPORARILY_UNAVAILABLE -> GnssSignalStrength.WEAK
//                    else -> GnssSignalStrength.LOST
//                }

                // 通知上层状态变化
                CoroutineScope(Dispatchers.Main).launch {
                    gnssLocationCallback?.onLocationStatusChanged(1)
                }
            }

            // GNSS开启时回调
            override fun onProviderEnabled(provider: String) {
                CoroutineScope(Dispatchers.Main).launch {
                    gnssLocationCallback?.onLocationStatusChanged(1)
                }
            }

            // GNSS关闭时回调
            override fun onProviderDisabled(provider: String) {
                CoroutineScope(Dispatchers.Main).launch {
                    gnssLocationCallback?.onLocationStatusChanged(0)
                }
                // 若GNSS关闭，停止定位
                if (provider == GNSS_PROVIDER) {
                    stopLocationUpdates()
                }
            }
        }
    }

    /**
     * 释放资源（避免内存泄漏）
     */
    fun release() {
        stopLocationUpdates()
        gnssLocationCallback = null
    }

    /**
     * 辅助方法：检查GNSS是否开启（供上层调用，引导用户开启GNSS）
     */
    fun isGnssEnabled(): Boolean {
        return locationManager.isProviderEnabled(GNSS_PROVIDER)
    }
}