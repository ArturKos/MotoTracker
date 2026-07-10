package com.mototracker.data.repository

import com.mototracker.data.model.GroupMember
import kotlinx.coroutines.flow.Flow

/**
 * Persistence contract for the user's riding group.
 *
 * Members are stored locally in Room. Adding a member by phone sends an SMS
 * invite outside this interface (platform concern); the repo only persists the
 * new record so it appears immediately in the group list.
 */
interface GroupRepository {

    /**
     * Returns a live stream of all riding-group members.
     *
     * Emits on every change (upsert / delete) without requiring a reload.
     */
    fun observeGroup(): Flow<List<GroupMember>>

    /**
     * Adds a member whose [phone] number was entered by the user.
     *
     * Creates a [GroupMember] with a generated UUID, using [phone] as both the
     * `name` and `phone` fields (the user can rename later). An empty bike name
     * is stored until the member updates their profile.
     *
     * @param phone The phone number to use as the member's identifier.
     */
    suspend fun addByPhone(phone: String)
}
