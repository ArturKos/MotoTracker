package com.mototracker.ui.screens.riders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Riders screen (B5).
 *
 * Combines [GroupRepository.observeGroup], [FeedRepository.observeFeed],
 * [WaveRepository.observeAll], [NetworkMonitor.isOnline], and
 * [AppSettingsSource.settings] into a single [StateFlow]<[RidersUiState]>.
 *
 * One-shot [RidersEvent]s are delivered via [events] using a [Channel] to ensure
 * they are consumed exactly once regardless of recomposition.
 *
 * @param groupRepository   Source of riding-group members.
 * @param feedRepository    Source of live-feed events (static seed; real push out of scope).
 * @param waveRepository    Source of all Bluetooth waves (empty until BT is real, 🔬).
 * @param networkMonitor    Provides current internet connectivity.
 * @param settingsSource    Provides [AppSettings.offlineOnly] flag.
 */
@HiltViewModel
class RidersViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val feedRepository: FeedRepository,
    private val waveRepository: WaveRepository,
    private val networkMonitor: NetworkMonitor,
    private val settingsSource: AppSettingsSource,
) : ViewModel() {

    private val _events = Channel<RidersEvent>(Channel.BUFFERED)

    /** One-shot UI events (e.g. invite-sent toast). Collect in the Composable. */
    val events: Flow<RidersEvent> = _events.receiveAsFlow()

    /** Live UI state exposed to [RidersScreen]. */
    val uiState: StateFlow<RidersUiState> = combine(
        groupRepository.observeGroup(),
        feedRepository.observeFeed(),
        waveRepository.observeAll(),
        networkMonitor.isOnline,
        settingsSource.settings,
    ) { members, feed, waves, isOnline, settings ->
        buildUiState(members, feed, waves, isOnline, settings)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RidersUiState(),
    )

    /**
     * Called when the user submits a phone number to add to the riding group.
     *
     * Persists the member via [GroupRepository.addByPhone] on [viewModelScope],
     * then emits [RidersEvent.InviteSent] for the Composable to show a toast.
     *
     * @param phone The phone number entered by the user.
     */
    fun onAddByPhone(phone: String) {
        viewModelScope.launch {
            groupRepository.addByPhone(phone)
            _events.send(RidersEvent.InviteSent)
        }
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private fun buildUiState(
        members: List<GroupMember>,
        feed: List<FeedEvent>,
        waves: List<Wave>,
        isOnline: Boolean,
        settings: AppSettings,
    ): RidersUiState {
        val feedAvailable = isOnline && !settings.offlineOnly
        return RidersUiState(
            members = members.map { it.toUi() },
            memberCount = members.size,
            feedAvailable = feedAvailable,
            feed = feed.map { it.toUi() },
            waves = waves.map { it.toUi() },
        )
    }

    private fun GroupMember.toUi() = GroupMemberUi(
        initial = name.take(1).uppercase(),
        name = name,
        phone = phone,
        bikeName = bikeName,
    )

    private fun FeedEvent.toUi(): FeedEventUi {
        val dotColor = when (type) {
            FeedType.START  -> FeedDotColor.ACCENT
            FeedType.MAX    -> FeedDotColor.ACCENT2
            FeedType.FINISH -> FeedDotColor.DIM
        }
        return FeedEventUi(
            who = who,
            value = value,
            isMax = type == FeedType.MAX,
            bikeName = bikeName,
            timeLabel = timeLabel,
            dotColor = dotColor,
            type = type,
        )
    }

    private fun Wave.toUi() = WaveUi(
        nick = nick,
        bikeName = bikeName,
        place = place,
        timeLabel = timeLabel,
    )
}
