package app.tripplanner.fieldguide.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tripplanner.fieldguide.logic.ListKind
import app.tripplanner.fieldguide.logic.ListTopic
import app.tripplanner.fieldguide.logic.TripList
import app.tripplanner.fieldguide.logic.phrasesIn

// ---------- S12 Lists hub ----------

@Composable
fun ListsTab(vm: AppViewModel, trip: TripUi) {
    val nav = LocalNav.current
    val ticks by vm.ticks.collectAsStateWithLifecycle()
    val prepDays = trip.days.filter { it.prep.isNotEmpty() }
    LazyColumn(contentPadding = PaddingValues(FgDims.margin), verticalArrangement = Arrangement.spacedBy(FgDims.gap)) {
        if (trip.lists.isEmpty() && prepDays.isEmpty()) {
            item { EmptyState("No lists yet", "Checklist cards from the planner show here, for example packing or before you fly.") }
        }
        val checklists = trip.lists.filter { it.kind == ListKind.CHECKLIST }
        val sheets = trip.lists.filter { it.kind == ListKind.REFERENCE }
        if (checklists.isNotEmpty()) item { SectionTitle("Checklists") }
        items(checklists, key = { it.blockId }) { list -> ListProgressCard(list, ticks) { nav.push(Route.ListDetail(list.blockId)) } }
        if (sheets.isNotEmpty()) item { SectionTitle("Reference") }
        items(sheets, key = { it.blockId }) { list ->
            FgCard(onClick = { if (list.topic == ListTopic.SAFETY) nav.push(Route.Safety) else nav.push(Route.ListDetail(list.blockId)) }) {
                Text("${list.emoji} ${list.title}".trim(), style = FgType.cardTitle, color = fg.text)
                Text(list.sections.mapNotNull { it.heading }.take(4).joinToString(" · ").ifEmpty { "${list.items.size} notes" }, style = FgType.meta, color = fg.textSecondary)
            }
        }
        if (prepDays.isNotEmpty()) item { SectionTitle("Morning prep by day") }
        items(prepDays, key = { "prep-${it.day.id}" }) { d ->
            FgCard {
                Text("Day ${d.day.dayNumber}", style = FgType.cardTitle, color = fg.text)
                d.prep.forEach { PrepRow(it) { c -> vm.checkItem(it.id, c) } }
            }
        }
        item { Text("These lists come from the planner. Edit them there. Your ticks on these lists stay on this phone.", style = FgType.meta, color = fg.textSecondary) }
    }
}

// ---------- S12 List detail ----------

@Composable
fun ListDetailScreen(vm: AppViewModel, trip: TripUi, blockId: String) {
    val nav = LocalNav.current
    val list = trip.lists.firstOrNull { it.blockId == blockId } ?: run { EmptyState("List not found", "It may have been removed in the planner."); return }
    val ticks by vm.ticks.collectAsStateWithLifecycle()
    var hideDone by rememberSaveable { mutableStateOf(false) }
    val (done, total) = listProgress(list, ticks)
    val phrases = if (list.topic == ListTopic.FOOD) phrasesIn(trip.byId[blockId]?.e?.subtitleOrTip) else emptyList()

    LazyColumn(contentPadding = PaddingValues(FgDims.margin), verticalArrangement = Arrangement.spacedBy(FgDims.gap)) {
        if (list.kind == ListKind.CHECKLIST) {
            item {
                FgCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(done, total, size = 60)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("$done of $total ${if (list.topic == ListTopic.PACKING) "packed" else "done"}", style = FgType.cardTitle, color = fg.text)
                            list.intro?.let { Text(it, style = FgType.meta, color = fg.textSecondary) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Hide done", style = FgType.body, color = fg.text, modifier = Modifier.weight(1f))
                        Switch(checked = hideDone, onCheckedChange = { hideDone = it })
                    }
                }
            }
        }
        if (phrases.isNotEmpty()) {
            item {
                FgCard {
                    SectionTitle("Phrases · tap to show")
                    phrases.forEach { ph ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = FgDims.touch).clickable { nav.modal = Modal.ShowLocal(phrases.map { LocalCard(null, null, it.local, it.meaning) }, phrases.indexOf(ph)) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Translate, null, tint = fg.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(ph.local, style = FgType.cardTitle, color = fg.text, modifier = Modifier.weight(1f))
                            Text(ph.meaning, style = FgType.body, color = fg.textSecondary)
                        }
                    }
                }
            }
        }
        list.sections.forEach { section ->
            item {
                FgCard {
                    section.heading?.let { SectionTitle(it) }
                    section.lines.forEach { line ->
                        val key = "${list.blockId}|${line.key}"
                        val checked = line.doneInSource || key in ticks
                        if (list.kind == ListKind.CHECKLIST) {
                            if (!(hideDone && checked)) {
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = FgDims.touch)
                                        .clickable(enabled = !line.doneInSource, role = Role.Checkbox) { vm.tick(list.blockId, line.key, !checked) },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(checked = checked, onCheckedChange = if (line.doneInSource) null else ({ c: Boolean -> vm.tick(list.blockId, line.key, c) }))
                                    Text(
                                        line.text, style = FgType.body, color = if (checked) fg.textSecondary else fg.text,
                                        textDecoration = if (checked) TextDecoration.LineThrough else null, modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        } else {
                            Column(Modifier.padding(vertical = 2.dp)) {
                                line.label?.let { Text(it, style = FgType.body.copy(fontWeight = FontWeight.SemiBold), color = fg.text) }
                                Bullet(line.text)
                            }
                        }
                    }
                }
            }
        }
        if (list.topic == ListTopic.SAFETY) {
            item { TextButton(onClick = { nav.push(Route.Safety) }) { Text("Open the emergency screen") } }
        }
        item { Text("This list came from the planner. Edit it there.", style = FgType.meta, color = fg.textSecondary) }
    }
}

fun safetyList(trip: TripUi): TripList? = trip.lists.firstOrNull { it.topic == ListTopic.SAFETY }
