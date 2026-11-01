package io.github.darkcosmos.kosmodarts.history

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/** Placeholder entity of the setup spike (T1); T7 replaces it with the real schema, still at version 1. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
)

@Dao
interface ProfileDao {
    @Insert
    fun insert(profile: ProfileEntity): Long

    @Query("SELECT * FROM profile ORDER BY id")
    fun all(): List<ProfileEntity>
}

@Database(entities = [ProfileEntity::class], version = 1, exportSchema = true)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    companion object {
        const val FILE_NAME = "kosmo-history.db"
    }
}

/**
 * Open helper factory that keeps a corrupt database file (ADR 0012, "Failure contract"): the framework default
 * deletes the file on corruption; here the corruption callback only does nothing, so the open fails and the bytes stay.
 */
class KeepCorruptFileOpenHelperFactory(
    private val delegate: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory(),
) : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val original = configuration.callback
        val keeping = object : SupportSQLiteOpenHelper.Callback(original.version) {
            override fun onConfigure(db: SupportSQLiteDatabase) = original.onConfigure(db)

            override fun onCreate(db: SupportSQLiteDatabase) = original.onCreate(db)

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                original.onUpgrade(db, oldVersion, newVersion)

            override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                original.onDowngrade(db, oldVersion, newVersion)

            override fun onOpen(db: SupportSQLiteDatabase) = original.onOpen(db)

            // Deliberately empty: the default implementation deletes the database file.
            override fun onCorruption(db: SupportSQLiteDatabase) = Unit
        }
        return delegate.create(
            SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                .name(configuration.name)
                .callback(keeping)
                .noBackupDirectory(configuration.useNoBackupDirectory)
                .allowDataLossOnRecovery(configuration.allowDataLossOnRecovery)
                .build(),
        )
    }
}
