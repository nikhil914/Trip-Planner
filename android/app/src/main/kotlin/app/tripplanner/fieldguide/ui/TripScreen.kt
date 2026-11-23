package app.tripplanner.fieldguide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tripplanner.fieldguide.logic.BlockKind
import app.tripplanner.fieldguide.logic.Health
import app.tripplanner.fieldguide.logic.bedTonight
import app.tripplanner.fieldguide.logic.dateRange
import app.tripplanner.fieldguide.logic.dayLabel
import app.tripplanner.fieldguide.logic.daysBetween
import app.tripplanner.fieldguide.logic.health
import app.tripplanner.fieldguide.logic.money
import app.tripplanner.fieldguide.logic.routeStrip
import app.tripplanner.fieldguide.logic.shortDate
import app.tripplanner.fieldguide.logic.todayIso
import app.tripplanner.fieldguide.logic.zoneOrDefault

private enum class HealthFilter { ALL, BOOKED, NOT_BOOKED, INCOMPLETE }

// ---------- S10 Trip overview ----------

@Composable
fun TripTab(vm: AppViewModel, trip: TripUi) {
    val nav = LocalNav.current
    val t = trip.trip ?: return
    var filter by rememberSaveable { mutableStateOf(HealthFilter.ALL) }
    val all = trip.scheduled.map { it.plan }
    val booked = all.count { health(it) == Health.BOOKED }
    val notBooked = all.count { health(it) == Health.NOT_BOOKED }
    val incomplete = all.count { health(it) == Health.INCOMPLETE }
    val first = trip.days.firstOrNull()
    val status = when (trip.phase()) {
        TripPhase.PRE -> first?.let { "Starts in ${daysBetween(todayIso(zoneOrDefault(it.day.timezone)), it.day.dateIso)} days" }
        TripPhase.DURING -> trip.today()?.let { "Day ${it.day.dayNumber} of ${trip.days.size}" }
        TripPhase.POST -> "Trip finished"
    }

    LazyColumn(contentPadding = PaddingValues(FgDims.margin), verticalArrangement = Arrangement.spacedBy(FgDims.gap)) {
        item {
            FgCard {
                Text(t.title, style = FgType.hero, color = fg.text)
                Text(listOfNotNull("${dateRange(t.startDate, t.endDate)} · ${trip.days.size} days", status).joinToString(" · "), style = FgType.body, color = fg.textSecondary)
                RouteStrip(trip)
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = filter == HealthFilter.ALL, onClick = { filter = HealthFilter.ALL }, label = { Text("All days") }) }
                item { FilterChip(selected = filter == HealthFilter.BOOKED, onClick = { filter = HealthFilter.BOOKED }, label = { Text("Booked $booked") }) }
                item {
                    FilterChip(
                        selected = filter == HealthFilter.NOT_BOOKED, onClick = { filter = HealthFilter.NOT_BOOKED },
                        label = { Text("Not booked $notBooked") }, leadingIcon = { Icon(Icons.Rounded.Warning, null, tint = fg.warning, modifier = Modifier.size(16.dp)) },
                    )
                }
                item { FilterChip(selected = filter == HealthFilter.INCOMPLETE, onClick = { filter = HealthFilter.INCOMPLETE }, label = { Text("Incomplete $incomplete") }) }
            }
        }
        if (filter == HealthFilter.ALL) {
            items(trip.days, key = { it.day.id }) { d -> DayRow(trip, d) { nav.push(Route.Day(d.day.id)) } }
        } else {
            val want = when (filter) {
                HealthFilter.BOOKED -> Health.BOOKED
                HealthFilter.NOT_BOOKED -> Health.NOT_BOOKED
                else -> Health.INCOMPLETE
            }
            val matches = trip.scheduled.filter { health(it.plan) == want }
            if (matches.isEmpty()) item { EmptyState("Nothing here", "No plans match this filter.") }
            items(matches, key = { it.id }) { b ->
                FgCard(onClick = { nav.push(Route.Block(b.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KindBadge(b.plan.kind)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.plan.displayTitle, style = FgType.cardTitle, color = fg.text)
                            Text(listOfNotNull(b.day?.let { dayLabel(it.dateIso) }, b.plan.flags.incomplete).joinToString(" · "), style = FgType.meta, color = fg.textSecondary)
                        }
                    }
                }
            }
        }
        item { SectionTitle("More") }
        item { NavRow(Icons.Rounded.Hotel, "Stays", "${trip.stays.size} hotels · where you sleep each night") { nav.push(Route.Stays) } }
        item { NavRow(Icons.Rounded.CurrencyExchange, "Money", "Budget, spends, cash and converter") { nav.push(Route.Money) } }
        item { NavRow(Icons.Rounded.Settings, "Settings", "Lock, theme, briefing, sync") { nav.push(Route.Settings) } }
    }
}

@Composable
fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    FgCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = fg.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = FgType.cardTitle, color = fg.text)
                Text(subtitle, style = FgType.meta, color = fg.textSecondary)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = fg.textSecondary)
        }
    }
}

@Composable
private fun RouteStrip(trip: TripUi) {
    val stops = routeStrip(trip.scheduled.map { it.plan })
    if (stops.isEmpty()) return
    LazyRow(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(stops.size) { i ->
            val s = stops[i]
            Row(verticalAlignment = Alignment.CenterVertically) {
                s.via?.let {
                    Icon(kindIcon(it), null, tint = kindColor(it), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(s.name, style = FgType.chip, color = fg.text)
            }
        }
    }
}

@Composable
private fun DayRow(trip: TripUi, d: DayUi, onClick: () -> Unit) {
    val today = d.isToday()
    val warn = d.blocks.count { isProblem(it) }
    val theme = d.blocks.filter { it.plan.kind != BlockKind.CHECK_OUT && it.plan.kind != BlockKind.FOOD }.take(2).joinToString(", ") { it.plan.displayTitle }
    val bed = bedTonight(trip.stays, d.day.dateIso)
    FgCard(onClick = onClick, border = if (today) fg.primary else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(56.dp)) {
                Text(dayLabel(d.day.dateIso).substringBefore(' '), style = FgType.chip, color = fg.textSecondary)
                Text(shortDate(d.day.dateIso).substringBefore(' '), style = FgType.railTime, color = fg.text)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Day ${d.day.dayNumber}" + if (today) " · Today" else "",
                    style = FgType.chip, color = if (today) fg.primary else fg.textSecondary,
                )
                Text(theme.ifEmpty { "Free day" }, style = FgType.body, color = fg.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    d.blocks.filter { it.plan.kind in setOf(BlockKind.FLIGHT, BlockKind.TRAIN, BlockKind.CHECK_IN) }.forEach {
                        Icon(kindIcon(it.plan.kind), null, tint = kindColor(it.plan.kind), modifier = Modifier.size(16.dp))
                    }
                    if (warn > 0) StatusChip("Not booked", Tone.WARNING, Icons.Rounded.Warning)
                    bed?.let { Text(it.name, style = FgType.meta, color = fg.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

// ---------- S11 Stays ----------

@Composable
fun StaysScreen(trip: TripUi) {
    val nav = LocalNav.current
    val t = trip.trip
    val today = trip.today()?.day?.dateIso
    LazyColumn(contentPadding = PaddingValues(FgDims.margin), verticalArrangement = Arrangement.spacedBy(FgDims.gap)) {
        if (trip.stays.isEmpty()) item { EmptyState("No stays yet", "Hotel check-ins from the planner show here.") }
        items(trip.stays, key = { it.checkIn.id }) { s ->
            val b = trip.byId[s.checkIn.id] ?: return@items
            val tonight = today != null && s.covers(today)
            FgCard(onClick = { nav.modal = Modal.Counter(b.id) }, border = if (tonight) fg.stay else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KindBadge(BlockKind.CHECK_IN)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.name, style = FgType.cardTitle, color = fg.text)
                        val dates = s.toIso?.let { dateRange(s.fromIso, it) } ?: shortDate(s.fromIso)
                        Text(listOfNotNull(dates, s.nights?.let { "$it ${if (it == 1) "night" else "nights"}" }).joinToString(" · "), style = FgType.meta, color = fg.textSecondary)
                    }
                    if (tonight) StatusChip("Tonight", Tone.PRIMARY)
                }
                NightsBar(trip, s.fromIso, s.toIso)
                BlockChips(b, overlaps = false)
                if (t != null) b.e.estimatedCostLocal?.let { Text(money(it, t.exchangeRate, t.localCurrency, t.homeCurrency), style = FgType.body, color = fg.text) }
                Text("Tap for the booking card", style = FgType.meta, color = fg.textSecondary)
            }
        }
    }
}

@Composable
private fun NightsBar(trip: TripUi, fromIso: String, toIso: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        trip.days.forEach { d ->
            val inStay = d.day.dateIso >= fromIso && (toIso == null || d.day.dateIso < toIso)
            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (inStay) fg.stay else fg.cardBorder))
        }
    }
}
