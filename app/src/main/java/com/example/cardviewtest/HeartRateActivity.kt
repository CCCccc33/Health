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
import java.util.concurrent.ConcurrentLinkedQueue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator


class HeartRateActivity : AppCompatActivity() {
    companion object{
        private const val BATCH_THRESHOLD = 6
        const val MAX_DISPLAY_POINTS = 500
        const val DRAW_PER_FRAME = 3
        const val DRAW_INTERVAL = 0L
        const val BUFFER_LOW_THRESHOLD = 15   // 缓冲区不足阈值
        const val BUFFER_HIGH_THRESHOLD = 250  // 缓冲区积压阈值
        const val MAX_POINTS_PER_FRAME = 4    // 单帧最大点数
        const val MIN_POINTS_PER_FRAME = 2     // 单帧最小点数
        const val EXTRA_SINGLE_DATA = "extra_single_data"
        const val ACTION_DATA_AVAILABLE = "com.example.bluetooth.ACTION_DATA_AVAILABLE"
        val DATA_RANGE_MAP = mapOf(
            "心率" to Pair(30f, 180f),       // 心率：0~180 bpm
            "体温" to Pair(15f, 50f),    // 体温：0~45 ℃
            "血压" to Pair(0f, 180f),       // 血压：0~180 mmHg（舒张压/收缩压统一范围）
            "血氧" to Pair(50f, 100f),       // 血氧：0~100 %
            "肺活量" to Pair(1000f, 8000f),  // 肺活量：0~8000 mL
            "皮肤状态" to Pair(0f, 10f)      // 皮肤状态：0~10 （示例范围）
        )
    }
    private var batchCount = 0
    private var dynamicDrawPerFrame:Int = 0
    private val TAG = "HeartRateActivity"
    private val tabTitles = mutableListOf("实时","日", "周", "月")
    private var curTab = ""
    var isServiceConnected: Boolean = false
    private lateinit var localBroadcastManager : LocalBroadcastManager
    //******图*******************//
    //******BarChart****************//
    private var mbarChart: BarChart? = null
    val barEntries1 = mutableListOf<BarEntry>() // 第一组：实际值
    val barEntries2 = mutableListOf<BarEntry>() // 第二组：参考值
    ///******lineChart***********//

    private var isShowingRealtimeChart = false
    private lateinit var mlineChart: LineChart
    private val chartEntries = mutableListOf<Entry>()
    private lateinit var chartContainer: FrameLayout
    private lateinit var charLineDataSet: LineDataSet
    private lateinit var charLineData: LineData
    private val drawBuffer = ConcurrentLinkedQueue<Float>()
    //////***************************************************
    private var typeData: String? = null
    private var typeCode : String = ""
    private var isRegistered: Boolean = false
    private lateinit var stateReceiver: StateReceiver
    val calendar = Calendar.getInstance()
    private lateinit var selectData: TextView
    private lateinit var dataUnit: TextView
    private lateinit var analyze: TextView
    private lateinit var analyzeContent: TextView
    private lateinit var healthRepository: HealthDataRepository


    // 蓝牙状态广播
    inner class StateReceiver(context: Context) : BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            when(action){
                ACTION_DATA_AVAILABLE ->{
                    if (typeData != "心率"){
                        val timestamp = TimeUtils.getCurrentTimestamp()
                        val timeUtils = TimeUtils.parseTimeFields(timestamp)
                        Log.d("ACTION_DATA_AVAILABLE","curTab:${curTab}," +
                                "year:${timeUtils.year},month:${timeUtils.month}," +
                                "day:${timeUtils.day}")
                        queryHealthData(curTab,timeUtils.year,
                            timeUtils.month,
                            timeUtils.week,
                            timeUtils.day)
//                        val data = intent.getIntExtra(EXTRA_SINGLE_DATA,0)
//                        Log.d(TAG,"data:${data}")
//                        runOnUiThread {
//                            selectData.setText("${String.format("%.1f", data)}")
//                        }

                    }else{
                        if (isShowingRealtimeChart) {
                            if (!isServiceConnected){
                                Log.e(TAG,"Service is not connected")
                                return
                            }
                            val batchData = bluetoothBinder.getBatchData()
                            drawBuffer.addAll(batchData)
                            batchCount = batchCount + 1
                            if (batchCount == BATCH_THRESHOLD){
                                dataAnalysis(bluetoothBinder.getHeartRataData().toInt(),typeData)
                                runOnUiThread {
                                    selectData.setText("${String.format("%.1f", bluetoothBinder.getHeartRataData())}")
                                }
                                batchCount = 0
                            }
                        }
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

    fun dataAnalysis(data: Int,type: String?,key: Int = 1){
        var highRange = 30 until 60
        var medRange = 30 until 60
        when (type){
            "心率" ->{
                medRange = 60 until 100
                highRange = 100 until 190
            }
            "血压" ->{
                if (key == 1){  //收缩压
                    medRange = 90 until 139
                    highRange = 139 until 180
                }else{  //舒张压
                    medRange = 60 until 89
                    highRange = 89 until 120
                }
            }
            "体温" ->{
                medRange = 36 until 38
                highRange = 38 until 42
            }
            "血氧" ->{
                medRange = 95 until 100
                highRange = 100 until 110
            }
            "皮肤状态" ->{
                medRange = 50 until 80
                highRange = 80 until 100
            }
            "肺活量" ->{
                medRange = 2500 until 5000
                highRange = 5000 until 7000
            }
            else -> {}
        }
        if (data in highRange){
            analyzeContent.setText("您的${typeData}过高，请多休息并定期监测\uFE0F！")
        }else if (data in medRange ){
            analyzeContent.setText("您的${typeData}正常,请继续保持\uD83D\uDCAA！")
        }else{
            analyzeContent.setText("您的${typeData}过低,请多休息并定期监测\uFE0F！")
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
        }
    }
    private lateinit var bluetoothBinder : BluetoothService.BluetoothBinder
    private val connect = object : ServiceConnection{
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bluetoothBinder = service as BluetoothService.BluetoothBinder
            isServiceConnected = true
            bluetoothBinder.setReceive()
            bluetoothBinder.setOnBleConnectListener()
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
        initFindView()
        val intent = Intent(this, BluetoothService::class.java)
        startService(intent)
        bindService(intent,connect, Context.BIND_AUTO_CREATE)
        stateReceiver = StateReceiver(this)
        healthRepository = HealthDataRepository(this)
        initBluetoothReceiver()
        initTabLayout()
        initCurData()
    }
    fun initFindView(){
        selectData = findViewById(R.id.select_data)
        dataUnit = findViewById(R.id.unit)
        analyze = findViewById(R.id.analyze)
        analyzeContent = findViewById(R.id.analyze_content)
        analyzeContent = findViewById(R.id.analyze_content)
        chartContainer = findViewById(R.id.chartContainer)
        val toolbarHeartRate: Toolbar = findViewById(R.id.toolbar_heart_rate)
        setSupportActionBar(toolbarHeartRate)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbarHeartRate.navigationIcon?.setTint(getColor(R.color.black))
        typeData = intent.getStringExtra("type")
        toolbarHeartRate.setTitle(typeData)
    }
    fun sendInitData(typeData: String){
        var sendData: ByteArray
        when(typeData){
            "心率" -> sendData = byteArrayOf(0xFA.toByte(),0x06.toByte())
            "体温" -> sendData = byteArrayOf(0xFA.toByte(),0x02.toByte())
            "血压" -> sendData = byteArrayOf(0xFA.toByte(),0x04.toByte())
            "血氧" -> sendData = byteArrayOf(0xFA.toByte(),0x01.toByte())
            "肺活量" -> sendData = byteArrayOf(0xFA.toByte(),0x03.toByte())
            "皮肤状态" -> sendData = byteArrayOf(0xFA.toByte(),0x05.toByte())
            else -> sendData = byteArrayOf(0xFA.toByte(),0xEE.toByte()) //error
        }
        if (!isServiceConnected){
            Toast.makeText(this,"未连接设备，请连接设备后重试！", Toast.LENGTH_SHORT).show()
            Log.e(TAG,"iServiceDisconnected")
            return
        }
        if (!bluetoothBinder.sendMessage(sendData)){
            Toast.makeText(this,"设备未连接，请连接设备后重试！", Toast.LENGTH_SHORT).show()
            Log.e(TAG,"sendInitData失败")
            return
        }
    }
    //*******添加View*******************//
    // 切换到“实时图”Tab时调用
    private fun showRealtimeChart() {
        chartContainer.removeAllViews()
        mlineChart = LineChart(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            this.setNoDataText("正在获取最新实时数据...")
            this.setNoDataTextColor(Color.parseColor("#666666"))
            xAxis.axisMaximum = MAX_DISPLAY_POINTS.toFloat() // X轴最大显示范围
            setVisibleXRangeMaximum(MAX_DISPLAY_POINTS.toFloat())
            description.isEnabled = false // 关闭描述
            legend.isEnabled = false      // 关闭图例
            setTouchEnabled(false)        // 关闭触摸交互
            xAxis.isEnabled = false       // 关闭X轴
            axisLeft.isEnabled = true    // 关闭左Y轴
            axisRight.isEnabled = false   // 关闭右Y轴

            val (minY, maxY) = Pair(0f, 3500f)
            axisLeft.axisMinimum = minY
            axisLeft.axisMaximum = maxY
            axisLeft.granularity = 100f // 刻度间隔10，可根据需求调整

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
        mlineChart.invalidate()
        chartContainer.addView(mlineChart)
    }

    private fun showBarChart(type: String) {
        chartContainer.removeAllViews()

        val (xAxisLabels, step,_) = when (type) {
            "日" -> {
                // 日维度：24小时，显示6个刻度（每4小时一个：00:00、04:00...20:00）
                val labels = listOf(
                    "00:00", "01:00", "02:00", "03:00", "04:00", "05:00", "06:00",
                    "07:00", "08:00", "09:00", "10:00", "11:00", "12:00", "13:00",
                    "14:00", "15:00", "16:00", "17:00", "18:00", "19:00", "20:00",
                    "21:00", "22:00", "23:00", "24:00"
                )
                Triple(labels, 6,0)
            }
            "周" -> {
                val labels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
                Triple(labels, 1, 0) // 显示4个标签，步长2
            }
            "月" -> {
                val labels = mutableListOf<String>()
                for (day in 1..30) labels.add("${day}日")
                Triple(labels, 7, 0)
            }
            else -> Triple(emptyList(), 0, 0)
        }

        val barChart =mbarChart?: BarChart(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // 样式配置
            description.isEnabled = false
            legend.isEnabled = true
            xAxis.apply {
                position= XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                axisMinimum = 0f // X轴最小值
                granularity = 1f // 刻度最小步长
            }
            axisLeft.apply {
                setDrawGridLines(false)
            }
            axisRight.apply {
                setDrawLabels(false)
                setDrawAxisLine(false)
            }

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener{
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e is BarEntry && h != null) {
                        val xIndex = h.x.toInt()
                        Log.d(TAG,"选择${e.x.toInt()}(e),${xIndex}(h)")
                        var value2 = 0f // 第二组（参考值）
                        var value = 0f//e.y // 比如 70.5f
                        for (entry in barEntries1) {
                            if (entry.x.toInt() == xIndex) {
                                value = entry.y
                                break
                            }
                        }
                        if (typeData == "血压"){
                            for (entry in barEntries2) {
                                if (entry.x.toInt() == xIndex) {
                                    value2 = entry.y
                                    break
                                }
                            }
                        }
                        val xLabel = if (xIndex in xAxisLabels.indices) xAxisLabels[xIndex] else "未知"

                        Log.d(TAG, "维度：${typeData}，标签：$xLabel，数值：$value")
                        runOnUiThread {
                            if (typeData == "血压"){
                                selectData.setText("${String.format("%.1f",value)}/${String.format("%.1f",value2)}")
                            }else{
                                selectData.setText("${String.format("%.1f", value)}")
                            }
                            dataAnalysis(value.toInt(),typeData)
                        }
                    }
                }
                override fun onNothingSelected() {
                    runOnUiThread {
                        if (typeData == "血压"){
                            selectData.setText("-/-")
                        }else{
                            selectData.setText("--")
                        }
                        analyzeContent.setText("")
                    }
                }
            })
            mbarChart = this
        }

        barChart.xAxis.setLabelCount(xAxisLabels.size, false)
        barChart.xAxis.valueFormatter = object : IAxisValueFormatter {
            override fun getFormattedValue(value: Float, axis: AxisBase?): String? {
                val index = value.toInt()
                Log.d(TAG,"step=$step,index=$index")
                return if (index % step == 0 && index in xAxisLabels.indices) {
                    Log.d(TAG,"${xAxisLabels[index]}")
                    xAxisLabels[index]
                } else {
                    ""
                }
            }
        }

        val (min, max) = DATA_RANGE_MAP[typeData] ?: Pair(0f, 100f)
        barChart.axisLeft.axisMinimum = min // 固定最小值
        barChart.axisLeft.axisMaximum = max // 固定最大值

        barEntries1.clear()// 第一组
        barEntries2.clear() // 第二组

        if (barEntries1.isEmpty()){
                addChartData(barEntries1,xAxisLabels.size)
        }
        if (barEntries2.isEmpty()){
                addChartData(barEntries2,xAxisLabels.size)
        }
        //这是啥
        val dataSet = barChart.data?.getDataSetByIndex(0) as? BarDataSet ?: BarDataSet(barEntries1, "${type}统计").apply {
            color = Color.BLUE
            barBorderWidth = 0f
            setDrawValues(false)
        }
        dataSet.values = barEntries1

        if (typeData == "血压"){
            val dataSet2 = barChart.data?.getDataSetByIndex(1) as? BarDataSet ?: BarDataSet(barEntries2, "舒张压").apply {
                color = Color.parseColor("#FF9800")
                barBorderWidth = 0f
                setDrawValues(false)
            }
            dataSet2.values = barEntries2
            barChart.data = BarData(dataSet,dataSet2)
            barChart.barData.barWidth = 0.4f
            barChart.barData.groupBars(0f, 0.1f, 0.05f)
        }else{
            barChart.data = BarData(dataSet)
        }

        barChart.data?.notifyDataChanged()
        barChart.notifyDataSetChanged()
        barChart.invalidate()
        chartContainer.addView(barChart)

        //************从数据库取数*****************//
        val timestamp = TimeUtils.getCurrentTimestamp()
        val timeUtils = TimeUtils.parseTimeFields(timestamp)
        queryHealthData(type,timeUtils.year,
            timeUtils.month,
            timeUtils.week,
            timeUtils.day)
        //************从数据库取数*****************//
    }

    fun addChartData(barEntry: MutableList<BarEntry>,length:Int){
        for (i in 0 until length){
            barEntry.add(BarEntry(i.toFloat(),0f))
        }
    }

    fun initTabLayout(){
        val tabLayout: TabLayout = findViewById(R.id.tab_layout1)
        val finalTabTitles = if (typeData != "心率") {
            tabTitles.filter { it != "实时" }.toList()
        } else {
            tabTitles.toList()
        }
        curTab = if (typeData != "心率") "日" else "实时"
        finalTabTitles.forEach { title ->
            tabLayout.addTab(tabLayout.newTab().setText(title))
        }
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                curTab = finalTabTitles[tab.position]
                chartDataChanged(curTab)
                Log.d(TAG,"切换页面到${curTab}")
                if (curTab == "实时"){
                    bluetoothBinder.sendMessage(byteArrayOf(0xFA.toByte(),0x06.toByte()))
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {
                val unselectedTitle = finalTabTitles[tab.position]
                if (unselectedTitle == "实时") {
                    isShowingRealtimeChart = false
                    bluetoothBinder.sendMessage(byteArrayOf(0xFA.toByte(),0xAB.toByte()))
                    handler.removeCallbacks(drawLineRunnable)
                    chartEntries.clear()
                    Log.d(TAG,"drawBuffer:${drawBuffer}")
                }
                if (typeData == "血压"){
                    selectData.setText("-/-")
                }else{ selectData.setText("--") }
                analyzeContent.setText("")
            }
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        if (finalTabTitles.isNotEmpty()) {
            chartDataChanged(finalTabTitles[0])
            Log.d(TAG,"drawBuffer:${drawBuffer}")
        }
    }
    //********************view********************//
    private fun chartDataChanged(tabTitle: String) {
        Log.d("chartDataChanged","chartDataChanged:${tabTitle}")
        when (tabTitle) {
            "实时" -> {
                isShowingRealtimeChart = true
                showRealtimeChart()
                handler.removeCallbacks(drawLineRunnable)
                handler.post(drawLineRunnable)
            }
            "日" -> {showBarChart("日") }
            "周" -> { showBarChart("周") }
            "月" -> {showBarChart("月") }
        }
    }


    fun initCurData(){
        if (typeData == null){
            Log.e(TAG,"initCurData() typeData为空!")
            return
        }
        analyze.setText("${typeData}分析")
        when(typeData){
            "心率" ->{
                selectData.setText("--")
                dataUnit.setText("bpm")
                typeCode = "06"
            }
            "血氧" ->{
                selectData.setText("--")
                dataUnit.setText("%")
                typeCode = "01"
            }
            "体温" ->{
                selectData.setText("--")
                dataUnit.setText("℃")
                typeCode = "02"
            }
            "肺活量" ->{
                selectData.setText("--")
                dataUnit.setText("mL")
                typeCode = "03"
            }
            "血压" ->{
                selectData.setText("-/-")
                dataUnit.setText("mmHg")
                typeCode = "04"
            }
            "皮肤状态" ->{
                selectData.setText("--")
                dataUnit.setText("")
                typeCode = "05"
            }
        }
    }

    //****************数据库取数**************************//

    fun setData(){
        when(typeData){
            "心率" ->{ typeCode = "06" }
            "血氧" ->{ typeCode = "01" }
            "体温" ->{ typeCode = "02" }
            "肺活量" ->{ typeCode = "03" }
            "血压" ->{ typeCode = "04" }
            "皮肤状态" ->{ typeCode = "05" }
        }
    }
    private fun queryHealthData(type:String,
                                targetYear:Int,
                                targetMonth:Int,
                                targetWeek:Int,
                                targetDay:Int) {
        lifecycleScope.launch {
            try {
                setData()
                Log.d("HealthQuery", "查询参数：type=$type, year=$targetYear, month=$targetMonth, day=$targetDay, typeCode=$typeCode")
                val healthDataList = when (type) {
                    "日" -> healthRepository.getHealthDataByDate(targetYear, targetMonth, targetDay, typeCode)
                    "周" -> healthRepository.getHealthDataByWeek(targetYear, targetMonth, targetWeek, typeCode)
                    "月" -> healthRepository.getHealthDataByMonth(targetYear, targetMonth, typeCode)
                    else -> emptyList()
                }

                if (healthDataList.isEmpty()) {
                    Log.d("HealthQuery", "该${type},${typeCode}没有健康数据")
                    return@launch
                }

                val typeChoose = when (type) {
                    "日" -> "hour"
                    "周" -> "week"
                    "月" -> "day"
                    else -> ""
                }

                getAverageData(healthDataList, 1, barEntries1, typeChoose)
                if (typeData == "血压") {
                    getAverageData(healthDataList, 2, barEntries2, typeChoose)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    fun getAverageData(dataList:List<HealthData>,dataType:Int,barEntry: MutableList<BarEntry>,keyType: String = "day"){
        val valueMap = mutableMapOf<Int, MutableList<Float>>()
        for (data in dataList) {
            val value: Float = if (dataType == 2){  //血压的收缩压
               data.value2?:continue
            }else{
                data.value1?:continue
            }
            val key = when (keyType) {
                "hour" -> {
                    val upLoadTime = TimeUtils.formatToTimestamp(data.uploadTime)
                    val timeUtils = TimeUtils.parseTimeFields(upLoadTime)
                    timeUtils.hour
                }
                else -> data.day
            }
            valueMap.getOrPut(key) { mutableListOf() }.add(value)
            Log.d("HealthData", "时间: ${data.uploadTime}, 数值: ${value.toInt()}")
        }

        val entries = mutableListOf<BarEntry>()
        for ((key, valueList) in valueMap) {
            val averageValue = valueList.average().toFloat()
            val x = if (keyType == "week") convertDayToWeekday(dataList.first().year,
                dataList.first().month,
                dataList.first().day) else key.toFloat()
            Log.d("getAverageData","day:${x},valueList:${valueList}")
            entries.add(BarEntry(x, averageValue))
        }
        barEntry.clear()
        barEntry.addAll(entries)
        //test
        runOnUiThread{
            refreshBarChart()
        }
    }
    private fun refreshBarChart() {
        mbarChart?.let { chart ->
            chart.data?.notifyDataChanged()
            chart.notifyDataSetChanged()
            chart.invalidate()
        }
    }
    fun convertDayToWeekday(year: Int, month: Int, day: Int): Float {
        val calendar = Calendar.getInstance().apply {
            // 注意：Calendar的月份是 0~11，因此需要将传入的 month（1~12）减 1
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
        }
        val weekdayValue = calendar.get(Calendar.DAY_OF_WEEK)
        return when (weekdayValue) {
            Calendar.MONDAY -> 0f
            Calendar.TUESDAY -> 1f
            Calendar.WEDNESDAY -> 2f
            Calendar.THURSDAY -> 3f
            Calendar.FRIDAY -> 4f
            Calendar.SATURDAY -> 5f
            Calendar.SUNDAY -> 6f
            else -> 99f // 异常情况返回空字符串
        }
    }
    fun clearChartData(){
        barEntries1.clear()
        barEntries2.clear()
    }
    //****************数据库取数**************************//

    ///*********************画点********************//
    private fun adjustDrawRate() {
        val bufferSize = drawBuffer.size

        when {
            // 缓冲区积压严重 - 加速消费
            bufferSize > BUFFER_HIGH_THRESHOLD -> {
                dynamicDrawPerFrame = minOf(
                    MAX_POINTS_PER_FRAME,
                    dynamicDrawPerFrame + 1  // 每次增加2点
                )
//                Log.w("DrawRate", "缓冲区积压($bufferSize)，加速绘制到: $dynamicDrawPerFrame")
            }
            // 缓冲区不足 - 减速消费
            bufferSize < BUFFER_LOW_THRESHOLD -> {
                dynamicDrawPerFrame = maxOf(
                    MIN_POINTS_PER_FRAME,
                    dynamicDrawPerFrame - 1  // 每次减少1点
                )
//                Log.w("DrawRate", "缓冲区不足($bufferSize)，减速绘制到: $dynamicDrawPerFrame")
            }
            // 缓冲区正常 - 回归基准速度
            else -> {
                val target = DRAW_PER_FRAME
                if (dynamicDrawPerFrame != target) {
                    dynamicDrawPerFrame = when {
                        dynamicDrawPerFrame > target -> dynamicDrawPerFrame - 1
                        dynamicDrawPerFrame < target -> dynamicDrawPerFrame + 1
                        else -> target
                    }
                }
            }
        }
    }
    private val drawLineRunnable = object : Runnable {
        override fun run() {
            if (isShowingRealtimeChart){
                adjustDrawRate()
                repeat(dynamicDrawPerFrame) {
//                    Log.d("drawBuffer","size:${drawBuffer.size}")
                    val data = drawBuffer.poll()
                    data?.let {
                        addEntryToLineChart(it) // 新增点到Chart
                    }
                }
            }
            handler.postDelayed(this, DRAW_INTERVAL)
        }
    }

    // 新增点到Chart
    private fun addEntryToLineChart(ecgData: Float) {
        // 1. 添加新Entry（X轴=当前点数，Y轴=电压值）
        chartEntries.add(Entry(chartEntries.size.toFloat(), ecgData))
        if (chartEntries.size > MAX_DISPLAY_POINTS) {
            chartEntries.removeFirstOrNull()
            chartEntries.forEachIndexed { index, entry ->
                entry.x = index.toFloat()
            }
            charLineDataSet.notifyDataSetChanged()
        }
        charLineDataSet.values = chartEntries
        charLineData.notifyDataChanged()
        mlineChart?.let {
            it.data = charLineData
            it.notifyDataSetChanged()
            it.invalidate()
        }
    }

    ///*******************画点********************//

    override fun onDestroy() {
        super.onDestroy()
        bluetoothBinder.clear()
        unbindService(connect)
        Log.e("BluetoothService", "Service 被销毁！")
        drawBuffer.clear()
        if (typeData == "心率"){
            handler.removeCallbacks(drawLineRunnable)
        }
        if (isRegistered){
            localBroadcastManager.unregisterReceiver(stateReceiver)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home){
            finish()
        }else if (item.itemId == R.id.calendar_select){
            DatePickerDialog(
                this,
                { _, selectYear, selectMonth, selectDay ->
                    val selectDate = "$selectYear-${selectMonth + 1}-$selectDay" // 月份从0开始，要+1
                    Toast.makeText(this, "选中日期：$selectDate", Toast.LENGTH_SHORT).show()
                    val cal = Calendar.getInstance().apply { set(selectYear, selectMonth, selectDay) }
                    val weekOfMonth = cal.get(Calendar.WEEK_OF_MONTH)
                    queryHealthData(curTab,
                        selectYear,
                        selectMonth+1,
                        weekOfMonth,
                        selectDay)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.calendar_menu,menu)
        return super.onCreateOptionsMenu(menu)
    }

}