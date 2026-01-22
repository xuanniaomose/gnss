package xuanniao.map.gnss

import android.location.GnssStatus
import android.location.Location
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val SPEED_BUFFER_SIZE = 5
/**
 * 处理原始Location数据：转换格式、平滑处理、封装回调
 */
fun processLocationData(location: Location,
                               gnssLocationCallback: GnssLocationCallback,
                               speedBuffer: MutableList<Float>) {
    // 0. 卫星元数据判读
    val constellation = getConstellationFromLocation(location)
    // 1. 基础数据提取
    var rawSpeed = location.speed // 原始速度（单位：m/s，原生返回值）
    val longitude = location.longitude
    val latitude = location.latitude
    val bearing = location.bearing // 航向
    val altitude = location.altitude // 海拔（GNSS不可用时为0.0）
    val accuracy = location.accuracy // 定位精度（米）
    val timeStamp = location.time // 时间戳

    // 2. 速度数据处理与单位转换
    rawSpeed = if (rawSpeed < 0) 0f else rawSpeed // 修正异常负值
    val smoothSpeed = smoothSpeedData(rawSpeed, speedBuffer) // 滑动平均平滑处理
    val speedKmH = (smoothSpeed * 3.6).toFloat() // m/s → km/h
    val speedKnot = (smoothSpeed * 1.94384).toFloat() // m/s → mph

    val direction = when {
        bearing / 45 == 0f -> Direction.NN
        bearing / 45 == 1f -> Direction.NE
        bearing / 45 == 2f -> Direction.EE
        bearing / 45 == 3f -> Direction.SE
        bearing / 45 == 4f -> Direction.SS
        bearing / 45 == 5f -> Direction.SW
        bearing / 45 == 6f -> Direction.WW
        bearing / 45 == 7f -> Direction.NW
        else -> Direction.NA
    }

    // 3. 判断GNSS信号强度（与Fused版本保持一致的判断标准）
    val signalStrength = when {
        accuracy < 10f -> GnssSignalStrength.STRONG
        accuracy in 10f..20f -> GnssSignalStrength.MODERATE
        accuracy > 20f -> GnssSignalStrength.WEAK
        else -> GnssSignalStrength.LOST
    }

    // 4. 封装最终数据（复用同一数据模型）
    val gnssLocationData = GnssLocationData(
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        bearing = bearing,
        direction = direction,
        speed = smoothSpeed,
        speedKmH = speedKmH,
        speedKnot = speedKnot,
        timeStamp = timeStamp,
        constellation = constellation,
        accuracy = accuracy,
        gnssSignalStrength = signalStrength
    )

    // 5. 推送给上层UI（切换到主线程，避免UI更新异常）
    CoroutineScope(Dispatchers.Main).launch {
        gnssLocationCallback.onLocationUpdated(gnssLocationData)
    }
}

/**
 * 滑动平均算法
 */
private fun smoothSpeedData(rawSpeed: Float,
                            speedBuffer: MutableList<Float>): Float {
    speedBuffer.add(rawSpeed)
    if (speedBuffer.size > SPEED_BUFFER_SIZE) {
        speedBuffer.removeAt(0)
    }
    return speedBuffer.average().toFloat()
}

/**
 * 从Location对象获取定位使用的星座类型（API 24+）
 */
fun getConstellationFromLocation(location: Location): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val constellationType = location.extras?.getInt("satellite_constellation_type")
        constellationType?.let {
            val constellationName = when (it) {
                GnssStatus.CONSTELLATION_GPS -> "GPS"
                GnssStatus.CONSTELLATION_SBAS -> "星基增强"
                GnssStatus.CONSTELLATION_GLONASS -> "格洛纳斯"
                GnssStatus.CONSTELLATION_QZSS -> "准天顶"
                GnssStatus.CONSTELLATION_BEIDOU -> "北斗"
                GnssStatus.CONSTELLATION_GALILEO -> "伽利略"
                GnssStatus.CONSTELLATION_IRNSS -> "印度区域导航"
                else -> "未知"
            }
            return constellationName
        }
        return "出错" // 获取星座名称出错
    }
    return "版本过低" //安卓版本过低，无法获取星座名称
}

/**
 * 获取当前可见的所有卫星及所属星座（API 24+）
 */
fun listenGnssSatellites(): MutableList<Triple<String, Int, Boolean>> {
    val satelliteList: ArrayList<Triple<String, Int, Boolean>> = ArrayList()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val gnssStatusCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                super.onSatelliteStatusChanged(status)
                // 遍历所有可见卫星
                for (i in 0 until status.satelliteCount) {
                    val prn = status.getSvid(i) // 卫星编号（不是卫星名称，是PRN码）
                    val constellation = status.getConstellationType(i) // 星座类型
                    val isUsedInFix = status.usedInFix(i) // 是否被用于定位
                    val constellationName = when (constellation) {
                        GnssStatus.CONSTELLATION_GPS -> "GPS"
                        GnssStatus.CONSTELLATION_BEIDOU -> "北斗"
                        GnssStatus.CONSTELLATION_GLONASS -> "格洛纳斯"
                        else -> "其他"
                    }
                    val satellite = Triple(constellationName, prn, isUsedInFix)
                    satelliteList.add(satellite)
                }
            }
        }
        // 可以在此处注册GNSS监听（需ACCESS_FINE_LOCATION权限）
    }
    return satelliteList
}