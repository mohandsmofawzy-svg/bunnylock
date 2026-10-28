// تُعرّف إضافات Gradle هنا مركزيًا وتُفعّل داخل الوحدات عند الحاجة.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
