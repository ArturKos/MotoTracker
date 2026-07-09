package com.mototracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a member of the user's riding group.
 *
 * Mirrors the group member state shape from README §State (line 144).
 *
 * @param id        UUID string (client-generated).
 * @param name      Display name of the rider.
 * @param phone     Phone number used to identify / contact the rider.
 * @param bikeName  Bike model name that the rider typically uses.
 */
@Entity(tableName = "group_members")
data class GroupMemberEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val bikeName: String,
)
