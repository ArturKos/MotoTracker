package com.mototracker.data.repository

import com.mototracker.data.model.Rider
import kotlinx.coroutines.flow.Flow

/**
 * Read/write contract for the BLE-discovered rider directory.
 *
 * The backing store is [com.mototracker.data.local.dao.RiderDao]; this interface isolates
 * ViewModels from Room so tests can supply a simple fake without an Android context.
 */
interface RiderRepository {

    /**
     * Returns a live stream of all known riders, newest-first.
     *
     * Backed by [com.mototracker.data.local.dao.RiderDao.getAll]; maps entities to [Rider]
     * domain objects. Emits on every rider-table change.
     */
    fun observeAll(): Flow<List<Rider>>

    /**
     * Sets the [Rider.inGroup] flag for the rider identified by [shortId].
     *
     * Delegates to [com.mototracker.data.local.dao.RiderDao.setInGroup] on [kotlinx.coroutines.Dispatchers.IO].
     *
     * @param shortId BLE device identifier.
     * @param inGroup New group-membership state.
     */
    suspend fun setInGroup(shortId: String, inGroup: Boolean)
}
