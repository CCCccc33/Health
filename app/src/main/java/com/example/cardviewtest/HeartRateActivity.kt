package com.example.cardviewtest

import android.app.DatePickerDialog
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.tabs.TabLayout
import java.lang.ref.WeakReference
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar


class HeartRateActivity : AppCompatActivity() {
    companion object{
        const val CHART_CHANGED = 1
        const val ACTION_DATA_AVAILABLE = "com.example.bluetooth.ACTION_DATA_AVAILABLE"
        private val tabTitles = arrayOf("日", "周", "月")
    }
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val TAG = "HeartRateActivity"
    var isServiceConnected: Boolean = false
    private val chartEntries = mutableListOf<Entry>()
    private lateinit var localBroadcastManager : LocalBroadcastManager
    private lateinit var lineDataSet: LineDataSet
    private lateinit var lineData: LineData
    private var typeData: String? = null
    private var curData: List<String>? = null
    private var isRegistered: Boolean = false
    val conversion = Conversion()
    private lateinit var healthDataManager:HealthDataManager
    private lateinit var stateReceiver: StateReceiver
    val calendar = Calendar.getInstance()
    private lateinit var chart : LineChart
    private lateinit var selectData: TextView
    private lateinit var dataUnit: TextView
    private lateinit var analyze: TextView
    private lateinit var analyzeContent: TextView

    @Suppress("DEPRECATION")
    fun getYearWeekForLowVersion(year: Int, month: Int, day: Int): Int {
        // 设置Calendar为ISO周模式（周一为一周第一天）
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.minimalDaysInFirstWeek = 4 // ISO 8601标准
        calendar.set(year, month - 1, day) // Calendar的月是0-11，需-1
        val weekOfYear = calendar.get(Calendar.WEEK_OF_YEAR)
        return year * 100 + weekOfYear
    }

    fun getCurrentTimeStr(): String {
        val now = LocalDateTime.now() // 获取当前本地时间
        return now.format(timeFormatter) // 转换为指定格式字符串
    }

    // 蓝牙状态广播
    inner class StateReceiver(context: Context) : BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            when(action){
                ACTION_DATA_AVAILABLE ->{
                    if (!isServiceConnected){
                        Log.e(TAG,"Service is not connected")
                        return
                    }
                    curData = bluetoothBinder.readMessage()
                    curData?.let {
                        val year = calendar.get(Calendar.YEAR)
                        val month = calendar.get(Calendar.MONTH)
                        val day = calendar.get(Calendar.DAY_OF_MONTH)
                        val week = getYearWeekForLowVersion(year,month,day)
                        val data = HealthData(
                             year = year,
                            month = month,
                            week = week,
                            day = day,
                            dataType = typeData?:"",
                            uploadTime = getCurrentTimeStr(),
                            value1 = curData,
                            value2 = 0.0,
                            value3 = 0.0
                        )
                        healthDataManager.insertData(data)
                        draw(curData)
                    }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED ->{
                    val state = intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE,0)
                    when(state){
                        BluetoothAdapter.STATE_OFF ->{
                            Toast.makeText(context,"检测到设备未连接，请连接设备后重试！", Toast.LENGTH_SHORT).show()
                            Log.e(TAG,"BluetoothAdapter.STATE_OFF")
                        }
                        BluetoothAdapter.STATE_ON ->{
                            Log.e(TAG,"BluetoothAdapter.STATE_ON")
                        }
                    }
                }
            }
        }
    }

    fun initBluetoothReceiver(){
        localBroadcastManager = LocalBroadcastManager.getInstance(this)
        val intentFilter = IntentFilter()
        intentFilter.addAction(ACTION_DATA_AVAILABLE)
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)//手机蓝牙状态监听
        localBroadcastManager.registerReceiver(stateReceiver, intentFilter)
        isRegistered = true
    }

    private val handler = MyHandler(this)

    private class MyHandler(activity: HeartRateActivity) : Handler(Looper.getMainLooper()) {
        private val weakActivity = WeakReference<HeartRateActivity>(activity)
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            val activity = weakActivity.get() ?: return
            val chart:LineChart = activity.findViewById(R.id.chart1)
            when(msg.what){
                CHART_CHANGED ->{
                    val lineDataSet = chart.data?.dataSets?.firstOrNull() as? LineDataSet
                    lineDataSet?.notifyDataSetChanged()
                    chart.notifyDataSetChanged()
                    chart.invalidate()
                }
            }
        }
    }
    private lateinit var bluetoothBinder : BluetoothService.BluetoothBinder
    private val connect = object : ServiceConnection{
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bluetoothBinder = service as BluetoothService.BluetoothBinder
            isServiceConnected = true
            typeData?.let { sendInitData(it) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.e(TAG,"Service connect faild")
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_heart_rate)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.heart_rate)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val toolbarHeartRate: Toolbar = findViewById(R.id.toolbar_heart_rate)
        setSupportActionBar(toolbarHeartRate)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbarHeartRate.navigationIcon?.setTint(getColor(R.color.black))
        typeData = intent.getStringExtra("type")
        toolbarHeartRate.setTitle(typeData)
        val intent = Intent(this, BluetoothService::class.java)
        startService(intent)
        bindService(intent,connect, Context.BIND_AUTO_CREATE)
        stateReceiver = StateReceiver(this)
        initBluetoothReceiver()
        healthDataManager = HealthDataManager(this)
        //初始化tabLayout
        initTabLayout()
        //初始化界面数据
        initCurData()
        //初始化图表
        initChart()
        //初始化数据库
        initDataBase(this)
        // 初始化图表默认显示“日”维度

    }
    fun sendInitData(typeData: String){
        var sendData = "00"
        when(typeData){
            "心率" -> sendData = "01"
            "体温" -> sendData = "02"
            "血压" -> sendData = "03"
            "血氧" -> sendData = "04"
            "肺活量" -> sendData = "05"
            "皮肤状态" -> sendData = "06"
            else -> sendData = "FF"
        }
        val sendMsg = conversion.hexString2Bytes(sendData)
        if (!isServiceConnected){
            Toast.makeText(this,"未连接设备，请连接设备后重试！", Toast.LENGTH_SHORT).show()
            Log.e(TAG,"iServiceDisconnected")
            return
        }
        if (!bluetoothBinder.sendMessage(sendMsg)){
            Toast.makeText(this,"设备未连接，请连接设备后重试！", Toast.LENGTH_SHORT).show()
            Log.e(TAG,"sendInitData失败")
            return
        }
    }
    fun initTabLayout(){
        val tabLayout: TabLayout = findViewById(R.id.tab_layout1)
        tabTitles.forEach { title ->
            tabLayout.addTab(tabLayout.newTab().setText(title))
        }
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                // 根据 Tab 位置更新图表（0=日，1=周，2=月）
                chartDataChanged(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }
    fun initCurData(){
        selectData = findViewById(R.id.select_data)
        dataUnit = findViewById(R.id.unit)
        analyze = findViewById(R.id.analyze)
        analyzeContent = findViewById(R.id.analyze_content)
        if (typeData == null){
            Log.e(TAG,"initCurData() typeData为空!")
            return
        }
        analyze.setText("${typeData}分析")
        when(typeData){
            "心率" ->{
                selectData.setText("--")
                dataUnit.setText("bpm")
            }
            "血氧" ->{
                selectData.setText("--")
                dataUnit.setText("%")
            }
            "体温" ->{
                selectData.setText("--")
                dataUnit.setText("℃")
            }
            "肺活量" ->{
                selectData.setText("--")
                dataUnit.setText("")
            }
            "血压" ->{
                selectData.setText("-/-")
                dataUnit.setText("mmHg")
            }
            "皮肤状态" ->{
                selectData.setText("--")
                dataUnit.setText("")
            }
        }


    }

    fun initChart(){
        chart = findViewById(R.id.chart1)
        // 初始化数据集
        if (chartEntries.isNotEmpty()) {
            lineDataSet = LineDataSet(chartEntries, typeData)
            lineDataSet.color = resources.getColor(R.color.black, theme) // 适配 Android 6.0+ 的正确写法
            lineDataSet.setDrawValues(false) // 不显示数值标签，提升性能

            lineData = LineData(lineDataSet)
            chart.data = lineData
        } else {
            chart.data = null
        }
        // 图表基础配置（按需调整）
        chart.setAutoScaleMinMaxEnabled(true) // 自动缩放Y轴
        chart.setDrawGridBackground(false)
        chart.setNoDataText("暂无数据！")
        chart.setGridBackgroundColor(getResources().getColor(R.color.white))
        chart.description.isEnabled = false
    }

    fun initDataBase(context: Context){
        val dbHelper = DataBase(context,"HealthData.db",2)
        val db = dbHelper.writableDatabase
        if (db.isOpen){
            Log.d(TAG,"SQL get")
        } else{
            Log.e(TAG,"SQL not get")
        }
    }

    fun draw(myData: List<String>?){
        if (myData == null) {
            Log.e(TAG,"传入draw数据为空!")
            return
        }
        val startX = chartEntries.size.toFloat()
        myData.forEachIndexed { index, dataStr ->
            val value = dataStr.toUInt().toInt()
            chartEntries.add(Entry(startX + index, value.toFloat())) // 新增数据点，修改数据集
        }
        val msg = Message()
        msg.what = CHART_CHANGED
        handler.sendMessage(msg)
    }

    fun chartDataChanged(type: Int){
        chart.clear()
        // 2. 根据维度配置横坐标和数据源
        val xAxis = chart.xAxis
        //还没写数据库QAQ

        when (type) {
            0 -> { // 日维度：横坐标 00:00-24:00

            }
            1 -> { // 周维度：横坐标 周一到周日

            }
            2 -> { // 月维度：横坐标 1-31日

            }
        }

        // 3. 刷新图表

        val msg = Message()
        msg.what = CHART_CHANGED
        handler.sendMessage(msg)
    }

    private fun getWeekData(){


    }

    private fun getMonthData(){

    }

    private fun getDayData(){

    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(connect)
        if (isRegistered){
            localBroadcastManager.unregisterReceiver(stateReceiver)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home){
            finish()
        }else if (item.itemId == R.id.calendar_select){
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            // 弹出日期选择器
            DatePickerDialog(
                this,
                // 选中日期后的回调（拿到年/月/日）
                { _, selectYear, selectMonth, selectDay ->
                    // 处理选中的日期（比如显示“2025-12-05”）
                    val selectDate = "$selectYear-${selectMonth + 1}-$selectDay" // 月份从0开始，要+1
                    Toast.makeText(this, "选中日期：$selectDate", Toast.LENGTH_SHORT).show()
                },
                year, month, day
            ).show()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.calendar_menu,menu)
        return super.onCreateOptionsMenu(menu)
    }

}