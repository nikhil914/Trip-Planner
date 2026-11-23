package app.tripplanner.fieldguide.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** True when the phone has a screen lock, so it can confirm the owner. */
fun canLock(context: Context): Boolean = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Asks for fingerprint, face or the phone's own PIN using the platform prompt, so no extra library is
 * needed. Android 8 and 9 without the device-credential option fall back to the keyguard screen.
 */
class OwnerCheck(private val context: Context, private val keyguardLauncher: (android.content.Intent) -> Unit) {
    fun run(onOk: () -> Unit, onError: (String) -> Unit) {
        val activity = context.findActivity() ?: return onError("Can't show the lock prompt.")
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        if (keyguard?.isDeviceSecure != true) return onOk()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle("Unlock Field Guide")
                .setSubtitle("Tickets and bookings are locked")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
            prompt.authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onOk()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) = onError(errString?.toString() ?: "Not unlocked")
            })
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle("Unlock Field Guide")
                .setDeviceCredentialAllowed(true)
                .build()
            prompt.authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onOk()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) = onError(errString?.toString() ?: "Not unlocked")
            })
        } else {
            @Suppress("DEPRECATION")
            val intent = keyguard!!.createConfirmDeviceCredentialIntent("Unlock Field Guide", "Tickets and bookings are locked")
            if (intent == null) onOk() else keyguardLauncher(intent)
        }
    }
}

@Composable
fun rememberOwnerCheck(onResult: (Boolean) -> Unit): OwnerCheck {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        onResult(r.resultCode == Activity.RESULT_OK)
    }
    return remember(context) { OwnerCheck(context) { launcher.launch(it) } }
}

// ---------- S0 Lock screen ----------

@Composable
fun LockScreen(vm: AppViewModel) {
    val trip by vm.trip.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    val check = rememberOwnerCheck { ok -> if (ok) vm.unlocked() else { error = "Not unlocked" } }
    val unlock = { check.run(onOk = vm::unlocked, onError = { error = it }) }
    LaunchedEffect(Unit) { unlock() }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Icon(Icons.Rounded.Lock, null, tint = fg.primary, modifier = Modifier.size(48.dp))
        Text("Field Guide", style = FgType.hero, color = fg.text)
        trip.trip?.let { Text(it.title, style = FgType.body, color = fg.textSecondary) }
        BigAction("Unlock", Icons.Rounded.Fingerprint, Modifier.fillMaxWidth()) { unlock() }
        error?.let { Text(it, style = FgType.meta, color = fg.textSecondary) }
        Spacer(Modifier.weight(1f))
        Text("Works without unlocking", style = FgType.section, color = fg.textSecondary)
        EmergencyButton()
        safetyPhones(trip).firstOrNull { it.label.contains("hotline", true) || it.label.contains("insur", true) }?.let { PhoneButton(it) }
    }
}
