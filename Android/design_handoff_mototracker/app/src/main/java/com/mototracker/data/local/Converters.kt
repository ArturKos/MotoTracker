package com.mototracker.data.local

import androidx.room.TypeConverter
import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.data.local.entity.CorrectionQueueState
import com.mototracker.data.local.entity.CorrectionStatus
import com.mototracker.data.local.entity.SyncQueueState

/**
 * Room TypeConverters for enum types stored as their [name] strings.
 *
 * Registered on [MotoDatabase] via `@TypeConverters(Converters::class)`.
 */
class Converters {

    /** Persists [BikeStatus] as its enum name string. */
    @TypeConverter
    fun bikeStatusToString(status: BikeStatus): String = status.name

    /** Restores [BikeStatus] from its persisted name string. */
    @TypeConverter
    fun stringToBikeStatus(value: String): BikeStatus = BikeStatus.valueOf(value)

    /** Persists [SyncQueueState] as its enum name string. */
    @TypeConverter
    fun syncQueueStateToString(state: SyncQueueState): String = state.name

    /** Restores [SyncQueueState] from its persisted name string. */
    @TypeConverter
    fun stringToSyncQueueState(value: String): SyncQueueState = SyncQueueState.valueOf(value)

    /** Persists [CorrectionStatus] as its enum name string. */
    @TypeConverter
    fun correctionStatusToString(status: CorrectionStatus): String = status.name

    /** Restores [CorrectionStatus] from its persisted name string. */
    @TypeConverter
    fun stringToCorrectionStatus(value: String): CorrectionStatus = CorrectionStatus.valueOf(value)

    /** Persists [CorrectionQueueState] as its enum name string. */
    @TypeConverter
    fun correctionQueueStateToString(state: CorrectionQueueState): String = state.name

    /** Restores [CorrectionQueueState] from its persisted name string. */
    @TypeConverter
    fun stringToCorrectionQueueState(value: String): CorrectionQueueState = CorrectionQueueState.valueOf(value)
}
