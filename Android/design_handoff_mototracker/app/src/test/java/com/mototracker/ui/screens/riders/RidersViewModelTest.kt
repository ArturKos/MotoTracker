package com.mototracker.ui.screens.riders

import app.cash.turbine.test
import com.mototracker.data.model.FeedEvent
import com.mototracker.data.model.FeedType
import com.mototracker.data.model.GroupMember
import com.mototracker.data.model.Wave
import com.mototracker.data.network.NetworkMonitor
import com.mototracker.data.repository.FeedRepository
import com.mototracker.data.repository.GroupRepository
import com.mototracker.data.repository.WaveRepository
import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.AppSettingsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

// ─────────────────────────────────────────────────────────────────────────────
// Fakes
// ─────────────────────────────────────────────────────────────────────────────

private class FakeGroupRepository : GroupRepository {
    private val _flow = MutableStateFlow<List<GroupMember>>(emptyList())
    val addedPhones = mutableListOf<String>()

    fun emit(members: List<GroupMember>) { _flow.value = members }
    override fun observeGroup(): Flow<List<GroupMember>> = _flow
    override suspend fun addByPhone(phone: String) {
        addedPhones += phone
        _flow.value = _flow.value + GroupMember(
            id = "fake-$phone",
            name = phone,
            phone = phone,
            bikeName = "",
        )
    }
}

private class FakeFeedRepository(feed: List<FeedEvent> = emptyList()) : FeedRepository {
    private val _flow = MutableStateFlow(feed)
    override fun observeFeed(): Flow<List<FeedEvent>> = _flow
}

private class FakeWaveRepository(waves: List<Wave> = emptyList()) : WaveRepository {
    private val _flow = MutableStateFlow(waves)
    override fun observeForRoute(routeId: String): Flow<List<Wave>> = _flow
    override fun observeAll(): Flow<List<Wave>> = _flow
}

private class FakeNetworkMonitor(online: Boolean = false) : NetworkMonitor {
    private val _flow = MutableStateFlow(online)
    fun emit(online: Boolean) { _flow.value = online }
    override val isOnline: Flow<Boolean> = _flow
}

private class FakeSettingsSource(settings: AppSettings = AppSettings()) : AppSettingsSource {
    private val _flow = MutableStateFlow(settings)
    fun emit(settings: AppSettings) { _flow.value = settings }
    override val settings: Flow<AppSettings> = _flow
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun makeMember(
    id: String = "m1",
    name: String = "Alice",
    phone: String = "+48123456789",
    bikeName: String = "MT-07",
) = GroupMember(id = id, name = name, phone = phone, bikeName = bikeName)

private fun makeFeedEvent(
    who: String = "Marek",
    bikeName: String = "MT-09",
    type: FeedType = FeedType.START,
    value: String? = null,
    timeLabel: String = "09:12",
) = FeedEvent(who = who, bikeName = bikeName, type = type, value = value, timeLabel = timeLabel)

private fun makeWave(
    id: String = "w1",
    nick: String = "Piotr",
    bikeName: String = "GSX-R",
    place: String = "Rynek",
    timeLabel: String = "14:00",
) = Wave(id = id, nick = nick, bikeName = bikeName, place = place, timeLabel = timeLabel, routeId = null)

// ─────────────────────────────────────────────────────────────────────────────
// Main test class
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class RidersViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun buildVm(
        groupRepo: FakeGroupRepository = FakeGroupRepository(),
        feedRepo: FakeFeedRepository = FakeFeedRepository(),
        waveRepo: FakeWaveRepository = FakeWaveRepository(),
        networkMonitor: FakeNetworkMonitor = FakeNetworkMonitor(online = false),
        settingsSource: FakeSettingsSource = FakeSettingsSource(),
    ) = RidersViewModel(
        groupRepository = groupRepo,
        feedRepository = feedRepo,
        waveRepository = waveRepo,
        networkMonitor = networkMonitor,
        settingsSource = settingsSource,
    )

    // ── initials ──────────────────────────────────────────────────────────────

    @Test
    fun `initial is first character of name uppercased`() = runTest {
        val repo = FakeGroupRepository().also { it.emit(listOf(makeMember(name = "alice"))) }
        val vm = buildVm(groupRepo = repo)
        vm.uiState.test {
            val ui = awaitItem()
            assertEquals("A", ui.members.first().initial)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial handles single-character name`() = runTest {
        val repo = FakeGroupRepository().also { it.emit(listOf(makeMember(name = "z"))) }
        val vm = buildVm(groupRepo = repo)
        vm.uiState.test {
            assertEquals("Z", awaitItem().members.first().initial)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── memberCount ───────────────────────────────────────────────────────────

    @Test
    fun `memberCount reflects size of members list`() = runTest {
        val repo = FakeGroupRepository().also {
            it.emit(listOf(makeMember("m1"), makeMember("m2"), makeMember("m3")))
        }
        val vm = buildVm(groupRepo = repo)
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(3, state.memberCount)
            assertEquals(3, state.members.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `memberCount is zero for empty group`() = runTest {
        val vm = buildVm()
        vm.uiState.test {
            assertEquals(0, awaitItem().memberCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── feedAvailable ─────────────────────────────────────────────────────────

    @Test
    fun `feedAvailable is TRUE when online and not offlineOnly`() = runTest {
        val vm = buildVm(
            networkMonitor = FakeNetworkMonitor(online = true),
            settingsSource = FakeSettingsSource(AppSettings(offlineOnly = false)),
        )
        vm.uiState.test {
            assertTrue(awaitItem().feedAvailable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `feedAvailable is FALSE when offline and not offlineOnly`() = runTest {
        val vm = buildVm(
            networkMonitor = FakeNetworkMonitor(online = false),
            settingsSource = FakeSettingsSource(AppSettings(offlineOnly = false)),
        )
        vm.uiState.test {
            assertFalse(awaitItem().feedAvailable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `feedAvailable is FALSE when online but offlineOnly is true`() = runTest {
        val vm = buildVm(
            networkMonitor = FakeNetworkMonitor(online = true),
            settingsSource = FakeSettingsSource(AppSettings(offlineOnly = true)),
        )
        vm.uiState.test {
            assertFalse(awaitItem().feedAvailable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `feedAvailable is FALSE when offline and offlineOnly is true`() = runTest {
        val vm = buildVm(
            networkMonitor = FakeNetworkMonitor(online = false),
            settingsSource = FakeSettingsSource(AppSettings(offlineOnly = true)),
        )
        vm.uiState.test {
            assertFalse(awaitItem().feedAvailable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── dotColor mapping ──────────────────────────────────────────────────────

    @Test
    fun `dotColor is ACCENT for START events`() = runTest {
        val feedRepo = FakeFeedRepository(listOf(makeFeedEvent(type = FeedType.START)))
        val vm = buildVm(feedRepo = feedRepo)
        vm.uiState.test {
            assertEquals(FeedDotColor.ACCENT, awaitItem().feed.first().dotColor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dotColor is ACCENT2 for MAX events`() = runTest {
        val feedRepo = FakeFeedRepository(listOf(makeFeedEvent(type = FeedType.MAX, value = "150 km/h")))
        val vm = buildVm(feedRepo = feedRepo)
        vm.uiState.test {
            assertEquals(FeedDotColor.ACCENT2, awaitItem().feed.first().dotColor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dotColor is DIM for FINISH events`() = runTest {
        val feedRepo = FakeFeedRepository(listOf(makeFeedEvent(type = FeedType.FINISH)))
        val vm = buildVm(feedRepo = feedRepo)
        vm.uiState.test {
            assertEquals(FeedDotColor.DIM, awaitItem().feed.first().dotColor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── isMax + value passthrough ─────────────────────────────────────────────

    @Test
    fun `isMax is true and value is passed through for MAX type`() = runTest {
        val feedRepo = FakeFeedRepository(listOf(makeFeedEvent(type = FeedType.MAX, value = "148 km/h")))
        val vm = buildVm(feedRepo = feedRepo)
        vm.uiState.test {
            val event = awaitItem().feed.first()
            assertTrue(event.isMax)
            assertEquals("148 km/h", event.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isMax is false and value is null for START type`() = runTest {
        val feedRepo = FakeFeedRepository(listOf(makeFeedEvent(type = FeedType.START, value = null)))
        val vm = buildVm(feedRepo = feedRepo)
        vm.uiState.test {
            val event = awaitItem().feed.first()
            assertFalse(event.isMax)
            assertNull(event.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── waves mapping ─────────────────────────────────────────────────────────

    @Test
    fun `waves are mapped to WaveUi`() = runTest {
        val waveRepo = FakeWaveRepository(listOf(makeWave(nick = "Piotr", bikeName = "GSX-R", place = "Rynek", timeLabel = "14:00")))
        val vm = buildVm(waveRepo = waveRepo)
        vm.uiState.test {
            val wave = awaitItem().waves.first()
            assertEquals("Piotr", wave.nick)
            assertEquals("GSX-R", wave.bikeName)
            assertEquals("Rynek", wave.place)
            assertEquals("14:00", wave.timeLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty waves list is passed through`() = runTest {
        val vm = buildVm(waveRepo = FakeWaveRepository(emptyList()))
        vm.uiState.test {
            assertTrue(awaitItem().waves.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── onAddByPhone + InviteSent event ───────────────────────────────────────

    @Test
    fun `onAddByPhone calls repo addByPhone with the given phone`() = runTest {
        val repo = FakeGroupRepository()
        val vm = buildVm(groupRepo = repo)
        vm.onAddByPhone("+48600111222")
        assertEquals(listOf("+48600111222"), repo.addedPhones)
    }

    @Test
    fun `onAddByPhone emits InviteSent event`() = runTest {
        val repo = FakeGroupRepository()
        val vm = buildVm(groupRepo = repo)
        vm.events.test {
            vm.onAddByPhone("+48600111222")
            assertEquals(RidersEvent.InviteSent, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `member added via onAddByPhone appears in uiState`() = runTest {
        val repo = FakeGroupRepository()
        val vm = buildVm(groupRepo = repo)
        vm.uiState.test {
            assertEquals(0, awaitItem().memberCount)
            vm.onAddByPhone("+48600111222")
            val updated = awaitItem()
            assertEquals(1, updated.memberCount)
            assertEquals("+48600111222", updated.members.first().phone)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── wavesEnabled (J3) ─────────────────────────────────────────────────────

    @Test
    fun `wavesEnabled defaults to true when settings emit default AppSettings`() = runTest {
        val vm = buildVm(settingsSource = FakeSettingsSource(AppSettings()))
        vm.uiState.test {
            assertTrue(awaitItem().wavesEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `wavesEnabled is false when settings emit wavesEnabled=false`() = runTest {
        val source = FakeSettingsSource(AppSettings(wavesEnabled = false))
        val vm = buildVm(settingsSource = source)
        vm.uiState.test {
            assertFalse(awaitItem().wavesEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `wavesEnabled reacts to settings update`() = runTest {
        val source = FakeSettingsSource(AppSettings(wavesEnabled = true))
        val vm = buildVm(settingsSource = source)
        vm.uiState.test {
            assertTrue(awaitItem().wavesEnabled)
            source.emit(AppSettings(wavesEnabled = false))
            assertFalse(awaitItem().wavesEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Parameterized feedAvailable combos
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class RidersViewModelFeedAvailableTest(
    private val isOnline: Boolean,
    private val offlineOnly: Boolean,
    private val expectedFeedAvailable: Boolean,
) {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "online={0} offlineOnly={1} expected={2}")
        fun data() = listOf(
            arrayOf(true, false, true),
            arrayOf(false, false, false),
            arrayOf(true, true, false),
            arrayOf(false, true, false),
        )
    }

    @Test
    fun `feedAvailable matches expected for given online + offlineOnly combo`() = runTest {
        val vm = RidersViewModel(
            groupRepository = FakeGroupRepository(),
            feedRepository = FakeFeedRepository(),
            waveRepository = FakeWaveRepository(),
            networkMonitor = FakeNetworkMonitor(online = isOnline),
            settingsSource = FakeSettingsSource(AppSettings(offlineOnly = offlineOnly)),
        )
        vm.uiState.test {
            assertEquals(expectedFeedAvailable, awaitItem().feedAvailable)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
