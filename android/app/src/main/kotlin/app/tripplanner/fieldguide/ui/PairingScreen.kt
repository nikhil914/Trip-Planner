package app.tripplanner.fieldguide.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PairingScreen(vm: AppViewModel, offer: PairingOffer?, canCancel: Boolean) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var server by rememberSaveable(offer) { mutableStateOf(offer?.serverUrl.orEmpty()) }
    var code by rememberSaveable(offer) { mutableStateOf(offer?.code.orEmpty()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Field Guide", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text(
            if (offer != null) "Pair this phone with the trip from the QR code?"
            else "Scan the QR code from the web builder with your camera, or enter the server address and the 6-digit code.",
            color = Dim,
        )
        if (canCancel) {
            Text("Pairing replaces the trip now on this phone.", color = MaterialTheme.colorScheme.error)
        }
        OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            label = { Text("Server address") },
            placeholder = { Text("https://trips.example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = code,
            onValueChange = { input -> code = input.filter(Char::isDigit).take(6) },
            label = { Text("Pairing code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { vm.pair(server, code) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Pairing…" else "Pair and download trip")
        }
        if (canCancel) {
            TextButton(onClick = { vm.pairingOffer.value = null }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        } else {
            OutlinedButton(onClick = vm::loadDemo, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Try the demo trip")
            }
            Text("The demo starts today, stays on this phone, and never syncs.", color = Dim, style = MaterialTheme.typography.bodySmall)
        }
    }
}
