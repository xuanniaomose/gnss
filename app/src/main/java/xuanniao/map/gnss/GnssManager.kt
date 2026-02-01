package xuanniao.map.gnss

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log

/**
 * GNSS+各种传感器融合管理器
 * @param context 上下文（建议传Application Context避免内存泄漏）
 */
class GnssManager(private val context: Context): SensorEventListener, LocationListener {
    val tag: String = "定位管理器"
    var isLocating: Boolean = false
    // 传感器管理器
    private val sensorManager by lazy {
        context.getSystemService(SensorManager::class.java)
    }
    // 原生GNSS定位管理器
    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    // 定位结果回调
    fun interface OnLocationChangeListener {
        fun onLocationChanged(location: Location)
    }
    private var locationChangeListener: OnLocationChangeListener? = null
    fun setOnLocationChangeListener(listener: OnLocationChangeListener?) {
        this.locationChangeListener = listener
    }

    // 传感器结果回调
    fun interface OnSensorChangeListener {
        fun onSensorChanged(event: SensorEvent)
    }
    private var sensorChangeListener: OnSensorChangeListener? = null
    fun setOnSensorChangeListener(listener: OnSensorChangeListener?) {
        this.sensorChangeListener = listener
    }

    // 原生GNSS刷新率（ms），建议1000ms=1Hz（过高易跳变，过低无意义）
    private val GNSS_INTERVAL = 1000L
    // 原生GNSS最小距离变化（米），0表示只要有更新就回调（纯靠时间间隔控制）
    private val GNSS_MIN_DISTANCE = 0f
    // GNSS提供者（优先用GNSS_PROVIDER，兼容GPS_PROVIDER）
    private val GNSS_PROVIDER = LocationManager.GPS_PROVIDER
    private val NETWORK_PROVIDER = LocationManager.NETWORK_PROVIDER

    // 上一次传感器时间戳（ns），用于计算时间差Δt
    private var lastSensorTime: Long = 0L
    private var location: Location? = null


    /**
     * 启动定位（注册原生GNSS传感器）
     */
    fun startLocation(): Int {
        if (isLocating) {
            Log.d(tag, "正在定位")
            return 0
        }
        val sensors = listOf(
            Sensor.TYPE_LINEAR_ACCELERATION to SensorManager.SENSOR_DELAY_NORMAL,
            Sensor.TYPE_ROTATION_VECTOR to SensorManager.SENSOR_DELAY_NORMAL
        )
        sensors.forEach { (type, delay) ->
            sensorManager.getDefaultSensor(type)?.let { sensor ->
                sensorManager.registerListener(this, sensor, delay)
            } ?: run {
                Log.w(tag, "传感器类型： $type 不可用")
            }
        }
        // 原生GNSS监听器
        try {
            // 检查GNSS是否开启（避免崩溃）
            if (locationManager.isProviderEnabled(GNSS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    GNSS_PROVIDER,
                    GNSS_INTERVAL,
                    GNSS_MIN_DISTANCE,
                    this, // 回调到当前类的onLocationChanged方法
                    Looper.getMainLooper() // 主线程回调（方便更新UI/地图）
                )
                isLocating = true
                return 1
            }
            if (locationManager.isProviderEnabled(NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    NETWORK_PROVIDER,
                    GNSS_INTERVAL * 2, // 网络定位更新间隔稍长
                    GNSS_MIN_DISTANCE,
                    this,
                    Looper.getMainLooper()
                )
                isLocating = true
                return 1
            }
            return -2
        } catch (e: SecurityException) {
            Log.d(tag, "位置服务未授权：" + e.message)
            return -1
        }
    }

    /**
     * 停止定位（注销原生GNSS传感器）
     */
    fun stopLocation() {
        if (!isLocating) return
        // 注销传感器
        sensorManager.unregisterListener(this)
        // 注销原生GNSS监听器
        locationManager.removeUpdates(this)
        // 重置状态，避免下次启动数据混乱
        lastSensorTime = 0L
        location = null
        isLocating = false
    }

    /**
     * 传感器回调
     */
    override fun onSensorChanged(event: SensorEvent) {
        if (sensorChangeListener == null) return
        sensorChangeListener!!.onSensorChanged(event)
    }

    /**
     * 传感器精度变化，暂不处理
     */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * 原生GNSS回调
     */
    override fun onLocationChanged(locationData: Location) {
        // 原生数据回传
        if (locationChangeListener == null) return
        locationChangeListener!!.onLocationChanged(locationData)
    }

    // 以下是LocationListener的其他默认方法，暂无需实现，保持空实现即可
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}