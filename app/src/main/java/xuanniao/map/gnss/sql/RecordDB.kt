package xuanniao.map.gnss.sql

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import android.util.Log
import androidx.core.database.getDoubleOrNull
import androidx.core.database.getFloatOrNull
import androidx.core.database.getStringOrNull
import xuanniao.map.gnss.ui.MovementRecord


class RecordDB(c: Context?) :
    SQLiteOpenHelper(c, DATABASE_NAME, null, DATABASE_VERSION) {
    val tag: String = "RecordDB"
    companion object {
        const val DATABASE_NAME = "MovementRecord.db"
        const val DATABASE_VERSION = 1
        const val TABLE_META = "metadata"
        // 列名
        const val COLUMN_ID = "id"

        const val COLUMN_STA_TIM = "startTime"
        const val COLUMN_END_TIM = "endTime"
        const val COLUMN_STA_LAT = "startLatitude"
        const val COLUMN_STA_LON = "startLongitude"
        const val COLUMN_END_LAT = "endLatitude"
        const val COLUMN_END_LON = "endLongitude"
        const val COLUMN_STA_ARE = "startAdminArea"
        const val COLUMN_END_ARE = "endAdminArea"
        const val COLUMN_TOT_DIS = "totalDistance"
        const val COLUMN_AVR_SPE = "averageSpeed"
        const val COLUMN_TIME = "time"
        const val COLUMN_LAT = "latitude"
        const val COLUMN_LON = "longitude"
        const val COLUMN_ALT = "altitude"
        const val COLUMN_SPE = "speed"
        const val COLUMN_BEA = "bearing"
        const val COLUMN_PRO = "provider"
        const val COLUMN_ACC = "accuracy"
        private var instance: RecordDB? = null

        /**
         * @param context 传入上下文
         * @return 返回DBHelper对象
         */
        @Synchronized
        fun getInstance(context: Context?): RecordDB {
            if (instance == null) instance = RecordDB(context)
            return instance!!
        }
    }


    //继承SQLiteOpenHelper以后，需要实现的两个方法onCreate和onUpgrade
    override fun onCreate(sqLiteDatabase: SQLiteDatabase?) {
        Log.d(tag, "dbName:${DATABASE_NAME}")
    }

    // 数据库版本更新的时候，在onUpgrade执行来进行更新
    override fun onUpgrade(sqLiteDatabase: SQLiteDatabase, i: Int, i1: Int) {
        // 给表增加一个列,一次只能增加一个字段
        val sql = "ALTER TABLE tab_name ADD COLUMN a VARCHAR;"
        sqLiteDatabase.execSQL(sql)
    }

    // 创建元数据表
    fun metaTabCreate() {
        val sql = """
            CREATE TABLE IF NOT EXISTS "$TABLE_META" (
            $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COLUMN_STA_TIM LONG,
            $COLUMN_END_TIM LONG,
            $COLUMN_STA_LAT REAL,
            $COLUMN_STA_LON REAL,
            $COLUMN_END_LAT REAL,
            $COLUMN_END_LON REAL,
            $COLUMN_STA_ARE VARCHAR,
            $COLUMN_END_ARE VARCHAR,
            $COLUMN_TOT_DIS REAL,
            $COLUMN_AVR_SPE REAL
            )
        """.trimIndent()
        writableDatabase.execSQL(sql)
    }

    // 创建表
    fun tabCreate(tabName: String) {
        //创建表的sql文
        val sql = """
            CREATE TABLE IF NOT EXISTS $tabName (
            $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COLUMN_TIME LONG,
            $COLUMN_LAT REAL,
            $COLUMN_LON REAL,
            $COLUMN_ALT REAL,
            $COLUMN_SPE REAL,
            $COLUMN_BEA REAL,
            $COLUMN_PRO VARCHAR,
            $COLUMN_ACC REAL
            )
        """.trimIndent()
        writableDatabase.execSQL(sql)
    }

    // 删除表
    fun tabDelete(tabName: String?) {
        val db = this.getWritableDatabase();
        try {
            db.execSQL("DROP TABLE IF EXISTS $tabName")
            Log.d(tag, "表 $tabName 删除成功")
        } catch (e: java.lang.Exception) {
            Log.e(tag, "删除表失败：" + e.message)
        } finally {
            if (db.isOpen()) {
                db.close()
            }
        }
    }

    // 严谨模式创建新行
    fun writeItem(record: MovementRecord) {
        val a = judgeDBTabExist(TABLE_META)
        if (a) {
            // 表存在则判断项是否存在
            val time: Long = record.startTime
            val b = judgeDBTabItemExist(time, TABLE_META)
            if (b) {
                // 项存在则不写
                Log.d("写入失败", "已在数据库中")
            } else {
                // 项不存在则写入
                insertMata(record)
                Log.d("写入-表存在", "写入了表:$TABLE_META")
            }
        } else {
            // 表不存在则创建后写入
            metaTabCreate()
            insertMata(record)
            Log.d("写入-表不存在", "写入了表:$TABLE_META")
        }
    }

    // 更新行,返回更新了多少行
    fun updateMetaItem(startTime: Long, field: String?, s: String?, i: Int?): Long {
        val values = ContentValues()
        if (s != null) { values.put(field, s) }
        else { values.put(field, i) }
        return writableDatabase.update(TABLE_META, values, "startTime=?",
            arrayOf<String>(startTime.toString())).toLong()
    }

    fun updateMetaItemAll(startTime: Long, record: MovementRecord): Long {
        val values = cursorMetaPutMove(record)
        return writableDatabase.update(TABLE_META, values, "startTime=?",
            arrayOf<String>(startTime.toString())).toLong()
    }

    fun judgeDBTabExist(tableName: String?): Boolean {
        var result = false
        if (tableName == null) {
            return false
        }
        try {
            val sql = "select count(*) as c from Sqlite_master  " +
                    "where type ='table' and name ='" + tableName.trim { it <= ' ' } + "' "
            val cursor = writableDatabase.rawQuery(sql, null)
            if (cursor.moveToNext()) {
                val count = cursor.getInt(0)
                if (count > 0) {
                    result = true
                }
            }
        } catch (_: Exception) {
            return false
        }
        return result
    }

    fun judgeDBTabItemExist(time: Long?, tabName: String): Boolean {
        // 创建游标对象
        val cursor = writableDatabase.query(
            tabName, arrayOf<String>("startTime"), "startTime=?",
            arrayOf<String?>(time.toString()), null, null, null
        )
        // 利用游标遍历所有数据对象
        while (cursor.moveToNext()) {
            @SuppressLint("Range")
            val timeStamp = cursor.getLong(
                cursor.getColumnIndex("startTime"))
            if (timeStamp == time) return true
            Log.i(tag, "db out startTime: $timeStamp")
        }
        cursor.close()
        return false
    }

    fun insert(tabName: String, location: Location) {
        Log.d("insert", "开始写入")
        // 创建存放数据的ContentValues对象
        val values = cursorPutMove(location)
        // 数据库执行插入命令
        val insert = writableDatabase.insert(tabName, COLUMN_ALT, values)
        Log.d("insert", insert.toString())
    }

    fun insertMata(record: MovementRecord) {
        Log.d("insert", "开始写入")
        // 创建存放数据的ContentValues对象
        val values = cursorMetaPutMove(record)
        // 数据库执行插入命令
        val insert = writableDatabase.insert(TABLE_META, "endTime", values)
        Log.d("insert", insert.toString())
    }

    // 删除行
    fun itemDeleteByUnique(tabName: String, fieldName: String,
                           uniqueArray: Array<String>) {
        var db: SQLiteDatabase? = null
        try {
            // 获取可写数据库
            db = writableDatabase;
            // 开启事务，保证操作的原子性
            db.beginTransaction();
            // 删除行
            if (!uniqueArray.isEmpty()) {
                // 构建删除条件的占位符
                val placeholders = StringBuilder()
                for (i in 0..<uniqueArray.size) {
                    placeholders.append("?")
                    if (i < uniqueArray.size - 1) { placeholders.append(",") }
                }
                // 执行删除操作
                val deleteSql = "DELETE FROM " + tabName +
                        " WHERE " + fieldName + " IN (" + placeholders + ")"
                db.execSQL(deleteSql, uniqueArray)
            }

            // 重置ID序列为连续的1,2,3...
            // 创建临时表，存储删除后的数据（不含id）
            val createTempTableSql = "CREATE TEMP TABLE temp_user AS SELECT " +
                    "$COLUMN_STA_TIM, $COLUMN_END_TIM, $COLUMN_STA_LAT, $COLUMN_STA_LON, " +
                    "$COLUMN_END_LAT, $COLUMN_END_LON, $COLUMN_STA_ARE, $COLUMN_END_ARE, " +
                    "$COLUMN_TOT_DIS, $COLUMN_AVR_SPE FROM $tabName"
            db.execSQL(createTempTableSql)
            // 删除原表所有数据
            db.execSQL("DELETE FROM $tabName")
            // 将临时表数据重新插入原表（id会自动从1开始自增）
            val insertSql = "INSERT INTO $tabName ( " +
                    "$COLUMN_STA_TIM, $COLUMN_END_TIM, $COLUMN_STA_LAT, $COLUMN_STA_LON, " +
                    "$COLUMN_END_LAT, $COLUMN_END_LON, $COLUMN_STA_ARE, $COLUMN_END_ARE, " +
                    "$COLUMN_TOT_DIS, $COLUMN_AVR_SPE ) SELECT " +
                    "$COLUMN_STA_TIM, $COLUMN_END_TIM, $COLUMN_STA_LAT, $COLUMN_STA_LON, " +
                    "$COLUMN_END_LAT, $COLUMN_END_LON, $COLUMN_STA_ARE, $COLUMN_END_ARE, " +
                    "$COLUMN_TOT_DIS, $COLUMN_AVR_SPE FROM temp_user"
            db.execSQL(insertSql)
            // 删除临时表
            db.execSQL("DROP TABLE temp_user")
            // 更新SQLite的自增计数器，确保下次插入从正确的ID开始
            val updateSeqSql = "UPDATE sqlite_sequence SET seq = " +
                    "(SELECT COUNT(*) FROM " + tabName + ") " +
                    "WHERE name = '" + tabName + "'"
            db.execSQL(updateSeqSql)
            // 标记事务成功
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (db != null) {
                db.endTransaction()
                db.close()
            }
        }
    }

    /** 删除所有行数据  */
    fun tabDeleteAll(tabName: String) {
        writableDatabase.delete(tabName, null, null)
        writableDatabase.close()
    }

    /** 查询元数据表的所有行数据  */
    fun queryMetaAll(): MutableList<MovementRecord> {
        if (!judgeDBTabExist(TABLE_META)) return mutableListOf()
        val cursor = writableDatabase.query(TABLE_META, null, null, null, null, null, null)
        val l: MutableList<MovementRecord> = cursorMetaSetMove(cursor)
        cursor.close()
        return l
    }

    /** 查询所有行数据  */
    fun queryAll(tabName: String): MutableList<Location> {
        val cursor = writableDatabase.query(tabName, null, null, null, null, null, null)
        val l: MutableList<Location> = cursorSetMove(cursor)
        cursor.close()
        return l
    }

    /** 查询所有行数据的序号  */
    fun queryAllToIndex(tabName: String): MutableList<Int?> {
        val cursor = writableDatabase.query(tabName, null, null, null, null, null, null)
        val l: MutableList<Int?> = ArrayList<Int?>()
        while (cursor.moveToNext()) {
            val i = cursor.getInt(0)
            l.add(i)
        }
        cursor.close()
        return l
    }

    // 获取第一条记录
    fun getFirstRecord(tableName: String): Location {
        val query = "SELECT * FROM $tableName ORDER BY id ASC LIMIT 1"
        val cursor = readableDatabase.rawQuery(query, null)
        return cursorSetMove(cursor)[0]
    }

    // 获取最后一条记录
    fun getLastRecord(tableName: String): Location {
        val query = "SELECT * FROM $tableName ORDER BY id DESC LIMIT 1"
        val cursor = readableDatabase.rawQuery(query, null)
        return cursorSetMove(cursor)[0]
    }

    // 根据字段查询：返回指定字段的值的列表
    fun queryFieldToList(tabName: String?, field: String): MutableList<Long?> {
        val sql = "SELECT $field FROM $tabName;"
        val cursor = writableDatabase.rawQuery(sql, null)
        val l: MutableList<Long?> = ArrayList<Long?>()
        while (cursor.moveToNext()) {
            val fieldResult = cursor.getLong(0)
            l.add(fieldResult)
        }
        cursor.close()
        return l
    }

    private fun cursorMetaPutMove(record: MovementRecord): ContentValues {
        val values = ContentValues().apply {
            put("startTime", record.startTime)
            put("endTime", record.endTime)
            record.startLocation.let {
                put("startLatitude", it.latitude)
                put("startLongitude", it.latitude)
            }
            record.endLocation.let {
                put("endLatitude", it.latitude)
                put("endLongitude", it.longitude)
            }
            record.startAdminArea?.let { put("startAdminArea", it) }
            record.endAdminArea?.let { put("endAdminArea", it) }
            put("totalDistance", record.totalDistance)
            put("averageSpeed", record.averageSpeed)
        }
        return values
    }

    private fun cursorMetaSetMove(cursor: Cursor): MutableList<MovementRecord> {
        val list: MutableList<MovementRecord> = ArrayList<MovementRecord>()
        while (cursor.moveToNext()) {
            val record = MovementRecord(Location("cursor"))
            record.startTime = cursor.getLong(1)
            record.endTime = cursor.getLong(2)
            record.startLocation.latitude = cursor.getDoubleOrNull(3)!!
            record.startLocation.longitude = cursor.getDoubleOrNull(4)!!
            record.endLocation.latitude = cursor.getDoubleOrNull(5)!!
            record.endLocation.longitude = cursor.getDoubleOrNull(6)!!
            cursor.getStringOrNull(7)?.let { record.startAdminArea = it }
            cursor.getStringOrNull(8)?.let { record.endAdminArea = it }
            cursor.getFloatOrNull(9)?.let { record.totalDistance = it }
            cursor.getFloatOrNull(10)?.let { record.averageSpeed = it }
            list.add(record)
        }
        return list
    }

    // 写入数据库准备：put遍历游标
    private fun cursorPutMove(location: Location): ContentValues {
        val values = ContentValues().apply {
            put(COLUMN_TIME, location.time )
            put(COLUMN_LAT, location.latitude)
            put(COLUMN_LON, location.longitude)
            put(COLUMN_ALT, location.altitude)
            put(COLUMN_SPE, location.speed)
            put(COLUMN_BEA, location.bearing)
            put(COLUMN_PRO, location.provider)
            put(COLUMN_ACC, location.accuracy)
        }
        return values
    }

    // 读取数据库准备：set遍历游标
    private fun cursorSetMove(cursor: Cursor): MutableList<Location> {
        val list: MutableList<Location> = ArrayList<Location>()
        while (cursor.moveToNext()) {
            val location = Location("cursor")
            location.time = cursor.getLong(1)
            cursor.getDoubleOrNull(2)?.let { location.latitude = it }
            cursor.getDoubleOrNull(3)?.let { location.longitude = it }
            cursor.getDoubleOrNull(4)?.let { location.altitude = it }
            cursor.getFloatOrNull(5)?.let { location.speed = it }
            cursor.getFloatOrNull(5)?.let { location.bearing = it }
            location.provider = cursor.getString(5)
            cursor.getFloatOrNull(5)?.let { location.accuracy = it }
            list.add(location)
        }
        return list
    }

    fun tablesInDB(): ArrayList<String> {
        val list = ArrayList<String>()
        val sql = "select name from sqlite_master where type='table'"
        val cursor = writableDatabase.rawQuery(sql, null)
        if (cursor.moveToFirst()) {
            do {
                val tabName = cursor.getString(0)
                Log.d("beforeTablesInDB", tabName)
                if (tabName == "android_metadata") continue
                if (tabName == "sqlite_sequence") continue
                if (tabName == TABLE_META) continue
                list.add(tabName)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun tablesInSourceDB(): ArrayList<String?> {
        val list = ArrayList<String?>()
        val sql = "select name from source_db.sqlite_master where type='table'"
        val cursor = writableDatabase.rawQuery(sql, null)
        if (cursor.moveToFirst()) {
            do {
                val tabName = cursor.getString(0)
                if (tabName == "android_metadata") continue
                if (tabName == "sqlite_sequence") continue
                list.add(tabName)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    // 查询表的结构
    @SuppressLint("Range")
    fun printTableStructure(dbName: String) {
        val cursor = readableDatabase.rawQuery("PRAGMA table_info($dbName)", null)
        while (cursor.moveToNext()) {
            val columnName = cursor.getString(cursor.getColumnIndex("name"))
            Log.d("TableStructure", "Column: $columnName")
        }
        cursor.close()
    }
}