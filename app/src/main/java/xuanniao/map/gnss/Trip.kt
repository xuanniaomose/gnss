package xuanniao.map.gnss

import android.annotation.SuppressLint
import android.location.Location
import android.util.Log

class Trip {
    private val tag = "旅程"
    private var isTripping: Boolean = false
    var satelliteTime: Long? = null
    var startTime: Long? = null

    lateinit var nowPoint: Location
    lateinit var lastPoint: Location
    lateinit var startPoint: Location


    lateinit var nowPointFused: Location
    lateinit var lastPointFused: Location
    lateinit var startPointFused: Location

    var distance: Double = 0.0
    var distanceFused: Double = 0.0

    // 初始化
    fun initializeDistance() {
        nowPoint = Location("initial")
        lastPoint = Location("initial")
        startPoint = Location("initial")

        nowPointFused = Location("initial")
        lastPointFused = Location("initial")
        startPointFused = Location("initial")
    }

    fun update(location: Location) {
        lastPoint.set(location)
        satelliteTime = location.time
    }

    fun accumulate(newLocation: Location): Double {
        if (lastPoint.provider != "initial") {
            distance += newLocation.distanceTo(lastPoint)
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
        Log.d(tag, String.format("%02d:%02d:%02d", hour, minutes, seconds))
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

        startPointFused = nowPointFused
        isTripping = true
        return 1
    }

    fun cleanTrip() {
        startTime = null
        distance = 0.0

        distanceFused = 0.0
        initializeDistance()
    }

}