plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.ririxi.bunnylock.modem"
    // يجب أن يتوافق هذا مع compileSdk الخاص بالتطبيق عند دمج الوحدة.
    compileSdk = 37
    // إصدار NDK المستخدم لبناء مكتبة QRTR الأصلية المضمنة مع المساعد.
    ndkVersion = "29.0.14206865"

    defaultConfig {
        minSdk = 30
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        // CMake يبني مكتبة QRTR فقط؛ بقية تنفيذ المودم مكتوب بـ Kotlin.
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    // coroutines جزء من واجهة الوحدة العامة، أما annotation فتستخدم لتعريف قيود Android API.
    api(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.annotation)
    testImplementation(libs.junit)
}
