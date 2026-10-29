package dev.ririxi.bunnylock

import android.Manifest
<<<<<<< HEAD
=======
import android.content.Context
import android.content.res.Configuration
>>>>>>> 1f1c3fc2ddb001caec522b01498a9ab0a4e36bb1
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
<<<<<<< HEAD
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ririxi.bunnylock.data.LockNotifier
import dev.ririxi.bunnylock.ui.BunnyApp
import dev.ririxi.bunnylock.ui.MainViewModel
import dev.ririxi.bunnylock.ui.theme.BunnyTheme
=======
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import dev.ririxi.bunnylock.data.LockNotifier
import dev.ririxi.bunnylock.ui.BunnyApp
import dev.ririxi.bunnylock.ui.LocalAppLanguage
import dev.ririxi.bunnylock.ui.MainViewModel
import dev.ririxi.bunnylock.ui.theme.BunnyTheme
import java.util.Locale
>>>>>>> 1f1c3fc2ddb001caec522b01498a9ab0a4e36bb1

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            vm.loadSims()
            vm.refresh()
        }

<<<<<<< HEAD
=======
    // يضبط اللغة قبل إنشاء Compose حتى تتطابق الموارد واتجاه التخطيط مع الاختيار المحفوظ.
    override fun attachBaseContext(newBase: Context) {
        val language =
            newBase.getSharedPreferences("bunnylock", Context.MODE_PRIVATE)
                .getString("language", if (Locale.getDefault().language == "ar") "ar" else "en") ?: "en"
        val locale = Locale.forLanguageTag(language)
        val configuration = Configuration(newBase.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

>>>>>>> 1f1c3fc2ddb001caec522b01498a9ab0a4e36bb1
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState === null) {
            val wanted =
                mutableListOf(
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) wanted += Manifest.permission.POST_NOTIFICATIONS
            permissionLauncher.launch(wanted.toTypedArray())
            handleNotificationAction(intent)
        }
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
<<<<<<< HEAD
            BunnyTheme(dynamicColor = state.dynamicColor) {
                BunnyApp(vm)
=======
            CompositionLocalProvider(
                LocalAppLanguage provides state.language,
                LocalLayoutDirection provides if (state.language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                BunnyTheme {
                    BunnyApp(vm)
                }
>>>>>>> 1f1c3fc2ddb001caec522b01498a9ab0a4e36bb1
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.resumed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationAction(intent)
    }

    private fun handleNotificationAction(intent: Intent?) {
        if (intent?.action == LockNotifier.ACTION_UNLOCK_ALL) vm.unlockFromNotification()
    }
}
