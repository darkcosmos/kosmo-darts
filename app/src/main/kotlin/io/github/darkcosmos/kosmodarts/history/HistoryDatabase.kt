package io.github.darkcosmos.kosmodarts.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/**
 * The archive database, schema version 1 (ADR 0012). Its exported schema is committed under `app/schemas/`. It is
 * built without any destructive fallback, now or later: every future version ships a tested `Migration` (plan D26).
 */
@Database(
    entities = [
        ProfileEntity::class,
        SessionEntity::class,
        MatchRecordEntity::class,
        MatchPlayerEntity::class,
        LegEntity::class,
        DartEntity::class,
    ],
    version = HistoryDatabase.VERSION,
    exportSchema = true,
)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    abstract fun archiveDao(): ArchiveDao

    abstract fun historyDao(): HistoryDao

    companion object {
        const val FILE_NAME = "kosmo-history.db"
        const val VERSION = 1

        /**
         * The builder of the app's database file, in private storage, with the corruption handler that keeps the
         * file (ADR 0012, "Failure contract"). Nothing opens until the first access, which must run off the main
         * thread (the persistence queue or the read executor).
         */
        fun builder(context: Context): Builder<HistoryDatabase> =
            Room.databaseBuilder(context, HistoryDatabase::class.java, FILE_NAME).keepingCorruptFile()

        /** The app's database (see [builder]). */
        fun open(context: Context): HistoryDatabase = builder(context).build()
    }
}

/** Replaces the framework's corruption handler, which deletes the file, by [KeepCorruptFileOpenHelperFactory]. */
fun RoomDatabase.Builder<HistoryDatabase>.keepingCorruptFile(): RoomDatabase.Builder<HistoryDatabase> =
    openHelperFactory(KeepCorruptFileOpenHelperFactory())

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
