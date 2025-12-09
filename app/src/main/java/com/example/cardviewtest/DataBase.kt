package com.example.cardviewtest

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log


class DataBase(val context: Context,name: String,version: Int):
    SQLiteOpenHelper(context,name,null,version){
    private val CREATE_TABLE_SQL = """
        CREATE TABLE ${DbConstants.TABLE_HEALTH} (
            ${DbConstants.COL_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
            ${DbConstants.COL_YEAR} INTEGER NOT NULL,
            ${DbConstants.COL_MONTH} INTEGER NOT NULL,
            ${DbConstants.COL_WEEK} INTEGER NOT NULL,
            ${DbConstants.COL_DAY} INTEGER NOT NULL,
            ${DbConstants.COL_UPLOAD_TIME} TEXT NOT NULL,
            ${DbConstants.COL_DATA_TYPE} INTEGER NOT NULL,
            ${DbConstants.COL_VALUE1} REAL,
            ${DbConstants.COL_VALUE2} REAL,
            ${DbConstants.COL_VALUE3} REAL,
            ${DbConstants.COL_REMARK} TEXT
        )
    """.trimIndent()

    private val CREATE_TIME_INDEX = """
        CREATE INDEX ${DbConstants.IDX_TIME} 
        ON ${DbConstants.TABLE_HEALTH} (${DbConstants.COL_YEAR}, ${DbConstants.COL_MONTH}, ${DbConstants.COL_DAY})
    """.trimIndent()

    private val CREATE_TIME_INDEX_WITH_WEEK = """
    CREATE INDEX ${DbConstants.IDX_TIME_WITH_WEEK} 
    ON ${DbConstants.TABLE_HEALTH} (${DbConstants.COL_WEEK}, ${DbConstants.COL_YEAR}, ${DbConstants.COL_MONTH}, ${DbConstants.COL_DAY})
""".trimIndent()

    // 创建数据类型索引
    private val CREATE_TYPE_INDEX = """
        CREATE INDEX ${DbConstants.IDX_DATA_TYPE} 
        ON ${DbConstants.TABLE_HEALTH} (${DbConstants.COL_DATA_TYPE})
    """.trimIndent()

    private val TAG = "DataBase"
    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(CREATE_TABLE_SQL)
        Log.d(TAG,"成功创建数据库")
        db?.execSQL(CREATE_TIME_INDEX)
        db?.execSQL(CREATE_TYPE_INDEX)
        db?.execSQL(CREATE_TIME_INDEX_WITH_WEEK)
        Log.d(TAG,"索引创建成功！")
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("drop table if exists myData")
        db?.execSQL("drop table if exists createCategory")
        onCreate(db)
    }
}