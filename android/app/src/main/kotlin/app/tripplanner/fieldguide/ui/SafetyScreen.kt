package app.tripplanner.fieldguide.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tripplanner.fieldguide.logic.Phone
import app.tripplanner.fieldguide.logic.bedTonight
import app.tripplanner.fieldguide.logic.phonesIn
import app.tripplanner.fieldguide.logic.warningsToday

const val EMERGENCY_NUMBER = "112"

/** Call buttons from the safety list, such as the insurer's 24/7 line. */
fun safetyPhones(trip: TripUi): List<Phone> =
    safetyList(trip)?.let { list -> phonesIn(trip.byId[list.blockId]?.e?.subtitleOrTip.orEmpty()) }.orEmpty()
        .filter { it.number.filter(Char::isDigit) != EMERGENCY_NUMBER }

@Composable
fun EmergencyButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Button(
        onClick = { dial(context, EMERGENCY_NUMBER) },
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828), contentColor = Color.White),
    ) {
        Icon(Icons.Rounded.Call, null)
        Spacer(Modifier.width(10.dp))
        Column {
            Text("Call $EMERGENCY_NUMBER", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Police, ambulance, fire", fontSize = 13.sp)
        }
    }
}

@Composable
fun PhoneButton(phone: Phone) {
    val context = LocalContext.current
    OutlinedButton(onClick = { dial(context, phone.number) }, modifier = Modifier.fillMaxWidth().heightIn(min = FgDims.bigTouch), shape = RoundedCornerShape(14.dp)) {
        Icon(Icons.Rounded.Call, null, tint = fg.danger)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(phone.label.take(60), style = FgType.body, color = fg.text)
            Text(phone.number, style = FgType.cardTitle, color = fg.text)
        }
    }
}

// ---------- S15 Safety and emergency ----------

@Composable
fun SafetyScreen(trip: TripUi) {
    val nav = LocalNav.current
    val list = safetyList(trip)
    val phones = safetyPhones(trip)
    val today = trip.today()
    val bed = today?.let { bedTonight(trip.stays, it.day.dateIso) }
        ?: trip.stays.firstOrNull()
    val bedBlock = bed?.let { trip.byId[it.checkIn.id] }
    val warnings = today?.let { warningsToday(it.plans, max = 6) }.orEmpty()

    LazyColumn(contentPadding = PaddingValues(FgDims.margin), verticalArrangement = Arrangement.spacedBy(FgDims.gap)) {
        item { EmergencyButton() }
        if (phones.isNotEmpty()) {
            item {
                FgCard {
                    SectionTitle("Call")
                    phones.forEach { PhoneButton(it) }
                    Text("Calls open your phone app. Calling works without data.", style = FgType.meta, color = fg.textSecondary)
                }
            }
        }
        if (bedBlock != null) {
            item {
                FgCard {
                    SectionTitle(if (today != null) "Where I'm staying tonight" else "First stay")
                    Text(bed?.name.orEmpty(), style = FgType.cardTitle, color = fg.text)
                    hotelAddress(bedBlock)?.let { Text(it, style = FgType.body, color = fg.text) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { nav.modal = Modal.ShowLocal(localCards(bedBlock)) }, modifier = Modifier.heightIn(min = FgDims.touch)) {
                            Icon(Icons.Rounded.Translate, null); Spacer(Modifier.width(6.dp)); Text("Show to local")
                        }
                    }
                }
            }
        }
        if (warnings.isNotEmpty()) item { Callout(Tone.WARNING, "Today's warnings", warnings) }
        if (list != null) {
            list.sections.forEach { s ->
                item {
                    FgCard {
                        s.heading?.let { SectionTitle(it) }
                        s.lines.forEach { line ->
                            line.label?.let { Text(it, style = FgType.body.copy(fontWeight = FontWeight.SemiBold), color = fg.text) }
                            Bullet(line.text)
                        }
                    }
                }
            }
        } else {
            item { Text("Add an emergency and safety card in the planner to see insurance and embassy details here.", style = FgType.body, color = fg.textSecondary) }
        }
        item { Text("Everything here works offline. News and weather links need a connection.", style = FgType.meta, color = fg.textSecondary) }
    }
}
