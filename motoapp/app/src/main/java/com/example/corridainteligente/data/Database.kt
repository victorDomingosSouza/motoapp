package com.example.corridainteligente.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "rides")
data class Ride(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val fare: Double,
    val totalKm: Double,
    val totalMin: Double,
    val perKm: Double,
    val perMin: Double,
    val lat: Double?,
    val lng: Double?,
)

@Dao
interface RideDao {
    @Insert suspend fun insert(ride: Ride)
    @Delete suspend fun delete(ride: Ride)
    @Query("SELECT * FROM rides ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Ride>>
}

// versão 2: a coluna perHour virou perMin (ganho por minuto)
@Database(entities = [Ride::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "corridas.db"
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
