package xuanniao.map.gnss

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import xuanniao.map.gnss.PermissionUtil.PermissionCallback
import xuanniao.map.gnss.databinding.ActivityGnssBinding
import xuanniao.map.gnss.ui.DisplayFragment
import xuanniao.map.gnss.ui.MapFragment
import xuanniao.map.gnss.ui.ViewPagerAdapter
import java.util.*
import kotlin.math.sqrt


class GnssActivity : AppCompatActivity() {
    var tag : String? = "主面板"
    private lateinit var binding: ActivityGnssBinding
    private lateinit var gnssManager: GnssManager
    private lateinit var pagerAdapter: ViewPagerAdapter
    private var displayFragment: DisplayFragment? = null
    private var mapFragment: MapFragment? = null
    lateinit var prefs: SharedPreferences
    private lateinit var gnssViewModel: GnssViewModel
    private var trip: Trip? = null
    private var location: Location? = null
    private var isMap = false
    private var isLocating: Boolean = false
    private var isTripping: Boolean = false
    private var isStationary: Boolean = true
    private var isLocked: Boolean = false
    private var headingDegrees: Float = 0f


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGnssBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        trip = Trip(this)
        location = trip?.initializeDistance()

        gnssViewModel = ViewModelProvider(this)[GnssViewModel::class.java]

        // 初始化Adapter
        pagerAdapter = ViewPagerAdapter(this)
        // 绑定Adapter到ViewPager
        binding.vpTop.adapter = pagerAdapter
        displayFragment = pagerAdapter.getDisplayFragment()
        mapFragment = pagerAdapter.getMapFragment()

        binding.vpTop.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback()  {
            // 页面选中时回调（对应旧 API 的 onPageSelected）
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // 处理页面选中逻辑，比如更新底部导航、标题等
                Log.d(tag, "当前选中页面：$position")
            }
        })

        // 创建OnBackPressedCallback，并设置是否启用默认处理
        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // TODO 解除fragment中的map
                onBackPressedDispatcher.onBackPressed()
                location?.let { prefs.edit {
                    LocationPrefsManager.setLocation(prefs, it)
                } }
            }
        }
        // 将回调添加到OnBackPressedDispatcher
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        binding.vpTop.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (position == 1) isMap = true
                Log.d(tag, position.toString())
            }
        })
    }

    override fun onResume() {
        super.onResume()
        locationPermissionCheck()
        initView()
    }

    private fun initView() {
        // 打开地图
        binding.btnMap.setOnClickListener {
            if (isLocked) {
                binding.btnTrip.isEnabled = true
                binding.btnSettings.isEnabled = true
                Toast.makeText(this@GnssActivity,
                    "按钮已解锁", Toast.LENGTH_LONG).show()
            }
        }
        // 长按锁定按钮
        binding.btnMap.setOnLongClickListener {
            if (!isLocked) {
                binding.btnTrip.isEnabled = false
                binding.btnSettings.isEnabled = false
                Toast.makeText(this@GnssActivity,
                    "按钮已锁定", Toast.LENGTH_LONG).show()
            }
            true
        }
        binding.btnTrip.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) { startTrip() }
            else { stopTrip() }
        }
        binding.btnCleanTrip.setOnClickListener { resetTrip() }
        binding.btnStopTrip.setOnClickListener { stopTrip() }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    /**
     * 初始化GNSS管理器，设置数据回调
     */
    private fun initGnssManager() {
        gnssManager = GnssManager(this)
        // 设置位置回调，更新 UI 数据
        gnssManager.setOnLocationChangeListener { location ->
            setGnssUpdated(location)
            gnssViewModel.updateLocationData(location)
        }
        gnssManager.setOnSensorChangeListener { event ->
            runOnUiThread { setSensorUpdate(event) }
        }
        when (gnssManager.startLocation()) {
            1 -> isLocating = true
            -1 -> { // 未授权
                isLocating = false
                PermissionUtil.checkPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
            }
            -2 -> { // 定位未开启
                isLocating = false
                PermissionUtil.showLocationServiceEnableDialog(this)
            }
        }
    }

    /**
     * 显示传感器数据
     */
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
            // 旋转矢量传感器
            Sensor.TYPE_ROTATION_VECTOR -> {
                val attitude = updateAttitude(event.values)
                headingDegrees = attitudeToBearing(attitude[0])
                location?.let { it.bearing = headingDegrees }
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
     * 接收实时定位数据
     */
    @SuppressLint("SetTextI18n", "DefaultLocale")
    fun setGnssUpdated(locationData: Location) {
        location = locationData
        binding.tvAvgSpeed.text = String.format("%.1f", locationData.speed / 3.6)
        binding.tvLon.text = "%.8f".format(locationData.longitude)
        binding.tvLat.text = "%.8f".format(locationData.latitude)
        binding.tvAltitude.text = "%.2f".format(locationData.altitude)
        binding.tvAccuracy.text = "%.2f".format(locationData.accuracy) + "m"
        binding.tvSatellite.text = locationData.provider
        binding.tvSatelliteTime.text = longToStringTime(locationData.time)

        if (isTripping) {
            trip?.let { binding.tvTiming.text = it.timing(locationData.time) }
            if (!isStationary) binding.tvMileage.text = "%.2f".format(
                trip?.accumulate(locationData)
            ) + "m"
        }
        if (!isStationary) {
            binding.tvBearing.text = locationData.bearing.toString()
            binding.tvDirection.text = bearingToDirection(locationData.bearing).dir
            trip?.update(locationData)
        }
    }

    /**
     * 开启旅程（开启计时，开启里程叠加）
     */
    fun startTrip() {
        if (trip == null || isTripping) return
        when (trip!!.startTrip()) {
            1 -> {
                isTripping = true
                binding.btnStopTrip.text = "停止旅程"
                binding.btnCleanTrip.text = "重置旅程"
                binding.btnStopTrip.isEnabled = true
                binding.btnCleanTrip.isEnabled = true
                gnssViewModel.updateModeData(isTripping)
            }
            -1 -> {
                isTripping = false
                Toast.makeText(
                    this, "请等待卫星信号接收后再开启旅程",
                    Toast.LENGTH_SHORT
                ).show()
                binding.btnTrip.isChecked = false
            }
            -2 -> {
                isTripping = false
                Toast.makeText(
                    this, "未授予后台定位权限，请将应用置于前台",
                    Toast.LENGTH_SHORT
                ).show()
                binding.btnTrip.isChecked = false
            }
        }
    }

    @SuppressLint("SetTextI18n")
    fun resetTrip() {
        if (!isTripping) return
        trip?.cleanTrip()
        binding.tvTiming.text = "00:00:00"
        binding.tvMileage.text = "0m"
    }

    /**
     * 结束旅程（停止计时，关闭里程叠加，重置按钮状态）
     */
    fun stopTrip() {
        if (!isTripping) return
        trip?.stopTrip()
        isTripping = false
        binding.btnStopTrip.text = "--"
        binding.btnCleanTrip.text = "--"
        binding.btnStopTrip.isEnabled = false
        binding.btnCleanTrip.isEnabled = false
        gnssViewModel.updateModeData(isTripping)
    }

    fun locationPermissionCheck() {
        PermissionUtil.checkAndRequestPermission(this,
            Manifest.permission.ACCESS_FINE_LOCATION,
            "精确定位", "高精度定位需要精确定位的权限",
            object : PermissionCallback {
                override fun onPermissionGranted() { initGnssManager() }
                override fun onPermissionDenied() { }
                override fun onPermissionPermanentlyDenied() { }
            }
        )
    }

    fun backgroundPermissionToStartTrip(): Int {
        var returnCode: Int = -2
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PermissionUtil.checkAndRequestPermission(this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                "后台定位", "旅程记录需要后台定位的权限",
                object : PermissionCallback {
                    override fun onPermissionGranted() {
                        trip?.let {
                            returnCode = it.startTrip()
                        }
                    }
                    override fun onPermissionDenied() {
                        trip?.let { returnCode = it.startTrip() }
                    }
                    override fun onPermissionPermanentlyDenied() {
                        trip?.let { returnCode = it.startTrip() }
                    }
                }
            )
        }
        return returnCode
    }
}