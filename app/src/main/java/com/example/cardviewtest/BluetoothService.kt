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
import java.nio.ByteBuffer
import java.util.Collections
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class BluetoothService : Service() {
    private val conversion = Conversion()
    var curPosition : Int = 0
    var curBatchData = mutableListOf<HealthData>()
    companion object {
        const val TAG = "BluetoothService"
        const val ACTION_DATA_AVAILABLE = "com.example.bluetooth.ACTION_DATA_AVAILABLE"
        const val ACTION_BLE_STATE = "com.example.bluetooth.ACTION_BLE_CONNECT_STATE"
        private const val BATCH_SIZE = 200 // 缓存达到 50 条数据，集中发送广播
        private const val MAX_DELAY = 1000L // 兜底：1s 内没凑够 15 条，也集中发送（避免数据积压）
        const val STATE_CONNECT_SUCCESS = 10    // 连接成功
        const val STATE_CONNECT_FAILURE = 11    // 连接失败
        const val STATE_DISCONNECT_SUCCESS = 12 // 断开成功
        // 广播中携带数据的 Key
        const val EXTRA_STATE = "extra_state"  // 状态码
        const val EXTRA_SINGLE_DATA = "extra_single_data"

        const val EXTRA_DEVICE_POSITION = "extra_device_position" // 设备地址（用于更新列表）
    }
    //******数据缓存****//
    private val dataCacheQueue = ConcurrentLinkedQueue<HealthData>()  // 接收buffer
    private val dataRepository by lazy { HealthDataRepository(applicationContext) }

    private val btController = BlueToothController
    private val mBinder = BluetoothBinder()
    private var curTypeData = ""//当前接收数据类型
    @Volatile
    private var isReceive = false //是否开始接收
    val batchHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        if ((curTypeData == "01") and isReceive)
            batchHandler.postDelayed(batchRunnable,MAX_DELAY)
    }
    private val batchRunnable= object :Runnable{
        override fun run() {
            if (dataCacheQueue.isNotEmpty()){
                sendStateBroadcast("BATCH_DATA_VALID",-1)
            }
            batchHandler.postDelayed(this,MAX_DELAY)
        }
    }

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
                15000,
                onBleConnectListener,
                readUuid,
                writeUuid,
                serviceUuid
            )
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
        fun getBatchData(): List<HealthData> {
            return curBatchData
        }

        //*****清理缓存******//
        fun clear(){
            isReceive = false
            if (btController.bluetoothState()){
                btController.sendMessage(conversion.hexString2Bytes("FF"))
            }
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
                    if (curTypeData == "01"){  //心率单独处理
                        dataCacheQueue.addAll(dataList)
                        if (dataCacheQueue.size>=BATCH_SIZE){
                            processBatchData()
                        }
                    }else{
                        val curData = dataList.firstOrNull()  // 想一下怎么送出去
                        val newDataList = ArrayList<HealthData>()
                        if (curData != null) {
                            newDataList.add(curData) // 单个对象加入集合
                        }
                        val intent = Intent(ACTION_DATA_AVAILABLE)
                        intent.putParcelableArrayListExtra(EXTRA_SINGLE_DATA,newDataList)
                        localBroadcastManager.sendBroadcast(intent) // 发送广播

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
            curTypeData = bytes
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
    private fun parseRawData(rawData: ByteArray): List<HealthData> {
        val dataList = mutableListOf<HealthData>()
        val receiveTimestamp = TimeUtils.getCurrentTimestamp()
        val timeFields = TimeUtils.parseTimeFields(receiveTimestamp)
        val uploadTime = TimeUtils.timestampToFormat(receiveTimestamp)
        var dataValue: Float = 0f
        var dataValue2 : Float = 0f
        when(curTypeData){  //默认大端模式了，不是的话再改
            "01" ->{ //心率 -12位
                val high4bits = rawData[0].toInt() and 0x0f
                val low8bits = rawData[1].toInt() and 0xff
                dataValue = ((high4bits shl 8) or low8bits).toFloat()
            }
            "02" ->{  //体温 -两字节：整数部分和小数部分
                val objInt = rawData[0].toInt()
                val objDec = rawData[1].toInt()
                dataValue = objInt + objDec.toFloat()/100f    //注意：送来的数的小数要是俩位的
            }
            "03" ->{  //血压
                val sbp = rawData[0].toInt()  //收缩压
                val dbp = rawData[1].toInt()  //舒张压
                dataValue = sbp.toFloat()
                dataValue2 = dbp.toFloat()
            }
            "04" ->{  //血氧

            }
            "05" ->{  //肺活量

            }
            "06" ->{  //皮肤状态

            }
        }
        dataList.add(HealthData(
            year = timeFields.year,
            month = timeFields.month,
            week = timeFields.week,
            day = timeFields.day,
            uploadTime = uploadTime,
            dataType = curTypeData,
            value1=dataValue,
            value2 = dataValue2,
            value3 = null))
        return dataList
    }

    // 处理批量数据：存库 + 广播
    private fun processBatchData() {
        // 拷贝并清空Buffer
        val batchData = mutableListOf<HealthData>()
        var data: HealthData? = dataCacheQueue.poll()
        while (data != null) {
            batchData.add(data)
            data = dataCacheQueue.poll()
        }
        if (batchData.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                dataRepository.saveBatchData(batchData)
            } catch (e: Exception) {
                Log.e(TAG, "存库失败：${e.message}")
                // 失败重试：重新入队
                dataCacheQueue.addAll(batchData)
                delay(1000)
                launch { dataRepository.saveBatchData(batchData) }
            }
        }
        curBatchData = batchData
        sendStateBroadcast("BATCH_DATA_VALID",-1)
    }

}