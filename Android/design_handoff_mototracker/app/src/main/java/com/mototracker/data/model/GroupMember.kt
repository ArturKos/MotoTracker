package com.mototracker.data.model

/**
 * Pure domain model representing a member of the user's riding group.
 *
 * @param id        UUID string.
 * @param name      Display name of the rider.
 * @param phone     Phone number used to identify / contact the rider.
 * @param bikeName  Bike model name the rider typically uses.
 */
data class GroupMember(
    val id: String,
    val name: String,
    val phone: String,
    val bikeName: String,
)
