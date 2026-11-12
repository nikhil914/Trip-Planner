package app.tripplanner.fieldguide.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.tripplanner.fieldguide.BuildConfig
import app.tripplanner.fieldguide.FieldGuideApp
import app.tripplanner.fieldguide.briefing.BriefingScheduler
import app.tripplanner.fieldguide.data.BlockWithContext
import app.tripplanner.fieldguide.data.ContextualItemEntity
import app.tripplanner.fieldguide.data.ShiftPreview
import app.tripplanner.fieldguide.data.Status
import app.tripplanner.fieldguide.data.TripDayEntity
import app.tripplanner.fieldguide.data.TripEntity
import app.tripplanner.fieldguide.logic.Slot
import app.tripplanner.fieldguide.logic.collidingIds
import app.tripplanner.fieldguide.logic.todayIso
import app.tripplanner.fieldguide.logic.zoneOrDefault
import app.tripplanner.fieldguide.sync.HttpStatusException
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TodayState(
    val loaded: Boolean = false,
    val trip: TripEntity? = null,
    val days: List<TripDayEntity> = emptyList(),
    val day: TripDayEntity? = null,
    val isToday: Boolean = false,
    val agenda: List<BlockWithContext> = emptyList(),
    val prep: List<ContextualItemEntity> = emptyList(),
    val firstDeparture: Int? = null,
    val spendLocal: Double = 0.0,
    val colliding: Set<String> = emptySet(),
)

data class PairingOffer(val serverUrl: String, val code: String)

data class PendingShift(val delta: Int, val preview: ShiftPreview)

private data class DayPick(val trip: TripEntity?, val days: List<TripDayEntity>, val day: TripDayEntity?, val isToday: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as FieldGuideApp).graph
    private val dao = graph.db.itineraryDao()
    private val repo = graph.repository

    val session = graph.session.state
    val pairingOffer = MutableStateFlow<PairingOffer?>(null)
    val busy = MutableStateFlow(false)
    val refreshing = MutableStateFlow(false)
    val pendingShift = MutableStateFlow<PendingShift?>(null)
    val lastShift = MutableStateFlow<Int?>(null)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val outboxSize: StateFlow<Int> = dao.observeOutboxSize().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val selectedDayId = MutableStateFlow<String?>(null)

    /** Bumped when the app comes to the foreground, so "today" is re-evaluated after midnight. */
    private val clock = MutableStateFlow(0L)

    val today: StateFlow<TodayState> = session
        .map { it.tripId }
        .distinctUntilChanged()
        .flatMapLatest { tripId ->
            if (tripId == null) flowOf(TodayState(loaded = true))
            else combine(dao.observeTrip(tripId), dao.observeDays(tripId), selectedDayId, clock) { trip, days, selected, _ ->
                pickDay(trip, days, selected)
            }.flatMapLatest(::dayState)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    init {
        viewModelScope.launch { graph.notices.collect { messages.emit(it) } }
    }

    private fun pickDay(trip: TripEntity?, days: List<TripDayEntity>, selectedId: String?): DayPick {
        // "Today" is decided in each day's own timezone, never the phone's.
        val isToday = { d: TripDayEntity -> d.dateIso == todayIso(zoneOrDefault(d.timezone)) }
        val day = days.firstOrNull { it.id == selectedId }
            ?: days.firstOrNull(isToday)
            ?: days.firstOrNull { it.dateIso > todayIso(zoneOrDefault(it.timezone)) }
            ?: days.lastOrNull()
        return DayPick(trip, days, day, day?.let(isToday) ?: false)
    }

    private fun dayState(pick: DayPick): Flow<TodayState> {
        val base = TodayState(loaded = true, trip = pick.trip, days = pick.days, day = pick.day, isToday = pick.isToday)
        val day = pick.day ?: return flowOf(base)
        return combine(
            dao.observeAgenda(day.id),
            dao.observePrep(day.id),
            dao.observeFirstDeparture(day.id),
            dao.observeDaySpend(day.id),
        ) { agenda, prep, departure, spend ->
            val slots = agenda.mapNotNull { row ->
                val b = row.block
                b.startMinute?.let { Slot(b.id, it, b.durationMinutes, b.isFixedTime, b.status == Status.PENDING) }
            }
            base.copy(agenda = agenda, prep = prep, firstDeparture = departure, spendLocal = spend, colliding = collidingIds(slots))
        }
    }

    private fun say(text: String) {
        messages.tryEmit(text)
    }

    private fun describe(e: Throwable): String = when {
        e is HttpStatusException && e.code == 404 -> "That pairing code is not valid."
        e is HttpStatusException && e.code == 410 -> "That pairing code has expired. Create a new one on the web."
        e is HttpStatusException && e.code == 429 -> "Too many attempts. Wait a few minutes."
        e is HttpStatusException && e.code == 401 -> "This phone was unpaired on the web. Pair again in Settings."
        e is HttpStatusException -> "The server answered with an error (${e.code})."
        e is IOException -> "Could not reach the server. Your trip is still available offline."
        else -> "Something went wrong: ${e.message ?: e.javaClass.simpleName}"
    }

    // ---- Pairing ----

    fun offerPairing(serverUrl: String?, code: String?) {
        if (!serverUrl.isNullOrBlank() && !code.isNullOrBlank()) pairingOffer.value = PairingOffer(serverUrl, code)
    }

    fun pair(serverUrl: String, code: String) {
        val url = serverUrl.trim().trimEnd('/')
        val digits = code.trim()
        val secure = url.startsWith("https://") || (BuildConfig.DEBUG && url.startsWith("http://"))
        if (!secure) return say("The server address must start with https://")
        if (digits.length != 6 || digits.any { !it.isDigit() }) return say("The pairing code is 6 digits.")
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                repo.pair(url, digits, "${Build.MANUFACTURER} ${Build.MODEL}", BuildConfig.VERSION_NAME)
                pairingOffer.value = null
                selectedDayId.value = null
            } catch (e: Exception) {
                say(describe(e))
            } finally {
                busy.value = false
            }
        }
    }

    fun loadDemo() {
        viewModelScope.launch {
            repo.loadDemo()
            pairingOffer.value = null
            selectedDayId.value = null
        }
    }

    fun unpair() {
        viewModelScope.launch {
            repo.unpair()
            selectedDayId.value = null
            lastShift.value = null
        }
    }

    // ---- Sync ----

    /** Pull on launch when a server is paired. Failures stay quiet because offline is the normal case. */
    fun onAppOpened() {
        clock.value = System.currentTimeMillis()
        val s = session.value
        val tripId = s.tripId ?: return
        viewModelScope.launch {
            if (s.canSync) {
                try {
                    repo.pull()
                } catch (e: Exception) {
                    if (e is HttpStatusException && e.code == 401) {
                        graph.session.markRevoked()
                        say(describe(e))
                    }
                }
            }
            BriefingScheduler.armNext(getApplication(), dao, tripId)
        }
    }

    fun refresh() {
        if (refreshing.value) return
        if (!session.value.canSync) {
            return say(if (session.value.demo) "The demo trip does not sync." else "Pair again in Settings to sync.")
        }
        refreshing.value = true
        viewModelScope.launch {
            try {
                val failed = repo.pull()
                say(if (failed > 0) "Synced, but $failed ${if (failed == 1) "ticket" else "tickets"} could not be downloaded." else "Up to date.")
            } catch (e: Exception) {
                if (e is HttpStatusException && e.code == 401) graph.session.markRevoked()
                say(describe(e))
            } finally {
                refreshing.value = false
            }
        }
    }

    fun setWifiOnly(value: Boolean) = graph.session.setWifiOnly(value)

    // ---- Day actions ----

    fun stepDay(offset: Int) {
        val state = today.value
        val index = state.days.indexOfFirst { it.id == state.day?.id }
        state.days.getOrNull(index + offset)?.let {
            selectedDayId.value = it.id
            lastShift.value = null
        }
    }

    fun checkItem(id: String, checked: Boolean) {
        viewModelScope.launch { repo.checkItem(id, checked) }
    }

    fun markBlock(id: String, status: String) {
        viewModelScope.launch { repo.markBlock(id, status) }
    }

    /** Checks for collisions first. A clean shift applies at once; otherwise the user confirms. */
    fun requestShift(delta: Int) {
        val day = today.value.day ?: return
        viewModelScope.launch {
            val preview = repo.previewDelay(day, delta)
            if (preview.isClean) applyShift(day, delta) else pendingShift.value = PendingShift(delta, preview)
        }
    }

    fun confirmShift() {
        val pending = pendingShift.value ?: return
        val day = today.value.day ?: return
        pendingShift.value = null
        viewModelScope.launch { applyShift(day, pending.delta) }
    }

    fun cancelShift() {
        pendingShift.value = null
    }

    fun undoShift() {
        val delta = lastShift.value ?: return
        val day = today.value.day ?: return
        viewModelScope.launch {
            repo.delay(day, -delta)
            lastShift.value = null
        }
    }

    private suspend fun applyShift(day: TripDayEntity, delta: Int) {
        val moved = repo.delay(day, delta)
        if (moved == 0) {
            say("Nothing left to move today.")
        } else {
            lastShift.value = delta
            say("Moved $moved ${if (moved == 1) "block" else "blocks"} by $delta min.")
        }
    }
}
