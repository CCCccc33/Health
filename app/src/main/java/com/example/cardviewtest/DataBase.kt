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
    suspend fun insertBatch(dataList: List<HealthData>)
    // 事务封装（批量插入提速3-5倍）
    @Transaction
    suspend fun insertBatchInTransaction(dataList: List<HealthData>) = insertBatch(dataList)

    @Query("SELECT * FROM HealthData WHERE year = :year AND month = :month AND day = :day")
    suspend fun getByDate(year: Int, month: Int, day: Int): List<HealthData>
}

class HealthDataRepository(context: Context) {
    private val dao = DataBase.getInstance(context).healthDataDao()
    // 对外暴露的批量存储方法
    suspend fun saveBatchData(dataList: List<HealthData>) {
        dao.insertBatchInTransaction(dataList)
    }
    suspend fun getHealthDataByDate(year: Int, month: Int, day: Int): List<HealthData> {
        return dao.getByDate(year, month, day)
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