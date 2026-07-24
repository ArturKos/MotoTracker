package com.mototracker.data.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreDeviceIdentityTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { tmp.newFile("id_${System.nanoTime()}.preferences_pb") }

    @Test
    fun `code generuje UUID i zwraca ten sam przy kolejnym wywolaniu`() = runTest {
        val store = newStore()
        val id = DataStoreDeviceIdentity(store, deviceName = "test dev")
        val first = id.code()
        val second = id.code()
        assertTrue("kod nie jest pusty", first.isNotBlank())
        assertEquals("kod stabilny między wywołaniami", first, second)
    }

    @Test
    fun `name zwraca wstrzykniete Build-owe zrodlo`() {
        val id = DataStoreDeviceIdentity(newStore(), deviceName = "samsung SM-G991B")
        assertEquals("samsung SM-G991B", id.name())
    }
}
