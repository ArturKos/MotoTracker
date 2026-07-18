package com.mototracker.ui.screens.record

import com.mototracker.data.model.Rider
import com.mototracker.data.repository.RiderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Minimal no-op fake for tests that do not exercise rider-roster logic. */
internal class FakeRiderRepository : RiderRepository {
    private val _riders = MutableStateFlow<List<Rider>>(emptyList())

    override fun observeAll(): Flow<List<Rider>> = _riders

    override suspend fun setInGroup(shortId: String, inGroup: Boolean) {
        _riders.value = _riders.value.map {
            if (it.shortId == shortId) it.copy(inGroup = inGroup) else it
        }
    }
}
