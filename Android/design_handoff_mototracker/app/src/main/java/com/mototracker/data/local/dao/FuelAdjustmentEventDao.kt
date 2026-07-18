package com.mototracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.mototracker.data.local.entity.FuelAdjustmentEventEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [FuelAdjustmentEventEntity] rows.
 *
 * Only used by [com.mototracker.data.repository.FuelAdjustmentRepositoryImpl].
 * Intentionally excluded from the refuel ledger path that feeds
 * [com.mototracker.domain.fuel.FuelConsumptionCalculator] (R1).
 */
@Dao
interface FuelAdjustmentEventDao {

    /**
     * Inserts a new fuel-adjustment event and returns its auto-generated primary key.
     *
     * @param entity The event to persist.
     * @return The newly assigned [FuelAdjustmentEventEntity.id].
     */
    @Insert
    suspend fun insert(entity: FuelAdjustmentEventEntity): Long

    /**
     * Returns a live stream of all fuel-adjustment events for [bikeId], ordered
     * chronologically ascending.
     *
     * Re-emits whenever a row for this bike is inserted.
     *
     * @param bikeId UUID of the bike whose correction events should be returned.
     */
    @Query("SELECT * FROM fuel_adjustment_event WHERE bikeId = :bikeId ORDER BY epochMs ASC")
    fun observeForBike(bikeId: String): Flow<List<FuelAdjustmentEventEntity>>

    /**
     * Returns the most-recently inserted fuel-adjustment event for [bikeId], or null
     * when no correction has been recorded for this bike.
     *
     * @param bikeId UUID of the bike.
     */
    @Query("SELECT * FROM fuel_adjustment_event WHERE bikeId = :bikeId ORDER BY epochMs DESC LIMIT 1")
    suspend fun latestForBike(bikeId: String): FuelAdjustmentEventEntity?
}
