package com.example.cardviewtest

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class DeviceScanActivity : AppCompatActivity() {
    companion object{
        private const val TAG = "DeviceScanActivity"
        const val REQUEST_PERMISSION_CODE = 1
        const val CONNECT_START = 10086
        const val CONNECT_END = 10089
        const val CONNECT_SUCCESS = 10
        const val CONNECT_FAILDURE = 11

        const val DISCONNECT_SUCCESS = 12
        const val START_DISCOVERY = 17
        const val STOP_DISCOVERY =18
        const val DISCOVERY_DEVICE = 19
        const val DISCOVERY_OUT_TIME =20
        const val SELECT_DEVICE = 21
        const val BT_OPENED = 22
        const val BT_CLOSED = 23
        const val SERVICE_UUID = "0000ffe0-0000-1000-8000-00805f9b34fb"
        const val READ_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb" //读特征
        const val WRITE_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb"  //写特征
        const val ACTION_BLE_STATE = "com.example.bluetooth.ACTION_BLE_CONNECT_STATE"
    }
    private var deniedPermissionList  = mutableListOf<String>()
    var btController: BlueToothController = BlueToothController
    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var curDevice: BluetoothDevice
    private var curPosition = 0
    private val deviceList = CopyOnWriteArrayList<Device>()
    val bluetoothStateReceiver = Receiver()
    var isRegistered = false
    var isUnknownFiltered = true
    private lateinit var localBroadcastManager : LocalBroadcastManager

    var isServiceConnected: Boolean = false
    val permissionRequest = PermissionRequest()

    private lateinit var bluetoothBinder : BluetoothService.BluetoothBinder
    private val connect = object : ServiceConnection{
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bluetoothBinder = service as BluetoothService.BluetoothBinder
            isServiceConnected = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.e(TAG,"Service connect faild")
        }
    }

    private val handler = MyHandler(this)

    private class MyHandler(activity: DeviceScanActivity) : Handler(Looper.getMainLooper()) {
        // 弱引用：不会阻止 Activity 被 GC 回收
        private val weakActivity = WeakReference<DeviceScanActivity>(activity)
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            val activity = weakActivity.get() ?: return
            when(msg.what){
                START_DISCOVERY -> Log.d(TAG, "开始搜索设备...")
                STOP_DISCOVERY -> Log.d(TAG, "停止搜索设备...")
                DISCOVERY_DEVICE -> {
                    val device = msg.obj as BluetoothDevice
                    @SuppressLint("MissingPermission")
                    if (activity.isUnknownFiltered){
                        device.name?:return
                    }
                    @SuppressLint("MissingPermission")
                    if (activity.deviceAdapter.addDevice(device)){
                        activity.deviceList.add(Device(device.name?: "未知设备","未连接",R.drawable.ic_bluetooth_icon))  //能加上去吗
                        val newPosition = activity.deviceList.size-1
                        activity.deviceAdapter.notifyItemInserted(newPosition)
                    }
                }
                SELECT_DEVICE ->{
                    val (position,action) = msg.obj as Pair<Int, Int>
                    if (action.equals(CONNECT_START)){
                        activity.deviceList[position].deviceState = "正在连接中..."
                        activity.deviceAdapter.notifyItemChanged(position)
                    }else if (action.equals(CONNECT_END)){
                        activity.deviceList[position].deviceState = "正在断开..."
                        activity.deviceAdapter.notifyItemChanged(position)
                    }
                }
                CONNECT_FAILDURE ->{
                    Log.d(TAG, "连接失败")
                    val position = msg.obj as Int
                    activity.deviceList[position].deviceState = "未连接"
                    activity.deviceAdapter.notifyItemChanged(activity.curPosition)
                }
                CONNECT_SUCCESS ->{
                    val position = msg.obj as Int
                    Log.d(TAG, "连接成功")
                    activity.deviceList[position].deviceState = "已连接"
                    activity.deviceAdapter.notifyItemChanged(activity.curPosition)
                }
                DISCONNECT_SUCCESS ->{
                    Log.d(TAG, "成功断开")
                    activity.deviceList[activity.curPosition].deviceState = "未连接"
                    activity.deviceAdapter.notifyItemChanged(activity.curPosition)
                }
                BT_CLOSED ->{
                    Log.e(TAG, "系统蓝牙已断开")

                }
                BT_OPENED ->{
                    Log.d(TAG, "系统蓝牙打开断开")
                }
            }
        }
    }
    // 蓝牙状态改变广播
    inner class Receiver : BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (TextUtils.equals(action, BluetoothAdapter.ACTION_DISCOVERY_STARTED)){
                val msg = Message()
                msg.what = START_DISCOVERY
                handler.sendMessage(msg)
            }else if (TextUtils.equals(action, BluetoothAdapter.ACTION_DISCOVERY_FINISHED)){
                val msg = Message()
                msg.what = STOP_DISCOVERY
                handler.sendMessage(msg)
            }else if (TextUtils.equals(action, BluetoothAdapter.ACTION_STATE_CHANGED)){
                val state = intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE,0)
                when(state){
                    BluetoothAdapter.STATE_OFF ->{
                        val msg = Message()
                        msg.what = BT_CLOSED
                        handler.sendMessage(msg)
                    }
                    BluetoothAdapter.STATE_ON ->{
                        val msg = Message()
                        msg.what = BT_OPENED
                        handler.sendMessage(msg)
                    }
                }
            }else if (TextUtils.equals(action,ACTION_BLE_STATE)){
                val connectState = intent?.getIntExtra("extra_state",-1)
                val position = intent?.getIntExtra("extra_device_position",-1)
                connectState?.let {
                    val msg = Message()
                    msg.what = connectState
                    msg.obj = position
                    handler.sendMessage(msg)
                }
            }
        }
    }

    fun initBluetoothReceiver(){
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED); //开始扫描
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);//扫描结束
        intentFilter.addAction("com.example.bluetooth.ACTION_BLE_CONNECT_STATE")
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);//手机蓝牙状态监听
        localBroadcastManager.registerReceiver(bluetoothStateReceiver, intentFilter)
        isRegistered = true
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_scan)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.device_scan)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val swipeRefreshScanDevice : SwipeRefreshLayout = findViewById(R.id.swipeRefresh_scan_device)
        swipeRefreshScanDevice.setColorSchemeResources(R.color.middle_gray)
        val toolbar: Toolbar = findViewById(R.id.toolbar_device_scan)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.navigationIcon?.setTint(getColor(R.color.black))
        toolbar.setTitle("添加设备")
        if (!btController.initBluetooth(applicationContext)){
            Toast.makeText(this,"您的设备不支持蓝牙，无法添加设备！", Toast.LENGTH_SHORT).show()
            finish()
        }
        localBroadcastManager = LocalBroadcastManager.getInstance(this)
        btController.turnOnBlueTooth(this)
        val intent = Intent(this, BluetoothService::class.java)
        startService(intent)
        bindService(intent,connect,Context.BIND_AUTO_CREATE)
        createDeviceCardView(this)
        bluetoothInit()
        initBluetoothReceiver()
        initPermissions()
        swipeRefreshScanDevice.setOnRefreshListener {
            refreshDevices(swipeRefreshScanDevice)
        }
    }
    private fun refreshDevices(swipeRefresh:SwipeRefreshLayout){
        thread {
            Thread.sleep(100)
            runOnUiThread {
                bluetoothInit()
                deviceAdapter.notifyDataSetChanged()
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun bluetoothInit(){
        searchDevice()
    }
    private fun initPermissions(){
        val permissions = permissionRequest.getRequiredPermissions(BluetoothAction.INIT)
        requestRuntimePermission(this,permissions)
    }

    //扫描设备
    private val onDeviceSearchListener = object : OnDeviceSearchListener{
        override fun onDeviceFound(device: BluetoothDevice) {
            val msg = Message()
            msg.what = DISCOVERY_DEVICE
            msg.obj = device
            handler.sendMessage(msg)
        }
        override fun onDiscoveryOutTime() {
            val msg = Message()
            msg.what = DISCOVERY_OUT_TIME
            handler.sendMessage(msg)
        }
    }

    fun searchDevice(){
        val permission = permissionRequest.getRequiredPermissions(BluetoothAction.SCAN)
        requestRuntimePermission(this,permission)
        if (deniedPermissionList.size>0){
            Toast.makeText(this,"${deniedPermissionList}权限被拒绝，部分功能可能无法使用！", Toast.LENGTH_SHORT).show()
            return
        }
        if (btController.isDiscovery()){
            btController.stopDiscoveryDevices()
        }
        deviceList.clear()
        deviceAdapter.clearList()
        btController.startDiscoveryDevices(this,onDeviceSearchListener,150000)
    }



    private fun createDeviceCardView(context: Context){
        val layoutManager = LinearLayoutManager(this)
        val recyclerView: RecyclerView = findViewById(R.id.recycleView_deviceScan)
        recyclerView.layoutManager = layoutManager
        deviceAdapter = DeviceAdapter(this,deviceList){action,position,cur_device ->
            this.curDevice = cur_device
            this.curPosition = position
            when(action){
                CONNECT_START ->{
                    val permissions = permissionRequest.getRequiredPermissions(BluetoothAction.SCAN)
                    requestRuntimePermission(this,permissions)
                    if (deniedPermissionList.size>0){
                        Toast.makeText(this,"${deniedPermissionList}权限被拒绝，部分功能可能无法使用！", Toast.LENGTH_SHORT).show()
                        return@DeviceAdapter
                    }
                    btController.stopDiscoveryDevices()
                    val startMsg = Message()
                    startMsg.obj = Pair<Int, Int>(position,action)
                    startMsg.what = SELECT_DEVICE
                    handler.sendMessage(startMsg)
                    val permission = permissionRequest.getRequiredPermissions(BluetoothAction.CONNECT)
                    requestRuntimePermission(this,permission)
                    if (deniedPermissionList.size>0){
                        Toast.makeText(this,"${deniedPermissionList}权限被拒绝，部分功能可能无法使用！", Toast.LENGTH_SHORT).show()
                        return@DeviceAdapter
                    }
                    if (!isServiceConnected){
                        Toast.makeText(this, "服务未连接，请稍后重试", Toast.LENGTH_SHORT).show()
                        return@DeviceAdapter
                    }
                    bluetoothBinder.connectDevice(READ_UUID,WRITE_UUID,SERVICE_UUID,curDevice,position)
                }
                CONNECT_END ->{
                    val permission = permissionRequest.getRequiredPermissions(BluetoothAction.CONNECT)
                    requestRuntimePermission(this,permission)
                    if (deniedPermissionList.size>0){
                        Toast.makeText(this,"${deniedPermissionList}权限被拒绝，部分功能可能无法使用！", Toast.LENGTH_SHORT).show()
                        return@DeviceAdapter
                    }
                    if (!isServiceConnected) {
                        Toast.makeText(this,"服务未连接，请稍后重试", Toast.LENGTH_SHORT).show()
                        return@DeviceAdapter
                    }
                    bluetoothBinder.disconnectDevice()
                }
            }
        }
        recyclerView.adapter = deviceAdapter
    }


    override fun onDestroy() {
        super.onDestroy()
        try {
            btController.setOnBleConnectListener(null)
            btController.setOnDeviceSearchListener(null)
            btController.stopDiscoveryDevices()
            handler.removeCallbacksAndMessages(null)
            if (isRegistered){
                localBroadcastManager.unregisterReceiver(bluetoothStateReceiver)
            }
            unbindService(connect)
        }catch (e: IllegalArgumentException){
            e.printStackTrace()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home){
            finish()
        }else if (item.itemId == R.id.filter){
            val popupMenu = PopupMenu(this, findViewById(item.itemId))
            popupMenu.menuInflater.inflate(R.menu.menu_filter, popupMenu.menu)
            popupMenu.menu.findItem(R.id.filter_non_empty_name)?.isChecked = isUnknownFiltered
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.filter_non_empty_name ->{
                        menuItem.isChecked = !menuItem.isChecked
                        isUnknownFiltered = menuItem.isChecked
                        thread {
                            runOnUiThread {
                                bluetoothInit()
                                deviceAdapter.notifyDataSetChanged()
                            }
                        }
                    }
                }
                true
            }
            // 5. 显示下拉菜单
            popupMenu.show()
        }
        return super.onOptionsItemSelected(item)
    }
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.add_device,menu)
        return super.onCreateOptionsMenu(menu)
    }


    ////////////////权限//////////////////////////////
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION_CODE){
            if (grantResults.size>0){
                val deniedPermissionList: MutableList<String> = mutableListOf()
                for (i in 0 until grantResults.size){
                    val permission = permissions[i]
                    val grantResult = grantResults[i]
                    if (grantResult != PackageManager.PERMISSION_GRANTED){
                        if (!deniedPermissionList.contains(permission)){
                            permission?.let {
                                deniedPermissionList.add(it)
                            }
                        }
                    }
                }
                if (deniedPermissionList.isEmpty()){
                    Log.d(PermissionRequest.Companion.TAG,"权限都授予了")
                }else{
                    Toast.makeText(this,"${deniedPermissionList}权限被拒绝，部分功能可能无法使用！",
                        Toast.LENGTH_SHORT).show()
                    finish()//不给我权限就退出吧你
                }
            }

        }
    }

    fun requestRuntimePermission(context: Context,permissions: Array<String>){
        val permissionList: MutableList<String> = mutableListOf()
        for (permission in permissions){
            if (ContextCompat.checkSelfPermission(context,permission)!= PackageManager.PERMISSION_GRANTED){
                if (!permissionList.contains(permission)){
                    permissionList.add(permission)
                }
            }
        }
        val activity = context as Activity
        if (!permissionList.isEmpty()){
            ActivityCompat.requestPermissions(activity,permissionList.toTypedArray(),REQUEST_PERMISSION_CODE)
        }else{
            Log.d(PermissionRequest.Companion.TAG,"权限都授予了")
        }
    }



}