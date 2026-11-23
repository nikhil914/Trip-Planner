package app.tripplanner.fieldguide.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readPairingLink(intent)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            FieldGuideTheme(settings.theme) { AppRoot(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readPairingLink(intent)
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
        vm.onAppOpened()
    }

    override fun onStop() {
        super.onStop()
        vm.onBackground()
    }

    /** fieldguide://pair?u=<server>&c=<code>, from the QR shown by the web planner. */
    private fun readPairingLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "fieldguide" && data.host == "pair") {
            vm.offerPairing(data.getQueryParameter("u"), data.getQueryParameter("c"))
        }
    }
}

/** First run, lock and the main shell. */
@Composable
private fun AppRoot(vm: AppViewModel) {
    val session by vm.session.collectAsStateWithLifecycle()
    val offer by vm.pairingOffer.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(fg.background)) {
        when {
            !session.hasTrip || offer != null -> PairingScreen(vm, offer, canCancel = session.hasTrip)
            settings.lockEnabled && locked -> LockScreen(vm)
            !settings.primerDone -> PrimerScreen(vm)
            else -> Shell(vm)
        }
    }
}
