package app.tripplanner.fieldguide.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readPairingLink(intent)
        setContent { FieldGuideTheme { AppRoot(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readPairingLink(intent)
    }

    override fun onStart() {
        super.onStart()
        vm.onAppOpened()
    }

    /** fieldguide://pair?u=<server>&c=<code>, from the QR shown by the web builder. */
    private fun readPairingLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "fieldguide" && data.host == "pair") {
            vm.offerPairing(data.getQueryParameter("u"), data.getQueryParameter("c"))
        }
    }
}

private enum class Screen { TODAY, SETTINGS }

@Composable
private fun AppRoot(vm: AppViewModel) {
    val session by vm.session.collectAsStateWithLifecycle()
    val offer by vm.pairingOffer.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.TODAY) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    // The briefing is the only reason for this permission, so it is requested once a trip exists.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(session.hasTrip) {
        if (session.hasTrip && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val showPairing = !session.hasTrip || offer != null
    BackHandler(enabled = !showPairing && screen == Screen.SETTINGS) { screen = Screen.TODAY }

    Scaffold(containerColor = Color.Black, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                showPairing -> PairingScreen(vm, offer, canCancel = session.hasTrip)
                screen == Screen.SETTINGS -> SettingsScreen(vm, onBack = { screen = Screen.TODAY })
                else -> TodayScreen(vm, onOpenSettings = { screen = Screen.SETTINGS })
            }
        }
    }
}
