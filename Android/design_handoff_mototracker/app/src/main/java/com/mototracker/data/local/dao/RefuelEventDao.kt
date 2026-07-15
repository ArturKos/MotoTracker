package com.mototracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.mototracker.data.local.entity.RefuelEventEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [RefuelEventEntity] rows.
 *
 * All mutations are suspend functions; the observe query returns a [Flow] so the
 * route-detail screen reacts immediately to new or deleted refuel events.
 */
@Dao
interface RefuelEventDao {

    /**
     * Inserts a new refuel event and returns its auto-generated primary key.
     *
     * @param entity The refuel event to persist.
     * @return The newly assigned [RefuelEventEntity.id].
     */
    @Insert
    suspend fun insert(entity: RefuelEventEntity): Long

    /**
     * Returns a live stream of all refuel events for [routeId], ordered by
     * [RefuelEventEntity.epochMs] ascending (chronological).
     *
     * Re-emits whenever a row for this route is inserted or deleted.
     *
     * @param routeId The parent route UUID to filter by.
     */
    @Query("SELECT * FROM refuel_event WHERE routeId = :routeId ORDER BY epochMs ASC")
    fun observeForRoute(routeId: String): Flow<List<RefuelEventEntity>>

    /**
     * Deletes the refuel event with the given [id].
     *
     * @param id Primary key of the row to remove.
     */
    @Query("DELETE FROM refuel_event WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Deletes all refuel events associated with [routeId].
     *
     * Normally handled by the CASCADE on the parent route; this is available for
     * explicit bulk-cleanup without deleting the route itself.
     *
     * @param routeId The parent route UUID whose events are to be removed.
     */
    @Query("DELETE FROM refuel_event WHERE routeId = :routeId")
    suspend fun deleteForRoute(routeId: String)

    /**
     * Returns a live stream of all refuel events for routes assigned to [bikeId],
     * ordered by route date (ascending) then event time (ascending).
     *
     * Used to build the odometer-ordered fuel-fill ledger for consumption calculation (H4).
     *
     * @param bikeId UUID of the bike whose route refuels should be returned.
     */
    @Query("""
        SELECT re.* FROM refuel_event re
        INNER JOIN routes r ON r.id = re.routeId
        WHERE r.bikeId = :bikeId
        ORDER BY r.dateEpochMs ASC, re.epochMs ASC
    """)
    fun observeForBikeRoutes(bikeId: String): Flow<List<RefuelEventEntity>>
}
