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
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.json.JSONException
import org.json.JSONObject
import xuanniao.map.gnss.PermissionUtil.PermissionCallback
import xuanniao.map.gnss.databinding.ActivityGnssBinding
import java.io.IOException
import java.util.*
import kotlin.math.sqrt


class GnssActivity : AppCompatActivity() {
    var tag : String? = "主面板"
    private lateinit var binding: ActivityGnssBinding
    private lateinit var gnssManager: GnssManager
    lateinit var prefs: SharedPreferences
    private var trip: Trip? = null
    private var location: Location? = null
    private var isMap = false
    private var isLocating: Boolean = false
    private var isTripping: Boolean = false
    private var isStationary: Boolean = true
    private var isLocked: Boolean = false
    private var headingDegrees: Float = 0f
    private val handler: Handler = Handler(Looper.getMainLooper())


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGnssBinding.inflate(layoutInflater)
        setContentView(binding.root)
        locationPermissionCheck()
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        trip = Trip(this)
        location = trip?.initializeDistance()
        // 打开地图
        binding.btnMap.setOnClickListener {
            if (isLocked) {
                binding.btnTrip.isEnabled = true
                binding.btnSettings.isEnabled = true
                Toast.makeText(this@GnssActivity,
                    "按钮已解锁", Toast.LENGTH_LONG).show()
            }
            else { setupMapView() }
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
        // 创建OnBackPressedCallback，并设置是否启用默认处理
        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isMap) {
                    setupMapView()
                } else {
                    onBackPressedDispatcher.onBackPressed()
                }
                location?.let {
                    prefs.edit { LocationPrefsManager.setLocation(prefs, it) }
                }
            }
        }
        // 将回调添加到OnBackPressedDispatcher
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    override fun onResume() {
        super.onResume()
        if (isMap) {
            setupWebView()
            loadLocalHtml()
        }
    }

    /**
     * 初始化融合管理器，设置数据回调
     */
    private fun initGnssManager() {
        gnssManager = GnssManager(applicationContext)
        // 设置位置回调，更新 UI 数据
        gnssManager.setOnLocationChangeListener { location ->
            runOnUiThread {
                setLocationUpdated(location)
                if (isMap || !isStationary) sendLocationToWeb(location)
            }
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
                if (isMap) {
                    val locationJson = java.lang.String.format(
                        Locale.US, "{\"degress\": %f}",
                        headingDegrees)
                    val jsCode = "javascript:if(typeof onBearingUpdate === 'function')" +
                            " { onBearingUpdate(" + locationJson + "); }"
                    executeJavaScript(jsCode)
                }
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
        if (trip == null || isTripping) return
        when (trip!!.startTrip()) {
            1 -> {
                isTripping = true
                binding.btnStopTrip.text = "停止旅程"
                binding.btnCleanTrip.text = "重置旅程"
                binding.btnStopTrip.isEnabled = true
                binding.btnCleanTrip.isEnabled = true
                location?.let { sendLocationToWeb(it) }
                drawTrack(true)
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
        drawTrack(false)
        binding.btnStopTrip.text = "--"
        binding.btnCleanTrip.text = "--"
        binding.btnStopTrip.isEnabled = false
        binding.btnCleanTrip.isEnabled = false
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

    private fun setupMapView() {
        if (!isMap) {
            isMap = true
            binding.llSpeed.visibility = View.GONE
            binding.tlData.visibility = View.GONE
            binding.llControlButtons.visibility = View.GONE
            binding.wvMap.visibility = View.VISIBLE
            netCheck()
            checkAndPromptForApiKey()
            onResume()
        } else {
            isMap = false
            binding.llSpeed.visibility = View.VISIBLE
            binding.tlData.visibility = View.VISIBLE
            binding.llControlButtons.visibility = View.VISIBLE
            binding.wvMap.visibility = View.GONE
        }
    }

    fun netCheck() {
        PermissionUtil.checkAndRequestPermission(this,
            Manifest.permission.INTERNET,
            "网络", "天地图服务需要联网使用",
            object : PermissionCallback {
                override fun onPermissionGranted() {
                    Toast.makeText(this@GnssActivity,
                        "网络已授权", Toast.LENGTH_SHORT).show()
                }
                override fun onPermissionDenied() {
                    Toast.makeText(this@GnssActivity,
                        "未授权，无法连接网络", Toast.LENGTH_SHORT).show()
                }
                override fun onPermissionPermanentlyDenied() { }
            }
        )
    }

    private fun checkAndPromptForApiKey() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val key = prefs.getString("tianditu_api_key", "")
        if (key.isNullOrEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("使用地图服务需要配置天地图密钥。是否现在去设置？")
                .setPositiveButton("去设置") { _, _ ->
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                .setNegativeButton("稍后", null)
                .show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = binding.wvMap.settings
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            // 允许混合内容（http/https），这对某些资源加载很重要
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        // 设置WebChromeClient（如果需要处理弹窗、进度条等）
        binding.wvMap.webViewClient = MyWebViewClient()
        // 注入JavaScript接口对象，并命名为“AndroidLocation”
        binding.wvMap.addJavascriptInterface(
            WebAppInterface(this), "AndroidLocation")
        // 设置WebViewClient，在页面加载完成后注入密钥
        binding.wvMap.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                // 页面加载完成后，主动将密钥传递给JavaScript
                injectApiKeyToWeb()
                injectLastPointToWeb()
                Log.d(tag, "页面加载完成: $url")
            }
        }
    }

    // 一个专门用于向WebView传递密钥的方法
    private fun injectApiKeyToWeb() {
        // 从SharedPreferences读取最新的密钥
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val userKey = prefs.getString("tianditu_api_key", "")
        Log.d(tag, "userKey: $userKey")

        // 构建JavaScript代码，设置一个全局变量
        val jsCode = if (userKey.isNullOrEmpty()) {
            // 如果用户没有设置，可以传递一个空值或默认值，并提示
            "console.warn('未检测到用户设置的天地图密钥，将尝试使用内置密钥。');"
        } else {
            // 将密钥注入到JavaScript的全局作用域中
            "window.USER_TIANDITU_KEY = '${userKey}'; console.log('Android端密钥已注入WebView。');"
        }
        // 在WebView中执行这段JavaScript代码
        binding.wvMap.evaluateJavascript(jsCode, null)
    }

    private fun injectLastPointToWeb() {
        val latLng: String
        if (location != null) {
            latLng = location!!.latitude.toString() + "," + location!!.longitude
            val jsCode = "window.LAST_POINT = '${latLng}'; " +
                    "console.log('Android端初始位置已注入WebView。');"
            binding.wvMap.evaluateJavascript(jsCode, null)
        }
    }

    private fun loadLocalHtml() {
        try {
            // 1. 从 assets 文件夹读取 HTML 文件内容
            val htmlContent: String = assets.open("tianditu_map.html")
                .bufferedReader().use { it.readText() }

            // 2. 使用一个合法的 HTTPS 域名作为基础 URL
            //    这里使用天地图 API 的域名，这样请求的来源就是这个域名，从而被服务器接受
            val baseUrl = "https://api.tianditu.gov.cn"
            val mimeType = "text/html"
            val encoding = "utf-8"
            val historyUrl: String? = null // 历史记录URL，通常为null

            // 3. 关键调用：加载数据并设置基础URL
            binding.wvMap.loadDataWithBaseURL(baseUrl, htmlContent, mimeType, encoding, historyUrl)

        } catch (e: IOException) {
            e.printStackTrace()
            // 如果出错，回退到旧的加载方式（可能失败，但作为兜底）
            binding.wvMap.loadUrl("file:///android_asset/tianditu_map.html")
        }
    }

    // 一个简单的WebViewClient，可以按需扩展
    private class MyWebViewClient : WebViewClient() {
        // 可以在这里拦截请求、处理错误等
        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            Log.e("MyWebViewClient", "加载错误: ${error?.description}")
        }
    }

    fun sendLocationToWeb(location: Location) {
        var locationJson: String
        try {
            val json = JSONObject()
            json.put("lat", location.latitude)
            json.put("lng", location.longitude)
            json.put("accuracy", location.accuracy)
            json.put("speed", location.speed)
            json.put("bearing", location.bearing)
            json.put("timestamp", location.time)
            json.put("provider", location.provider)
            locationJson = json.toString()
        } catch (e: JSONException) {
            locationJson = java.lang.String.format(
                Locale.US, "{\"lat\": %f, \"lng\": %f}",
                location.latitude, location.longitude
            )
        }
        val jsCode = "javascript:if(typeof onLocationUpdate === 'function') { " +
                "onLocationUpdate(" + locationJson + "); }"
        executeJavaScript(jsCode)
        if (isTripping) drawTrack(true)
    }

    // 是否绘制轨迹
    private fun drawTrack(draw: Boolean) {
        val jsCode = "javascript:if(typeof drawTrack === 'function') { " +
                "drawTrack(" + draw + "); }"
        executeJavaScript(jsCode)
    }

    // 执行JavaScript代码
    private fun executeJavaScript(jsCode: String) {
//        Log.d(tag, "jsCode: $jsCode")
        handler.post{
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                binding.wvMap.evaluateJavascript(jsCode, null)
            } else {
                binding.wvMap.loadUrl(jsCode)
            }
        }
    }

    // 地图居中到当前位置
    private fun centerMapOnLocation() {
        if (location != null) {
            val jsCode = java.lang.String.format(
                Locale.US,
                "javascript:centerOnLocation(%f, %f);",
                location!!.latitude,
                location!!.longitude
            )
            executeJavaScript(jsCode)
            Toast.makeText(this, "地图已定位到当前位置", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "暂无位置信息", Toast.LENGTH_SHORT).show()
        }
    }

    fun getLocation(): Location? {
        if (location != null) return location
        else return null
    }

    // 释放WebView资源
    override fun onDestroy() {
        binding.wvMap.destroy()
        super.onDestroy()
    }
}