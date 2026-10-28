# يبدأ app_process المساعد باسمه، فلا يستطيع R8 اكتشاف نقطة الدخول ويجب إبقاء أصنافه.
-keep class dev.ririxi.bunnylock.root.** { *; }
# تحفظ أسماء الدوال native كي تبقى مطابقة لروابط JNI في المكتبة الأصلية.
-keepclasseswithmembernames class * {
    native <methods>;
}
