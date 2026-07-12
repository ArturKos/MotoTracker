package com.mototracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.mototracker.data.local.entity.BikeEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [BikeEntity] rows.
 *
 * All mutations are suspend functions; queries return [Flow] so callers
 * observe live updates without polling.
 */
@Dao
interface BikeDao {

    /**
     * Inserts a new bike or replaces the existing row with the same primary key.
     *
     * @param entity The bike to persist.
     */
    @Upsert
    suspend fun upsert(entity: BikeEntity)

    /**
     * Removes the given bike from the database.
     *
     * @param entity The bike to remove (matched by primary key).
     */
    @Delete
    suspend fun delete(entity: BikeEntity)

    /**
     * Returns a live stream of all bikes, newest inserts last (unordered).
     */
    @Query("SELECT * FROM bikes")
    fun getAll(): Flow<List<BikeEntity>>

    /**
     * Returns the bike with the given [id], or `null` if not found.
     *
     * @param id The primary key to look up.
     */
    @Query("SELECT * FROM bikes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BikeEntity?

    /**
     * Deletes every row from the bikes table.
     *
     * Used exclusively by [com.mototracker.data.repository.BackupRepositoryImpl] during a
     * [com.mototracker.domain.backup.RestoreMode.REPLACE] import to clear stale data before
     * inserting the imported set.
     */
    @Query("DELETE FROM bikes")
    suspend fun deleteAll()
}
