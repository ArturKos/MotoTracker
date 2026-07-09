package com.mototracker.data.model.mapper

import com.mototracker.data.local.entity.GroupMemberEntity
import com.mototracker.data.model.GroupMember

/** Converts a [GroupMemberEntity] to its domain representation. */
fun GroupMemberEntity.toDomain(): GroupMember = GroupMember(
    id = id,
    name = name,
    phone = phone,
    bikeName = bikeName,
)

/** Converts a [GroupMember] domain model to its Room entity counterpart. */
fun GroupMember.toEntity(): GroupMemberEntity = GroupMemberEntity(
    id = id,
    name = name,
    phone = phone,
    bikeName = bikeName,
)
