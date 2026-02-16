package xuanniao.map.gnss

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.location.Location
import android.util.Log
import androidx.preference.PreferenceManager
import xuanniao.map.gnss.LocationPrefsManager.getLocation
import xuanniao.map.gnss.sql.RecordDB
import xuanniao.map.gnss.ui.MovementRecord
import java.util.*


class Trip(context: GnssActivity) {
    private val tag = "旅程"
    private val activity: GnssActivity = context
    lateinit var prefs: SharedPreferences
    lateinit var dbh: RecordDB
    private var isTripping: Boolean = false
    var satelliteTime: Long? = null
    var startTime: Long? = null
    lateinit var lastPoint: Location
    lateinit var startPoint: Location
    var historyPoints: ArrayList<Location> = ArrayList()
    var distance: Float = 0f

    // 初始化
    fun initializeDistance(): Location {
        prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        lastPoint = getLocation(prefs)
        startPoint = Location("initial")
        dbh = RecordDB(activity)
        return lastPoint
    }

    fun update(location: Location) {
        lastPoint.set(location)
        satelliteTime = location.time
    }

    fun accumulate(newLocation: Location): Float {
        if (lastPoint.provider != "initial") {
            distance += newLocation.distanceTo(lastPoint)
            historyPoints.add(newLocation)
        }
        return distance
    }

    fun record(location: Location) {
        if (isTripping) {
            dbh.insert("r$startTime", location)
        }
    }

    @SuppressLint("DefaultLocale")
    fun timing(timeNow: Long): String {
        if (startTime == null) { startTime = timeNow }
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
        startPoint.set(lastPoint)
        isTripping = true
        val movementRecord = MovementRecord(startPoint)
        dbh.writeItem(movementRecord)
        // TODO 添加分线程获取起始地的逻辑
        dbh.tabCreate("r$startTime")
        return 1
    }

    fun cleanTrip() {
        startTime = null
        distance = 0f
        initializeDistance()
        historyPoints.clear()
    }

    fun stopTrip() {
        isTripping = false
        Log.d(tag, "startTime3:  ${longToStringTime(startPoint.time)}")
        val endRecord = setEndRecord()
        startTime?.let {
            dbh.updateMetaItemAll(it, endRecord)
        }
        cleanTrip()
    }

    fun setEndRecord(): MovementRecord {
        satelliteTime?.let { Log.d(tag, "endTime4:  ${longToStringTime(it)}") }
        Log.d(tag, "startTime4:  ${longToStringTime(startPoint.time)}")
        val movementRecord = MovementRecord(startPoint)
        movementRecord.endTime = satelliteTime!!
        movementRecord.endLocation = lastPoint
        movementRecord.totalDistance = distance
        movementRecord.averageSpeed = distance / (satelliteTime!! - startTime!!)
        movementRecord.transportation = MovementRecord.TransportType.WALKING
        return movementRecord
    }
}