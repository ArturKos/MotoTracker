package com.mototracker.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncAutoStarterTest {

    private class CountingSyncRepository : SyncRepository {
        var starts = 0
        override val pendingCount: Flow<Int> = MutableStateFlow(0)
        override suspend fun enqueue(routeId: String) = Unit
        override suspend fun syncNow(): Int = 0
        override fun start(scope: CoroutineScope) { starts++ }
    }

    @Test
    fun `ensureStarted starts the sync loop exactly once`() {
        val repo = CountingSyncRepository()
        val starter = SyncAutoStarter(repo, CoroutineScope(Dispatchers.Unconfined))

        starter.ensureStarted()
        starter.ensureStarted()
        starter.ensureStarted()

        assertEquals(1, repo.starts)
    }
}
