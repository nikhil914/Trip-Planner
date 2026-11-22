package app.tripplanner.fieldguide.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.tripplanner.fieldguide.BuildConfig
import app.tripplanner.fieldguide.FieldGuideApp
import app.tripplanner.fieldguide.briefing.BriefingScheduler
import app.tripplanner.fieldguide.data.AttachmentEntity
import app.tripplanner.fieldguide.data.BlockWithContext
import app.tripplanner.fieldguide.data.Category
import app.tripplanner.fieldguide.data.ContextualItemEntity
import app.tripplanner.fieldguide.data.PairPhase
import app.tripplanner.fieldguide.data.ShiftPreview
import app.tripplanner.fieldguide.data.Status
import app.tripplanner.fieldguide.data.ThemeMode
import app.tripplanner.fieldguide.data.TimelineBlockEntity
import app.tripplanner.fieldguide.data.TripDayEntity
import app.tripplanner.fieldguide.data.TripEntity
import app.tripplanner.fieldguide.logic.PlanBlock
import app.tripplanner.fieldguide.logic.Slot
import app.tripplanner.fieldguide.logic.Stay
import app.tripplanner.fieldguide.logic.TripList
import app.tripplanner.fieldguide.logic.collidingIds
import app.tripplanner.fieldguide.logic.minuteOfDay
import app.tripplanner.fieldguide.logic.parseList
import app.tripplanner.fieldguide.logic.stays
import app.tripplanner.fieldguide.logic.todayIso
import app.tripplanner.fieldguide.logic.zoneOrDefault
import app.tripplanner.fieldguide.sync.HttpStatusException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FileUi(val attachment: AttachmentEntity, val ready: Boolean)

/** One block with everything a screen needs: the row, its parsed notes and flags, linked items and files. */
data class BlockUi(
    val e: TimelineBlockEntity,
    val plan: PlanBlock,
    val items: List<ContextualItemEntity>,
    val files: List<FileUi>,
    val day: TripDayEntity?,
) {
    val id: String get() = e.id
    val hasPin: Boolean get() = e.lat != null && e.lng != null
}

data class DayUi(val day: TripDayEntity, val blocks: List<BlockUi>, val prep: List<ContextualItemEntity>) {
    val plans: List<PlanBlock> get() = blocks.map { it.plan }
    fun isToday(): Boolean = day.dateIso == todayIso(zoneOrDefault(day.timezone))
    fun nowMinute(): Int = minuteOfDay(day.dateIso, zoneOrDefault(day.timezone))
}

enum class TripPhase { PRE, DURING, POST }

data class TripUi(
    val loaded: Boolean = false,
    val trip: TripEntity? = null,
    val days: List<DayUi> = emptyList(),
    val unscheduled: List<BlockUi> = emptyList(),
    val lists: List<TripList> = emptyList(),
    val stays: List<Stay> = emptyList(),
    val colliding: Set<String> = emptySet(),
    val byId: Map<String, BlockUi> = emptyMap(),
) {
    val scheduled: List<BlockUi> get() = days.flatMap { it.blocks }
    val files: List<FileUi> get() = byId.values.flatMap { it.files }

    fun today(): DayUi? = days.firstOrNull { it.isToday() }

    fun phase(): TripPhase {
        if (days.isEmpty()) return TripPhase.PRE
        if (today() != null) return TripPhase.DURING
        val first = days.first().day
        return if (todayIso(zoneOrDefault(first.timezone)) < first.dateIso) TripPhase.PRE else TripPhase.POST
    }

    fun dayOf(id: String?): DayUi? = days.firstOrNull { it.day.id == id }
}

enum class SyncRun { IDLE, SYNCING, FAILED }

data class SyncUi(val run: SyncRun = SyncRun.IDLE, val message: String? = null)

data class Notice(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null)

data class PairingOffer(val serverUrl: String, val code: String)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as FieldGuideApp).graph
    private val dao = graph.db.itineraryDao()
    private val repo = graph.repository
    private val prefs = graph.uiPrefs

    val session = graph.session.state
    val settings = prefs.settings
    val ticks = prefs.ticks
    val pairingOffer = MutableStateFlow<PairingOffer?>(null)
    val pairPhase = MutableStateFlow<PairPhase?>(null)
    val sync = MutableStateFlow(SyncUi())
    val notices = MutableSharedFlow<Notice>(extraBufferCapacity = 4)
    val outboxSize: StateFlow<Int> = dao.observeOutboxSize().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Ticks every 30 seconds so the now line, countdowns and "today" stay current. */
    val now: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    val trip: StateFlow<TripUi> = session
        .map { it.tripId }
        .distinctUntilChanged()
        .flatMapLatest { tripId ->
            if (tripId == null) flowOf(TripUi(loaded = true))
            else combine(dao.observeTrip(tripId), dao.observeDays(tripId), dao.observeBlocks(tripId), dao.observeItems(tripId)) { t, d, b, i ->
                build(t, d, b, i)
            }.flowOn(Dispatchers.Default)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripUi())

    // ---- App lock ----

    val locked = MutableStateFlow(prefs.settings.value.lockEnabled)
    private var backgroundAt = 0L

    fun onBackground() {
        backgroundAt = System.currentTimeMillis()
    }

    fun onForeground() {
        val s = prefs.settings.value
        if (s.lockEnabled && backgroundAt > 0 && System.currentTimeMillis() - backgroundAt >= s.lockAfterMinutes * 60_000L) {
            locked.value = true
        }
        backgroundAt = 0
    }

    fun unlocked() {
        locked.value = false
    }

    init {
        viewModelScope.launch { graph.notices.collect { notices.emit(Notice(it)) } }
    }

    private fun build(trip: TripEntity?, days: List<TripDayEntity>, rows: List<BlockWithContext>, items: List<ContextualItemEntity>): TripUi {
        val dayById = days.associateBy { it.id }
        val vault = graph.vault
        val blocks = rows.map { r ->
            val b = r.block
            val day = b.dayId?.let(dayById::get)
            val plan = PlanBlock(
                id = b.id, dayId = b.dayId, dateIso = day?.dateIso, startMinute = b.startMinute,
                durationMinutes = b.durationMinutes, isFixed = b.isFixedTime, blockType = b.blockType, title = b.title,
                notes = b.subtitleOrTip, bookingRef = b.bookingRef, costLocal = b.estimatedCostLocal, status = b.status,
            )
            BlockUi(b, plan, r.items, r.attachments.map { FileUi(it, vault.isReady(it)) }, day)
        }
        val scheduled = blocks.filter { it.day != null && it.e.startMinute != null }
        val dayUis = days.sortedBy { it.dayNumber }.map { d ->
            DayUi(
                day = d,
                blocks = scheduled.filter { it.e.dayId == d.id }.sortedWith(compareBy({ it.e.startMinute }, { it.e.sortOrder })),
                prep = items.filter { it.dayId == d.id && it.category == Category.PREP },
            )
        }
        val unscheduled = blocks.filter { it.day == null || it.e.startMinute == null }.sortedBy { it.e.sortOrder }
        val lists = unscheduled.filter { !it.e.subtitleOrTip.isNullOrBlank() }.map { parseList(it.id, it.e.title, it.e.subtitleOrTip) }
        val colliding = dayUis.flatMap { d ->
            collidingIds(d.blocks.mapNotNull { r -> r.e.startMinute?.let { Slot(r.id, it, r.e.durationMinutes, r.e.isFixedTime, r.e.status == Status.PENDING) } })
        }.toSet()
        return TripUi(
            loaded = true,
            trip = trip,
            days = dayUis,
            unscheduled = unscheduled,
            lists = lists,
            stays = stays(dayUis.flatMap { it.plans }),
            colliding = colliding,
            byId = blocks.associateBy { it.id },
        )
    }

    private fun say(text: String) {
        notices.tryEmit(Notice(text))
    }

    private fun describe(e: Throwable): String = when {
        e is HttpStatusException && e.code == 404 -> "That pairing code is not valid."
        e is HttpStatusException && e.code == 410 -> "This code has expired. Make a new one in the planner."
        e is HttpStatusException && e.code == 429 -> "Too many attempts. Wait a few minutes."
        e is HttpStatusException && e.tokenRejected -> "This phone was removed from the trip in the planner. Pair again to sync."
        e is HttpStatusException && (e.code == 401 || e.code == 403) && e.error == null ->
            "The server's host blocked the request (${e.code}). If it runs on Vercel, turn off Deployment Protection."
        e is HttpStatusException -> "The planner answered with an error (${e.code})."
        e is IOException -> "Couldn't reach the planner. Your plan still works offline."
        else -> "Something went wrong: ${e.message ?: e.javaClass.simpleName}"
    }

    // ---- Pairing ----

    fun offerPairing(serverUrl: String?, code: String?) {
        if (!serverUrl.isNullOrBlank() && !code.isNullOrBlank()) pairingOffer.value = PairingOffer(serverUrl, code)
    }

    /** Returns an error message for the form, or null when pairing started. */
    fun pair(serverUrl: String, code: String): String? {
        val url = serverUrl.trim().trimEnd('/')
        val digits = code.trim()
        val secure = url.startsWith("https://") || (BuildConfig.DEBUG && url.startsWith("http://"))
        if (!secure) return "The planner address must start with https://"
        if (digits.length != 6 || digits.any { !it.isDigit() }) return "The pairing code is 6 digits."
        if (pairPhase.value != null) return null
        pairPhase.value = PairPhase.CONNECTING
        viewModelScope.launch {
            try {
                repo.pair(url, digits, "${Build.MANUFACTURER} ${Build.MODEL}", BuildConfig.VERSION_NAME) { pairPhase.value = it }
                pairingOffer.value = null
                prefs.clearTicks()
                sync.value = SyncUi()
            } catch (e: Exception) {
                say(if (e is IOException) "Pairing needs internet once. After that the trip works offline." else describe(e))
            } finally {
                pairPhase.value = null
            }
        }
        return null
    }

    /** Opens the pairing form over the current trip. Pairing replaces it; cancelling keeps it. */
    fun pairAnother() {
        pairingOffer.value = PairingOffer(session.value.serverUrl.orEmpty(), "")
    }

    fun cancelOffer() {
        pairingOffer.value = null
    }

    fun loadDemo() {
        viewModelScope.launch {
            repo.loadDemo()
            prefs.clearTicks()
            pairingOffer.value = null
        }
    }

    fun unpair() {
        viewModelScope.launch {
            repo.unpair()
            prefs.clearTicks()
            prefs.setPrimerDone(false)
            sync.value = SyncUi()
        }
    }

    // ---- Sync ----

    /** Pull on launch when paired. Failures stay quiet because offline is the normal case. */
    fun onAppOpened() {
        val s = session.value
        val tripId = s.tripId ?: return
        viewModelScope.launch {
            if (s.canSync) {
                try {
                    repo.pull()
                    sync.value = SyncUi()
                } catch (e: Exception) {
                    if (e is HttpStatusException && e.tokenRejected) {
                        graph.session.markRevoked()
                        say(describe(e))
                    }
                }
            }
            BriefingScheduler.armNext(getApplication(), dao, tripId)
        }
    }

    fun refresh() {
        if (sync.value.run == SyncRun.SYNCING) return
        if (!session.value.canSync) {
            return say(if (session.value.demo) "The demo trip does not sync." else "Pair again to sync.")
        }
        sync.value = SyncUi(SyncRun.SYNCING)
        viewModelScope.launch {
            try {
                val failed = repo.pull()
                sync.value = SyncUi()
                say(if (failed > 0) "Synced, but $failed ${if (failed == 1) "file" else "files"} could not be downloaded." else "Up to date.")
            } catch (e: Exception) {
                if (e is HttpStatusException && e.tokenRejected) graph.session.markRevoked()
                sync.value = SyncUi(SyncRun.FAILED, describe(e))
            }
        }
    }

    fun setWifiOnly(value: Boolean) = graph.session.setWifiOnly(value)

    // ---- Settings ----

    fun setTheme(mode: ThemeMode) = prefs.setTheme(mode)
    fun setLock(enabled: Boolean) {
        prefs.setLock(enabled)
        if (!enabled) locked.value = false
    }
    fun setLockAfter(minutes: Int) = prefs.setLockAfter(minutes)
    fun finishPrimer() = prefs.setPrimerDone(true)

    // ---- The three phone writes ----

    fun checkItem(id: String, checked: Boolean) {
        viewModelScope.launch { repo.checkItem(id, checked) }
    }

    fun markBlock(id: String, status: String, undoable: Boolean = false) {
        val before = trip.value.byId[id]?.e?.status ?: Status.PENDING
        viewModelScope.launch {
            repo.markBlock(id, status)
            if (undoable && before != status) {
                val title = trip.value.byId[id]?.plan?.displayTitle.orEmpty()
                val verb = if (status == Status.DONE) "Done" else if (status == Status.SKIPPED) "Skipped" else "Reopened"
                notices.emit(Notice("$verb: $title", "Undo") { markBlock(id, before) })
            }
        }
    }

    /** Ticks on list lines stay on this phone. */
    fun tick(blockId: String, key: String, ticked: Boolean) = prefs.setTick("$blockId|$key", ticked)

    suspend fun previewShift(dayId: String, delta: Int): ShiftPreview? {
        val day = trip.value.dayOf(dayId)?.day ?: return null
        return repo.previewDelay(day, delta)
    }

    fun shift(dayId: String, delta: Int) {
        val day = trip.value.dayOf(dayId)?.day ?: return
        viewModelScope.launch {
            val moved = repo.delay(day, delta)
            if (moved == 0) {
                say("Everything left today is a fixed booking.")
            } else {
                notices.emit(
                    Notice("Moved $moved ${if (moved == 1) "plan" else "plans"} by $delta min", "Undo") {
                        viewModelScope.launch { repo.delay(day, -delta) }
                    },
                )
            }
        }
    }
}
