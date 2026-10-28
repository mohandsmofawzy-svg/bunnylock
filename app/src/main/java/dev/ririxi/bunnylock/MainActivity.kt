package dev.ririxi.bunnylock

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import dev.ririxi.bunnylock.data.AppLanguage
import dev.ririxi.bunnylock.ui.BunnyApp
import dev.ririxi.bunnylock.ui.MainViewModel
import dev.ririxi.bunnylock.ui.theme.BunnyTheme

/** نقطة دخول واجهة التطبيق؛ تدير طلب الصلاحيات وتربط العرض بـ ViewModel. */
class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    /** بعد نتيجة الإذن يعيد تحميل SIM وطلب قراءة حديثة بدل افتراض أن الصلاحيات مُنحت. */
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            vm.loadSims()
            vm.refresh()
        }

    /** يطبق لغة التطبيق على Context قبل إنشاء موارد الواجهة. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    /** يهيئ حواف النظام ويطلب الصلاحيات مرة عند إنشاء Activity جديدة. */
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
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
            BunnyTheme {
                BunnyApp(vm)
            }
        }
    }

    /** يعيد فحص منح Shizuku وتغير اللغة عند عودة المستخدم إلى التطبيق. */
    override fun onResume() {
        super.onResume()
        vm.resumed()
    }

    /** يعالج إجراءات الإشعار التي تصل إلى Activity ذات وضع singleTop. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationAction(intent)
    }

    /** يمرر الإجراء المعروف إلى ViewModel؛ التحقق والتنفيذ الفعليان في طبقة التطبيق. */
    private fun handleNotificationAction(intent: Intent?) {
        vm.handleSystemAction(intent?.action)
    }
}
