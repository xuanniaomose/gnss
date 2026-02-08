package xuanniao.map.gnss

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.preference.PreferenceManager
import org.json.JSONException
import org.json.JSONObject
import xuanniao.map.gnss.ui.MapFragment
import java.lang.ref.WeakReference


class WebAppInterface(private val fragmentRef: WeakReference<MapFragment?>) {
    // 让JavaScript可以调用SharedPreferences中的key
    @JavascriptInterface
    fun getTiandituKey(): String {
        val mapFragment = fragmentRef.get() ?: run { return "" }
        val context = mapFragment.context ?: run { return "" }
        val prefs: SharedPreferences = PreferenceManager
            .getDefaultSharedPreferences(context)
        // 第一个参数是Preference的key，第二个参数是默认值（可用于首次引导）
        val key = prefs.getString(
            "tianditu_api_key", "YOUR_DEFAULT_KEY_OR_EMPTY") ?: ""
        Log.d("WebAppInterface", key)
        return key
    }

    // 让JavaScript可以显示Toast的方法，用于调试
    @JavascriptInterface
    fun showToast(toast: String) {
        // 获取 Fragment 的 Context（先检查 Fragment 是否存活）
        val fragment = fragmentRef.get() ?: return // Fragment 已销毁，直接返回
        val context = fragment.context ?: return   // Context 无效，直接返回
        // 切换到主线程显示 Toast（JS 调用在子线程，必须切换）
        fragment.activity?.runOnUiThread {
            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun getCurrentLocation(currentLocation: Location): String {
        if (currentLocation != null) {
            try {
                val json = JSONObject()
                json.put("lat", currentLocation.getLatitude())
                json.put("lng", currentLocation.getLongitude())
                json.put("accuracy", currentLocation.getAccuracy())
                json.put("speed", currentLocation.getSpeed())
                json.put("bearing", currentLocation.getBearing())
                json.put("time", currentLocation.getTime())
                json.put("provider", currentLocation.getProvider())
                return json.toString()
            } catch (e: JSONException) {
                return "{\"error\": \"JSON error\"}"
            }
        }
        return "{\"error\": \"No location available\"}"
    }

    @JavascriptInterface
    fun requestLocationUpdate() {
        val mapFragment = fragmentRef.get() ?: return
        mapFragment.requireActivity().runOnUiThread {
            val location = mapFragment.getLocation()
            if (location != null) {
                mapFragment.sendLocationToWeb(location)
            } else {
                showToast("暂无位置信息")
            }
        }
    }

    @JavascriptInterface
    fun getLastLocation(): String {
        val mapFragment = fragmentRef.get() ?: run { return "" }
        val location = mapFragment.getLocation()
        val latLng: String
        if (location != null) {
            latLng = location.latitude.toString() + "," + location.longitude
        } else {
            latLng = ""
        }
        Log.d("WebAppInterface", latLng)
        return latLng
    }
}