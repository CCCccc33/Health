package com.example.cardviewtest

import android.app.DatePickerDialog
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IAxisValueFormatter
import com.google.android.material.tabs.TabLayout
import java.lang.ref.WeakReference
import java.util.Calendar
import kotlin.apply
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.utils.Utils
import java.util.concurrent.ConcurrentLinkedQueue


class HeartRateActivity : AppCompatActivity() {
    companion object{
        const val CHART_CHANGED = 1
        const val MAX_DISPLAY_POINTS = 500
        const val DRAW_PER_FRAME = 1
        const val DRAW_INTERVAL = 4L
        const val EXTRA_ECG_BATCH = "ecg_batch_data"
        const val ACTION_DATA_AVAILABLE = "com.example.bluetooth.ACTION_DATA_AVAILABLE"
    }
    private val TAG = "HeartRateActivity"
    private val tabTitles = mutableListOf("实时","日", "周", "月")
    var isServiceConnected: Boolean = false
    private lateinit var localBroadcastManager : LocalBroadcastManager
    //******图*******************//
    //******BarChart****************//
    private lateinit var barChart: BarChart
    private val barChartEntries = mutableListOf<BarEntry>()
    private lateinit var charBarDataSet: LineDataSet
    private lateinit var charBarData: LineData
    ///******lineChart***********//
    private var isShowingRealtimeChart = false
    private lateinit var lineChart: LineChart
    private val chartEntries = mutableListOf<Entry>()
    private lateinit var chartContainer: FrameLayout
    private lateinit var charLineDataSet: LineDataSet
    private lateinit var charLineData: LineData
    private val drawBuffer = ConcurrentLinkedQueue<HealthData>().apply {
        addAll(generateTestHealthData(500)) // 一次性填充500个测试点
    }
    //////***************************************************
    private var typeData: String? = null
    private var curData: List<String>? = null
    private var isRegistered: Boolean = false
    val conversion = Conversion()
    private lateinit var stateReceiver: StateReceiver
    val calendar = Calendar.getInstance()
    private lateinit var selectData: TextView
    private lateinit var dataUnit: TextView
    private lateinit var analyze: TextView
    private lateinit var analyzeContent: TextView

    ///******************test***********************
    private fun generateTestHealthData(count: Int): List<HealthData> {
        val testData = mutableListOf<HealthData>()
        val currentTime = System.currentTimeMillis()
        val timeFields = TimeUtils.parseTimeFields(currentTime) // 复用你之前的时间工具类
        val uploadTime = TimeUtils.timestampToFormat(currentTime)

        // 生成模拟心电数据（正弦波，模拟真实波形）
        for (i in 0 until count) {
            // 正弦波公式：模拟心率波形（1mV振幅，500Hz采样）
            val voltage = Math.sin(i * 0.1).toFloat() // 正弦波，值范围[-1,1]
            testData.add(
                HealthData(
                    id = 0,
                    year = timeFields.year,
                    month = timeFields.month,
                    week = timeFields.week,
                    day = timeFields.day,
                    uploadTime = uploadTime,
                    dataType = "4", // 心电类型
                    value1 = voltage, // 核心：模拟电压值
                    value2 = null,
                    value3 = null,
                    remark = "测试数据"
                )
            )
        }
        return testData
    }
    ///*******************test**********************


    @Suppress("DEPRECATION")
    fun getYearWeekForLowVersion(year: Int, month: Int, day: Int): Int {
        // 设置Calendar为ISO周模式（周一为一周第一天）
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.minimalDaysInFirstWeek = 4 // ISO 8601标准
        calendar.set(year, month - 1, day) // Calendar的月是0-11，需-1
        val weekOfYear = calendar.get(Calendar.WEEK_OF_YEAR)
        return year * 100 + weekOfYear
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
                    val batchData = intent?.getParcelableArrayListExtra<HealthData>(EXTRA_ECG_BATCH)
                    batchData?.let { drawBuffer.addAll(it) }
                    curData = bluetoothBinder.readMessage()
                    curData?.let {

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
            val chart:LineChart = activity.findViewById(R.id.chartContainer)
            when(msg.what){
                CHART_CHANGED ->{
                    activity.lineChart?.let { chart ->
                        chart.data?.notifyDataChanged()
                        chart.notifyDataSetChanged()
                        chart.invalidate()
                    }
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
        analyzeContent = findViewById(R.id.analyze_content)
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
        //初始化tabLayout
        initTabLayout()
        //初始化界面数据
        initCurData()
        //初始化图表
        //初始化数据库
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
    //*******添加View*******************//
    // 切换到“实时图”Tab时调用
    private fun showRealtimeChart() {
        chartContainer = findViewById(R.id.chartContainer)
        chartContainer.removeAllViews()
        lineChart = LineChart(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            description.isEnabled = false // 关闭描述
            legend.isEnabled = false      // 关闭图例
            setTouchEnabled(false)        // 关闭触摸交互
            xAxis.isEnabled = false       // 关闭X轴
            axisLeft.isEnabled = false    // 关闭左Y轴
            axisRight.isEnabled = false   // 关闭右Y轴
            // 折线样式
            chartEntries.clear()
            chartEntries.add(Entry(0f,0f))
            charLineDataSet = LineDataSet(chartEntries, "实时心电").apply {
                color = Color.RED
                lineWidth = 2f
                setDrawCircles(false) // 不画每个点的圆圈
                setDrawValues(false)  // 不显示数值
            }
            charLineData = LineData(listOf(charLineDataSet)) // 设置空数据（后续动态填充）
            this.data = charLineData
        }
        // 第三步：把LineChart添加到FrameLayout容器
        chartContainer.addView(lineChart)
    }

    // 切换到“日/周/年”Tab时调用
    private fun showBarChart(type: String) {
        chartContainer = findViewById<FrameLayout>(R.id.chartContainer)
        chartContainer.removeAllViews()
        // ========== 1. 定义各维度的完整标签 + 要显示的刻度数量 ==========
        val (xAxisLabels, showLabelCount, step) = when (type) {
            "日" -> {
                // 日维度：24小时，显示6个刻度（每4小时一个：00:00、04:00...20:00）
                val labels = listOf(
                    "00:00", "01:00", "02:00", "03:00", "04:00", "05:00", "06:00",
                    "07:00", "08:00", "09:00", "10:00", "11:00", "12:00", "13:00",
                    "14:00", "15:00", "16:00", "17:00", "18:00", "19:00", "20:00",
                    "21:00", "22:00", "23:00", "24:00"
                )
                Triple(labels, 4, 6) // 显示6个标签，步长4（每4个索引显示一个）
            }
            "周" -> {
                // 周维度：7天，显示4个刻度（周一、周三、周五、周日）
                val labels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
                Triple(labels, 4, 2) // 显示4个标签，步长2
            }
            "月" -> {
                // 月维度：30天，显示10个刻度（每3天一个：1日、4日...28日）
                val labels = mutableListOf<String>()
                for (day in 1..30) labels.add("${day}日")
                Triple(labels, 5, 6) // 显示10个标签，步长3
            }
            else -> Triple(emptyList(), 0, 0)
        }
        barChartEntries.clear()
        for (i in xAxisLabels.indices) {
            val randomValue = (65 + Math.random() * 10).toFloat()
            barChartEntries.add(BarEntry(i.toFloat(), randomValue))
        }
        barChart = BarChart(this).apply {
            // 设置图表尺寸：全屏填充FrameLayout
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // 样式配置
            description.isEnabled = false
            legend.isEnabled = false
            xAxis.apply {
                position= XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setLabelCount(showLabelCount, false)
            }
            axisRight.setDrawLabels(false)
            axisRight.setDrawAxisLine(false)
            axisLeft.setDrawGridLines(false)

            val valueFormatter = object : IAxisValueFormatter {
                override fun getFormattedValue(value: Float, axis: AxisBase?): String? {
                    val index = value.toInt()
                    return if (index % step == 0 && index in xAxisLabels.indices) {
                        xAxisLabels[index]
                    } else {
                        ""
                    }
                }
            }
            val dataSet = BarDataSet(barChartEntries, "${type}统计").apply {
                color = Color.BLUE
                barBorderWidth = 0f
                setDrawValues(false)
            }
            data = BarData(listOf(dataSet))
            setOnChartValueSelectedListener(object : OnChartValueSelectedListener{
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e is BarEntry) {
                        val value = e.y // 比如 70.5f
                        val xIndex = e.x.toInt()
                        val xLabel = if (xIndex in xAxisLabels.indices) xAxisLabels[xIndex] else "未知"
                        val currentType = type
                        Log.d("ChartClick", "维度：${typeData}，标签：$xLabel，数值：$value")
                        runOnUiThread {
                            selectData.setText("$value")
                            analyzeContent.setText("您的${typeData}正常,请继续保持\uD83D\uDCAA！")
                        }
                    }
                }

                override fun onNothingSelected() {
                    runOnUiThread {
                        selectData.setText("--")
                        analyzeContent.setText("")
                    }
                }
            })
        }
        chartContainer.addView(barChart)
    }

    fun initTabLayout(){
        val tabLayout: TabLayout = findViewById(R.id.tab_layout1)
        val finalTabTitles = if (typeData != "心率") {
            tabTitles.filter { it != "实时" }.toList()
        } else {
            tabTitles.toList()
        }

        finalTabTitles.forEach { title ->
            tabLayout.addTab(tabLayout.newTab().setText(title))
        }
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                chartDataChanged(finalTabTitles[tab.position])
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {
                val unselectedTitle = finalTabTitles[tab.position]
                if (unselectedTitle == "实时") {
                    isShowingRealtimeChart = false
                    chartEntries.clear()
                }
            }
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        if (finalTabTitles.isNotEmpty()) {
            chartDataChanged(finalTabTitles[0])
        }
    }

    private fun chartDataChanged(tabTitle: String) {
        when (tabTitle) {
            "实时" -> {
                showRealtimeChart()
                isShowingRealtimeChart = true
                handler.removeCallbacks(drawLineRunnable)
                handler.post(drawLineRunnable)
            }
            "日" -> {showBarChart("日")
                isShowingRealtimeChart = false}
            "周" -> {showBarChart("周")
                isShowingRealtimeChart = false}
            "月" -> {showBarChart("月")
                isShowingRealtimeChart = false}
        }
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
                dataUnit.setText("mL")
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
    ///*********************取点********************
    private val drawLineRunnable = object : Runnable {
        override fun run() {
            repeat(DRAW_PER_FRAME) {
                val data = drawBuffer.poll() ?: return@repeat
                addEntryToLineChart(data) // 新增点到Chart
            }
            handler.postDelayed(this, DRAW_INTERVAL)
        }
    }

    // 新增点到Chart
    private fun addEntryToLineChart(ecgData: HealthData) {
        // 1. 添加新Entry（X轴=当前点数，Y轴=电压值）
        ecgData.value1?:return
        chartEntries.add(Entry(chartEntries.size.toFloat(), ecgData.value1))
        if (chartEntries.size > MAX_DISPLAY_POINTS) {
            chartEntries.removeFirstOrNull()
            chartEntries.forEachIndexed { index, entry ->
                entry.x = index.toFloat()
            }
            charLineDataSet.notifyDataSetChanged()
        }

        charLineDataSet.values = chartEntries
        charLineData.notifyDataChanged()
        lineChart.data = charLineData
        lineChart.notifyDataSetChanged()
        lineChart.invalidate()
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