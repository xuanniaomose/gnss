package xuanniao.map.gnss

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import xuanniao.map.gnss.PermissionUtil.PermissionCallback
import xuanniao.map.gnss.SettingsFragment.Companion.PREF_FOLDER_URI
import xuanniao.map.gnss.databinding.ActivityGnssBinding
import xuanniao.map.gnss.sql.Export
import xuanniao.map.gnss.sql.RecordDB
import xuanniao.map.gnss.ui.DisplayFragment
import xuanniao.map.gnss.ui.MapFragment
import xuanniao.map.gnss.ui.MovementRecord
import xuanniao.map.gnss.ui.RecordListAdapter
import xuanniao.map.gnss.ui.ViewPagerAdapter
import kotlin.math.sqrt
import xuanniao.map.gnss.ui.OnMainButtonClickListener
import xuanniao.map.gnss.ui.OnMainButtonLongClickListener
import xuanniao.map.gnss.ui.RecordListAdapter.OnItemDeleteClickListener
import androidx.core.net.toUri


class GnssActivity : AppCompatActivity() {
    var tag : String = "主面板"
    private lateinit var binding: ActivityGnssBinding
    private lateinit var gnssManager: GnssManager
    private lateinit var trip: Trip
    private lateinit var pagerAdapter: ViewPagerAdapter
    private lateinit var recordAdapter: RecordListAdapter
    private var displayFragment: DisplayFragment? = null
    private var mapFragment: MapFragment? = null
    lateinit var prefs: SharedPreferences
    private lateinit var gnssViewModel: GnssViewModel
    lateinit var dbh: RecordDB
    private var location: Location? = null
    private var isMap = false
    private var isLocating: Boolean = false
    private var isTripping: Boolean = false
    private var isStationary: Boolean = true
    private var isLocked: Boolean = false
    private var isRecordPage: Boolean = false
    private var headingDegrees: Float = 0f


    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGnssBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        trip = Trip(this)
        location = trip?.initializeDistance()
        dbh = RecordDB(this)
        gnssViewModel = ViewModelProvider(this)[GnssViewModel::class.java]

        // 初始化Adapter
        pagerAdapter = ViewPagerAdapter(this)
        // 绑定Adapter到ViewPager
        binding.vpTop.adapter = pagerAdapter
        displayFragment = pagerAdapter.getDisplayFragment()
        mapFragment = pagerAdapter.getMapFragment()
        binding.indicator.setIndicatorCount(pagerAdapter.getItemCount())
        binding.indicator.reset()

        // 主按钮1：对应3个展开按钮
        if (savedInstanceState == null) {
            binding.sbgControl.addMainButton("打开地图", null)

            binding.sbgControl.addMainButton("长按锁定", null)

            val expandButtons1 = listOf(
                Button(this).apply {
                    tag = "1-0"
                    text = "编辑"
                    setOnClickListener { turnEdit() }
                },
                Button(this).apply {
                    tag = "1-1"
                    text = "--"
                    setOnClickListener {
                        if (recordAdapter.isEditing) {
                            if (!recordAdapter.checkedList.all { it == true }) {
                                recordAdapter.checkAll(true)
                                this.text = "取消\n全选"
                            } else {
                                recordAdapter.checkAll(false)
                                this.text = "全选"
                            }
                        }
                    }
                },
                Button(this).apply {
                    tag = "1-2"
                    text = "--"
                    setOnClickListener {
                        if (recordAdapter.isEditing &&
                            !recordAdapter.checkedList.all { it == false }) {
                            val startTimeList = dbh.queryFieldToList(
                                RecordDB.TABLE_META, "startTime")
                            val checkedList = recordAdapter.checkedList
                            if (startTimeList.size == checkedList.size) {
                                val deleteList = mutableListOf<String>()
                                val deleteNum = mutableListOf<Int>()
                                for (i in 0 until checkedList.size) {
                                    if (checkedList[i]) {
                                        val startTime = startTimeList[i]
                                        Log.d(this@GnssActivity.tag, "startTime: $startTime")
                                        dbh.tabDelete("r$startTime")
                                        deleteList.add(startTime.toString())
                                        deleteNum.add(i)
                                    }
                                }
                                val deleteArray = deleteList.toTypedArray()
                                val deleteN: ArrayList<Int> = ArrayList(deleteNum)
                                dbh.itemDeleteByUnique(RecordDB.TABLE_META,
                                    RecordDB.COLUMN_STA_TIM, deleteArray)
                                recordAdapter.cleanCheckedRecords(deleteN)
                            }
                        }
                    }
                },
                Button(this).apply {
                    tag = "1-3"
                    text = "导出"
                    setOnClickListener {
                        val uriString = prefs.getString(PREF_FOLDER_URI, null)
                        uriString?.toUri()
                        val intent = Intent(this@GnssActivity, Export::class.java)
                        intent.putExtra("dbType", 1)
                        // 2. 启动Service（Android 8.0+ 必须用startForegroundService）
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Log.d("主界面", "开启服务")
                            startForegroundService(intent) // 前台Service，避免被系统杀死
                        } else {
                            startService(intent) // 8.0以下用普通启动
                        }
                    }
                }
            )
            binding.sbgControl.addMainButton("打开记录", expandButtons1)
            val expandButtons2 = listOf(
                Button(this).apply {
                    text = "停止"
                    setOnClickListener { stopTrip() }
                },
                Button(this).apply {
                    text = "重置"
                    setOnClickListener { resetTrip() }
                },
//                Button(this).apply {
//                    text = "暂停"
//                    setOnClickListener {  }
//                }
            )
            binding.sbgControl.addMainButton("开始旅途", expandButtons2)
        }

        binding.vpTop.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback()  {
            // 页面选中时回调（对应旧 API 的 onPageSelected）
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // 处理页面选中逻辑，比如更新底部导航、标题等
                Log.d(tag, "当前选中页面：$position")
            }
        })

        // 创建OnBackPressedCallback，并设置是否启用默认处理
        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                location?.let { prefs.edit {
                    LocationPrefsManager.setLocation(prefs, it)
                } }
//                mapFragment?.onDestroyView()
                // 自定义逻辑处理完仍需执行默认返回，调用如下代码
                isEnabled = false // 先禁用当前回调，避免循环调用
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true // 恢复启用
            }
        }
        // 将回调添加到OnBackPressedDispatcher
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        binding.vpTop.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (position == 1) isMap = true
                binding.indicator.updateSelectedPosition(position)
                Log.d(tag, position.toString())
            }
        })
    }

    override fun onResume() {
        super.onResume()
        locationPermissionCheck()
        initView()
    }

    private fun initView() {
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.sbgControl.setOnMainButtonClickListener(
            object : OnMainButtonClickListener {
            override fun onMainButtonSelected(mainButton: Button): Boolean {
                var isCanToSub = true
                when (mainButton.tag as Int) {
                    0 -> turnMap()
                    1 -> if (isLocked) lockOff()
                    2 -> turnRecord(mainButton)
                    3 -> {
                        if (!isTripping) {
                            startTrip(mainButton)
                        } else {
                            mainButton.text = "返回"
                        }
                        isCanToSub = isTripping
                    }
                }
                return isCanToSub
            }
            override fun onMainButtonReset(mainButton: Button) {
                when (mainButton.tag as Int) {
                    2 -> turnRecord(mainButton)
                    3-> if (isTripping) {
                        mainButton.text = "正在旅途"
                    } else {
                        mainButton.text = "开始旅程"
                        resetTrip()
                    }
                }
            }
        })
        binding.sbgControl.setOnMainButtonLongClickListener(
            object : OnMainButtonLongClickListener {
            override fun onMainButtonLongClick(mainButton: Button): Boolean {
                if (mainButton.tag as Int == 1 && !isLocked) lockOn()
                return true
            }
        })

    }

    /**
     * 初始化GNSS管理器，设置数据回调
     */
    private fun initGnssManager() {
        gnssManager = GnssManager(this)
        // 设置位置回调，更新 UI 数据
        gnssManager.setOnLocationChangeListener { location ->
            setGnssUpdated(location)
            gnssViewModel.updateLocationData(location)
        }
        gnssManager.setOnSensorChangeListener { event ->
            runOnUiThread { setSensorUpdate(event) }
        }
        when (gnssManager.startLocation()) {
            1 -> isLocating = true
            -1 -> { // 未授权
                isLocating = false
                PermissionUtil.checkPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
            }
            -2 -> { // 定位未开启
                isLocating = false
                PermissionUtil.showLocationServiceEnableDialog(this)
            }
        }
    }

    /**
     * 显示传感器数据
     */
    @SuppressLint("SetTextI18n")
    fun setSensorUpdate(event: SensorEvent?) {
        // 根据传感器类型处理数据
        if (event == null) return
        when (event.sensor.type) {
            // 线性加速度传感器：判断静止
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                location?.let { isStationary = detectZeroVelocity(
                    it, event.values)
                }
            }
            // 旋转矢量传感器
            Sensor.TYPE_ROTATION_VECTOR -> {
                val attitude = updateAttitude(event.values)
                headingDegrees = attitudeToBearing(attitude[0])
                location?.let { it.bearing = headingDegrees }
                binding.tvBearing.text = "%.1f".format(headingDegrees)
                binding.tvDirection.text = bearingToDirection(headingDegrees).dir
            }
        }
        if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            // event.values 包含 [x, y, z] 三个轴的加速度，单位 m/s²
            val lx = event.values[0]
            val ly = event.values[1]
            val lz = event.values[2]
            val linearMagnitude = sqrt(lx * lx + ly * ly + lz * lz)
//            Log.d(tag, "系统加速度: %.5f".format(linearMagnitude))
            binding.tvAcceleration.text = "%.1f".format(linearMagnitude)
        }
    }

    /**
     * 接收实时定位数据
     */
    @SuppressLint("SetTextI18n", "DefaultLocale")
    fun setGnssUpdated(locationData: Location) {
        location = locationData
        binding.tvAvgSpeed.text = String.format("%.1f", locationData.speed / 3.6)
        binding.tvLon.text = "%.8f".format(locationData.longitude)
        binding.tvLat.text = "%.8f".format(locationData.latitude)
        binding.tvAltitude.text = "%.2f".format(locationData.altitude)
        binding.tvAccuracy.text = "%.2f".format(locationData.accuracy) + "m"
        binding.tvSatellite.text = locationData.provider
        binding.tvSatelliteTime.text = longToStringTime(locationData.time)

        if (isTripping) {
            trip?.let { binding.tvTiming.text = it.timing(locationData.time) }
            if (!isStationary) {
                binding.tvMileage.text = "%.2f".format(
                    trip?.accumulate(locationData)) + "m"
                trip?.update(locationData)
                trip?.record(locationData)
            }
        }
        // 初始收到信号立刻更新一次
        if (trip.satelliteTime == null || trip.lastPoint.latitude == 200.0) {
            trip?.update(locationData)
        }
        if (!isStationary) {
            binding.tvBearing.text = locationData.bearing.toString()
            binding.tvDirection.text = bearingToDirection(locationData.bearing).dir
        }
    }

    /**
     * 开启旅程（开启计时，开启里程叠加）
     */
    fun startTrip(mainButton: Button) {
        if (trip == null || isTripping) return
        when (trip!!.startTrip()) {
            1 -> {
                isTripping = true
                gnssViewModel.updateModeData(true)
                location?.let {
                    mapFragment?.sendLocationToWeb(it)
                    mapFragment?.drawTrack(true)
                }
            }
            -1 -> {
                isTripping = false
                Toast.makeText(
                    this, "请等待卫星信号接收后再开启旅程",
                    Toast.LENGTH_SHORT
                ).show()
            }
            -2 -> {
                isTripping = false
                Toast.makeText(
                    this, "未授予后台定位权限，请将应用置于前台",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        if (isTripping) { mainButton.text = "返回"}
    }

    @SuppressLint("SetTextI18n")
    fun resetTrip() {
//        if (!isTripping) return
        trip?.cleanTrip()
        binding.tvTiming.text = "00:00:00"
        binding.tvMileage.text = "0m"
    }

    /**
     * 结束旅程（停止计时，关闭里程叠加，重置按钮状态）
     */
    fun stopTrip() {
        if (!isTripping) return
        trip?.stopTrip()
        isTripping = false
        gnssViewModel.updateModeData(isTripping)
    }

    fun locationPermissionCheck() {
        PermissionUtil.checkAndRequestPermission(this,
            Manifest.permission.ACCESS_FINE_LOCATION,
            "精确定位", "高精度定位需要精确定位的权限",
            object : PermissionCallback {
                override fun onPermissionGranted() { initGnssManager() }
                override fun onPermissionDenied() { }
                override fun onPermissionPermanentlyDenied() { }
            }
        )
    }

    fun turnMap() {
        if (isMap) {
            isMap = false
            binding.sbgControl.originalMainButtons[0].text = "打开地图"
            binding.vpTop.setCurrentItem(0, true)
        } else {
            isMap = true
            binding.sbgControl.originalMainButtons[0].text = "关闭地图"
            binding.vpTop.setCurrentItem(1, true)
        }
    }

    fun turnRecord(mainButton: Button) {
        if (!isRecordPage) {
            isRecordPage = true
            mainButton.text = "返回"
            binding.tlData.visibility = View.GONE
            binding.rvRecord.visibility = View.VISIBLE
            binding.rvRecord.setLayoutManager(LinearLayoutManager(this));
            recordAdapter = RecordListAdapter(getRecordList())
            recordAdapter.setOnItemDeleteClickListener(
                object : OnItemDeleteClickListener {
                    override fun onDeleteClick(position: Int, item: MovementRecord) {
                        dbh.itemDeleteByUnique(RecordDB.TABLE_META,
                            RecordDB.COLUMN_ID,
                            arrayOf((position + 1).toString()))
                        dbh.tabDelete("r${item.startTime}")
                        binding.rvRecord.adapter?.notifyItemRemoved(position)
                        // 可选：显示 SnackBar 或 Toast
                        Toast.makeText(this@GnssActivity, "删除成功",
                            Toast.LENGTH_SHORT).show()
                    }
                })
            recordAdapter.setOnItemClickListener(
                object : RecordListAdapter.OnItemClickListener {
                    @SuppressLint("Range")
                    override fun onClick(position: Int, record: MovementRecord) {
                        binding.vpTop.setCurrentItem(1, true)
                        val mapFragment = pagerAdapter.getMapFragment()
                        // 提取数据库中的路径点，并计算四至点
                        Log.d(tag, "打开轨迹： r${record.startTime}")
                        val locationList = dbh.queryAll("r${record.startTime}")
                        if (locationList.isEmpty() || locationList[0].latitude == 200.0) {
                            val location = Location("init")
                            location.latitude = 200.0
                            location.longitude = 200.0
                            locationList.add(location)
                        }
                        val pointList = mutableListOf<DoubleArray>()
                        var minLat = locationList[0].latitude
                        var minLon = locationList[0].longitude
                        var maxLat = locationList[0].latitude
                        var maxLon = locationList[0].longitude
                        for (i in 0 until locationList.size) {
                            val location = locationList[i]
                            val lon = location.longitude
                            val lat = location.latitude
                            if (lon < minLon) { minLon = lon }
                            if (lon > maxLon) { maxLon = lon }
                            if (lat < minLat) { minLat = lat }
                            if (lat > maxLat) { maxLat = lat }
                            val point = doubleArrayOf(lat, lon)
                            pointList.add(point)
                        }
                        val pointArray: Array<DoubleArray> = pointList.toTypedArray()
                        val bound = doubleArrayOf(minLat, minLon, maxLat, maxLon)
                        Log.d(tag, "打开轨迹： r$minLat, $minLon, $maxLat, $maxLon")
                        mapFragment?.sendRecordToMap(pointArray, bound)
                    }
                }
            )
            binding.rvRecord.adapter = recordAdapter
            binding.rvRecord.post {
                Log.d(tag, """
                RecyclerView状态：
                - 宽度: ${binding.rvRecord.width}
                - 高度: ${binding.rvRecord.height}
                - 适配器条目数: ${recordAdapter.itemCount}
            """.trimIndent())
            }
        } else {
            if (recordAdapter.isEditing) { turnEdit() }
            isRecordPage = false
            mainButton.text = "打开记录"
            binding.tlData.visibility = View.VISIBLE
            binding.rvRecord.visibility = View.GONE
        }
    }

    fun turnEdit() {
        val btn1_0 = binding.sbgControl.findSubButtonByTag("1-0")
        val btn1_1 = binding.sbgControl.findSubButtonByTag("1-1")
        val btn1_2 = binding.sbgControl.findSubButtonByTag("1-2")
        if (!recordAdapter.isEditing) {
            recordAdapter.turnEditing(true)
            btn1_0?.text = "退出\n编辑"
            btn1_1?.text = "全选"
            btn1_2?.text = "删除"
        } else {
            recordAdapter.turnEditing(false)
            btn1_0?.text = "编辑"
            btn1_1?.text = "--"
            btn1_2?.text = "--"
        }
    }

    fun lockOn() {
        isLocked = true
        for (button in binding.sbgControl.originalMainButtons) {
            if (button.tag as Int != 1) button.isEnabled = false
            else button.text = "短按解锁"
        }
        Toast.makeText(this@GnssActivity, "按钮已锁定",
            Toast.LENGTH_LONG).show()
    }

    fun lockOff() {
        isLocked = false
        for (button in binding.sbgControl.originalMainButtons) {
            if (button.tag as Int != 1) button.isEnabled = true
            else button.text = "长按锁定"
        }
        Toast.makeText(this@GnssActivity, "按钮已解锁",
            Toast.LENGTH_LONG).show()
    }

    fun getRecordList(): MutableList<MovementRecord> {
        val tables = dbh.tablesInDB()
        val recordList = dbh.queryMetaAll()
        // 进行交叉验证
//        for (record in recordList) {
//            if (record == null) continue
//            val tableName = "r" + record.startTime.toString()
//            val startLocation = Location("record")
//            val firstLocation = dbh.getFirstRecord(tableName)
//            firstLocation?.let {
//                startLocation.time = it.time
//                startLocation.latitude = firstLocation.latitude
//                startLocation.longitude = firstLocation.longitude
//            }
//            val record = MovementRecord(startLocation)
//            val lastLocation = dbh.getLastRecord(tableName)
//            lastLocation?.let {
//                record.endTime = it.time
//                record.endLocation.latitude = lastLocation.latitude
//                record.endLocation.longitude = lastLocation.longitude
//            }
//            // 变更为对比
//            recordList.add(record)
//        }
        if (recordList.isEmpty()) {
            val startLocation = Location("noRecord")
            val record = MovementRecord(startLocation)
            recordList.add(record)
        }
        Log.d(tag, "记录列表条数: ${recordList.size}")
        return recordList
    }

    fun backgroundPermissionToStartTrip(): Int {
        var returnCode: Int = -2
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PermissionUtil.checkAndRequestPermission(this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                "后台定位", "旅程记录需要后台定位的权限",
                object : PermissionCallback {
                    override fun onPermissionGranted() {
                        trip?.let {
                            returnCode = it.startTrip()
                        }
                    }
                    override fun onPermissionDenied() {
                        trip?.let { returnCode = it.startTrip() }
                    }
                    override fun onPermissionPermanentlyDenied() {
                        trip?.let { returnCode = it.startTrip() }
                    }
                }
            )
        }
        return returnCode
    }
}