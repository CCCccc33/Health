package com.example.cardviewtest

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.Context
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.util.Log
import android.os.Looper
import android.os.Handler
import androidx.core.content.ContextCompat

object BlueToothController {
    private const val TAG = "BlueToothController"
    const val MAX_CONNECT_TIME: Long = 3000
    private var mBluetoothGatt: BluetoothGatt? = null  //当前连接的Gatt
    //UUID
    private var serviceUUID: String? = null
    private var readUUID: String? = null
    private var writeUUID: String? = null
    private val permissionRequest = PermissionRequest()

    private var bluetoothGattService: BluetoothGattService? = null //服务
    private var readCharacteristic: BluetoothGattCharacteristic? = null  //读特征
    private var writeCharacteristic: BluetoothGattCharacteristic? = null  //写特征
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var  onDeviceSearchListener: OnDeviceSearchListener? = null //扫描设备监听
    private var onBleConnectListener:OnBleConnectListener? = null  //连接监听
    private var curConnDevice: BluetoothDevice ? = null
    private var mScanner: BluetoothLeScanner? = null
    val deviceList = mutableListOf<BluetoothDevice>()
    //状态
    private var isConnected: Boolean = false
    private var isConnecting: Boolean = false
    private var isLeScanStarted: Boolean =false
    private val mHandler = Handler(Looper.getMainLooper())

    fun setOnBleConnectListener(listener: OnBleConnectListener?) {
        this.onBleConnectListener = listener
    }
    fun setOnDeviceSearchListener(listener: OnDeviceSearchListener?) {
        this.onDeviceSearchListener = listener
    }

    fun initBluetooth(context: Context): Boolean{
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        return bluetoothAdapter != null
    }

    fun bluetoothState(): Boolean = bluetoothAdapter?.isEnabled()?:false


    fun turnOnBlueTooth(context: Context) : Boolean{
        if (bluetoothState()) return true
        val permission = permissionRequest.getRequiredPermissions(BluetoothAction.TURN_ON)
        val areAllGranted = permission.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (!areAllGranted) {
            Log.e(TAG, "没有权限：${permission}")
            return false
        }
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        context.startActivity(intent)
        bluetoothAdapter?.enable()  //需要BLUETOOTH_ADMIN
        return true

    }
    ///////////////////扫描///////////////////
    /*
    * 扫描设备回调
    * */
    @SuppressLint("MissingPermission")
    private val scanCallback  = object :ScanCallback() {
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG,"扫描失败:${errorCode}")
        }

        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            val device: BluetoothDevice? = result?.device
            device?.let {
                val deviceName = it.name?:"未知设备"  ////需要权限
                val deviceMac = it.address
                Log.d("BLE_Scan", "设备名称：$deviceName，MAC 地址：$deviceMac")
                onDeviceSearchListener?.onDeviceFound(it)
            }
        }
    }
    /*
    * 扫描设备
    * */
    fun startDiscoveryDevices(context: Context,onDeviceSearchListener:OnDeviceSearchListener,scanTime: Long){
        if (turnOnBlueTooth(context)){
            if (isLeScanStarted) return
            setOnDeviceSearchListener(onDeviceSearchListener)
            isLeScanStarted = true
            val permission = permissionRequest.getRequiredPermissions(BluetoothAction.SCAN)
            val areAllGranted = permission.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
            if (!areAllGranted) {
                Log.e(TAG, "没有权限：${permission}")
                return
            }
            mScanner = bluetoothAdapter?.getBluetoothLeScanner() //需要权限
            mScanner?.startScan(scanCallback)
            mHandler.postDelayed(stopRunnable,scanTime)
        }
    }
    //设定最长扫描时间BluetoothLeScanner
    private val stopRunnable = Runnable{
        onDeviceSearchListener?.onDiscoveryOutTime()
        Log.e(TAG,"扫描超时！未连接到设备")
        stopDiscoveryDevices()
    }
    /*
    * 停止扫描
    * */
    @SuppressLint("MissingPermission")
    fun stopDiscoveryDevices(){
        if (isLeScanStarted){
            mScanner?.stopScan(scanCallback)
            isLeScanStarted = false
            Log.d("bluetoothConnect","停止扫描设备")
        }
    }
    ///////////////连接//////////////////////////
    /*
    * 连接/通讯结果回调
    * */
    private val bluetoothGattCallback = object :BluetoothGattCallback(){
        //连接状态回调-连接成功/断开连接
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            Log.d("bluetoothStatue","statue:$status")
            Log.d("bluetoothState","newState:$newState")
            val bluetoothDevice = gatt?.device
            @SuppressLint("MissingPermission")
            Log.d("","连接的设备：${bluetoothDevice?.name} 设备地址：${bluetoothDevice?.address}")
            mHandler.removeCallbacks(connectOutTimeRunnable)
            if (newState == BluetoothGatt.STATE_CONNECTED){
                mHandler.removeCallbacks(connectOutTimeRunnable)
                isConnecting = false
                isConnected = true
                mBluetoothGatt = gatt
                Log.d("bluetoothConnect","连接成功")
                @SuppressLint("MissingPermission")
                gatt?.discoverServices()
                mHandler.postDelayed(serviceDiscoverOutTimeRunnable,MAX_CONNECT_TIME)
                onBleConnectListener?.onConnectSuccess(gatt,bluetoothDevice,status)
            }else if (newState == BluetoothGatt.STATE_DISCONNECTED){
                isConnecting = false
                isConnected = false
                Log.e("bluetoothConnect","断开连接statue:$status")
                @SuppressLint("MissingPermission")
                gatt?.close()
                when(status){
                    133 ->onBleConnectListener?.let { it.onConnectFailure(gatt,bluetoothDevice,"连接异常",status) }//无法连接
                    0 -> onBleConnectListener?.let { it.onDisConnectSuccess(gatt,bluetoothDevice,status) }
                    8 -> onBleConnectListener?.let { it.onDisConnectSuccess(gatt,bluetoothDevice,status) }
                    34 -> onBleConnectListener?.let { it.onDisConnectSuccess(gatt,bluetoothDevice,status) }
                    62 -> onBleConnectListener?.let{ it.onConnectFailure(gatt,bluetoothDevice,"连接成功服务未发现断开！",status) }
                    else -> onBleConnectListener?.let { it.onDisConnectSuccess(gatt,bluetoothDevice,status) }
                }
            }else if(newState == BluetoothGatt.STATE_CONNECTING){
                isConnecting = true
                isConnected = false
                Log.d("bluetoothConnect","正在连接...")
                onBleConnectListener?.let { it.onConnecting(gatt,bluetoothDevice) }
            }else if (newState == BluetoothGatt.STATE_DISCONNECTING){
                isConnecting = false
                isConnected = false
                Log.d("bluetoothConnect","正在断开...")
                onBleConnectListener?.let { it.onDisConnecting(gatt,bluetoothDevice) }
            }
        }
        //发现服务
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            mHandler.removeCallbacks(serviceDiscoverOutTimeRunnable)//为什么？？？
            Log.d("bluetoothConnect","移除发现服务超时")
            Log.d("bluetoothConnect","发现服务")
            if (setupService(gatt,serviceUUID,readUUID,writeUUID)){
                onBleConnectListener?.let {
                    it.onServiceDiscoverySucceed(gatt,gatt?.device,status)
                }
            }else{
                onBleConnectListener?.onServiceDiscoveryFailed(gatt,gatt?.device,"获取服务特征异常")
            }

        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, value, status)
            if (characteristic?.value == null){
                Log.e("bluetoothConnect","onCharacteristicRead:characteristic.getValue() == null")
                return
            }
            val msg = characteristic.value  //存疑？？？？
            when(status){
                BluetoothGatt.GATT_SUCCESS ->{
                    Log.w("bluetoothConnect","读取成功：${msg.toHexString()}")
//                    onBleConnectListener?.onReceiveMessage(gatt,gatt?.device,characteristic,
//                        msg)
                }
                BluetoothGatt.GATT_FAILURE -> {
                    Log.w("bluetoothConnect","读取失败：${msg.toHexString()},statue:$status")
                    onBleConnectListener?.onReceiveError(status)
                }
                BluetoothGatt.GATT_WRITE_NOT_PERMITTED ->{
                    Log.w("bluetoothConnect","没有权限！")
                }
            }
        }

        //向蓝牙设备写入数据结果回调
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (characteristic?.value == null){
                Log.e("bluetoothConnect","onCharacteristicWrite:characteristic.getValue() == null")
                return
            }
            val msg = characteristic.value  //存疑？？？？
            when(status){
                BluetoothGatt.GATT_SUCCESS ->{
                    Log.w("bluetoothConnect","写入成功：${msg.toHexString()}")
                    onBleConnectListener?.let {
                        it.onWriteSuccess(gatt,gatt?.device,characteristic,
                            msg.toHexString())
                    }
                }
                BluetoothGatt.GATT_FAILURE -> {
                    Log.w("bluetoothConnect","写入失败：${msg.toHexString()}")
                    onBleConnectListener?.let {
                        it.onWriteFailure(gatt,gatt?.device,characteristic,
                            characteristic?.value,"写入失败")
                    }
                }
                BluetoothGatt.GATT_WRITE_NOT_PERMITTED ->{
                    Log.w("bluetoothConnect","没有权限！")
                }
            }
        }
        //读取蓝牙设备发出来的数据回调
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            val bytes: ByteArray? = characteristic?.value
            bytes?.let {
                Log.w("bluetoothConnect","收到数据：${bytes.toHexString()},长度:${bytes.size}")
//                Log.w("bluetoothConnect","onBleConnectListener 是否为空：${onBleConnectListener == null}")
                onBleConnectListener?.onReceiveMessage(gatt,gatt?.device,characteristic,bytes)

            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            when(status){
                BluetoothGatt.GATT_SUCCESS ->{
                    Log.d("bluetoothConnect","设置MTU成功！ 新MTU值为${mtu-3},statue:$status")
                    onBleConnectListener?.let { it.onMTUSetSuccess(mtu - 3) }
                }
                BluetoothGatt.GATT_FAILURE ->{
                    Log.d("bluetoothConnect","设置MTU失败！ ${mtu-3},statue:$status")
                    onBleConnectListener?.let { it.onMTUSetFailure(mtu - 3) }
                }
            }
        }
    }

    fun ByteArray.toHexString(separator: String = " "): String {
        return this.joinToString(separator) { byte ->
            // %02X：将字节转为两位大写十六进制，不足两位补0；%02x为小写
            String.format("%02X", byte)
        }
    }

    /**
     * 通过蓝牙设备连接
     * @param context  上下文
     * @param bluetoothDevice  蓝牙设备
     * @param outTime          连接超时时间
     * @param onBleConnectListener  蓝牙连接监听者
     * @return
     */
    fun connectBleDevice(context: Context,bluetoothDevice: BluetoothDevice,outTime:Long, listener: OnBleConnectListener?,readUUID: String,writeUUID: String,serviceUUID: String):BluetoothGatt?{
        bluetoothAdapter?.let {
            this.readUUID = readUUID
            this.writeUUID = writeUUID
            this.serviceUUID = serviceUUID
            this.onBleConnectListener = listener
            disConnectDevice()
            isConnecting = true
            this.curConnDevice = bluetoothDevice
            @SuppressLint("MissingPermission")
            Log.d("bluetoothConnect","准备开始连接：${bluetoothDevice.name}")
            try {
                val permission = permissionRequest.getRequiredPermissions(BluetoothAction.CONNECT)
                val areAllGranted = permission.all { permission ->
                    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                }
                if (!areAllGranted) {
                    Log.e(TAG, "没有权限：${permission}")
                    return null
                }
                mBluetoothGatt = bluetoothDevice.connectGatt(context.applicationContext, true, bluetoothGattCallback)   //断了的话不断尝试连接
                listener?.onConnecting(mBluetoothGatt,bluetoothDevice)
                @SuppressLint("MissingPermission")
                mBluetoothGatt?.connect()
            }catch (error:Exception){
                Log.e("bluetoothConnect",error.message?:"")
            }
            mHandler.postDelayed(connectOutTimeRunnable,outTime)
        }
        return mBluetoothGatt
    }
    //连接超时
    private val connectOutTimeRunnable = Runnable{
        mBluetoothGatt?.let {
            isConnecting = false //不明白我靠
            @SuppressLint("MissingPermission")
            mBluetoothGatt?.disconnect()
            onBleConnectListener?.let {
                onBleConnectListener?.onConnectFailure(mBluetoothGatt,curConnDevice,"连接超时！",-1)
            }

        }
    }

    //发现服务超时
    private val serviceDiscoverOutTimeRunnable = Runnable{
        mBluetoothGatt?.let {
            isConnecting = false
            @SuppressLint("MissingPermission")
            mBluetoothGatt?.disconnect()
            onBleConnectListener?.let {
                onBleConnectListener?.onConnectFailure(mBluetoothGatt,curConnDevice,"发现服务超时！",-1)
            }

        }
    }

    //断开连接
    fun disConnectDevice(){
        if (!isConnected) return
        if (isConnecting) {
            mHandler.removeCallbacks(connectOutTimeRunnable)
            isConnecting = false
            return
        }
        mBluetoothGatt?.let {
            onBleConnectListener?.onDisConnecting(mBluetoothGatt,curConnDevice)
            @SuppressLint("MissingPermission")
            mBluetoothGatt?.disconnect()
            isConnected = false
        }
    }

    /**
     * 获取特定服务及特征
     * 1个serviceUUID -- 1个readUUID -- 1个writeUUID
     * @param bluetoothGatt
     * @param serviceUUID
     * @param readUUID
     * @param writeUUID
     * @return
     */
    fun setupService(bluetoothGatt: BluetoothGatt?,
                     serviceUUID: String?,
                     readUUID: String?,
                     writeUUID: String?): Boolean{
        //val gatt = bluetoothGatt?:return false
        if (bluetoothGatt == null){
            Log.e(TAG,"bluetoothGatt为空")
            return false
        }
//        val serviceuuid = serviceUUID?:return false
        if (serviceUUID == null){
            Log.e(TAG,"serviceUUID为空")
            return false
        }
        for (service in bluetoothGatt.services){
            if (service.uuid.toString().equals(serviceUUID)){
                bluetoothGattService = service
                Log.d(TAG,"找到对应服务UUID")
            }
        }
//        val gattService = bluetoothGattService?:return false
        if (bluetoothGattService == null){
            Log.e(TAG,"bluetoothGattService为空")
            return false
        }
        Log.d("bluetoothConnect", "setupService()-->bluetoothGattService = ${bluetoothGattService.toString()}")
        if (readUUID == null || writeUUID == null){
            Log.e("bluetoothConnect", "setupService()-->readUUID == null || writeUUID == null")
            return false
        }
        for (characteristic in bluetoothGattService!!.characteristics){
            if (characteristic.uuid.toString().equals(readUUID)){
                readCharacteristic = characteristic
            }
            if (characteristic.uuid.toString().equals(writeUUID)){
                writeCharacteristic = characteristic
            }
        }
        if (readCharacteristic == null){
            Log.e("bluetoothConnect", "setupService()-->readCharacteristic == null")
            return false
        }
        if (writeCharacteristic == null){
            Log.e("bluetoothConnect", "setupService()-->writeCharacteristic == null")
            return false
        }
        enableNotification(true,bluetoothGatt,readCharacteristic)
        val descriptors : List<BluetoothGattDescriptor> = readCharacteristic!!.getDescriptors()
        for (descriptor in descriptors){
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            @SuppressLint("MissingPermission")
            bluetoothGatt.writeDescriptor(descriptor)
        }
        mHandler.postDelayed(Runnable{

        },2000)
        return true
    }
    ///////////////////通知/////////////////////
    /**
     * 设置读特征接收通知
     * @param enable  为true打开通知
     * @param gatt    连接
     * @param characteristic  特征
     */
    fun enableNotification(enable: Boolean,
                           gatt: BluetoothGatt?,
                           characteristic: BluetoothGattCharacteristic?){
        gatt?.let {
            characteristic?.let {
                @SuppressLint("MissingPermission")
                gatt.setCharacteristicNotification(characteristic,enable)

            }
        }
    }

    /**
     * 发送消息  byte[]数组
     * @param msg  消息
     * @return  true  false
     */
    fun sendMessage(msg: ByteArray): Boolean{
        val char = writeCharacteristic?:return false
        val gatt = mBluetoothGatt?:return false
        if (!isConnected) return false
        char.value = msg
        Log.d("bluetoothConnect", "写特征设置值结果：$msg")
        @SuppressLint("MissingPermission")
        return mBluetoothGatt!!.writeCharacteristic(char)
    }



    /**
     * 直接关闭蓝牙
     */
    fun closeBluetooth(){
        bluetoothAdapter?.let {
            @SuppressLint("MissingPermission")
            it.disable()
        }
    }
    /**
     * 本地蓝牙是否处于正在扫描状态
     * @return true false
     */
    fun isDiscovery(): Boolean{
        val adapter = bluetoothAdapter?:return false
        @SuppressLint("MissingPermission")
        return bluetoothAdapter?.isDiscovering?:false
    }

    fun clearList(){
        deviceList.clear()
    }

    fun addDevice(device: BluetoothDevice): Boolean {
        deviceList.forEach {
            if (it.address == device.address) {
                return false
            }
        }
        deviceList.add(device)
        return true
    }

    fun releaseResources() {
        // 停止所有超时任务
        mHandler.removeCallbacks(connectOutTimeRunnable)
        mHandler.removeCallbacks(stopRunnable)
        mHandler.removeCallbacks(serviceDiscoverOutTimeRunnable)
        // 释放 Gatt 实例（必须调用 close()）
        @SuppressLint("MissingPermission")
        mBluetoothGatt?.disconnect()
        @SuppressLint("MissingPermission")
        mBluetoothGatt?.close()
        // 重置所有状态和引用
        mBluetoothGatt = null
        curConnDevice = null
        isConnected = false
        isConnecting = false
        serviceUUID = null
        writeUUID = null
        readUUID = null
        writeCharacteristic = null
        readCharacteristic = null
        // 注销回调（避免页面泄漏）
        onBleConnectListener = null
        onDeviceSearchListener = null
    }
}
