package xuanniao.map.gnss

import android.Manifest
import android.annotation.SuppressLint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import xuanniao.map.gnss.databinding.ActivityMainBinding
import xuanniao.map.gnss.PermissionUtil.PermissionCallback
import kotlin.math.sqrt

class GnssActivity : AppCompatActivity() {
    var tag : String? = "主面板"
    private lateinit var binding: ActivityMainBinding
    private lateinit var gnssManager: GnssManager
    private var trip: Trip? = null
    private var location: Location? = null
    private var isLocating: Boolean = false
    private var isTripping: Boolean = false
    private var isStationary: Boolean = true
    private var headingDegrees: Float = 0f


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        trip = Trip()
        trip?.initializeDistance()

        // 开始/停止 定位（先检查GNSS是否开启）
        binding.btnSwitchLoc.setOnCheckedChangeListener { _, isChecked ->
            if (!isLocating) {
                Log.d(tag, "开始定位")
                permissionCheck(isChecked)
            } else {
                Log.d(tag, "停止定位")
                binding.tvDirection.text = "--"
                binding.tvSatellite.text = "-"
                binding.btnSwitchLoc.isChecked = false
                isLocating = false
                gnssManager.stopLocation()
            }
        }

        // 开启/停止 旅程
        binding.btnTrip.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {startTrip()}
            else { stopTrip() }
        }

        // 重置旅程
        binding.btnCleanTrip.setOnCheckedChangeListener { _, _ ->
            resetTrip()
        }
    }

    /**
     * 初始化融合管理器，设置数据回调
     */
    private fun initGnssManager() {
        gnssManager = GnssManager(applicationContext)
        // 设置位置回调，更新 UI 数据
        gnssManager.setOnLocationChangeListener { location ->
            runOnUiThread { setLocationUpdated(location) }
        }
        gnssManager.setOnSensorChangeListener { event ->
            runOnUiThread { setSensorUpdate(event) }
        }
        when (gnssManager.startLocation()) {
            1 -> isLocating = true
            -1 -> { // 未授权
                isLocating = false
                binding.btnSwitchLoc.isChecked = false
                PermissionUtil.checkPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
            }
            -2 -> { // 定位未开启
                isLocating = false
                binding.btnSwitchLoc.isChecked = false
                PermissionUtil.showLocationServiceEnableDialog(this)
            }
        }
    }

    /**
     * 接收实时定位数据
     */
    @SuppressLint("SetTextI18n", "DefaultLocale")
    fun setLocationUpdated(locationData: Location) {
        location = locationData
        binding.tvSpeedKmh.text = String.format("%.1f", locationData.speed / 3.6)
        binding.tvAvgSpeed.text = String.format("%.1f", locationData.speed / 3.6)

        val lon = locationData.longitude
        val lat = locationData.latitude
        binding.tvLon.text = "%.8f".format(lon)
        binding.tvLat.text = "%.8f".format(lat)
        binding.tvAltitude.text = "%.2f".format(locationData.altitude)

        val accuracy = locationData.accuracy
        binding.tvAccuracy.text = "%.2f".format(accuracy) + "m"
        binding.tvSatellite.text = locationData.provider
        binding.tvSatelliteTime.text = longToStringTime(locationData.time)

        if (isTripping) {
            binding.tvTiming.text = trip!!.timing(locationData.time)
            if (!isStationary) binding.tvMileage.text = "%.2f".format(
                trip!!.accumulate(locationData)) + "m"
        }
        if (!isStationary) {
            binding.tvBearing.text = locationData.bearing.toString()
            binding.tvDirection.text = bearingToDirection(locationData.bearing).dir
            trip?.update(locationData)
        }
    }

    @SuppressLint("SetTextI18n")
    fun setSensorUpdate(event: SensorEvent?) {
        // 根据传感器类型处理数据
        if (event == null) return
        when (event.sensor.type) {
            // 线性加速度传感器：判断静止
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                location?.let { isStationary = detectZeroVelocity(
                    it, event.values)
                }
            }
//          // 磁力计
//            Sensor.TYPE_MAGNETIC_FIELD -> {
//                headingDegrees = analyzeMagneticField(event.values)
//            }
            // 旋转矢量传感器
            Sensor.TYPE_ROTATION_VECTOR -> {
                val attitude = updateAttitude(event.values)
                headingDegrees = attitudeToBearing(attitude[0])
                binding.tvBearing.text = "%.1f".format(headingDegrees)
                binding.tvDirection.text = bearingToDirection(headingDegrees).dir
            }
        }
        if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            // event.values 包含 [x, y, z] 三个轴的加速度，单位 m/s²
            val lx = event.values[0]
            val ly = event.values[1]
            val lz = event.values[2]
            val linearMagnitude = sqrt(lx * lx + ly * ly + lz * lz)
//            Log.d(tag, "系统加速度: %.5f".format(linearMagnitude))
            binding.tvAcceleration.text = "%.1f".format(linearMagnitude)
        }
    }

    /**
     * 开启旅程（开启计时，开启里程叠加）
     */
    fun startTrip() {
        when (backgroundPermissionToStartTrip()) {
            -1 ->
                Toast.makeText(
                    this, "请等待卫星信号接收后再开启旅程",
                    Toast.LENGTH_SHORT
                ).show()
            -2 ->
                Toast.makeText(
                    this, "未授予后台定位权限，请将应用置于前台",
                    Toast.LENGTH_SHORT
                ).show()
        }
        isTripping = true
        binding.btnCleanTrip.isChecked = true
    }

    /**
     * 结束旅程（停止计时，关闭里程叠加，把重置旅程按钮还原状态）
     */
    fun stopTrip() {
        if (!isTripping) return
        isTripping = false
        binding.btnCleanTrip.isChecked = false
    }

    fun permissionCheck(isChecked: Boolean) {
        PermissionUtil.checkAndRequestPermission(this,
            Manifest.permission.ACCESS_FINE_LOCATION,
            "精确定位", "高精度定位需要精确定位的权限",
            object : PermissionCallback {
                override fun onPermissionGranted() { initGnssManager() }
                override fun onPermissionDenied() {
                    if (isChecked) binding.btnSwitchLoc.isChecked = false
                }
                override fun onPermissionPermanentlyDenied() {
                    if (isChecked) binding.btnSwitchLoc.isChecked = false
                }
            }
        )
    }

    fun backgroundPermissionToStartTrip(): Int {
        var returnCode: Int = -2
        PermissionUtil.checkAndRequestPermission(this,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            "后台定位", "旅程记录需要后台定位的权限",
            object : PermissionCallback {
                override fun onPermissionGranted() {
                    trip?.let { returnCode = it.startTrip() }
                }
                override fun onPermissionDenied() {
                    trip?.let { returnCode = it.startTrip() }
                }
                override fun onPermissionPermanentlyDenied() {
                    trip?.let { returnCode = it.startTrip() }
                }
            }
        )
        return returnCode
    }

    @SuppressLint("SetTextI18n")
    fun resetTrip() {
        if (!isTripping) return
        trip?.cleanTrip()
        binding.tvTiming.text = "00:00:00"
        binding.tvMileage.text = "0m"
        binding.btnCleanTrip.isChecked = true
    }
}
