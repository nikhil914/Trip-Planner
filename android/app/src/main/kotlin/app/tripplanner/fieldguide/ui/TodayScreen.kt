package app.tripplanner.fieldguide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tripplanner.fieldguide.data.ContextualItemEntity
import app.tripplanner.fieldguide.logic.BlockKind
import app.tripplanner.fieldguide.logic.Health
import app.tripplanner.fieldguide.logic.ListKind
import app.tripplanner.fieldguide.logic.ListTopic
import app.tripplanner.fieldguide.logic.TripList
import app.tripplanner.fieldguide.logic.bedTonight
import app.tripplanner.fieldguide.logic.carryToday
import app.tripplanner.fieldguide.logic.cashNeeded
import app.tripplanner.fieldguide.logic.dayLabel
import app.tripplanner.fieldguide.logic.daySpend
import app.tripplanner.fieldguide.logic.daysBetween
import app.tripplanner.fieldguide.logic.health
import app.tripplanner.fieldguide.logic.hhmm
import app.tripplanner.fieldguide.logic.humanDuration
import app.tripplanner.fieldguide.logic.leaveBy
import app.tripplanner.fieldguide.logic.money
import app.tripplanner.fieldguide.logic.nowNext
import app.tripplanner.fieldguide.logic.shortDate
import app.tripplanner.fieldguide.logic.todayIso
import app.tripplanner.fieldguide.logic.warningsToday
import app.tripplanner.fieldguide.logic.zoneOrDefault

@Composable
fun TodayTab(vm: AppViewModel, trip: TripUi) {
    if (!trip.loaded) return
    if (trip.trip == null || trip.days.isEmpty()) {
        EmptyState("No days yet", "Add days in the web planner, then pull down to sync.")
        return
    }
    when (trip.phase()) {
        TripPhase.PRE -> PreTripToday(vm, trip)
        TripPhase.DURING -> TripDayToday(vm, trip, trip.today()!!)
        TripPhase.POST -> PostTrip(vm, trip)
    }
}

/** Done count for a checklist: lines already done in the planner text plus ticks on this phone. */
fun listProgress(list: TripList, ticks: Set<String>): Pair<Int, Int> {
    val items = list.items
    return items.count { it.doneInSource || "${list.blockId}|${it.key}" in ticks } to items.size
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Refreshable(vm: AppViewModel, content: @Composable () -> Unit) {
    val sync by vm.sync.collectAsStateWithLifecycle()
    PullToRefreshBox(isRefreshing = sync.run == SyncRun.SYNCING, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) { content() }
}

// ---------- S3 pre-trip ----------

@Composable
private fun PreTripToday(vm: AppViewModel, trip: TripUi) {
    val nav = LocalNav.current
    val ticks by vm.ticks.collectAsStateWithLifecycle()
    val t = trip.trip!!
    val first = trip.days.first()
    val daysLeft = daysBetween(todayIso(zoneOrDefault(first.day.timezone)), first.day.dateIso)
    val firstBlock = first.blocks.firstOrNull { it.plan.kind == BlockKind.FLIGHT || it.plan.kind == BlockKind.TRAIN } ?: first.blocks.firstOrNull()
    val problems = trip.scheduled.filter { isProblem(it) && it.plan.isPending }
    val files = trip.files
    val place = t.title.substringBefore(':').trim()

    Refreshable(vm) {
        LazyColumn(contentPadding = PaddingValues(FgDims.margin), verticalArrangement = Arrangement.spacedBy(FgDims.gap)) {
            item {
                FgCard {
                    Text(
                        when (daysLeft) {
                            0 -> "$place today"
                            1 -> "$place tomorrow"
                            else -> "$place in $daysLeft days"
                        },
                        style = FgType.hero, color = fg.text,
                    )
                    val line = listOfNotNull(dayLabel(first.day.dateIso), firstBlock?.plan?.displayTitle, firstBlock?.e?.startMinute?.hhmm())
                    Text(line.joinToString(" · "), style = FgType.body, color = fg.textSecondary)
                    if (daysLeft <= 1 && firstBlock != null) {
                        leaveBy(first.plans)?.let { lb ->
                            Text("Leave by ${lb.minute.hhmm()} on ${dayLabel(first.day.dateIso)}", style = FgType.cardTitle, color = fg.primary)
                        }
                    }
                }
            }
            if (problems.isNotEmpty()) {
                item {
                    FgCard(border = fg.warning, background = fg.warningBg) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Warning, null, tint = fg.warning)
                            Spacer(Modifier.width(8.dp))
                            Text("Not booked yet (${problems.size})", style = FgType.cardTitle, color = fg.text)
                        }
                        problems.forEach { b ->
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { nav.push(Route.Block(b.id)) }.padding(vertical = 6.dp)) {
                                Text(b.plan.displayTitle, style = FgType.body, color = fg.text)
                                val detail = if (health(b.plan) == Health.INCOMPLETE) b.plan.flags.incomplete
                                else listOfNotNull(b.day?.let { dayLabel(it.dateIso) }, b.e.startMinute?.hhmm(), b.e.estimatedCostLocal?.let { "from ${it.toInt()} ${t.localCurrency}" }).joinToString(" · ")
                                Text(detail.orEmpty(), style = FgType.meta, color = fg.textSecondary)
                            }
                        }
                        Text("Book it, then add the booking in the web planner.", style = FgType.meta, color = fg.textSecondary)
                    }
                }
            }
            val checklists = trip.lists.filter { it.kind == ListKind.CHECKLIST }
            items(checklists, key = { it.blockId }) { list -> ListProgressCard(list, ticks) { nav.push(Route.ListDetail(list.blockId)) } }
            item { SectionTitle("Trip at a glance") }
            item { DayStrip(trip) { nav.push(Route.Day(it)) } }
            if (files.isNotEmpty()) {
                item {
                    val ready = files.count { it.ready }
                    Callout(
                        if (ready == files.size) Tone.SUCCESS else Tone.WARNING,
                        "Files on this phone: $ready of ${files.size}",
                        if (ready == files.size) emptyList() else listOf("Connect and pull down to download the rest."),
                    )
                }
            }
            if (problems.isEmpty() && checklists.all { listProgress(it, ticks).let { (d, n) -> d == n } }) {
                item { Callout(Tone.SUCCESS, "All set. Have a great trip.", icon = Icons.Rounded.Celebration) }
            }
        }
    }
}

@Composable
fun ListProgressCard(list: TripList, ticks: Set<String>, onOpen: () -> Unit) {
    val (done, total) = listProgress(list, ticks)
    val ticksKey = ticks
    FgCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(done, total)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("${list.emoji} ${list.title}".trim(), style = FgType.cardTitle, color = fg.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val verb = if (list.topic == ListTopic.PACKING) "packed" else "done"
                Text("$done of $total $verb", style = FgType.meta, color = fg.textSecondary)
                list.items.filter { !it.doneInSource && "${list.blockId}|${it.key}" !in ticksKey }.take(2).forEach {
                    Text("• ${it.text}", style = FgType.meta, color = fg.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Compact strip of trip days. Warning dot where something is not booked. */
@Composable
fun DayStrip(trip: TripUi, onOpen: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(trip.days, key = { it.day.id }) { d ->
            val warn = d.blocks.any { isProblem(it) }
            val today = d.isToday()
            Column(
                Modifier.width(64.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (today) toneColors(Tone.PRIMARY).second else fg.card)
                    .clickable { onOpen(d.day.id) }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(dayLabel(d.day.dateIso).substringBefore(' '), style = FgType.chip, color = fg.textSecondary)
                Text(shortDate(d.day.dateIso).substringBefore(' '), style = FgType.railTime, color = fg.text)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    d.blocks.filter { it.plan.kind == BlockKind.FLIGHT || it.plan.kind == BlockKind.TRAIN || it.plan.kind == BlockKind.CHECK_IN }
                        .take(2).forEach { Icon(kindIcon(it.plan.kind), null, Modifier.size(14.dp), tint = kindColor(it.plan.kind)) }
                    if (warn) Icon(Icons.Rounded.Warning, "Not booked", Modifier.size(14.dp), tint = fg.warning)
                }
            }
        }
    }
}

// ---------- S4 trip day ----------

@Composable
private fun TripDayToday(vm: AppViewModel, trip: TripUi, day: DayUi) {
    val nav = LocalNav.current
    val list = rememberLazyListState()
    val nowMinute = day.nowMinute()
    val nn = nowNext(day.plans, nowMinute)
    val collapsed by remember { derivedStateOf { list.firstVisibleItemIndex > 0 } }
    val firstStart = day.blocks.firstOrNull()?.e?.startMinute ?: 0
    val nextDay = trip.days.getOrNull(trip.days.indexOf(day) + 1)

    Box(Modifier.fillMaxSize()) {
        Refreshable(vm) {
            LazyColumn(state = list, contentPadding = PaddingValues(top = FgDims.margin, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                item(key = "hero") { Box(Modifier.padding(horizontal = FgDims.margin)) { NowNextHero(vm, trip, day, nn, nowMinute) } }
                item(key = "hero-sp") { Spacer(Modifier.height(FgDims.gap)) }
                item(key = "brief") { Box(Modifier.padding(horizontal = FgDims.margin)) { BriefingCard(vm, trip, day, initiallyOpen = nowMinute < firstStart) } }
                item(key = "brief-sp") { Spacer(Modifier.height(FgDims.gap)) }
                if (day.blocks.isEmpty()) {
                    item { EmptyState("A free day", "Nothing is planned. Lists has ideas and reference sheets.") }
                } else {
                    item { SectionTitle("Today", Modifier.padding(horizontal = FgDims.margin)) }
                    timeline(vm, trip, day, nowMinute)
                }
                item(key = "eod") {
                    Box(Modifier.padding(FgDims.margin)) { EndOfDayCard(trip, day, nextDay) { nav.push(Route.Day(it)) } }
                }
            }
        }
        if (collapsed) SlimNowBar(nn, nowMinute, Modifier.align(Alignment.TopCenter))
        if (day.blocks.any { it.plan.isPending && !it.e.isFixedTime && (it.e.startMinute ?: 0) + it.e.durationMinutes > nowMinute }) {
            ExtendedFloatingActionButton(
                onClick = { nav.sheet = Sheet.RunningLate(day.day.id) },
                icon = { Icon(Icons.Rounded.Timelapse, null) },
                text = { Text("Running late") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(FgDims.margin),
                containerColor = fg.primary,
                contentColor = fg.onPrimary,
            )
        }
    }
}

@Composable
private fun SlimNowBar(nn: app.tripplanner.fieldguide.logic.NowNext, nowMinute: Int, modifier: Modifier) {
    val b = nn.now ?: nn.next ?: return
    val text = if (nn.now != null) "Now: ${b.displayTitle} · ${humanDuration(nn.minutesLeft ?: 0)} left"
    else "Next ${b.startMinute?.hhmm()}: ${b.displayTitle}"
    Row(
        modifier.fillMaxWidth().background(fg.card).padding(horizontal = FgDims.margin, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(kindIcon(b.kind), null, tint = kindColor(b.kind), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = FgType.body, color = fg.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NowNextHero(vm: AppViewModel, trip: TripUi, day: DayUi, nn: app.tripplanner.fieldguide.logic.NowNext, nowMinute: Int) {
    val nav = LocalNav.current
    val context = LocalContext.current
    val local = trip.trip?.localCurrency.orEmpty()
    val nowUi = nn.now?.let { trip.byId[it.id] }
    val nextUi = nn.next?.let { trip.byId[it.id] }
    val firstStart = day.blocks.firstOrNull()?.e?.startMinute

    FgCard {
        when {
            nowUi != null -> {
                Text("Now", style = FgType.section, color = fg.primary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KindBadge(nowUi.plan.kind)
                    Spacer(Modifier.width(10.dp))
                    Text(nowUi.plan.displayTitle, style = FgType.cardTitle, color = fg.text, modifier = Modifier.weight(1f))
                }
                val end = (nowUi.e.startMinute ?: 0) + nowUi.e.durationMinutes
                Text("${humanDuration(nn.minutesLeft ?: 0)} left · until ${end.hhmm()}", style = FgType.hero, color = fg.text)
                val done = (nowMinute - (nowUi.e.startMinute ?: 0)).toFloat() / nowUi.e.durationMinutes.coerceAtLeast(1)
                LinearProgressIndicator(progress = { done.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = fg.primary, trackColor = fg.cardBorder)
            }
            firstStart != null && nowMinute < firstStart -> {
                val lb = leaveBy(day.plans, nowMinute)
                if (lb != null) {
                    Text("Leave by ${lb.minute.hhmm()}", style = FgType.hero, color = fg.text)
                    val there = lb.beThereBy?.let { " · be there by ${it.hhmm()}" }.orEmpty()
                    Text("${lb.block.displayTitle} at ${lb.block.startMinute?.hhmm()}$there", style = FgType.body, color = fg.textSecondary)
                } else {
                    Text("First up at ${firstStart.hhmm()}", style = FgType.hero, color = fg.text)
                }
            }
            nextUi == null -> {
                Text("Day complete", style = FgType.hero, color = fg.text)
                Text("Nothing left today. Rest well.", style = FgType.body, color = fg.textSecondary)
            }
        }
        if (nextUi != null) {
            Spacer(Modifier.height(4.dp))
            Text("Next · in ${humanDuration(nn.minutesUntilNext ?: 0)}", style = FgType.section, color = fg.textSecondary)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { nav.push(Route.Block(nextUi.id)) }) {
                Icon(kindIcon(nextUi.plan.kind), null, tint = kindColor(nextUi.plan.kind), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("${nextUi.e.startMinute?.hhmm()}  ${nextUi.plan.displayTitle}", style = FgType.cardTitle, color = fg.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val callout = when {
                nextUi.plan.flags.cashOnly -> "Cash only" + (nextUi.e.estimatedCostLocal?.let { " · ~${it.toInt()} $local" } ?: "")
                nextUi.plan.flags.incomplete != null -> nextUi.plan.flags.incomplete
                nextUi.plan.flags.notBooked -> "Not booked yet"
                else -> null
            }
            callout?.let { StatusChip(it, Tone.WARNING, Icons.Rounded.Warning) }
        }
        val target = nowUi ?: nextUi
        target?.let { quickAction(context, nav, it) }?.let { qa ->
            BigAction(qa.label, qa.icon, Modifier.fillMaxWidth(), onClick = qa.run)
        }
    }
}

@Composable
private fun BriefingCard(vm: AppViewModel, trip: TripUi, day: DayUi, initiallyOpen: Boolean) {
    val t = trip.trip ?: return
    val plans = day.plans
    val bed = bedTonight(trip.stays, day.day.dateIso)
    val carry = carryToday(plans)
    val cash = cashNeeded(plans)
    val spend = daySpend(plans)
    val warnings = warningsToday(plans)
    val lb = leaveBy(plans)

    FgCard {
        Expandable("Morning briefing", initiallyOpen = initiallyOpen) {
            lb?.let {
                BriefRow(Icons.Rounded.Schedule, "Leave by ${it.minute.hhmm()}", "${it.block.displayTitle} at ${it.block.startMinute?.hhmm()}")
            }
            BriefRow(Icons.Rounded.Hotel, "Bed tonight", bed?.name ?: "No hotel tonight")
            if (cash > 0) BriefRow(Icons.Rounded.Payments, "Cash needed today", "~${money(cash, t.exchangeRate, t.localCurrency, t.homeCurrency)}")
            if (spend > 0) BriefRow(Icons.Rounded.Payments, "Day spend", "About ${money(spend, t.exchangeRate, t.localCurrency, t.homeCurrency)}")
            day.day.weatherSummaryCache?.takeIf { it.isNotBlank() }?.let { BriefRow(Icons.Rounded.Schedule, "Weather", it) }
            if (carry.isNotEmpty()) {
                Text("Carry today", style = FgType.section, color = fg.textSecondary)
                ChipRow { carry.forEach { StatusChip(it, Tone.NEUTRAL) } }
            }
            if (warnings.isNotEmpty()) {
                Callout(Tone.WARNING, "Warnings today", warnings)
            }
            if (day.prep.isNotEmpty()) {
                Text("Morning prep", style = FgType.section, color = fg.textSecondary)
                day.prep.forEach { PrepRow(it) { c -> vm.checkItem(it.id, c) } }
            }
        }
    }
}

@Composable
private fun BriefRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = fg.textSecondary, modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = FgType.chip, color = fg.textSecondary)
            Text(value, style = FgType.body, color = fg.text)
        }
    }
}

@Composable
fun PrepRow(item: ContextualItemEntity, onCheck: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = FgDims.touch).clickable { onCheck(!item.isChecked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.isChecked, onCheckedChange = onCheck)
        Text(item.title, style = FgType.body, color = fg.text, textDecoration = if (item.isChecked) TextDecoration.LineThrough else null)
    }
}

@Composable
private fun EndOfDayCard(trip: TripUi, day: DayUi, next: DayUi?, onOpen: (String) -> Unit) {
    val t = trip.trip ?: return
    if (next == null) {
        FgCard { Text("Last day of the trip. Safe travels home.", style = FgType.body, color = fg.text) }
        return
    }
    val first = next.blocks.firstOrNull()
    FgCard(onClick = { onOpen(next.day.id) }) {
        Text("Tomorrow · ${dayLabel(next.day.dateIso)}", style = FgType.section, color = fg.textSecondary)
        Text(
            first?.let { "${it.plan.displayTitle} at ${it.e.startMinute?.hhmm()}" } ?: "Nothing planned yet",
            style = FgType.cardTitle, color = fg.text,
        )
        Text("Briefing at ${t.wakeMinute.hhmm()}", style = FgType.meta, color = fg.textSecondary)
        day.blocks.filter { it.plan.flags.cashOnly && it.plan.kind == BlockKind.CHECK_IN }.firstOrNull()?.let {
            if (next.blocks.any { b -> b.plan.kind == BlockKind.CHECK_OUT }) Text("Settle the cash bill tonight if you leave early.", style = FgType.meta, color = fg.warning)
        }
    }
}

// ---------- S19 post-trip ----------

@Composable
private fun PostTrip(vm: AppViewModel, trip: TripUi) {
    val nav = LocalNav.current
    val t = trip.trip!!
    val all = trip.scheduled
    val done = all.count { it.plan.isDone }
    val skipped = all.count { it.plan.isSkipped }
    val spend = daySpend(all.map { it.plan })
    LazyColumn(contentPadding = PaddingValues(FgDims.margin), verticalArrangement = Arrangement.spacedBy(FgDims.gap)) {
        item {
            FgCard {
                Text("Welcome home", style = FgType.hero, color = fg.text)
                Text("${t.title} · ${trip.days.size} days", style = FgType.body, color = fg.textSecondary)
                KeyFacts(
                    listOf(
                        "Plans done" to "$done of ${all.size}",
                        "Skipped" to "$skipped",
                        "Stays" to "${trip.stays.size}",
                        "Planned spend" to money(spend, t.exchangeRate, t.localCurrency, t.homeCurrency),
                    ),
                )
            }
        }
        item { DayStrip(trip) { nav.push(Route.Day(it)) } }
        item {
            FgCard {
                Text("Keep the trip for reference, or remove it from this phone in Settings.", style = FgType.body, color = fg.text)
                BigAction("Open settings", null, Modifier.fillMaxWidth(), primary = false) { nav.push(Route.Settings) }
            }
        }
    }
}

// ---------- S5 day view ----------

@Composable
fun DayScreen(vm: AppViewModel, trip: TripUi, dayId: String) {
    val nav = LocalNav.current
    val day = trip.dayOf(dayId) ?: return
    val isToday = day.isToday()
    val past = day.day.dateIso < todayIso(zoneOrDefault(day.day.timezone))
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            LazyRow(contentPadding = PaddingValues(FgDims.margin), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(trip.days, key = { it.day.id }) { d ->
                    val selected = d.day.id == dayId
                    val (ink, bg) = toneColors(if (selected) Tone.PRIMARY else Tone.NEUTRAL)
                    Text(
                        "${shortDate(d.day.dateIso).substringBefore(' ')}",
                        style = FgType.railTime, color = if (selected) ink else fg.text,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(bg)
                            .clickable { nav.stack[nav.stack.lastIndex] = Route.Day(d.day.id) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
        item {
            Box(Modifier.padding(horizontal = FgDims.margin)) {
                val bed = bedTonight(trip.stays, day.day.dateIso)
                Text(
                    listOfNotNull(if (past) "Past day" else null, bed?.let { "Sleep at ${it.name}" }).joinToString(" · ").ifEmpty { " " },
                    style = FgType.meta, color = fg.textSecondary,
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
        if (day.blocks.isEmpty()) item { EmptyState("A free day", "Nothing is planned on this day.") }
        timeline(vm, trip, day, if (isToday) day.nowMinute() else null)
    }
}
