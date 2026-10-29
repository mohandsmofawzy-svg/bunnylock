package dev.ririxi.bunnylock.ui

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
        "Bands" to "النطاقات",
        "Cell lock" to "قفل الخلية",
        "Tools" to "الأدوات",
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
        "Carriers" to "الحوامل",
        "Carrier" to "الحامل",
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
        "Backend" to "الواجهة الخلفية",
        "Needs root" to "يتطلب Root",
        "Detected for this device" to "تم اكتشافه لهذا الجهاز",
        "Keep band locks after reboot" to "الإبقاء على قفل النطاقات بعد إعادة التشغيل",
        "When this is off, locks are cleared on reboot." to "عند إيقافه، تُزال الأقفال بعد إعادة التشغيل.",
        "Language" to "اللغة",
        "Updates" to "التحديثات",
        "Check for updates" to "التحقق من التحديثات",
        "Looks for a new release on GitHub when the app starts." to "التحقق من إصدار جديد على GitHub عند تشغيل التطبيق.",
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
        "Application" to "التطبيق",
        "Exit" to "خروج",
        "Stop app tasks and close safely." to "إيقاف مهام التطبيق وإغلاقه بأمان.",
        "by @ririxi (BTSearch.pl developer)" to "بواسطة @ririxi (مطور BTSearch.pl)",
        "Bunny fact:" to "معلومة عن الأرانب:",
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
    Regex("SIM (\\d+): (.+)").matchEntire(text)?.let { return "الشريحة ${it.groupValues[1]}: ${it.groupValues[2]}" }
    Regex("SIM (\\d+), (.+)\\. Switch SIM").matchEntire(text)?.let { return "الشريحة ${it.groupValues[1]}، ${it.groupValues[2]}. تبديل الشريحة" }
    Regex("Services checked \\((\\d+)\\)").matchEntire(text)?.let { return "الخدمات التي تم فحصها (${it.groupValues[1]})" }
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
    Regex("Changes apply right away over QMI\\. (Locks stay after a reboot \\(you can change that in Tools\\)\\.|Locks are cleared when you reboot\\.)")
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
    MaterialText(
        text = translateUiText(text, LocalAppLanguage.current),
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