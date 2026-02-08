package xuanniao.map.gnss.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.hardware.Sensor
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import org.json.JSONException
import org.json.JSONObject
import xuanniao.map.gnss.GnssViewModel
import xuanniao.map.gnss.PermissionUtil
import xuanniao.map.gnss.PermissionUtil.PermissionCallback
import xuanniao.map.gnss.SettingsActivity
import xuanniao.map.gnss.WebAppInterface
import xuanniao.map.gnss.attitudeToBearing
import xuanniao.map.gnss.databinding.FragmentMapBinding
import xuanniao.map.gnss.updateAttitude
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.Locale

class MapFragment : Fragment() {
    private val tag = "地图页面"
    private lateinit var activity: Activity
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    val mapHandler: Handler = Handler(Looper.getMainLooper())
    private lateinit var gnssViewModel: GnssViewModel
    private var location: Location? = null
    private var isTripping = false

    companion object {
        fun newInstance(mapId: String): MapFragment {
            val fragment = MapFragment()
            val bundle = Bundle()
            bundle.putString("id", mapId)
            fragment.arguments = bundle
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?): View {
        _binding = FragmentMapBinding.inflate(
            inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity = requireActivity()
        // 初始化ViewModel
        gnssViewModel = ViewModelProvider(requireActivity())[GnssViewModel::class.java]
        // 订阅数据（自动在主线程接收，生命周期安全）
//        Log.d(tag, "开始观察")
        gnssViewModel.isTripping.observe(viewLifecycleOwner) { mode ->
            if (mode != isTripping) drawTrack(mode)
            isTripping = mode
        }
        gnssViewModel.location.observe(viewLifecycleOwner) { location ->
            this.location = location
            sendLocationToWeb(location)
        }
        gnssViewModel.senSorEvent.observe(viewLifecycleOwner) { sensorEvent ->
            when (sensorEvent.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    val attitude = updateAttitude(sensorEvent.values)
                    val headingDegrees = attitudeToBearing(attitude[0])
                    val locationJson = java.lang.String.format(
                        Locale.US, "{\"degrees\": %f}",
                        headingDegrees)
                    val jsCode = "javascript:if(typeof onBearingUpdate === 'function')" +
                            " { onBearingUpdate(" + locationJson + "); }"
                    executeJavaScript(jsCode)
                }
            }
        }
        setupMapView()
    }

    fun setupMapView() {
        netCheck()
        checkAndPromptForApiKey()
        setupWebView()
        loadLocalHtml()
    }

    fun netCheck() {
        PermissionUtil.checkAndRequestPermission(activity as FragmentActivity,
            Manifest.permission.INTERNET,
            "网络", "天地图服务需要联网使用",
            object : PermissionCallback {
                override fun onPermissionGranted() {
                    Toast.makeText(activity, "网络已授权"
                        , Toast.LENGTH_SHORT).show()
                }
                override fun onPermissionDenied() {
                    Toast.makeText(activity, "未授权，无法连接网络"
                        , Toast.LENGTH_SHORT).show()
                }
                override fun onPermissionPermanentlyDenied() { }
            }
        )
    }

    private fun checkAndPromptForApiKey() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val key = prefs.getString("tianditu_api_key", "")
        if (key.isNullOrEmpty()) {
            AlertDialog.Builder(activity)
                .setTitle("提示")
                .setMessage("使用地图服务需要配置天地图密钥。是否现在去设置？")
                .setPositiveButton("去设置") { _, _ ->
                    startActivity(Intent(activity, SettingsActivity::class.java))
                }
                .setNegativeButton("稍后", null)
                .show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun setupWebView() {
//        Log.d(tag, "页面开始加载……")
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
            WebAppInterface(WeakReference(this)), "AndroidLocation")
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
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
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

    fun loadLocalHtml() {
        try {
            // 1. 从 assets 文件夹读取 HTML 文件内容
            val htmlContent: String = activity.assets.open("tianditu_map.html")
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
            Log.e(tag, "错误：$e")
        }
        val jsCode = "javascript:if(typeof onLocationUpdate === 'function') { " +
                "onLocationUpdate(" + locationJson + "); }"
        executeJavaScript(jsCode)
    }

    // 是否绘制轨迹
    fun drawTrack(draw: Boolean) {
        Log.d(tag, "开始绘制")
        val jsCode = "javascript:if(typeof drawTrack === 'function') { " +
                "drawTrack(" + draw + "); }"
        executeJavaScript(jsCode)
    }

    // 执行JavaScript代码
    fun executeJavaScript(jsCode: String) {
//        Log.d(tag, "jsCode: $jsCode")
        mapHandler.post{
            binding.wvMap.evaluateJavascript(jsCode, null)
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
            Toast.makeText(activity, "地图已定位到当前位置", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(activity, "地图已定位到当前位置", Toast.LENGTH_SHORT).show()
        }

    }

    fun getLocation(): Location? {
        return if (location != null) location
        else null
    }

    override fun onDestroy() {
        super.onDestroy()
        mapHandler.removeCallbacksAndMessages(null)
    }
}