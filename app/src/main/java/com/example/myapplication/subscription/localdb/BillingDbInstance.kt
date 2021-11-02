package com.example.myapplication.subscription.localdb
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
@Database(
    entities = [
        SkuDetailsModel::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BillingDbInstance : RoomDatabase() {
    abstract fun skuDetailsDao(): PremiumDao

    companion object {
        @Volatile
        private var INSTANCE: BillingDbInstance? = null
        private const val DATABASE_NAME = "translator.db"

        fun getInstance(context: Context): BillingDbInstance =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also {
                    INSTANCE = it
                }
            }

        private fun buildDatabase(appContext: Context): BillingDbInstance {
            return Room.databaseBuilder(appContext, BillingDbInstance::class.java, DATABASE_NAME)
                .fallbackToDestructiveMigration() // Data is cache, so it is OK to delete
                .build()
        }
    }
}
