package com.example.cardviewtest

import android.content.Context
import android.content.ContentValues
import android.database.Cursor
import android.util.Log

class HealthDataManager(context: Context) {
    private val TAG = "HealthDataDao"
    private val dbHelper: DataBase = DataBase(context,DbConstants.DB_NAME,1)//存疑

    // 1. 插入健康数据
    fun insertData(data: HealthData): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DbConstants.COL_YEAR, data.year)
            put(DbConstants.COL_MONTH, data.month)
            put(DbConstants.COL_WEEK,data.week)
            put(DbConstants.COL_DAY, data.day)
            put(DbConstants.COL_UPLOAD_TIME, data.uploadTime)
            put(DbConstants.COL_DATA_TYPE, data.dataType)
            put(DbConstants.COL_VALUE1, data.value1)
            put(DbConstants.COL_VALUE2, data.value2)
            put(DbConstants.COL_VALUE3, data.value3)
            put(DbConstants.COL_REMARK, data.remark)
        }
        // 插入成功返回自增ID，失败返回-1
        val id = db.insert(DbConstants.TABLE_HEALTH, null, values)
        db.close()
        Log.d(TAG, "插入数据：id=$id, data=$data")
        return id
    }

    // 2. 查询指定年月日的所有数据
    fun queryByDateDetail(year: Int, month: Int, day: Int): List<HealthData> {
        val db = dbHelper.readableDatabase
        val dataList = mutableListOf<HealthData>()

        // 利用联合索引快速查询
        val cursor: Cursor = db.query(
            DbConstants.TABLE_HEALTH,
            null, // 查询所有字段
            "${DbConstants.COL_YEAR}=? AND ${DbConstants.COL_MONTH}=? AND ${DbConstants.COL_DAY}=?",
            arrayOf(year.toString(), month.toString(), day.toString()),
            null, // 分组
            null, // 筛选
            "${DbConstants.COL_UPLOAD_TIME} ASC" // 按上传时间升序
        )

        // 解析Cursor为实体类
        while (cursor.moveToNext()) {
            val data = parseCursorToHealthData(cursor)
            dataList.add(data)
        }
        cursor.close()
        db.close()
        Log.d(TAG, "查询${year}年${month}月${day}日数据：共${dataList.size}条")
        return dataList
    }

    // 3. 查询指定年月的某类数据（如2025年5月的血压数据）
    fun queryByMonthAndType(year: Int, month: Int, dataType: Int): List<HealthData> {
        val db = dbHelper.readableDatabase
        val dataList = mutableListOf<HealthData>()
        val cursor: Cursor = db.query(
            DbConstants.TABLE_HEALTH,
            null,
            "${DbConstants.COL_YEAR}=? AND ${DbConstants.COL_MONTH}=? AND ${DbConstants.COL_DATA_TYPE}=?",
            arrayOf(year.toString(), month.toString(), dataType.toString()),
            null,
            null,
            "${DbConstants.COL_DAY} ASC, ${DbConstants.COL_UPLOAD_TIME} ASC"
        )

        while (cursor.moveToNext()) {
            val data = parseCursorToHealthData(cursor)
            dataList.add(data)
        }
        cursor.close()
        db.close()
        return dataList
    }

    // 查询周的数据
    fun queryByWeek(week: Int,dataType: Int): List<HealthData> {
        val db = dbHelper.readableDatabase
        val dataList = mutableListOf<HealthData>()
        val cursor: Cursor = db.query(
            DbConstants.TABLE_HEALTH,
            null,
            "${DbConstants.COL_WEEK}=? AND ${DbConstants.COL_DATA_TYPE}=?", // 按周筛选
            arrayOf(week.toString(),dataType.toString()),
            null,
            null,
            "${DbConstants.COL_UPLOAD_TIME} ASC"
        )
        while (cursor.moveToNext()) {
            val data = parseCursorToHealthData(cursor)
            dataList.add(data)
        }
        cursor.close()
        db.close()
        Log.d(TAG, "查询周${week}数据：共${dataList.size}条")
        return dataList
    }

    // 4. 删除指定ID的数据
    fun deleteDataById(id: Long): Int {
        val db = dbHelper.writableDatabase
        val deleteCount = db.delete(
            DbConstants.TABLE_HEALTH,
            "${DbConstants.COL_ID}=?",
            arrayOf(id.toString())
        )
        db.close()
        Log.d(TAG, "删除数据：id=$id，成功删除$deleteCount 条")
        return deleteCount
    }

    // 辅助方法：Cursor转HealthData
    private fun parseCursorToHealthData(cursor: Cursor): HealthData {
        return HealthData(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(DbConstants.COL_ID)),
            year = cursor.getInt(cursor.getColumnIndexOrThrow(DbConstants.COL_YEAR)),
            month = cursor.getInt(cursor.getColumnIndexOrThrow(DbConstants.COL_MONTH)),
            week = cursor.getInt(cursor.getColumnIndexOrThrow(DbConstants.COL_WEEK)),
            day = cursor.getInt(cursor.getColumnIndexOrThrow(DbConstants.COL_DAY)),
            uploadTime = cursor.getString(cursor.getColumnIndexOrThrow(DbConstants.COL_UPLOAD_TIME)),
            dataType = cursor.getString(cursor.getColumnIndexOrThrow(DbConstants.COL_DATA_TYPE)),
            value1 = cursor.getFloat(cursor.getColumnIndexOrThrow(DbConstants.COL_VALUE1)),
            value2 = cursor.getFloat(cursor.getColumnIndexOrThrow(DbConstants.COL_VALUE2)),
            value3 = cursor.getFloat(cursor.getColumnIndexOrThrow(DbConstants.COL_VALUE3)),
            remark = cursor.getString(cursor.getColumnIndexOrThrow(DbConstants.COL_REMARK))
        )
    }

    // 关闭数据库（可选，SQLiteOpenHelper会自动管理连接）
    fun closeDb() {
        dbHelper.close()
    }
}