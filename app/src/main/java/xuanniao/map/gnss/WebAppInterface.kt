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


class WebAppInterface(private val context: Context) {

    // 让JavaScript可以调用SharedPreferences中的key
    @JavascriptInterface
    fun getTiandituKey(): String {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        // 第一个参数是Preference的key，第二个参数是默认值（可用于首次引导）
        val key = prefs.getString(
            "tianditu_api_key", "YOUR_DEFAULT_KEY_OR_EMPTY") ?: ""
        Log.d("WebAppInterface", key)
        return key
    }

    // 让JavaScript可以显示Toast的方法，用于调试
    @JavascriptInterface
    fun showToast(toast: String) {
        Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
    }

    // 获取经纬度及其他位置信息
    @JavascriptInterface
    fun getLonLat(toast: String) {
        Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
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
        context as GnssActivity
        context.runOnUiThread {
            val location = context.getLocation()
            if (location != null) {
                context.sendLocationToWeb(location)
            } else {
                Toast.makeText(
                    context, "暂无位置信息", Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}