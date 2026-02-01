package xuanniao.map.gnss

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import android.Manifest
import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import kotlin.String

object PermissionUtil {
    private const val PERMISSION_PREFS = "permission_prefs"
    private const val REQUEST_COUNT = "request_count_"
    private const val NEVER_ASK_AGAIN = "never_ask_again_"
    private const val TAG_PERMISSION_FRAGMENT = "permission_fragment"

    // 定义权限回调接口
    interface PermissionCallback {
        // 权限授予成功
        fun onPermissionGranted()
        // 权限被拒绝（选择了"不再询问"）
        fun onPermissionDenied()
        // 权限被永久拒绝（未选择"不再询问"）
        fun onPermissionPermanentlyDenied()
    }

    private fun getPref(context: Context): SharedPreferences {
        return context.getSharedPreferences(
            PERMISSION_PREFS, Context.MODE_PRIVATE)
    }

    /**
     * 检查并请求权限（智能策略）
     * @param activity 活动实例
     * @param permission 权限
     * @param permissionName 权限显示名称
     * @param rationale 权限解释说明
     * @param callback 权限回调
     */
    fun checkAndRequestPermission(
        activity: FragmentActivity,
        permission: String,
        permissionName: String?,
        rationale: String?,
        callback: PermissionCallback?
    ) {
        // 检查是否已有权限
        if (checkPermission(activity, permission)) {
            resetPermissionState(activity, permission)
            callback?.onPermissionGranted()
            return
        }
        // 检查是否是"不再询问"状态
        if (isNeverAskAgain(activity, permission)) {
            // 永久拒绝后只显示提示，不跳转
            showPermanentDenialDialog(activity, permissionName, rationale)
            callback?.onPermissionPermanentlyDenied()
            return
        }
        // 获取请求次数
        val requestCount = getPref(activity).getInt(
            REQUEST_COUNT + permission, 0)
        if (requestCount == 0) {
            // 第一次请求：直接请求权限
            val prefs = getPref(activity)
            val count = prefs.getInt(REQUEST_COUNT + permission, 0)
            prefs.edit { putInt(REQUEST_COUNT + permission, count + 1) }
            requestPermissionInternal(activity, permission, callback)
        } else {
            // 第二次及以后：显示解释后请求权限
            showRationaleAndRequestDialog(activity, callback)
        }
    }

    /**
     * 检查是否拥有指定权限
     */
    fun checkPermission(context: Context, permission: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        // android12及以上，要获取精确位置，就要申请大致位置之后再申请精确位置，不能只申请精确位置
        return ActivityCompat.checkSelfPermission(
            context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun isNeverAskAgain(context: Context, permission: String): Boolean {
        return context.getSharedPreferences(
            PERMISSION_PREFS, Context.MODE_PRIVATE)
            .getBoolean(NEVER_ASK_AGAIN + permission, false)
    }

    /**
     * 重置权限状态（当用户主动开启权限时调用）
     */
    fun resetPermissionState(context: Context, permission: String?) {
        getPref(context).edit {
            remove(REQUEST_COUNT + permission)
            remove(NEVER_ASK_AGAIN + permission)
        }
    }

    /**
     * 引导用户开启系统位置服务
     */
    fun showLocationServiceEnableDialog(activity: FragmentActivity) {
        AlertDialog.Builder(activity)
            .setTitle("位置服务未开启")
            .setMessage("为了提供精准服务，请前往设置开启位置服务")
            .setPositiveButton("去开启") { _, _ ->
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                activity.startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示解释并请求的对话框
     */
    private fun showRationaleAndRequestDialog(
        activity: FragmentActivity,
        callback: PermissionCallback?
    ) {
        AlertDialog.Builder(activity)
            .setTitle("缺少权限")
            .setMessage("需要定位权限以提供定位服务")
            .setPositiveButton("去授权") { _, _ -> }
            .setNegativeButton("取消"
            ) { _, _ -> callback?.onPermissionDenied() }
            .show()
    }

    /**
     * 永久拒绝后的提示对话框
     */
    fun showPermanentDenialDialog(
        activity: FragmentActivity,
        permissionName: String?,
        rationale: String?
    ) {
        AlertDialog.Builder(activity)
            .setTitle("需要" + permissionName + "权限")
            .setMessage("您拒绝授予" + permissionName + "权限\n" + rationale + "\n因此无法使用相关功能")
            .setPositiveButton("去设置") { _, _ ->
                openAppSettings(activity) }
            .setNeutralButton("取消", null)
            .show()
    }

    /**
     * 打开应用设置页面
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", context.packageName, null)
        intent.setData(uri)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 获取或创建权限Fragment
     */
    private fun requestPermissionInternal(
        activity: FragmentActivity,
        permission: String,
        callback: PermissionCallback?
    ) {
        val fragmentManager = activity.supportFragmentManager
        var fragment = fragmentManager
            .findFragmentByTag(TAG_PERMISSION_FRAGMENT) as PermissionFragment?
        if (fragment == null) {
            fragment = PermissionFragment()
            fragmentManager.beginTransaction()
                .add(fragment, TAG_PERMISSION_FRAGMENT)
                .commitNowAllowingStateLoss()
        }
        fragment.requestPermission(permission, callback)
    }


    /**
     * 透明Fragment，用于处理权限请求和回调
     */
    class PermissionFragment : Fragment() {
        private var mPermission: String? = null
        private var mCallback: PermissionCallback? = null
        private var mIsRequesting = false

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setRetainInstance(true)
        }

        // 拉起底部弹出的系统请求权限对话框
        fun requestPermission(permission: String, callback: PermissionCallback?) {
            this.mCallback = callback
            this.mPermission = permission
            this.mIsRequesting = true

            requestPermissions(
                arrayOf<String?>(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSION_REQUEST_CODE)
        }
        @Deprecated("Deprecated in Java")
        override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String?>,
            grantResults: IntArray
        ) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            if (requestCode != PERMISSION_REQUEST_CODE || !mIsRequesting) { return }
            mIsRequesting = false

            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限授予
                resetPermissionState(requireActivity(), mPermission)
                if (mCallback != null) {
                    mCallback!!.onPermissionGranted()
                }
            } else {
                // 权限拒绝
                val activity: Activity? = getActivity()
                if (activity != null) {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, mPermission!!)) {
                        // 用户选择了"不再询问"，标记为永久拒绝
                        getPref(activity).edit { putBoolean(NEVER_ASK_AGAIN + mPermission, true) }
                        // 这里如果希望不授权就直接弹窗让用户无法使用app的话，可以加上showPermanentDenialDialog
                        if (mCallback != null) {
                            mCallback!!.onPermissionPermanentlyDenied()
                        }
                    } else {
                        // 普通拒绝，增加请求计数
                        val prefs = getPref(activity)
                        val count = prefs.getInt(REQUEST_COUNT + mPermission, 0)
                        prefs.edit { putInt(REQUEST_COUNT + mPermission, count + 1) }
                        if (mCallback != null) {
                            mCallback!!.onPermissionDenied()
                        }
                    }
                }
            }
            // 清理引用
            mCallback = null
        }
        companion object { private const val PERMISSION_REQUEST_CODE = 1000 }
    }
}