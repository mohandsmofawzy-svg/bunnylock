package dev.ririxi.bunnylock.ui

import android.text.BidiFormatter
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

val LocalAppLanguage = staticCompositionLocalOf { "en" }

private val arabicText =
    mapOf(
        "Home" to "الرئيسية",
        "Main" to "الرئيسية",
        "Bands" to "النطاقات",
        "Cell lock" to "قفل الخلية",
        "Tools" to "الأدوات",
        "Menu" to "القائمة",
        "English" to "الإنجليزية",
        "This device's SIM-to-modem mapping could not be verified; modem controls remain disabled." to "تعذّر التحقق من ربط الشريحة بالمودم على هذا الجهاز؛ ستظل أدوات المودم معطلة.",
        "Refresh" to "تحديث",
        "Selected" to "محدد",
        "Collapse" to "طي",
        "Expand" to "توسيع",
        "Pick favorite bands" to "اختيار النطاقات المفضلة",
        "Band and cell locking" to "قفل النطاقات والخلايا",
        "Root or Shizuku needed" to "يلزم Root أو Shizuku",
        "Try again" to "إعادة المحاولة",
        "Copy diagnostics" to "نسخ التشخيصات",
        "Diagnostics copied to the clipboard." to "تم نسخ التشخيصات إلى الحافظة.",
        "The modem replied. See the output below." to "رد المودم. راجع المخرجات أدناه.",
        "Cell locked." to "تم قفل الخلية.",
        "Cell unlocked." to "تم إلغاء قفل الخلية.",
        "Cell lock cleared." to "تمت إزالة قفل الخلية.",
        "Network locked." to "تم قفل الشبكة.",
        "Back to automatic network selection." to "تمت العودة إلى اختيار الشبكة التلقائي.",
        "Everything is unlocked." to "تم إلغاء جميع الأقفال.",
        "Radio restarted." to "تمت إعادة تشغيل الراديو.",
        "Modem is rebooting." to "جارٍ إعادة تشغيل المودم.",
        "Opened the Samsung band menu." to "تم فتح قائمة نطاقات سامسونج.",
        "Update available" to "يتوفر تحديث",
        "Download" to "تنزيل",
        "Skip this version" to "تخطي هذا الإصدار",
        "Later" to "لاحقًا",
        "Network mode" to "وضع الشبكة",
        "5G mode" to "وضع 5G",
        "5G NSA (EN-DC)" to "5G NSA (EN-DC)",
        "NSA on" to "تشغيل NSA",
        "NSA off" to "إيقاف NSA",
        "Samsung" to "سامسونج",
        "Samsung's own band lock, built into the phone." to "قفل النطاقات الأصلي من سامسونج.",
        "Open Samsung band menu" to "فتح قائمة نطاقات سامسونج",
        "Tap the bands you want to keep in view." to "حدد النطاقات التي تريد إضافتها للمفضلة.",
        "Show favorites only" to "عرض المفضلة فقط",
        "Done" to "تم",
        "All" to "الكل",
        "None" to "لا شيء",
        "Lock" to "قفل",
        "Unlock" to "إلغاء القفل",
        "Network (PLMN)" to "الشبكة (PLMN)",
        "Current cell lock" to "قفل الخلية الحالي",
        "Clear cell lock" to "إزالة قفل الخلية",
        "Lock to" to "القفل على",
        "Visible cells (tap one to fill in)" to "الخلايا الظاهرة (اضغط لتعبئة البيانات)",
        "Automatic" to "تلقائي",
        "MCC" to "MCC",
        "MNC" to "MNC",
        "LTE anchor" to "مرساة LTE",
        "5G NR" to "5G NR",
        "NR secondary (SCG)" to "خلية NR ثانوية (SCG)",
        "Band" to "النطاق",
        "Subcarrier spacing (kHz)" to "تباعد الحوامل الفرعية (kHz)",
        "Connection" to "الاتصال",
        "Game connection" to "اتصال اللعب",
        "Observe-only diagnostics: four small network checks every five seconds. It does not change the modem, radio, or game process and shows a foreground notification." to "تشخيص بالمراقبة فقط: أربعة فحوصات صغيرة للشبكة كل خمس ثوانٍ. لا يغيّر المودم أو الراديو أو عملية اللعبة، ويعرض إشعارًا دائمًا.",
        "Starting network monitor…" to "جارٍ بدء مراقبة الشبكة…",
        "Waiting for the first sample…" to "بانتظار أول عينة…",
        "Monitor stopped" to "المراقبة متوقفة",
        "Stop monitor" to "إيقاف المراقبة",
        "Start monitor" to "بدء المراقبة",
        "Idle" to "خامل",
        "Internet reachable" to "الإنترنت متاح",
        "High latency on every probe" to "زمن استجابة مرتفع في جميع الفحوصات",
        "Some probes failed; likely routing or a remote server" to "فشلت بعض الفحوصات؛ قد تكون المشكلة في التوجيه أو الخادم البعيد",
        "Registered, but every probe failed" to "الشبكة مسجلة لكن جميع الفحوصات فشلت",
        "No cellular registration" to "لا يوجد تسجيل خلوي",
        "No default network" to "لا توجد شبكة افتراضية",
        "Default network is not validated yet" to "لم يتم التحقق من الشبكة الافتراضية بعد",
        "Captive portal" to "بوابة تسجيل دخول للشبكة",
        "Default network is Wi‑Fi" to "الشبكة الافتراضية هي Wi‑Fi",
        "RTT" to "زمن الاستجابة",
        "jitter" to "التذبذب",
        "ms" to "مللي ثانية",
        "Carriers" to "الحوامل",
        "Carrier" to "الحامل",
        "Network" to "الشبكة",
        "LTE cell" to "خلية LTE",
        "NR cell" to "خلية NR",
        "2G/3G cell" to "خلية 2G/3G",
        "Manual selection" to "اختيار يدوي",
        "Modem" to "المودم",
        "Chipset" to "الشريحة",
        "Device" to "الجهاز",
        "Platform" to "المنصة",
        "Baseband" to "النطاق الأساسي",
        "Ignored" to "تم تجاهله",
        "Locked" to "مقفل",
        "Free" to "غير مقيد",
        "Unlock all" to "إلغاء كل الأقفال",
        "Cell" to "الخلية",
        "total bandwidth" to "عرض النطاق الإجمالي",
        "Primary" to "أساسي",
        "Secondary" to "ثانوي",
        "Unknown network" to "شبكة غير معروفة",
        "No serving cell" to "لا توجد خلية تخدم الاتصال",
        "Live network" to "الشبكة الحالية",
        "LOCK CONTROL" to "التحكم في الأقفال",
        "Lock control" to "التحكم في الأقفال",
        "Band plan" to "خطة النطاقات",
        "Dynamic Band Switching" to "التبديل الديناميكي للنطاقات",
        "Turn Dynamic Band Switching off" to "إيقاف التبديل الديناميكي للنطاقات",
        "Turn Dynamic Band Switching on" to "تشغيل التبديل الديناميكي للنطاقات",
        "Switch interval (seconds)" to "الفاصل بين التبديلات (بالثواني)",
        "10–3600 seconds; 30 seconds by default." to "من 10 إلى 3600 ثانية؛ الافتراضي 30 ثانية.",
        "Custom" to "مخصص",
        "Current band" to "النطاق الحالي",
        "Next band" to "النطاق التالي",
        "Last switch" to "آخر تبديل",
        "Not confirmed" to "لم يتم التأكد",
        "Not started" to "لم يبدأ",
        "Waiting to start" to "بانتظار البدء",
        "Switching" to "جارٍ التبديل",
        "Switch succeeded" to "نجح التبديل",
        "Lock applied; serving band confirmed" to "تم تطبيق القفل وتأكيد نطاق الخدمة",
        "Lock applied; serving band not yet confirmed" to "تم تطبيق القفل، ولم يُؤكد نطاق الخدمة بعد",
        "Lock rejected by modem" to "رفض المودم قفل النطاق",
        "Lock state unknown" to "حالة قفل النطاق غير معروفة",
        "Serving band read failed" to "تعذرت قراءة نطاق الخدمة",
        "Read-only refresh mode" to "وضع التحديث للقراءة فقط",
        "Read-only status refreshed" to "تم تحديث الحالة للقراءة فقط",
        "One selected band uses read-only status refresh. No band-lock command or handover is sent." to "عند تحديد نطاق واحد، تُحدّث الحالة للقراءة فقط؛ لا يُرسل أمر قفل للنطاق ولا أمر تسليم بين الخلايا.",
        "The modem status refresh failed. No band command was sent." to "تعذّر تحديث حالة المودم. لم يُرسل أي أمر لتغيير النطاق.",
        "Phone functionality · AT+CFUN" to "وظائف الهاتف · AT+CFUN",
        "Full functionality" to "كامل وظائف المودم",
        "Enables full modem functionality; the radio may register or reconnect." to "يفعّل وظائف المودم كاملة؛ قد يعيد الراديو التسجيل أو الاتصال.",
        "RF transmit disabled" to "إيقاف إرسال RF",
        "Disables RF transmission on common implementations; service or data may be interrupted." to "يوقف إرسال RF في التطبيقات الشائعة؛ قد تتأثر الخدمة أو البيانات.",
        "RF transmit and receive disabled" to "إيقاف إرسال واستقبال RF",
        "Disables RF transmit and receive on common implementations; cellular service may be lost." to "يوقف إرسال واستقبال RF في التطبيقات الشائعة؛ قد تفقد الخدمة الخلوية.",
        "CFUN settings can change radio functionality; exact behavior depends on modem firmware." to "قد تغيّر إعدادات CFUN وظائف الراديو؛ ويتوقف السلوك الدقيق على برنامج المودم.",
        "Choose a preset to fill the AT field; it is not sent until you press Send. CFUN behavior varies by modem firmware and may interrupt mobile service." to "اختر أمرًا لملء حقل AT فقط؛ لن يُرسل حتى تضغط إرسال. يختلف سلوك CFUN حسب برنامج المودم وقد يقطع الخدمة الخلوية.",
        "Confirm modem functionality change" to "تأكيد تغيير وظائف المودم",
        "This may interrupt cellular data. The application cannot guarantee a no-disconnect transition." to "قد يؤدي ذلك إلى انقطاع بيانات الهاتف. لا يستطيع التطبيق ضمان تنفيذ التغيير دون انقطاع.",
        "Send anyway" to "إرسال على أي حال",
        "Cancel" to "إلغاء",
        "Switch failed" to "فشل التبديل",
        "Stopped; last band lock kept" to "توقف؛ تم الإبقاء على آخر قفل للنطاق",
        "Stopped; verification skipped" to "توقف؛ لم يتم التحقق من النتيجة",
        "Experimental: band locks can trigger network reselection and interrupt data. Pixel is not supported because its band-lock path restarts the radio." to "ميزة تجريبية: قد يؤدي قفل النطاقات إلى إعادة اختيار الشبكة وانقطاع البيانات. أجهزة Pixel غير مدعومة لأن مسار قفل النطاق فيها يعيد تشغيل الراديو.",
        "Stop Dynamic Band Switching before refreshing modem state." to "أوقف التبديل الديناميكي للنطاقات قبل تحديث حالة المودم.",
        "Stop Dynamic Band Switching before changing SIM." to "أوقف التبديل الديناميكي للنطاقات قبل تغيير الشريحة.",
        "Stop Dynamic Band Switching before changing the modem backend." to "أوقف التبديل الديناميكي للنطاقات قبل تغيير واجهة المودم.",
        "Stop Dynamic Band Switching before changing modem persistence." to "أوقف التبديل الديناميكي للنطاقات قبل تغيير حفظ إعدادات المودم.",
        "Stop Dynamic Band Switching before changing the band selection." to "أوقف التبديل الديناميكي للنطاقات قبل تغيير تحديد النطاقات.",
        "Stop Dynamic Band Switching before using other modem controls." to "أوقف التبديل الديناميكي للنطاقات قبل استخدام أدوات المودم الأخرى.",
        "Wait for the current Dynamic Band Switching task to stop." to "انتظر حتى تتوقف مهمة التبديل الديناميكي الحالية.",
        "Enter an interval between 10 and 3600 seconds." to "أدخل فاصلًا بين 10 و3600 ثانية.",
        "Wait for the current modem operation to finish." to "انتظر حتى تنتهي عملية المودم الحالية.",
        "The modem helper is not ready. Reconnect before starting Dynamic Band Switching." to "خدمة المساعدة للمودم غير جاهزة. أعد الاتصال قبل بدء التبديل الديناميكي.",
        "Dynamic Band Switching is unavailable while a cell or network lock is active." to "لا يتوفر التبديل الديناميكي أثناء وجود قفل خلية أو شبكة.",
        "Dynamic Band Switching is blocked by an active cell lock." to "تم إيقاف التبديل الديناميكي بسبب وجود قفل خلية نشط.",
        "Couldn't verify that the cell lock is cleared. Refresh modem state and try again." to "تعذّر التأكد من إزالة قفل الخلية. حدّث حالة المودم ثم أعد المحاولة.",
        "Dynamic Band Switching is unavailable on Pixel because applying band locks restarts the radio." to "لا يتوفر التبديل الديناميكي على Pixel لأن تطبيق قفل النطاق يعيد تشغيل الراديو.",
        "Connect to a supported modem backend before starting Dynamic Band Switching." to "اتصل بواجهة مودم مدعومة قبل بدء التبديل الديناميكي.",
        "Select at least two supported LTE or NR bands before starting." to "حدد نطاقين مدعومين على الأقل من LTE أو NR قبل البدء.",
        "Couldn't determine whether the active connection is LTE or NR. Refresh the connection and try again." to "تعذّر تحديد ما إذا كان الاتصال الحالي LTE أم NR. حدّث الاتصال ثم أعد المحاولة.",
        "Band groups" to "مجموعات النطاقات",
        "Choose a radio group, then apply the selection." to "اختر مجموعة راديوية ثم طبّق التحديد.",
        "selected bands" to "نطاقات محددة",
        "radio groups" to "مجموعات راديوية",
        "network mode" to "وضع الشبكة",
        "Preferred radio technologies" to "تقنيات الراديو المفضلة",
        "ACTIVE" to "نشط",
        "How changes apply" to "كيفية تطبيق التغييرات",
        "Choose which bands stay pinned here." to "اختر النطاقات التي تريد تثبيتها هنا.",
        "FAVORITES" to "المفضلة",
        "BANDS" to "نطاقات",
        "Favorites" to "المفضلة",
        "Show all" to "عرض الكل",
        "Clear" to "مسح",
        "Unlock group" to "فك قفل المجموعة",
        "Apply selection" to "تطبيق التحديد",
        "Cell-lock status" to "حالة قفل الخلية",
        "Nearby cells" to "الخلايا القريبة",
        "Tap a cell to use its channel and PCI in the lock form below." to "اضغط على خلية لاستخدام قناتها وPCI في نموذج القفل أدناه.",
        "Build a cell lock" to "إعداد قفل خلية",
        "Network selection" to "اختيار الشبكة",
        "Control room" to "مركز التحكم",
        "Modem backend" to "واجهة المودم",
        "SIM confirmation required" to "يلزم تأكيد الشريحة",
        "Control path ready" to "مسار التحكم جاهز",
        "SIM" to "الشريحة",
        "Not selected" to "غير محددة",
        "Detecting" to "جارٍ الاكتشاف",
        "Access" to "الصلاحية",
        "Unavailable" to "غير متاح",
        "Radio maintenance" to "صيانة الراديو",
        "Temporarily toggles airplane mode." to "يبدّل وضع الطيران مؤقتًا.",
        "Reconnects data using QMI." to "يعيد اتصال البيانات عبر QMI.",
        "Restarts the selected SIM." to "يعيد تشغيل الشريحة المحددة.",
        "Samsung bands" to "نطاقات سامسونج",
        "Opens the OEM band menu." to "يفتح قائمة النطاقات الخاصة بالشركة المصنّعة.",
        "Control method" to "طريقة التحكم",
        "Language and display" to "اللغة والعرض",
        "Engineering console · AT" to "وحدة أوامر AT",
        "Engineering console · QMI" to "وحدة أوامر QMI",
        "Carrier composition" to "تركيب الحوامل",
        "Serving carrier" to "الحامل الحالي",
        "Band group" to "مجموعة النطاقات",
        "Network diagnostics" to "تشخيص الشبكة",
        "Read-only checks; this does not change the modem, radio, or game process." to "فحوصات للقراءة فقط؛ لا تغيّر المودم أو الراديو أو عملية اللعبة.",
        "Monitor" to "مراقبة",
        "Stop" to "إيقاف",
        "SIM selection needs confirmation" to "يلزم تأكيد اختيار الشريحة",
        "The active SIM mapping is not verified. Choose an active SIM from the selector; if none is listed, grant phone-state access. Modem controls stay disabled until the subscription ID and SIM slot can be matched." to "لم يتم التحقق من ربط الشريحة النشطة. اختر شريحة من القائمة، أو امنح إذن حالة الهاتف إذا لم تظهر أي شريحة. ستظل أدوات المودم معطلة حتى يمكن مطابقة Subscription ID ومنفذ الشريحة.",
        "Select an active SIM again before using modem controls." to "أعد اختيار شريحة نشطة قبل استخدام أدوات المودم.",
        "The active modem operation took too long to finish; its helper was closed safely." to "استغرقت عملية المودم وقتًا طويلًا؛ أُغلقت خدمة المساعدة بأمان.",
        "No valid update link is configured." to "لا يوجد رابط تحديث صالح.",
        "Couldn't open the update link." to "تعذّر فتح رابط التحديث.",
        "Backend" to "الواجهة الخلفية",
        "Needs root" to "يتطلب Root",
        "Detected for this device" to "تم اكتشافه لهذا الجهاز",
        "Keep band locks after reboot" to "الإبقاء على قفل النطاقات بعد إعادة التشغيل",
        "When this is off, locks are cleared on reboot." to "عند إيقافه، تُزال الأقفال بعد إعادة التشغيل.",
        "Language" to "اللغة",
        "Updates" to "التحديثات",
        "Check for updates" to "التحقق من التحديثات",
        "Looks for a new release on GitHub when the app starts." to "التحقق من إصدار جديد على GitHub عند تشغيل التطبيق.",
        "Checks for a release on launch when a releases API URL is configured." to "يفحص توفر إصدار عند التشغيل بعد إعداد رابط API للإصدارات.",
        "Radio" to "الراديو",
        "Restart radio" to "إعادة تشغيل الراديو",
        "Turns airplane mode on and off again." to "تشغيل وضع الطيران ثم إيقافه.",
        "Re-attach" to "إعادة الاتصال بالشبكة",
        "Drops the data connection and attaches again over QMI." to "قطع اتصال البيانات ثم الاتصال مجددًا عبر QMI.",
        "Reboot modem" to "إعادة تشغيل المودم",
        "This restarts the SIM too." to "سيُعاد تشغيل الشريحة أيضًا.",
        "Samsung band menu" to "قائمة نطاقات سامسونج",
        "Opens Samsung's hidden band settings." to "فتح إعدادات النطاقات المخفية من سامسونج.",
        "AT console" to "وحدة تحكم AT",
        "AT port" to "منفذ AT",
        "Command" to "الأمر",
        "Send" to "إرسال",
        "QMI console" to "وحدة تحكم QMI",
        "Service" to "الخدمة",
        "Message (hex)" to "الرسالة (ست عشري)",
        "TLV payload (hex)" to "بيانات TLV (ست عشري)",
        "NAS (service 3) talks to the selected SIM." to "تتواصل NAS (الخدمة 3) مع الشريحة المحددة.",
        "Output" to "المخرجات",
        "Clear" to "مسح",
        "Access" to "الوصول",
        "Modem command passthrough" to "تمرير أوامر المودم",
        "Device details, the passthrough check and the log" to "معلومات الجهاز وفحص تمرير الأوامر والسجل",
        "No AT ports found." to "لم يتم العثور على منافذ AT.",
        "SoC" to "النظام على الشريحة",
        "RIL" to "RIL",
        "Android SDK" to "إصدار Android SDK",
        "QMI over QRTR" to "QMI عبر QRTR",
        "HAL channel-lock API" to "واجهة قفل القنوات HAL",
        "Log" to "السجل",
        "About" to "حول التطبيق",
        "Previously selected SIM:" to "الشريحة المختارة سابقًا:",
        "Application" to "التطبيق",
        "Exit" to "خروج",
        "Stop app tasks and close safely." to "إيقاف مهام التطبيق وإغلاقه بأمان.",
        "by @ririxi (BTSearch.pl developer)" to "بواسطة @ririxi (مطور BTSearch.pl)",
        "Bunny fact:" to "معلومة عن الأرانب:",
        "A rabbit's teeth never stop growing, so they have to keep chewing to wear them down." to "تستمر أسنان الأرنب في النمو، لذلك يمضغ باستمرار ليبريها.",
        "A happy rabbit does a \"binky\": a jump with a twist in mid-air." to "يقفز الأرنب السعيد ويلتف في الهواء، وتُسمى هذه الحركة «بينكي».",
        "Rabbits can see almost all the way around themselves. Their one blind spot is right in front of the nose." to "تستطيع الأرانب الرؤية في معظم الاتجاهات، ولديها نقطة عمياء صغيرة أمام الأنف مباشرة.",
        "Rabbits can't vomit, so they're very careful about what they eat." to "لا تستطيع الأرانب التقيؤ، لذلك تنتبه كثيرًا لما تأكله.",
        "A content rabbit \"purrs\" by gently grinding its teeth." to "قد يطحن الأرنب المسترخي أسنانه برفق تعبيرًا عن ارتياحه.",
        "Baby rabbits are called kits." to "يُطلق على صغير الأرنب اسم الخِرْنِق.",
        "Rabbits are most active at dawn and dusk." to "تنشط الأرانب غالبًا عند الفجر والغروب.",
        "Those big ears also work as radiators and help a rabbit cool down." to "تساعد أذنا الأرنب الكبيرتان على تبديد الحرارة وتبريد جسمه.",
        "A group of rabbits is sometimes called a fluffle." to "يُطلق أحيانًا على مجموعة الأرانب اسم «فلَفِل».",
        "A thumping rabbit is warning everyone nearby that something seems off." to "يحذر الأرنب الذي يضرب الأرض بقدمه من حوله بوجود أمر مقلق.",
        "No cells found" to "لم يتم العثور على خلايا",
        "Cell scans need location access." to "يتطلب فحص الخلايا إذن الموقع.",
        "Not registered on a network." to "غير مسجل على شبكة.",
        "Nothing is pinned." to "لا يوجد قفل نشط.",
        "The cell is pinned." to "الخلية مثبتة.",
        "The network is pinned." to "الشبكة مثبتة.",
        "The bands and cell are pinned." to "النطاقات والخلية مثبتة.",
        "The bands and network are pinned." to "النطاقات والشبكة مثبتة.",
        "The cell and network are pinned." to "الخلية والشبكة مثبتتان.",
        "The bands, cell and network are pinned." to "النطاقات والخلية والشبكة مثبتة.",
        "The modem is pinned to a specific cell." to "المودم مثبت على خلية محددة.",
        "The modem picks cells on its own." to "يختار المودم الخلايا تلقائيًا.",
        "The phone is on" to "الهاتف متصل بـ",
        "Not locked" to "غير مقفل",
        "Set over QMI first. If the modem refuses, Android network selection is used instead." to "تُرسل عبر QMI أولًا. إذا رفض المودم، يُستخدم اختيار الشبكة في Android.",
        "Uses Android's manual network selection, which stays after a reboot." to "يستخدم اختيار الشبكة اليدوي في Android، ويستمر بعد إعادة التشغيل.",
        "Locks LTE and NR to a PCI (the SA PCell or the NSA SCG). Leave the NR PCI empty to lock only the frequency, and separate several PCIs with commas." to "يقفل LTE وNR على PCI (خلية PCell في SA أو SCG في NSA). اترك PCI الخاص بـ NR فارغًا لقفل التردد فقط، وافصل عدة قيم بفواصل.",
        "Uses AT+EMMCHLCK, which holds one lock at a time. The PCI is optional, and the radio restarts to apply the lock." to "يستخدم AT+EMMCHLCK لقفل واحد في كل مرة. PCI اختياري ويُعاد تشغيل الراديو لتطبيق القفل.",
        "This modem doesn't take AT+EMMCHLCK, so the frequency (ARFCN) is locked through the radio HAL instead. That can't lock to a PCI, and some modems ignore it." to "هذا المودم لا يقبل AT+EMMCHLCK؛ لذلك يُقفل التردد (ARFCN) عبر radio HAL. لا يدعم ذلك القفل على PCI وقد تتجاهله بعض المودمات.",
        "Locks the frequency (ARFCN) through the radio HAL. This backend can't lock to a PCI, and some modems (stock Qualcomm, for example) only apply the band and ignore the channel." to "يقفل التردد (ARFCN) عبر radio HAL. لا تدعم هذه الواجهة القفل على PCI، وقد تطبق بعض المودمات النطاق وتتجاهل القناة.",
        "LTE" to "LTE",
        "NR" to "NR",
        "2G/3G" to "2G/3G",
        "Unknown" to "غير معروف",
        "Present" to "متاح",
        "Absent" to "غير متاح",
        "Yes" to "نعم",
        "No" to "لا",
    )

/** توحّد ترجمة النصوص الثابتة، وتترك أسماء التقنيات والقيم التي يرسلها المودم دون تغيير. */
fun translateUiText(
    text: String,
    language: String,
): String {
    if (language != "ar") return text
    arabicText[text]?.let { return it }
    Regex("Show all (\\d+) bands").matchEntire(text)?.let { return "عرض جميع النطاقات (${it.groupValues[1]})" }
    Regex("Version (.+) is out\\.").matchEntire(text)?.let { return "يتوفر الإصدار ${it.groupValues[1]}." }
    Regex("Version (.+)").matchEntire(text)?.let { return "الإصدار ${it.groupValues[1]}" }
    Regex("Previously selected SIM: (.+)").matchEntire(text)?.let { return "الشريحة المختارة سابقًا: ${it.groupValues[1]}" }
    Regex("(.+), (\\d+) carriers?").matchEntire(text)?.let { return "${it.groupValues[1]}، ${it.groupValues[2]} من الحوامل" }
    Regex("B(\\d+) at ([\\d.]+) MHz").matchEntire(text)?.let { return "النطاق B${it.groupValues[1]} عند ${it.groupValues[2]} MHz" }
    Regex("SIM (\\d+), (.+)\\. Switch SIM").matchEntire(text)?.let { return "الشريحة ${it.groupValues[1]}، ${it.groupValues[2]}. تبديل الشريحة" }
    Regex("SIM (\\d+): (.+)").matchEntire(text)?.let { return "الشريحة ${it.groupValues[1]}: ${it.groupValues[2]}" }
    Regex("Services checked \\((\\d+)\\)").matchEntire(text)?.let { return "الخدمات التي تم فحصها (${it.groupValues[1]})" }
    Regex("(\\d+) radio groups are currently locked\\.").matchEntire(text)?.let { return "مجموعات الراديو المقفلة حاليًا: ${it.groupValues[1]}." }
    Regex("(\\d+) of (\\d+) selected(.*)").matchEntire(text)?.let { match ->
        val suffix =
            match.groupValues[3]
                .replace(", not applied yet", "، لم يُطبق بعد")
                .replace(", locked but the phone is on ", "، مقفل لكن الهاتف يستخدم ")
                .replace(", locked", "، مقفل")
        return "تم تحديد ${match.groupValues[1]} من ${match.groupValues[2]}$suffix"
    }
    Regex("Locked to (.+)").matchEntire(text)?.let { return "مقفل على ${it.groupValues[1]}" }
    Regex("Connected to (.+)\\.").matchEntire(text)?.let { return "متصل بـ ${it.groupValues[1]}." }
    Regex("Found (.+)\\.").matchEntire(text)?.let { return "تم العثور على ${it.groupValues[1]}." }
    Regex("Select at least one supported (.+) band before starting\\.").matchEntire(text)?.let {
        return "حدد نطاقًا مدعومًا واحدًا على الأقل من ${it.groupValues[1]} قبل البدء."
    }
    Regex("The modem did not provide a readable (.+) band-lock state\\. No further lock command was sent\\.").matchEntire(text)?.let {
        return "لم يقدّم المودم حالة مقروءة لقفل النطاق ${it.groupValues[1]}؛ لم يُرسل أمر قفل آخر."
    }
    Regex("The modem readback did not show (.+) as the active band lock\\. Switching stopped\\.").matchEntire(text)?.let {
        return "لم تُظهر قراءة المودم أن ${it.groupValues[1]} هو قفل النطاق النشط؛ تم إيقاف التبديل."
    }
    Regex("(.+) is confirmed as the active lock, but no serving-carrier data was available\\. Switching stopped\\.").matchEntire(text)?.let {
        return "تم تأكيد ${it.groupValues[1]} كقفل نشط، لكن بيانات الحوامل الخادمة غير متاحة؛ تم إيقاف التبديل."
    }
    Regex("(.+) is confirmed as the active lock, but it was not reported among serving carriers after bounded refreshes\\. Switching stopped without repeating the lock command\\.").matchEntire(text)?.let {
        return "تم تأكيد ${it.groupValues[1]} كقفل نشط، لكن النطاق لم يظهر ضمن الحوامل الخادمة بعد تحديثات محدودة؛ توقف التبديل دون إعادة إرسال أمر القفل."
    }
    Regex("Dynamic Band Switching stopped\\. (.+)").matchEntire(text)?.let {
        return "توقف التبديل الديناميكي للنطاقات. ${it.groupValues[1]}"
    }
    Regex("The phone is on (.+)\\.").matchEntire(text)?.let { return "الهاتف متصل بـ ${it.groupValues[1]}." }
    Regex("(.+) bands (locked|unlocked)\\.").matchEntire(text)?.let {
        val action = if (it.groupValues[2] == "locked") "مقفلة" else "تم إلغاء قفلها"
        return "نطاقات ${it.groupValues[1]} $action."
    }
    Regex("5G mode switched to (.+)\\.").matchEntire(text)?.let { return "تم تغيير وضع 5G إلى ${it.groupValues[1]}." }
    Regex("Network mode is now \"(.+?)\"\\.(.*)").matchEntire(text)?.let {
        return "وضع الشبكة الآن \"${it.groupValues[1]}\".${it.groupValues[2]}"
    }
    Regex("SIM (\\d+) is attached again\\.").matchEntire(text)?.let { return "تمت إعادة توصيل الشريحة ${it.groupValues[1]} بالشبكة." }
    Regex("SIM (\\d+) is locked to (.+)\\.").matchEntire(text)?.let { return "الشريحة ${it.groupValues[1]} مقفلة على ${it.groupValues[2]}." }
    Regex("(.+) bands (locked|unlocked)\\. The radio restarted to apply the change\\.").matchEntire(text)?.let {
        val action = if (it.groupValues[2] == "locked") "مقفلة" else "تم إلغاء قفلها"
        return "نطاقات ${it.groupValues[1]} $action. أُعيد تشغيل الراديو لتطبيق التغيير."
    }
    Regex("(.+) bands (locked|unlocked)\\.").matchEntire(text)?.let {
        val action = if (it.groupValues[2] == "locked") "مقفلة" else "تم إلغاء قفلها"
        return "نطاقات ${it.groupValues[1]} $action."
    }
    Regex("The helper isn't running, so nothing was changed\\. Reconnecting\\.").matchEntire(text)?.let {
        return "المساعد غير قيد التشغيل، لم يتغير شيء. جارٍ إعادة الاتصال."
    }
    Regex("(.*), through Shizuku").matchEntire(text)?.let { return "${it.groupValues[1]}، عبر Shizuku" }
    Regex("Changes apply right away over QMI\\. (Locks stay after a reboot \\(you can change that in Menu\\)\\.|Locks are cleared when you reboot\\.)")
        .matchEntire(text)?.let {
            val persistence = if (it.groupValues[1].startsWith("Locks stay")) "تبقى الأقفال بعد إعادة التشغيل (يمكن تغيير ذلك في الأدوات)." else "تُزال الأقفال عند إعادة التشغيل."
            return "تُطبق التغييرات فورًا عبر QMI. $persistence"
        }
    Regex("Changes are sent with AT\\+EPBSE and AT\\+EPBSEH on (.+)\\.").matchEntire(text)?.let {
        return "تُرسل التغييرات عبر AT+EPBSE وAT+EPBSEH على ${it.groupValues[1]}."
    }
    if (text == "Changes are saved to the modem's NV memory, and the radio restarts by itself to apply them.") {
        return "تُحفظ التغييرات في ذاكرة NV للمودم، ويُعاد تشغيل الراديو تلقائيًا لتطبيقها."
    }
    if (text == "This uses the radio HAL's system selection channels. Whether the modem follows them depends on the phone maker.") {
        return "يستخدم هذا قنوات اختيار النظام في radio HAL. يعتمد التزام المودم بها على الشركة المصنّعة للهاتف."
    }
    Regex("Android accepted the lock, but the phone is on (.+)\\.").matchEntire(text)?.let {
        return "قبل Android القفل، لكن الهاتف يستخدم ${it.groupValues[1]}."
    }
    return text
}

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    val language = LocalAppLanguage.current
    val displayText = translateUiText(text, language)
    MaterialText(
        text = if (language == "ar") BidiFormatter.getInstance(true).unicodeWrap(displayText) else displayText,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style,
    )
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    MaterialText(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style,
    )
}