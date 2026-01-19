package com.example.cardviewtest

import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.motionapps.kotlin_ecg_detectors.Detectors
import kotlin.math.sqrt

class BluetoothService : Service() {
    var curPosition : Int = 0
    var curBatchData = mutableListOf<Float>()
    var heartRateData: Double = 0.0

    companion object {
        const val TAG = "BluetoothService"
        const val ACTION_DATA_AVAILABLE = "com.example.bluetooth.ACTION_DATA_AVAILABLE"
        const val ACTION_DATA_UNAVAILABLE = "com.example.bluetooth.ACTION_DATA_UNAVAILABLE"

        const val ACTION_BLE_STATE = "com.example.bluetooth.ACTION_BLE_CONNECT_STATE"
        const val SAMPLE_RATE = 1000.0
        private const val BATCH_SIZE = 400 // 缓存达到 200 条数据，集中发送广播
        const val STATE_CONNECT_SUCCESS = 10    // 连接成功
        const val STATE_CONNECT_FAILURE = 11    // 连接失败
        const val STATE_DISCONNECT_SUCCESS = 12 // 断开成功
        const val ecgNormalMax = 3500
        const val ecgNormalMin = 1000
        const val ecgStdNormalMin = 0.05  //存疑
        const val ecgStdNormalMax = 3
        // 广播中携带数据的 Key
        const val EXTRA_STATE = "extra_state"  // 状态码
        const val EXTRA_SINGLE_DATA = "extra_single_data"

        const val EXTRA_DEVICE_POSITION = "extra_device_position" // 设备地址（用于更新列表）
    }
    //******数据缓存****//
    private val ecgData = mutableListOf<Double>()
    private val dataCacheQueue = ConcurrentLinkedQueue<Float>()  // 接收buffer
    private val dataRepository by lazy { HealthDataRepository(applicationContext) }

    private val btController = BlueToothController
    private val mBinder = BluetoothBinder()
    private var curTypeData = ""//当前接收数据类型
    @Volatile
    private var isReceive = false //是否开始接收

    val batchHandler = Handler(Looper.getMainLooper())

    private val localBroadcastManager = LocalBroadcastManager.getInstance(this)
    private fun sendStateBroadcast(action: String, state: Int, position: Int? = null) {
        when(action){
            "CONNECT" ->{
                val intent = Intent(ACTION_BLE_STATE)
                intent.putExtra(EXTRA_STATE, state)    // 传入状态码
                intent.putExtra(EXTRA_DEVICE_POSITION,position)
                localBroadcastManager.sendBroadcast(intent) // 发送广播
            }
            "BATCH_DATA_VALID" ->{
                val intent = Intent(ACTION_DATA_AVAILABLE)
                localBroadcastManager.sendBroadcast(intent) // 发送广播
            }
            "FAILURE" ->{
                val intent = Intent(ACTION_DATA_UNAVAILABLE)
                localBroadcastManager.sendBroadcast(intent) // 发送广播
            }
        }

    }
    inner class BluetoothBinder: Binder(){
        fun connectDevice(readUuid: String, writeUuid: String, serviceUuid: String,
                            bluetoothDevice: BluetoothDevice,position: Int){
            btController.setOnBleConnectListener(onBleConnectListener)
            curPosition = position
            btController.connectBleDevice(
                applicationContext,
                bluetoothDevice,
                6000,
                onBleConnectListener,
                readUuid,
                writeUuid,
                serviceUuid
            )
        }

        fun setOnBleConnectListener(){
            btController.setOnBleConnectListener(onBleConnectListener)
        }

        fun disconnectDevice(){
            btController.setOnBleConnectListener(onBleConnectListener)
            btController.disConnectDevice()
        }
        fun sendMessage(msg: ByteArray): Boolean{
            return btController.sendMessage(msg)
        }
        //*****获取数据*********//
        fun setReceive(){
            isReceive = true
        }
        fun getBatchData(): List<Float> {
            return curBatchData
        }
        fun getHeartRataData(): Double{
            return heartRateData
        }

        //*****清理缓存******//
        fun clear(){
            isReceive = false
            dataCacheQueue.clear()
            synchronized(this) {
                curBatchData?.clear()
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "onCreate executed")
        return mBinder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand executed")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        btController.releaseResources()
        batchHandler.removeCallbacksAndMessages(null)
    }


    private val onBleConnectListener :OnBleConnectListener = object : OnBleConnectListener{
        override fun onConnecting(gatt: BluetoothGatt?, bluetoothDevice: BluetoothDevice?) {
            Log.d(TAG, "onConnecting")
        }

        override fun onConnectSuccess(
            gatt: BluetoothGatt?,
            bluetoothDevice: BluetoothDevice?,
            statues: Int
        ) {
            Log.d(TAG, "onConnectSuccess")
        }

        override fun onConnectFailure(
            gatt: BluetoothGatt?,
            bluetoothDevice: BluetoothDevice?,
            description: String,
            statues: Int
        ) {
            sendStateBroadcast("CONNECT",STATE_CONNECT_FAILURE,curPosition)
        }

        override fun onDisConnecting(gatt: BluetoothGatt?, bluetoothDevice: BluetoothDevice?) {
            Log.d(TAG, "onDisConnecting")
        }

        override fun onDisConnectSuccess(
            gatt: BluetoothGatt?,
            bluetoothDevice: BluetoothDevice?,
            statues: Int
        ) {
            sendStateBroadcast("CONNECT",STATE_DISCONNECT_SUCCESS,curPosition)
        }

        override fun onServiceDiscoverySucceed(
            gatt: BluetoothGatt?,
            bluetoothDevice: BluetoothDevice?,
            statues: Int
        ) {
            Log.d(TAG, "onServiceDiscoverySucceed")
            sendStateBroadcast("CONNECT",STATE_CONNECT_SUCCESS,curPosition)
        }

        override fun onServiceDiscoveryFailed(
            gatt: BluetoothGatt?,
            bluetoothDevice: BluetoothDevice?,
            statues: String
        ) {
            sendStateBroadcast("CONNECT",STATE_CONNECT_FAILURE,curPosition)
        }

        override fun onReceiveMessage(
            gatt: BluetoothGatt?,
            device: BluetoothDevice?,
            characteristic: BluetoothGattCharacteristic?,
            bytes: ByteArray?
        ) {
            Log.d(TAG, "onReceiveMessage")
            if (isReceive){
                bytes?.let {
                    val dataList = parseRawData(bytes) //解析接收数据
                    if (curTypeData != "06"){
                        dataList?:return
                        saveData(dataList)
                    }
                }
            }
        }

        override fun onReceiveError(error: Int) {
            Log.d(TAG, "onReceiveError")
        }

        override fun onWriteSuccess(
            gatt: BluetoothGatt?,
            device: BluetoothDevice?,
            characteristic: BluetoothGattCharacteristic?,
            bytes: String
        ) {
            val hexSegments = bytes.split(" ")
            val targetHex = hexSegments[1]
            curTypeData = targetHex
            Log.d(TAG, "onWriteSuccess")
        }

        override fun onWriteFailure(
            gatt: BluetoothGatt?,
            device: BluetoothDevice?,
            characteristic: BluetoothGattCharacteristic?,
            bytes: ByteArray?,
            description: String
        ) {
            Log.d(TAG, "onWriteFailure")
        }

        override fun onMTUSetSuccess(mtu: Int) {
            Log.d(TAG, "onMTUSetSuccess")
        }

        override fun onMTUSetFailure(mtu: Int) {
            Log.d(TAG, "onMTUSetFailure")
        }
    }
    // 解析原始BLE数据
    private fun parseRawData(rawData: ByteArray): HealthData? {
        val receiveTimestamp = TimeUtils.getCurrentTimestamp()
        val timeFields = TimeUtils.parseTimeFields(receiveTimestamp)
        val uploadTime = TimeUtils.timestampToFormat(receiveTimestamp)
        var dataValue = 0f
        var dataValue2 = 0f
        when(curTypeData){  //默认大端模式了，不是的话再改
            "06" ->{ //心率 -12位
                var twoBytes : ByteArray
                for (i in rawData.indices step 4) {
                    val currentFrame = rawData.copyOfRange(i, i + 4)
                    twoBytes = currentFrame.copyOfRange(2, 4) // 从索引2开始，到索引4结束（不包含4，即取2、3索引）
                    val byteArray = byteArrayOf(twoBytes[0],twoBytes[1])
                    val data = twoBytesBigEndianToInt(byteArray,true)
                    dataValue = data.toFloat()
                    dataCacheQueue.add(dataValue)
                    ecgData.add(dataValue.toDouble())
                }
                if (dataCacheQueue.size>=BATCH_SIZE){ processBatchData() }
                if (ecgData.size >= 3000){
                    val doubleArray: DoubleArray = ecgData.toDoubleArray() // 最终转为DoubleArray
                    if (!ecgDetect(doubleArray)){
                        sendStateBroadcast("FAILURE",-1)
                    }
                    ecgData.clear()
                }

            }
            "02" ->{  //体温 -两字节：整数部分和小数部分
                val intPart = rawData[2].toInt()
                val decimalPart = rawData[4].toInt()
                val decimalFloat = decimalPart.toFloat() / 100
                dataValue = intPart.toFloat() + decimalFloat
            }
            "04" ->{  //血压
                dataValue = rawData[2].toFloat()  //收缩压
                dataValue2 = rawData[3].toFloat()   //舒张压
            }
            "01" ->{  //血氧
                dataValue = rawData[2].toFloat()
            }
            "03" ->{  //肺活量
                val byteArray = byteArrayOf(rawData[2],rawData[3])
                val data = twoBytesBigEndianToInt(byteArray,true)
                dataValue = data.toFloat()

            }
            "05" ->{  //皮肤状态
                dataValue = rawData[2].toFloat()  //皮肤水分
                dataValue2 = rawData[3].toFloat()  //皮肤油分
            }
        }
        Log.d("parseRawData","curTypeData:${curTypeData},dataValue:${dataValue},dataValue2:${dataValue2}")
        return if(curTypeData == "06") null
        else HealthData(
            year = timeFields.year,
            month = timeFields.month,
            week = timeFields.week,
            day = timeFields.day,
            uploadTime = uploadTime,
            dataType = curTypeData,
            value1=dataValue,
            value2 = dataValue2,
            value3 = 0f)
    }

    // 处理批量数据：存库 + 广播
    private fun processBatchData() {
        val dataList = mutableListOf<Float>()
        var data: Float? = dataCacheQueue.poll()
        while (data != null) {
            dataList.add(data)
            data = dataCacheQueue.poll()
        }
        if (dataList.isEmpty()) return
        curBatchData = dataList
        sendStateBroadcast("BATCH_DATA_VALID",-1)
    }

    /**
    * 脉搏信号处理
    * */
    fun ecgDetect(data: DoubleArray): Boolean{
//        if (!isEcgBatchValid(data)) return false
        var heartRate = processECGWithPost(data, SAMPLE_RATE)
        heartRateData =heartRate?:0.0
        val receiveTimestamp = TimeUtils.getCurrentTimestamp()
        val timeFields = TimeUtils.parseTimeFields(receiveTimestamp)
        val uploadTime = TimeUtils.timestampToFormat(receiveTimestamp)
        val HRData = HealthData(
            year = timeFields.year,
            month = timeFields.month,
            week = timeFields.week,
            day = timeFields.day,
            uploadTime = uploadTime,
            dataType = curTypeData,
            value1= heartRateData.toFloat(),
            value2 = null,
            value3 = null)
        saveData(HRData)
        return true
    }

    /**
     * 步骤2：判断批次数据是否有效
     */
    private fun isEcgBatchValid(ecgData: DoubleArray): Boolean {
        // 计算最大值、最小值
        val maxVal = ecgData.maxOrNull() ?: return false
        val minVal = ecgData.minOrNull() ?: return false

        // 1. 幅值超出正常范围 → 无效
        if (maxVal > ecgNormalMax || minVal < ecgNormalMin) {
            Log.d("EcgProcessor", "幅值异常：$minVal ~ $maxVal（超出$ecgNormalMin ~ $ecgNormalMax）")
            return false
        }

        // 2. 标准差异常（过平/波动过大）→ 无效
        val stdVal = calculateStandardDeviation(ecgData)
        if (stdVal < ecgStdNormalMin || stdVal > ecgStdNormalMax) {
            Log.d("EcgProcessor", "标准差异常：$stdVal（超出$ecgStdNormalMin ~ $ecgStdNormalMax）")
            return false
        }
        return true
    }

    private fun calculateStandardDeviation(data: DoubleArray): Double {
        val mean = data.average()
        val sumOfSquares = data.sumOf { (it - mean) * (it - mean) }
        return sqrt(sumOfSquares / data.size)
    }

    fun processECGWithPost(data: DoubleArray, sampleRate: Double): Double? {
        val detectors = Detectors(sampleRate)
        val rawRWaveIndices = detectors.panTompkinsDetector(data).toList()
        Log.d("processECGWithPost","原始R波索引: $rawRWaveIndices")
        val postProcessor = RWavePostProcessor(sampleRate = sampleRate.toInt())
        val validRWaveIndices = postProcessor.postProcessRWaveIndices(rawRWaveIndices)
        Log.d("processECGWithPost","后处理后有效R波索引: $validRWaveIndices")
        val heartRate = postProcessor.calculateHeartRate(validRWaveIndices)
        heartRate?.let {
            Log.d("processECGWithPost","最终心率: $it bpm")
        } ?: Log.d("processECGWithPost","心率计算失败")
        return heartRate
    }


    fun twoBytesBigEndianToInt(byteArray: ByteArray, isUnsigned: Boolean = false): Int {
        require(byteArray.size == 2) { "转换2字节Int需要长度为2的ByteArray，当前长度：${byteArray.size}" }
        val byteBuffer = ByteBuffer.wrap(byteArray)
            .order(ByteOrder.BIG_ENDIAN)
        val signedShort = byteBuffer.short
        return if (isUnsigned) {
            signedShort.toInt() and 0xFFFF
        } else {
            signedShort.toInt()
        }
    }



    fun saveData(dataList:HealthData){
        CoroutineScope(Dispatchers.IO).launch {
            var saveSuccess = false
            try {
                dataRepository.saveBatchData(dataList)
                Log.d(TAG,"dataList.value1:${dataList.value1}")
                saveSuccess = true
            } catch (e: Exception) {
                Log.e(TAG, "存库失败：${e.message}")
                delay(1000)
                launch { dataRepository.saveBatchData(dataList) }
            }
            if (saveSuccess and (curTypeData != "06")) {
                val intent = Intent(ACTION_DATA_AVAILABLE)
                intent.putExtra(EXTRA_SINGLE_DATA, dataList.value1?.toInt())    // 传入状态码
                localBroadcastManager.sendBroadcast(intent) // 发送广播
            }
        }

    }
}