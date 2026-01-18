package com.example.cardviewtest

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Dao
interface HealthDataDao{
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(dataList: HealthData)
    // 事务封装（批量插入提速3-5倍）
    @Transaction
    suspend fun insertBatchInTransaction(dataList: HealthData) = insertBatch(dataList)

    @Query("SELECT * FROM HealthData WHERE year = :year AND month = :month AND day = :day AND dataType = :dataType")
    suspend fun getByDate(year: Int, month: Int, day: Int, dataType: String): List<HealthData>

    @Query("SELECT * FROM HealthData WHERE year = :year AND month = :month AND dataType = :dataType")
    suspend fun getByMonth(year: Int, month: Int, dataType: String): List<HealthData>

    @Query("SELECT * FROM HealthData WHERE year = :year AND month = :month AND week = :week AND dataType = :dataType")
    suspend fun getByWeek(year: Int, month: Int, week:Int, dataType: String): List<HealthData>
}

class HealthDataRepository(context: Context) {
    private val dao = DataBase.getInstance(context).healthDataDao()
    // 对外暴露的批量存储方法
    suspend fun saveBatchData(dataList: HealthData) {
        dao.insertBatchInTransaction(dataList)
    }
    suspend fun getHealthDataByDate(year: Int, month: Int, day: Int, dataType: String): List<HealthData> {
        return dao.getByDate(year, month, day, dataType)
    }
    suspend fun getHealthDataByMonth(year: Int, month: Int, dataType: String): List<HealthData> {
        return dao.getByMonth(year, month, dataType)
    }
    suspend fun getHealthDataByWeek(year: Int, month: Int,week:Int, dataType: String): List<HealthData> {
        return dao.getByWeek(year, month,week, dataType)
    }
}

@Database(entities = [HealthData::class], version = 1, exportSchema = false)
abstract class DataBase: RoomDatabase(){
    abstract fun healthDataDao(): HealthDataDao
    companion object{
        @Volatile
        private var INSTANCE: DataBase? = null
        fun getInstance(context: Context): DataBase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DataBase::class.java,
                    "ecg_db"
                )
//                    .allowMainThreadQueries() // 默认禁止主线程操作
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}