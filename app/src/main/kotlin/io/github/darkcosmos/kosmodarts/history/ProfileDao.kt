package io.github.darkcosmos.kosmodarts.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/** Profile writes (PF1, PF2). No delete (PF5). */
@Dao
abstract class ProfileDao {

    /** The profile whose name key is [nameKey], or `null` when there is none. */
    @Query("SELECT * FROM profile WHERE name_key = :nameKey")
    abstract fun findByNameKey(nameKey: String): ProfileEntity?

    @Insert
    protected abstract fun insert(profile: ProfileEntity): Long

    @Query("UPDATE profile SET last_used_at = MAX(last_used_at, :at) WHERE id = :id")
    protected abstract fun touch(id: Long, at: Long)

    /**
     * The one profile upsert (review A3), shared by the archive and the profile repository: creates the profile of
     * [nameKey] with the spelling [name] and `created_at = reset_at = last_used_at = at` (PF1, D32), or, when it
     * exists, only moves its `last_used_at` forward to [at]; its spelling and dates stay (PF2). Returns the profile id.
     */
    @Transaction
    open fun upsertUsed(name: String, nameKey: String, at: Long): Long {
        val existing = findByNameKey(nameKey)
        if (existing != null) {
            touch(existing.id, at)
            return existing.id
        }
        return insert(
            ProfileEntity(id = 0, name = name, nameKey = nameKey, createdAt = at, lastUsedAt = at, resetAt = at),
        )
    }
}
