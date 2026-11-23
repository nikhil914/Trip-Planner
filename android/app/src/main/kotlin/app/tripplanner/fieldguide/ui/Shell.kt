package app.tripplanner.fieldguide.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tripplanner.fieldguide.data.SessionState
import app.tripplanner.fieldguide.logic.dayLabel
import app.tripplanner.fieldguide.logic.hhmm
import app.tripplanner.fieldguide.logic.utcOffset
import app.tripplanner.fieldguide.logic.zoneCity
import app.tripplanner.fieldguide.logic.zoneOrDefault
import java.time.Instant
import java.time.ZoneId

enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Rounded.Today),
    TRIP("Trip", Icons.Rounded.Explore),
    LISTS("Lists", Icons.Rounded.CheckBox),
    WALLET("Wallet", Icons.Rounded.AccountBalanceWallet),
}

sealed interface Route {
    data class Day(val dayId: String) : Route
    data class Block(val id: String) : Route
    data class ListDetail(val blockId: String) : Route
    data object Stays : Route
    data object Money : Route
    data object Safety : Route
    data object Settings : Route
}

/** A card for a stranger to read. */
data class LocalCard(val local: String?, val phonetic: String?, val english: String, val note: String? = null)

sealed interface Modal {
    data class ShowLocal(val cards: List<LocalCard>, val start: Int = 0) : Modal
    data class Ticket(val files: List<FileUi>, val titles: List<String>, val start: Int = 0) : Modal
    data class Counter(val blockId: String) : Modal
}

sealed interface Sheet {
    data class RunningLate(val dayId: String) : Sheet
    data object Sync : Sheet
}

/** A small back stack. A navigation library is not worth its size for eleven screens. */
@Stable
class Nav {
    var tab by mutableStateOf(Tab.TODAY)
    val stack = mutableStateListOf<Route>()
    var modal by mutableStateOf<Modal?>(null)
    var sheet by mutableStateOf<Sheet?>(null)

    val top: Route? get() = stack.lastOrNull()

    fun push(route: Route) {
        if (stack.lastOrNull() != route) stack.add(route)
    }

    fun selectTab(t: Tab) {
        stack.clear()
        tab = t
    }

    /** Modals first, then sheets, then pushed screens, then back to Today. False when the app should exit. */
    fun back(): Boolean = when {
        modal != null -> { modal = null; true }
        sheet != null -> { sheet = null; true }
        stack.isNotEmpty() -> { stack.removeAt(stack.lastIndex); true }
        tab != Tab.TODAY -> { tab = Tab.TODAY; true }
        else -> false
    }

    val canGoBack: Boolean get() = modal != null || sheet != null || stack.isNotEmpty() || tab != Tab.TODAY
}

val LocalNav = staticCompositionLocalOf<Nav> { error("No Nav") }

@Composable
fun Shell(vm: AppViewModel) {
    val nav = remember { Nav() }
    val snackbar = remember { SnackbarHostState() }
    val trip by vm.trip.collectAsStateWithLifecycle()
    vm.now.collectAsStateWithLifecycle().value // recompose on the clock tick

    LaunchedEffect(Unit) {
        vm.notices.collect { n ->
            val result = snackbar.showSnackbar(n.text, actionLabel = n.action, duration = if (n.action != null) SnackbarDuration.Long else SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) n.onAction?.invoke()
        }
    }

    BackHandler(enabled = nav.canGoBack) { nav.back() }

    androidx.compose.runtime.CompositionLocalProvider(LocalNav provides nav) {
        val route = nav.top
        Scaffold(
            containerColor = fg.background,
            topBar = { ShellTopBar(vm, nav, trip, route) },
            bottomBar = { if (route == null) BottomBar(nav) },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (route) {
                    null -> when (nav.tab) {
                        Tab.TODAY -> TodayTab(vm, trip)
                        Tab.TRIP -> TripTab(vm, trip)
                        Tab.LISTS -> ListsTab(vm, trip)
                        Tab.WALLET -> WalletTab(vm, trip)
                    }
                    is Route.Day -> DayScreen(vm, trip, route.dayId)
                    is Route.Block -> BlockDetailScreen(vm, trip, route.id)
                    is Route.ListDetail -> ListDetailScreen(vm, trip, route.blockId)
                    Route.Stays -> StaysScreen(trip)
                    Route.Money -> MoneyScreen(trip)
                    Route.Safety -> SafetyScreen(trip)
                    Route.Settings -> SettingsScreen(vm)
                }
            }
        }

        when (val s = nav.sheet) {
            is Sheet.RunningLate -> RunningLateSheet(vm, trip, s.dayId) { nav.sheet = null }
            Sheet.Sync -> SyncSheet(vm, trip) { nav.sheet = null }
            null -> Unit
        }
        when (val m = nav.modal) {
            is Modal.ShowLocal -> ShowLocalModal(m) { nav.modal = null }
            is Modal.Ticket -> TicketModal(m) { nav.modal = null }
            is Modal.Counter -> trip.byId[m.blockId]?.let { CounterModal(it, trip) { nav.modal = null } }
            null -> Unit
        }
    }
}

private fun routeTitle(route: Route, trip: TripUi): Pair<String, String?> = when (route) {
    is Route.Day -> trip.dayOf(route.dayId)?.let { "Day ${it.day.dayNumber} · ${dayLabel(it.day.dateIso)}" to zoneLine(it.day.timezone, it.day.dateIso) } ?: ("Day" to null)
    is Route.Block -> (trip.byId[route.id]?.plan?.displayTitle ?: "Plan") to null
    is Route.ListDetail -> (trip.lists.firstOrNull { it.blockId == route.blockId }?.title ?: "List") to null
    Route.Stays -> "Stays" to null
    Route.Money -> "Money" to null
    Route.Safety -> "Safety and emergency" to null
    Route.Settings -> "Settings" to null
}

/** "Tbilisi time · UTC+4", or null when the phone is already on that time. */
fun zoneLine(zoneId: String, dateIso: String): String? {
    val zone = zoneOrDefault(zoneId)
    val phone = ZoneId.systemDefault()
    val same = zone.rules.getOffset(Instant.now()) == phone.rules.getOffset(Instant.now())
    return "${zoneCity(zoneId)} time · ${utcOffset(zone, dateIso)}" + if (same) "" else " · not phone time"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShellTopBar(vm: AppViewModel, nav: Nav, trip: TripUi, route: Route?) {
    val session by vm.session.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val outbox by vm.outboxSize.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }

    val (title, subtitle) = when {
        route != null -> routeTitle(route, trip)
        nav.tab == Tab.TODAY -> trip.today()?.let { "Day ${it.day.dayNumber} · ${dayLabel(it.day.dateIso)}" to zoneLine(it.day.timezone, it.day.dateIso) }
            ?: ((trip.trip?.title ?: "Today") to null)
        nav.tab == Tab.TRIP -> (trip.trip?.title ?: "Trip") to null
        nav.tab == Tab.LISTS -> "Lists" to null
        else -> "Wallet" to null
    }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = fg.background, titleContentColor = fg.text),
        navigationIcon = {
            if (route != null) {
                IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = fg.text) }
            }
        },
        title = {
            Column {
                Text(title, style = FgType.cardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, style = FgType.meta, color = fg.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        },
        actions = {
            SyncChip(session, sync, outbox) { nav.sheet = Sheet.Sync }
            IconButton(onClick = { nav.push(Route.Safety) }) {
                Icon(Icons.Rounded.Shield, contentDescription = "Safety and emergency", tint = fg.danger)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = fg.text) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; nav.push(Route.Settings) })
                    DropdownMenuItem(text = { Text("Money and converter") }, onClick = { menu = false; nav.push(Route.Money) })
                    DropdownMenuItem(text = { Text("Sync now") }, onClick = { menu = false; vm.refresh() })
                }
            }
        },
    )
}

/** Quiet offline-first status: when the plan was last updated, amber when older than a day. */
@Composable
private fun SyncChip(session: SessionState, sync: SyncUi, outbox: Int, onClick: () -> Unit) {
    val stale = session.lastSyncAt > 0 && System.currentTimeMillis() - session.lastSyncAt > 24 * 3_600_000L
    val (label, tone, icon) = when {
        session.demo -> Triple("Demo", Tone.NEUTRAL, Icons.Rounded.CloudOff)
        session.revoked -> Triple("Unpaired", Tone.DANGER, Icons.Rounded.CloudOff)
        sync.run == SyncRun.SYNCING -> Triple("Syncing", Tone.PRIMARY, Icons.Rounded.Sync)
        sync.run == SyncRun.FAILED -> Triple("Offline" + lastTime(session.lastSyncAt), Tone.NEUTRAL, Icons.Rounded.CloudOff)
        stale -> Triple("Updated" + lastTime(session.lastSyncAt), Tone.WARNING, Icons.Rounded.CloudOff)
        else -> Triple(if (session.lastSyncAt > 0) "Updated" + lastTime(session.lastSyncAt) else "On phone", Tone.NEUTRAL, Icons.Rounded.CloudDone)
    }
    val (ink, bg) = toneColors(tone)
    val waiting = if (outbox > 0) " · $outbox" else ""
    Row(
        Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(18.dp)).background(bg).clickable(onClick = onClick)
            .padding(horizontal = 10.dp).semantics { contentDescription = "Sync status: $label${if (outbox > 0) ", $outbox changes waiting" else ""}. Opens sync details." },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label + waiting, style = FgType.chip, color = ink, maxLines = 1)
    }
}

private fun lastTime(millis: Long): String {
    if (millis <= 0) return ""
    val zone = ZoneId.systemDefault()
    val then = Instant.ofEpochMilli(millis).atZone(zone)
    val today = Instant.now().atZone(zone).toLocalDate()
    return if (then.toLocalDate() == today) " ${(then.hour * 60 + then.minute).hhmm()}" else " ${dayLabel(then.toLocalDate().toString()).substringAfter(' ')}"
}

@Composable
private fun BottomBar(nav: Nav) {
    NavigationBar(containerColor = fg.card) {
        Tab.entries.forEach { t ->
            NavigationBarItem(
                selected = nav.tab == t,
                onClick = { nav.selectTab(t) },
                icon = { Icon(t.icon, contentDescription = null) },
                label = { Text(t.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = fg.primary, selectedTextColor = fg.primary,
                    unselectedIconColor = fg.textSecondary, unselectedTextColor = fg.textSecondary,
                ),
            )
        }
    }
}
