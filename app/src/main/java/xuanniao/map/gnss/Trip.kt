package xuanniao.map.gnss

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.location.Location
import android.util.Log
import androidx.preference.PreferenceManager
import org.json.JSONException
import org.json.JSONObject
import xuanniao.map.gnss.LocationPrefsManager.getLocation
import java.util.*


class Trip(context: GnssActivity) {
    private val tag = "旅程"
    private val activity: GnssActivity = context
    private var isTripping: Boolean = false
    var satelliteTime: Long? = null
    var startTime: Long? = null
    lateinit var prefs: SharedPreferences
    lateinit var nowPoint: Location
    lateinit var lastPoint: Location
    lateinit var startPoint: Location
    var historyPoints: ArrayList<Location> = ArrayList()

    var distance: Double = 0.0
    var distanceFused: Double = 0.0

    // 初始化
    fun initializeDistance(): Location {
        prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        nowPoint = Location("initial")
        lastPoint = getLocation(prefs)
        startPoint = Location("initial")
        return lastPoint
    }

    fun update(location: Location) {
        lastPoint.set(location)
        satelliteTime = location.time
    }

    fun accumulate(newLocation: Location): Double {
        if (lastPoint.provider != "initial") {
            distance += newLocation.distanceTo(lastPoint)
            historyPoints.add(newLocation)
        }
        return distance
    }

    @SuppressLint("DefaultLocale")
    fun timing(timeNow: Long): String {
        if (startTime == null) startTime = timeNow
        val time = timeNow - startTime!!
        val hour = time / 3600000
        val minutes = time / 60000 - (hour * 60)
        val seconds = time / 1000 - (minutes * 60)
//        Log.d(tag, String.format("%02d:%02d:%02d", hour, minutes, seconds))
        return String.format("%02d:%02d:%02d", hour, minutes, seconds)
    }

    fun startTrip(): Int {
        if (isTripping) return 0
        if (satelliteTime == null || lastPoint.latitude == 200.0) {
            return -1
        }
        startTime = satelliteTime
        startPoint = nowPoint
        isTripping = true
        return 1
    }

    fun cleanTrip() {
        startTime = null
        distance = 0.0
        distanceFused = 0.0
        initializeDistance()
        historyPoints.clear()
    }

    fun stopTrip() {
        cleanTrip()
        isTripping = false
    }
}