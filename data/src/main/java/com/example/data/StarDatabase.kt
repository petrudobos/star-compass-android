package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Star::class], version = 1, exportSchema = false)
abstract class StarDatabase : RoomDatabase() {
    abstract fun starDao(): StarDao

    companion object {
        @Volatile
        private var INSTANCE: StarDatabase? = null

        fun getDatabase(context: Context): StarDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StarDatabase::class.java,
                    "star_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
