package xuanniao.map.gnss

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat

class Permission(private val activity: Activity) {
    var tag : String = "Permission"
    companion object {
        // 权限请求码
        const val REQUEST_LOCATION_PERMISSION = 1001
    }

    // 定义权限回调接口
    interface OnLocationPermissionCallback {
        // 权限授予成功
        fun onPermissionGranted()
        // 权限被拒绝（未勾选"不再询问"）
        fun onPermissionDenied()
        // 权限被永久拒绝（勾选"不再询问"）
        fun onPermissionPermanentlyDenied()
    }

    // 回调实例（需由Activity设置）
    private var permissionCallback: OnLocationPermissionCallback? = null

    // 设置权限回调
    fun setPermissionCallback(callback: OnLocationPermissionCallback) {
        this.permissionCallback = callback
    }

    fun checkLocationService(): Boolean {
        when {
            // 1.  → 申请权限
            !hasLocationPermission() -> {
                Log.i(tag, "权限未授予")
                requestLocationPermission()
                return false
            }
            // 2. 权限已授予，但系统位置服务未开启 → 引导开启
            !isLocationServiceEnabled() -> {
                Log.i(tag, "权限已授予，但系统位置服务未开启")
                showLocationServiceEnableDialog()
                return false
            }
            // 3. 所有条件满足 → 执行定位逻辑
            else -> { return true }
        }
    }

    // 检查系统位置服务是否开启
    fun isLocationServiceEnabled(): Boolean {
        val locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    // 检查应用是否已授予位置权限
    fun hasLocationPermission(): Boolean {
        val fineGranted = ActivityCompat.checkSelfPermission(
            activity,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseGranted = ActivityCompat.checkSelfPermission(
            activity,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    // 处理权限申请结果
    fun handlePermissionResult(
        requestCode: Int,
        grantResults: IntArray
    ) {
        if (requestCode != REQUEST_LOCATION_PERMISSION) return

        when {
            // 权限授予成功
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED -> {
                permissionCallback?.onPermissionGranted()
                Log.i(tag, "授权成功")
            }
            // 权限被永久拒绝（用户勾选"不再询问"）
            !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                permissionCallback?.onPermissionPermanentlyDenied()
            }
            // 权限被拒绝（未勾选"不再询问"）
            else -> {
                permissionCallback?.onPermissionDenied()
            }
        }
    }

    // 申请位置权限
    fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            REQUEST_LOCATION_PERMISSION
        )
    }

    // 引导用户开启系统位置服务（弹窗提示 + 跳转设置）
    fun showLocationServiceEnableDialog() {
        AlertDialog.Builder(activity)
            .setTitle("位置服务未开启")
            .setMessage("为了提供精准服务，请前往设置开启位置服务")
            .setPositiveButton("去开启") { _, _ ->
                // 跳转系统位置设置页面
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                activity.startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 权限被拒绝且勾选"不再询问"时，引导用户到应用权限设置页
    fun showPermissionDeniedDialog() {
        AlertDialog.Builder(activity)
            .setTitle("权限被拒绝")
            .setMessage("位置权限是核心功能所需，请前往应用设置开启权限")
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent().apply {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = Uri.fromParts("package", activity.packageName, null)
                }
                activity.startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }
}