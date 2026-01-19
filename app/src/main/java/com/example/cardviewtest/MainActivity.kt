package com.example.cardviewtest

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    val fruits = mutableListOf(
        Fruit("心率","bpm",R.drawable.ic_action_heart_rate,"--"),
    Fruit("血压","mmHg",R.drawable.ic_action_blood_pressure,"-/-"),
    Fruit("体温","℃",R.drawable.ic_action_temperture,"--"),
    Fruit("血氧","%",R.drawable.ic_action_blood_oxygen,"--"),
    Fruit("皮肤状态","",R.drawable.ic_action_skin,"-/-"),
    Fruit("肺活量","mL",R.drawable.ic_vital_capacity,"--")
    )

    private val dataTypeMap = mapOf(
        "心率" to "06",
        "血氧" to "01",
        "体温" to "02",
        "肺活量" to "03",
        "血压" to "04",
        "皮肤状态" to "05"
    )
    private val fruitList = ArrayList<Fruit>()
    private lateinit var adapter: FruitAdapter
    private lateinit var healthDataRepository: HealthDataRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        fruitInit()
        healthDataRepository = HealthDataRepository(applicationContext)
        val layoutManager = GridLayoutManager(this,2)
        val recyclerView : RecyclerView = findViewById(R.id.recycleView)
        recyclerView.layoutManager = layoutManager
        adapter = FruitAdapter(this,fruitList)
        recyclerView.adapter = adapter
        val autoUpdater = AutoUpdater(this)
        autoUpdater.CheckUpdate()
        queryAllLatestDataFromDB()
        Log.d("AutoUpdater","AutoUpdater")
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.addDevice){
            Toast.makeText(this,"开始搜索设备!", Toast.LENGTH_SHORT).show()
            val intent = Intent("com.example.cardviewtest.DEVICESCAN_START")
            startActivity(intent)
        }
        return super.onOptionsItemSelected(item)
    }

    private fun fruitInit(){
        fruitList.clear()
        for (i in 0 until fruits.size){
            fruitList.add(fruits[i])
        }
    }

    /**
     * 核心方法：查询所有健康指标的最新数据（纯数据库查询，无广播）
     */
    private fun queryAllLatestDataFromDB() {
        CoroutineScope(Dispatchers.IO).launch {
            fruitList.forEach { fruit ->
                val dataType = dataTypeMap[fruit.name] ?: return@forEach
                val latestData = healthDataRepository.getLatestHealthData(dataType)
                Log.d("queryAllLatestDataFromDB","latestData:${latestData}")

                val displayValue = when (fruit.name) {
                    "心率" -> latestData?.value1?.let { String.format("%.1f", it) } ?: "--"
                    "血压" -> {
                        val systolic = latestData?.value1?.toDouble() ?: 0.0
                        val diastolic = latestData?.value2?.toDouble() ?: 0.0
                        if (systolic > 0 && diastolic > 0) "$systolic/$diastolic" else "-/-"
                    }
                    "体温" -> latestData?.value1?.let { String.format("%.2f", it) } ?: "--"
                    "血氧" -> latestData?.value1?.let { it.toInt().toString() } ?: "--"
                    "肺活量" -> latestData?.value1?.let { it.toInt().toString() } ?: "--"
                    "皮肤状态" -> latestData?.value1?.let { "水分:$it%" } ?: "-/-"
                    else -> "--"
                }

                // 切换到主线程，更新卡片UI（UI操作必须在主线程执行）
                withContext(Dispatchers.Main) {
                    adapter.updateFruitValue(fruit.name, displayValue)
                }
            }
        }
    }
}