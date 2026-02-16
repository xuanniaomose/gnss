package xuanniao.map.gnss.sql

import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import xuanniao.map.gnss.SettingsFragment
import java.io.*


class Export : Service() {
    lateinit var context: Context
    var tag: String = "Export"

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(tag, "onStartCommand方法被调用!")
        context = this.applicationContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        var path: String = prefs.getString(SettingsFragment.PREF_FOLDER_PATH, "")!!
        val targetUri = prefs.getString(SettingsFragment.PREF_FOLDER_URI, "")!!
        Log.d("selected_folder_path", path)
        if (path == "") path = "/storage/emulated/0/"
        val fileName = RecordDB.DATABASE_NAME
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            createFileInTree(targetUri.toUri(), fileName)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    // 在已授权的树 Uri 下创建文件 [citation:3][citation:7]
    fun createFileInTree(treeUri: Uri, fileName: String): Uri? {
        // 从树 Uri 构建 DocumentFile
        val rootDocument = DocumentFile.fromTreeUri(this, treeUri) ?: return null
        val mimeType = "application/octet-stream"
        // 确保文件不存在（可选，这里展示如何查找已存在的文件）
        var targetFile = rootDocument.findFile(fileName)
        if (targetFile == null) {
            // 创建新文件，指定 MIME 类型 [citation:3]
            targetFile = rootDocument.createFile(mimeType, fileName)
        }
        // 向文件写入内容
        targetFile?.let { file ->
            writeDataToDocumentFile(file.uri, fileName)
            return file.uri
        }
        return null
    }

    // 写入数据到 DocumentFile 的 Uri [citation:7]
    private fun writeDataToDocumentFile(uri: Uri, fileName: String) {
        val privatePath = applicationContext.getDatabasePath(fileName).toString()
        try {
            val privateFile = File(privatePath)
            val fis = FileInputStream(privateFile)
            val fos = contentResolver.openOutputStream(uri)
            val buffer = ByteArray(1024)
            var byteRead: Int
            while (-1 != (fis.read(buffer).also { byteRead = it })) {
                fos?.write(buffer, 0, byteRead)
            }
            fis.close()
            fos?.flush()
            fos?.close()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }


    fun toDB(newPathName: String?, type: Int): Boolean {
        val dbName: String? = if (type == 1) "MovementRecord.db" else "recode.db"
        try {
            val privatePath = applicationContext.getDatabasePath(dbName).toString()
            Log.d("数据库路径", privatePath)
            val privateFile = File(privatePath)
            if (!privateFile.exists()) {
                Log.e(tag, "复制：私有文件不存在")
                return false
            } else if (!privateFile.isFile()) {
                Log.e(tag, "复制：私有文件不是文件")
                return false
            } else if (!privateFile.canRead()) {
                Log.e(tag, "复制：私有文件无法读取")
                return false
            }
            val fis = FileInputStream(privateFile)
            val fos = FileOutputStream("$newPathName/$dbName")
            val buffer = ByteArray(1024)
            var byteRead: Int
            while (-1 != (fis.read(buffer).also { byteRead = it })) {
                fos.write(buffer, 0, byteRead)
            }
            fis.close()
            fos.flush()
            fos.close()
            Toast.makeText(context, "导出完毕！", Toast.LENGTH_SHORT).show()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    companion object {
        fun toCSV(cursor: Cursor, fileName: String) {
            val fw: FileWriter?
            val bfw: BufferedWriter?
            val sdCardDir = Environment.getExternalStorageDirectory()
            val saveFile = File(sdCardDir, fileName)
            try {
                val rowCount = cursor.count
                val colCount = cursor.columnCount
                fw = FileWriter(saveFile)
                bfw = BufferedWriter(fw)
                if (rowCount > 0) {
                    cursor.moveToFirst()
                    // 写入表头
                    for (i in 0..colCount) {
                        if (i != colCount - 1) bfw.write(cursor.getColumnName(i) + ',')
                        else bfw.write(cursor.getColumnName(i))
                    }
                    // 写好表头后换行
                    bfw.newLine()
                    // 写入数据
                    for (i in 0..rowCount) {
                        cursor.moveToPosition(i)
                        //                    Toast.makeText(context, "正在导出第"+(i+1)+"条", Toast.LENGTH_SHORT).show();
                        Log.v("导出数据", "正在导出第" + (i + 1) + "条")
                        for (j in 0..colCount) {
                            if (j != colCount - 1) bfw.write(cursor.getString(j) + ',')
                            else bfw.write(cursor.getString(j))
                        }
                        // 写好每条记录后换行
                        bfw.newLine()
                    }
                }
                // 将缓存数据写入文件
                bfw.flush()
                // 释放缓存
                bfw.close()
                //            Toast.makeText(context, "导出完毕！", Toast.LENGTH_SHORT).show();
                Log.v("导出数据", "导出完毕！")
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                cursor.close()
            }
        }
    }
}