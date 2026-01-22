package xuanniao.map.gnss

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import xuanniao.map.gnss.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity(), GnssLocationCallback,
    Permission.OnLocationPermissionCallback{
    // TODO 是否正在定位应该有一个状态机来管理
    var tag : String? = "主面板"
    private lateinit var gnssLocationService: GNSSLocationManagerService
    private lateinit var binding: ActivityMainBinding
    private lateinit var permission: Permission
    private var isLocating: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化原生定位服务
        permission = Permission(this)
        permission.setPermissionCallback(this)
        gnssLocationService = GNSSLocationManagerService(this)
        gnssLocationService.setGNSSLocationCallback(this)

        // 开始/停止 定位（先检查GNSS是否开启）
        binding.btnSwitchLoc.setOnCheckedChangeListener { _, isChecked ->
            if (!isLocating) {
                Log.d(tag, "开始定位")
                if (permission.checkLocationService())
                    gnssLocationService.startLocationUpdates()
            } else {
                Log.d(tag, "停止定位")
                gnssLocationService.stopLocationUpdates()
                binding.tvSatellite.text = "-"
            }
        }
    }

    // 只需把权限结果转发给工具类处理
    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // 调用工具类的处理方法
        permission.handlePermissionResult(requestCode, grantResults)
    }

    // 实现权限回调接口的方法
    override fun onPermissionGranted() {
        // 权限授予成功，开启服务
        gnssLocationService.startLocationUpdates()
    }

    override fun onPermissionDenied() {
        // 权限被拒绝（可提示用户重新授权）
        Toast.makeText(this, "位置权限被拒绝，功能无法使用", Toast.LENGTH_SHORT).show()
    }

    override fun onPermissionPermanentlyDenied() {
        // 权限被永久拒绝，弹出引导到设置页的弹窗
        permission.showPermissionDeniedDialog()
    }

    /**
     * 接收实时定位数据
     */
    override fun onLocationUpdated(gnssLocationData: GnssLocationData) {
        binding.tvAvgSpeed.text = gnssLocationData.speedKmH.toInt().toString()
        binding.tvBearing.text = gnssLocationData.bearing.toString()
        binding.tvDirection.text = gnssLocationData.direction.dir

        binding.tvLon.text = gnssLocationData.longitude.toString()
        binding.tvLat.text = gnssLocationData.latitude.toString()
        binding.tvAltitude.text = "%.2f".format(gnssLocationData.altitude)

        binding.tvAccuracy.text = "%.2f".format(gnssLocationData.accuracy) + "m"
        binding.tvSatellite.text = gnssLocationData.constellation
        binding.tvSatelliteTime.text = convertLongToDate(gnssLocationData.timeStamp)
    }

    /**
     * 接收定位状态变化
     */
    override fun onLocationStatusChanged(status: Int) {
        when (status) {
            -1 -> {
                binding.btnSwitchLoc.isChecked = false
                permission.checkLocationService()
            }
            0 -> {
                binding.btnSwitchLoc.isChecked = false
            }
            1 -> {
                binding.btnSwitchLoc.isChecked = true
            }
        }
    }

    /**
     * 页面销毁时释放资源，避免内存泄漏
     */
    override fun onDestroy() {
        super.onDestroy()
        gnssLocationService.release()
    }
}

fun convertLongToDate(time: Long): String {
    val date = Date(time) // 将 Long 转换为 Date
    val format = SimpleDateFormat("MM.dd HH:mm:ss", Locale.getDefault()) // 定义日期格式
    return format.format(date) // 格式化日期为字符串
}
