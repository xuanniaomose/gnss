package xuanniao.map.gnss

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.net.toUri
import xuanniao.map.gnss.PermissionUtil.PermissionCallback

class SettingsFragment : PreferenceFragmentCompat() {

    companion object {
        private const val REQUEST_CODE_FOLDER_PICKER = 1001
        const val PREF_FOLDER_URI = "selected_folder_uri"
        const val PREF_FOLDER_PATH = "selected_folder_path"
    }

    private lateinit var currentFolderPreference: Preference
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleSelectedFolder(uri)
            }
        }
    }

    // 对于 Android 11+ 的 MANAGE_EXTERNAL_STORAGE 权限请求
    private val requestManageStoragePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openFolderPicker()
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_preferences, rootKey)
        setupPreferences()
    }

    private fun setupPreferences() {
        // 获取当前文件夹显示项
        currentFolderPreference = findPreference("current_folder")!!
        // 设置文件夹选择器的点击事件
        findPreference<Preference>("folder_selector")?.setOnPreferenceClickListener {
            folderPermissionCheck()
            true
        }
        // 显示当前已保存的文件夹路径
        updateCurrentFolderDisplay()
    }

    fun folderPermissionCheck() {
        activity?.let {
            PermissionUtil.checkAndRequestPermission(it,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                "写入权限", "导出文件需要写入权限",
                object : PermissionCallback {
                    override fun onPermissionGranted() { openFolderPicker() }
                    override fun onPermissionDenied() { }
                    override fun onPermissionPermanentlyDenied() { }
                }
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_FOLDER_PICKER) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openFolderPicker()
            } else {
                showPermissionDeniedDialog()
            }
        }
    }

    private fun openFolderPicker() {
        val intent: Intent

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 使用 Storage Access Framework
            intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                // 可选：设置初始目录
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, getCurrentFolderUri())
                }
            }
        } else {
            // Android 10 及以下使用不同的方式
            intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            }
        }

        // 使用 Activity Result API 启动
        folderPickerLauncher.launch(intent)
    }

    private fun handleSelectedFolder(uri: Uri) {
        try {
            // 获取持久化权限
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            // 获取文件夹路径（显示给用户）
            val folderPath = getFolderPathFromUri(uri)

            // 保存到 SharedPreferences
            saveFolderPreference(uri.toString(), folderPath)

            // 更新显示
            updateCurrentFolderDisplay()

            showSuccessMessage(folderPath)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                requireContext(),
                "选择文件夹失败: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun getFolderPathFromUri(uri: Uri): String {
        return try {
            if (DocumentsContract.isDocumentUri(requireContext(), uri)) {
                // 使用 DocumentFile 获取信息
                val documentFile = DocumentFile.fromTreeUri(requireContext(), uri)
                documentFile?.name ?: "未知文件夹"
            } else {
                // 尝试从 URI 获取路径
                val path = uri.path
                path?.substringAfterLast(":") ?: "自定义文件夹"
            }
        } catch (e: Exception) {
            "已授权文件夹"
        }
    }

    private fun saveFolderPreference(uriString: String, path: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        prefs.edit()
            .putString(PREF_FOLDER_URI, uriString)
            .putString(PREF_FOLDER_PATH, path)
            .apply()
    }

    private fun updateCurrentFolderDisplay() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val savedPath = prefs.getString(PREF_FOLDER_PATH, null)

        if (savedPath != null) {
            currentFolderPreference.summary = savedPath
        } else {
            currentFolderPreference.summary = "未选择文件夹"
        }
    }

    private fun getCurrentFolderUri(): Uri? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val uriString = prefs.getString(PREF_FOLDER_URI, null)
        return uriString?.let { Uri.parse(it) }
    }

    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("需要存储权限")
            .setMessage("请授予存储权限以选择文件夹")
            .setPositiveButton("去设置") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSuccessMessage(folderPath: String) {
        Toast.makeText(
            requireContext(),
            "已选择文件夹: $folderPath",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:${requireContext().packageName}")
        startActivity(intent)
    }

    // 工具方法：检查文件夹是否仍然可访问
    fun isFolderAccessible(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val uriString = prefs.getString(PREF_FOLDER_URI, null) ?: return false

        return try {
            val uri = Uri.parse(uriString)
            val documentFile = DocumentFile.fromTreeUri(requireContext(), uri)
            documentFile?.canRead() == true
        } catch (e: Exception) {
            false
        }
    }

    // 工具方法：获取已保存的文件夹 URI（供其他部分使用）
    fun getSavedFolderUri(): Uri? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val uriString = prefs.getString(PREF_FOLDER_URI, null)
        return uriString?.let { Uri.parse(it) }
    }
}