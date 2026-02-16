package xuanniao.map.gnss

import android.hardware.SensorManager
import android.location.Location
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit

const val tag: String = "GnssData数据类"

fun attitudeToBearing(attitude: Float): Float {
    val bearing: Float =
        if (attitude <= 0) {
            180 + (attitude + 3) * 60
        } else {
            (attitude) * 60
        }
    return bearing
}

fun bearingToDirection(bearing: Float): Direction {
    val direction = when {
        ((bearing + 22.5) / 45).toInt() == 0 -> Direction.NN
        ((bearing + 22.5) / 45).toInt() == 1 -> Direction.NE
        ((bearing + 22.5) / 45).toInt() == 2 -> Direction.EE
        ((bearing + 22.5) / 45).toInt() == 3 -> Direction.SE
        ((bearing + 22.5) / 45).toInt() == 4 -> Direction.SS
        ((bearing + 22.5) / 45).toInt() == 5 -> Direction.SW
        ((bearing + 22.5) / 45).toInt() == 6 -> Direction.WW
        ((bearing + 22.5) / 45).toInt() == 7 -> Direction.NW
        ((bearing + 22.5) / 45).toInt() == 8 -> Direction.NN
        else -> Direction.NA
    }
//    Log.d(tag, (bearing /45).toString())
//    Log.d(tag, " 方位: $direction")
    return direction
}

/**
 * 方位
 */
enum class Direction(var dir: String) {
    NN("北"),
    NE("东北"),
    EE("东"),
    SE("东南"),
    SS("南"),
    SW("西南"),
    WW("西"),
    NW("西北"),
    NA("--")
}

/**
 * 零速度检测
 */
fun detectZeroVelocity(location: Location, linearAcceleration: FloatArray): Boolean {
    // 检查加速度幅值
    val accelMagnitude = sqrt(
        linearAcceleration[0] * linearAcceleration[0] +
                linearAcceleration[1] * linearAcceleration[1] +
                linearAcceleration[2] * linearAcceleration[2]
    )

    // 检查GNSS速度
    val gnssSpeed = if (location.hasSpeed()) location.speed else 0f

    // 静止条件：低加速度且低速度
    return accelMagnitude < 0.2f && gnssSpeed < 0.5f
}

fun longToStringTime(time: Long): String {
    val date = Date(time) // 将 Long 转换为 Date
    val format = SimpleDateFormat("MM.dd HH:mm:ss", Locale.getDefault()) // 定义日期格式
    return format.format(date) // 格式化日期为字符串
}

/**
 * 更新姿态信息
 */
fun updateAttitude(rotationVector: FloatArray): FloatArray {
    val rotationMatrix = FloatArray(9)
    val orientation = FloatArray(3)
    // 从旋转矢量获取旋转矩阵
    SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
    // 提取欧拉角
    SensorManager.getOrientation(rotationMatrix, orientation)
    return orientation
}

/**
 * SharedPreferences 存储/读取 Location 的工具类
 */
object LocationPrefsManager {
    // SharedPreferences 文件名
    const val PREFS_NAME = "location_prefs"

    // 存储 Location 属性的 key
    const val KEY_LATITUDE = "location_latitude"
    const val KEY_LONGITUDE = "location_longitude"
    const val KEY_ACCURACY = "location_accuracy"
    const val KEY_PROVIDER = "location_provider"
    const val KEY_TIME = "location_time"

    /**
     * 存储 Location 对象到 SharedPreferences
     * @param prefs SharedPreferences
     * @param location 要存储的 Location 对象
     */
    fun saveLocation(prefs: SharedPreferences, location: Location) {
        prefs.edit {
            // 拆解 Location 的核心属性并存储
            putFloat(KEY_LATITUDE, location.latitude.toFloat()) // 纬度（double转float）
            putFloat(KEY_LONGITUDE, location.longitude.toFloat()) // 经度（double转float）
            putFloat(KEY_ACCURACY, location.accuracy) // 定位精度
            putString(KEY_PROVIDER, location.provider) // 定位提供者（如gps、network）
            putLong(KEY_TIME, location.time) // 定位时间
        }
        // 提交保存（apply 异步，commit 同步，推荐 apply）
    }

    /**
     * 从 SharedPreferences 读取 Location 对象
     * @param prefs SharedPreferences
     * @return 组装后的 Location 对象，若无数据则返回 null
     */
    fun getLocation(prefs: SharedPreferences): Location {
        // 检查是否有存储的纬度（核心属性），无则返回 null
//        if (!prefs.contains(KEY_LATITUDE)) return null

        // 读取所有属性
        val latitude = prefs.getFloat(KEY_LATITUDE, 200f).toDouble()
        val longitude = prefs.getFloat(KEY_LONGITUDE, 200f).toDouble()
        val accuracy = prefs.getFloat(KEY_ACCURACY, 50f)
        val provider = prefs.getString(KEY_PROVIDER, "initial") ?: "gps"
        val time = prefs.getLong(KEY_TIME, 0L)

        // 重新组装 Location 对象
        val location = Location(provider)
        location.latitude = latitude
        location.longitude = longitude
        location.accuracy = accuracy
        location.time = time
        return location
    }


    /**
     * 清除存储的 Location 数据
     * @param prefs SharedPreferences
     * @param location 最后已知点
     */
    fun setLocation(prefs: SharedPreferences, location: Location) {
        prefs.edit {
            Log.d(tag, "存储最后位置到sp")
            putFloat(KEY_LATITUDE, location.latitude.toFloat()) // 纬度（double转float）
            putFloat(KEY_LONGITUDE, location.longitude.toFloat()) // 经度（double转float）
            putFloat(KEY_ACCURACY, location.accuracy) // 定位精度
            putString(KEY_PROVIDER, location.provider)
            putLong(KEY_TIME, location.time) // 定位时间
        }
    }

    /**
     * 清除存储的 Location 数据
     * @param prefs SharedPreferences
     */
    fun clearLocation(prefs: SharedPreferences) {
        prefs.edit {
            putFloat(KEY_LATITUDE, 200f) // 纬度（double转float）
            putFloat(KEY_LONGITUDE, 200f) // 经度（double转float）
            putFloat(KEY_ACCURACY, 40f) // 定位精度
            putString(KEY_PROVIDER, "initial")
            putLong(KEY_TIME, 0) // 定位时间
        }
    }
}

/**
 * 获取格式化后的时间字符串
 */
fun getFormattedDate(timeStamp: Long): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return if (timeStamp > 0) dateFormat.format(Date(timeStamp)) else "未初始化"
}
fun getFormattedTime(timeStamp: Long): String {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return if (timeStamp > 0) timeFormat.format(Date(timeStamp)) else "未初始化"
}