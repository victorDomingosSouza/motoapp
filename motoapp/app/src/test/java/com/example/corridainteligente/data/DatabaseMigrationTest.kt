package com.example.corridainteligente.data

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.*
import org.junit.Test

class DatabaseMigrationTest {
    private fun database(connection: Connection): SupportSQLiteDatabase = Proxy.newProxyInstance(
        SupportSQLiteDatabase::class.java.classLoader,
        arrayOf(SupportSQLiteDatabase::class.java),
    ) { _, method, args ->
        check(method.name == "execSQL") { "Unexpected database call ${method.name}" }
        connection.createStatement().use { it.execute(args!![0] as String) }
        null
    } as SupportSQLiteDatabase

    @Test fun versionOnePreservesRowsAndConvertsHourlyRate() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use {
                it.execute("CREATE TABLE rides (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestamp INTEGER NOT NULL, fare REAL NOT NULL, totalKm REAL NOT NULL, totalMin REAL NOT NULL, perKm REAL NOT NULL, perHour REAL NOT NULL, lat REAL, lng REAL)")
                it.execute("INSERT INTO rides VALUES (7, 1234, 30, 10, 20, 3, 90, -23.5, -46.6)")
            }
            val db = database(connection)
            AppDatabase.MIGRATION_1_2.migrate(db)
            AppDatabase.MIGRATION_2_3.migrate(db)
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT * FROM rides").use {
                    assertTrue(it.next())
                    assertEquals(7L, it.getLong("id"))
                    assertEquals(1234L, it.getLong("timestamp"))
                    assertEquals(30.0, it.getDouble("fare"), 0.0)
                    assertEquals(1.5, it.getDouble("perMin"), 0.0)
                    assertEquals(-23.5, it.getDouble("lat"), 0.0)
                    assertNull(it.getString("acceptanceId"))
                    assertFalse(it.next())
                }
                assertEquals(1, statement.executeUpdate("INSERT INTO rides (timestamp, fare, totalKm, totalMin, perKm, perMin) VALUES (2, 20, 10, 20, 2, 1)"))
            }
        }
    }

    @Test fun versionTwoKeepsMinuteRateAndDeduplicatesAcceptance() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use {
                it.execute("CREATE TABLE rides (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestamp INTEGER NOT NULL, fare REAL NOT NULL, totalKm REAL NOT NULL, totalMin REAL NOT NULL, perKm REAL NOT NULL, perMin REAL NOT NULL, lat REAL, lng REAL)")
                it.execute("INSERT INTO rides VALUES (1, 1234, 30, 10, 20, 3, 1.5, NULL, NULL)")
            }
            AppDatabase.MIGRATION_2_3.migrate(database(connection))
            connection.createStatement().use { statement ->
                val insert = "INSERT OR IGNORE INTO rides (timestamp, fare, totalKm, totalMin, perKm, perMin, acceptanceId) VALUES (2, 30, 10, 20, 3, 1.5, 'event-1')"
                assertEquals(1, statement.executeUpdate(insert))
                assertEquals(0, statement.executeUpdate(insert))
                statement.executeQuery("SELECT count(*), sum(perMin) FROM rides").use {
                    assertTrue(it.next())
                    assertEquals(2, it.getInt(1))
                    assertEquals(3.0, it.getDouble(2), 0.0)
                }
            }
        }
    }
}
