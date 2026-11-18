-keep class com.lockfox.search.** { *; }
-keep public class * extends android.app.Activity
-keep public class * extends android.appwidget.AppWidgetProvider
-keep public class * extends android.content.BroadcastReceiver
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
