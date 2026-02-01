package xuanniao.map.gnss

import android.hardware.SensorManager
import android.location.Location
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt


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
    Log.d(tag, (bearing /45).toString())
    Log.d(tag, " 方位: $direction")
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
