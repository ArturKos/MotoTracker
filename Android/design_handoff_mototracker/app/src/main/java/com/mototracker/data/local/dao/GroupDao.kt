package com.mototracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.mototracker.data.local.entity.GroupMemberEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [GroupMemberEntity] rows.
 */
@Dao
interface GroupDao {

    /**
     * Inserts a new group member or replaces an existing row with the same primary key.
     *
     * @param entity The group member to persist.
     */
    @Upsert
    suspend fun upsert(entity: GroupMemberEntity)

    /**
     * Removes the given group member from the database.
     *
     * @param entity The member to remove (matched by primary key).
     */
    @Delete
    suspend fun delete(entity: GroupMemberEntity)

    /**
     * Returns a live stream of all group members.
     */
    @Query("SELECT * FROM group_members")
    fun getAll(): Flow<List<GroupMemberEntity>>
}
