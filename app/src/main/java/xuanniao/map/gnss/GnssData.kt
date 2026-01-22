package xuanniao.map.gnss

/**
 * GNSS定位数据封装
 */
data class GnssLocationData(
    val longitude: Double, // 经度
    val latitude: Double, // 纬度
    val altitude: Double, // 海拔（单位：米）
    val bearing: Float, // 航向（单位：度，0-360）
    val direction: Direction, // 航向（方位）
    val speed: Float, // 速度（单位：m/s，后续可转换为 km/h 或 节）
    val speedKmH: Float, // 速度（单位：km/h，直接供UI展示）
    val speedKnot: Float, // 速度（单位：节，直接供UI展示）
    val timeStamp: Long, // 时间戳（毫秒）
    val constellation: String, // 星座名称
    val accuracy: Float, // 定位精度（单位：米）
//    val provider: LocationProvider, // 定位方式：gps（星座定位）/network（基站定位）
//    val prn: String, // 卫星编号
    val gnssSignalStrength: GnssSignalStrength // 卫星信号强度
)


/**
 * GNSS 信号强度枚举
 */
enum class GnssSignalStrength {
    STRONG, // 信号强（精度<10米）
    MODERATE, // 信号中等（10米≤精度≤20米）
    WEAK, // 信号弱（精度>20米）
    LOST // 信号丢失
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
 * 定位状态回调接口（用于通知上层UI定位状态变化）
 */
interface GnssLocationCallback {
    // 实时定位数据回调
    fun onLocationUpdated(gnssLocationData: GnssLocationData)
    // 定位状态变化回调（如信号丢失、开始定位）
    fun onLocationStatusChanged(status: Int)
}