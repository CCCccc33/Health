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

class BluetoothService : Service() {
    var curPosition : Int = 0
    companion object {
        const val TAG = "BluetoothService"
        const val ACTION_DATA_AVAILABLE = "com.example.bluetooth.ACTION_DATA_AVAILABLE"
        const val ACTION_BLE_STATE = "com.example.bluetooth.ACTION_BLE_CONNECT_STATE"
        private const val BATCH_SIZE = 50 // 缓存达到 50 条数据，集中发送广播
        private const val MAX_DELAY = 800L // 兜底：800ms 内没凑够 15 条，也集中发送（避免数据积压）
        const val STATE_CONNECT_SUCCESS = 10    // 连接成功
        const val STATE_CONNECT_FAILURE = 11    // 连接失败
        const val STATE_DISCONNECT_SUCCESS = 12 // 断开成功
        // 广播中携带数据的 Key
        const val EXTRA_STATE = "extra_state"  // 状态码
        const val EXTRA_ECG_BATCH = "ecg_batch_data"
        const val EXTRA_DEVICE_POSITION = "extra_device_position" // 设备地址（用于更新列表）
    }
    //******数据缓存****//
    private val dataCacheQueue = ConcurrentLinkedQueue<HealthData>()  // 接收buffer
    private val dataRepository by lazy { HealthDataRepository(applicationContext) }

    private val btController = BlueToothController
    private val mBinder = BluetoothBinder()
    val conversion = Conversion()
    private val dataCache = Collections.synchronizedList(mutableListOf<String>())
    val batchHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        val batchRunnable= object :Runnable{
            override fun run() {
                if (dataCache.isNotEmpty()){
                    sendStateBroadcast("DATA_VALID",-1)
                }
                batchHandler.postDelayed(this,MAX_DELAY)
            }
        }
        batchHandler.postDelayed(batchRunnable,MAX_DELAY)
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
            "DATA_VALID" ->{
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
        fun readMessage():List<String>{
            synchronized(dataCache) { // 加锁保证原子性
                val dataList = ArrayList(dataCache) // 创建副本
                dataCache.clear()
                return dataList
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
            bytes?.let {
                val dataList = parseRawData(bytes) //解析接收数据
                dataCacheQueue.addAll(dataList)
                if (dataCacheQueue.size>=BATCH_SIZE){
                    processBatchData()
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
    // 解析原始BLE数据（示例：500Hz，2字节=1个采样点）
    private fun parseRawData(rawData: ByteArray): List<HealthData> {
        val dataList = mutableListOf<HealthData>()
        val receiveTimestamp = TimeUtils.getCurrentTimestamp()
        val timeFields = TimeUtils.parseTimeFields(receiveTimestamp)
        val uploadTime = TimeUtils.timestampToFormat(receiveTimestamp)
        for (i in 0 until rawData.size step 2) {
            if (i + 1 >= rawData.size) break
            // 解析16位有符号整型（替换为你的硬件解析规则）
            val voltageRaw = ByteBuffer.wrap(rawData, i, 2).short
            val voltage = voltageRaw.toFloat() / 1000f // 转换为mV（校准系数自行调整）
            dataList.add(HealthData(
                year = timeFields.year,
                month = timeFields.month,
                week = timeFields.week,
                day = timeFields.day,
                uploadTime = uploadTime,
                dataType = "4",
                value1=voltage,
                value2 = null,
                value3 = null))
        }
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
        // 1. 异步批量存库（IO线程，不阻塞）
        CoroutineScope(Dispatchers.IO).launch {
            try {
                dataRepository.saveBatchData(batchData)
            } catch (e: Exception) {
                Log.e("ECGService", "存库失败：${e.message}")
                // 失败重试：重新入队
                dataCacheQueue.addAll(batchData)
                delay(1000)
                launch { dataRepository.saveBatchData(batchData) }
            }
        }

        // 2. 发送本地广播（Buffer→Activity）
        val intent = Intent(ACTION_DATA_AVAILABLE).apply {
            putParcelableArrayListExtra(EXTRA_ECG_BATCH, ArrayList(batchData))        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

}