package xuanniao.map.gnss.ui

import android.location.Location
import java.text.SimpleDateFormat
import java.util.*

/**
 * 移动轨迹记录类
 * @param startLocation 初始位置
 * @property startTime 开始时间（记录创建时间）
 * @property endTime 结束时间（最后更新时间）
 * @property startLocation 起始位置
 * @property endLocation 最新位置
 * @property startAdminArea 起始行政区（初始为空）
 * @property endAdminArea 当前行政区
 * @property locations 完整的轨迹点列表
 */
class MovementRecord(startLocation: Location) {
    // 时间信息
    var startTime: Long = startLocation.time
    var endTime: Long = startTime

    // 位置信息
    val startLocation: Location = Location(startLocation)
    var endLocation: Location = Location(startLocation)

    // 行政区信息
    var startAdminArea: String? = null  // 起始行政区，初始为空
    var endAdminArea: String? = null

    // 完整的轨迹点记录
    private val _locations: MutableList<Location> = mutableListOf(Location(startLocation))
    val locations: List<Location>
        get() = _locations.toList()  // 返回不可修改的副本

    // 轨迹长度（累计移动距离，单位：米）
    var totalDistance: Float = 0f
    var averageSpeed: Float = 0f
    var transportation = TransportType.STATIONARY

    /**
     * 更新当前行政区（不更新位置）
     * @param adminArea 行政区名称
     */
    fun updateAdminArea(adminArea: String?) {
        endAdminArea = adminArea

        // 如果是第一次设置起始行政区且adminArea不为空
        if (startAdminArea == null && adminArea != null) {
            startAdminArea = adminArea
        }
    }

    /**
     * 获取轨迹持续时间（单位：秒）
     */
    fun getDurationSeconds(): Long {
        return (endTime - startTime) / 1000
    }

    /**
     * 获取平均速度（单位：米/秒）
     */
    fun calculateAverageSpeed(): Float {
        val durationSeconds = getDurationSeconds()
        return if (durationSeconds > 0) totalDistance / durationSeconds else 0f
    }

    /**
     * 获取轨迹点数
     */
    fun getLocationCount(): Int {
        return _locations.size
    }

    /**
     * 获取格式化后的时间字符串
     */
    fun getFormattedStartDate(): String {
        val dateFormat = SimpleDateFormat("MM.dd", Locale.getDefault())
        return if (startTime > 0) dateFormat.format(Date(startTime)) else "未初始化"
    }

    fun getFormattedStartTime(): String {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return if (startTime > 0) timeFormat.format(Date(startTime)) else "未初始化"
    }

    fun getFormattedEndDate(): String {
        val dateFormat = SimpleDateFormat("MM.dd", Locale.getDefault())
        return if (endTime > 0) dateFormat.format(Date(endTime)) else "未初始化"
    }

    fun getFormattedEndTime(): String {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return if (endTime > 0) timeFormat.format(Date(endTime)) else "未初始化"
    }

    /**
     * 获取最后已知位置，如果不存在则返回null
     */
    fun getLastKnownLocation(): Location? {
        return endLocation ?: this@MovementRecord.startLocation
    }

    /**
     * 清空轨迹数据（保留初始化状态）
     */
    fun clearTrack() {
        // 保留第一个位置作为起始点
        if (_locations.isNotEmpty()) {
            val firstLocation = _locations.first()
            _locations.clear()
            _locations.add(Location(firstLocation))
        }

        // 重置位置和时间
        this@MovementRecord.startLocation.let {
            endLocation = Location(it)
            endTime = startTime
        }

        // 重置统计信息
        totalDistance = 0f
        endAdminArea = startAdminArea  // 重置为起始行政区
    }

    /**
     * 重置整个记录（回到未初始化状态）
     */
    fun reset() {
        endTime = startTime
        startAdminArea = null
        endAdminArea = null
        _locations.clear()
        totalDistance = 0f
    }

    /**
     * 导出为字符串（可用于保存或传输）
     */
    fun exportToString(): String {
        return buildString {
            append("MovementRecord|")
            append("${startTime}|")
            append("${endTime}|")
            append("${this@MovementRecord.startLocation.latitude ?: 0},${this@MovementRecord.startLocation.longitude ?: 0}|")
            append("${endLocation.latitude ?: 0},${endLocation.longitude ?: 0}|")
            append("${startAdminArea ?: ""}|")
            append("${endAdminArea ?: ""}|")
            append("${totalDistance}|")
            append("${_locations.size}")
        }
    }

    // 交通工具速度特征（m/s）
    enum class TransportType(val minSpeed: Float, val maxSpeed: Float) {
        STATIONARY(0f, 0.3f),      // 静止
        WALKING(0.3f, 2.0f),       // 步行
        RUNNING(2.0f, 5.0f),       // 跑步
        CYCLING(2.5f, 10.0f),      // 骑行
        CAR(5.0f, 40.0f),          // 汽车
        TRAIN(10.0f, 50.0f),       // 火车
        HIGH_SPEED(25f, 100f)      // 高铁/高速
    }

    fun analyzeSpeedPattern(avgSpeed: Float): TransportType {
        return when {
            avgSpeed < TransportType.STATIONARY.maxSpeed -> TransportType.STATIONARY
            avgSpeed < TransportType.WALKING.maxSpeed -> TransportType.WALKING
            avgSpeed < TransportType.RUNNING.maxSpeed -> TransportType.RUNNING
            avgSpeed < TransportType.CYCLING.maxSpeed -> TransportType.CYCLING
            avgSpeed < TransportType.CAR.maxSpeed -> TransportType.CAR
            else -> TransportType.HIGH_SPEED
        }
    }
}