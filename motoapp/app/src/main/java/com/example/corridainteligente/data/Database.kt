package com.example.corridainteligente.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "rides", indices = [Index(value = ["acceptanceId"], unique = true)])
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
    val acceptanceId: String? = null,
)

@Dao
interface RideDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(ride: Ride)
    @Delete suspend fun delete(ride: Ride)
    @Query("SELECT * FROM rides ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Ride>>
}

// Versão 3 acrescenta a identidade do evento de aceite.
@Database(entities = [Ride::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE rides_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        fare REAL NOT NULL,
                        totalKm REAL NOT NULL,
                        totalMin REAL NOT NULL,
                        perKm REAL NOT NULL,
                        perMin REAL NOT NULL,
                        lat REAL,
                        lng REAL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO rides_new (id, timestamp, fare, totalKm, totalMin, perKm, perMin, lat, lng)
                    SELECT id, timestamp, fare, totalKm, totalMin, perKm, perHour / 60.0, lat, lng
                    FROM rides
                """.trimIndent())
                db.execSQL("DROP TABLE rides")
                db.execSQL("ALTER TABLE rides_new RENAME TO rides")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rides ADD COLUMN acceptanceId TEXT")
                db.execSQL("CREATE UNIQUE INDEX index_rides_acceptanceId ON rides (acceptanceId)")
            }
        }
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "corridas.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
