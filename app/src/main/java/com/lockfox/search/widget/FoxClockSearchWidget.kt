package com.lockfox.search.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.provider.AlarmClock
import android.widget.RemoteViews
import com.lockfox.search.MainActivity
import com.lockfox.search.R

class FoxClockSearchWidget : AppWidgetProvider() {

    companion object {
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, FoxClockSearchWidget::class.java))
            for (id in ids) {
                updateAppWidget(context, appWidgetManager, id)
            }
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences("fox_search_prefs", Context.MODE_PRIVATE)
            val style = prefs.getString("widget_style", "light") ?: "light"

            // 1. Intent para abrir la aplicación de Reloj
            val clockIntent = (context.packageManager.getLaunchIntentForPackage("com.android.deskclock")
                ?: Intent(AlarmClock.ACTION_SHOW_ALARMS)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }

            val clockPendingIntent = PendingIntent.getActivity(
                context,
                101,
                clockIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 2. Intent para abrir el Buscador Fox
            val searchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val searchPendingIntent = PendingIntent.getActivity(
                context,
                102,
                searchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 3. Intent para alternar estilo al tocar el badge FOX
            val toggleIntent = Intent(context, FoxSearchWidget::class.java).apply {
                action = FoxSearchWidget.ACTION_TOGGLE_STYLE
            }
            val togglePendingIntent = PendingIntent.getBroadcast(
                context,
                103,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val views = RemoteViews(context.packageName, R.layout.widget_clock_search).apply {
                setOnClickPendingIntent(R.id.clockContainer, clockPendingIntent)
                setOnClickPendingIntent(R.id.searchContainer, searchPendingIntent)
                setOnClickPendingIntent(R.id.tvClockWidgetBadge, togglePendingIntent)

                when (style) {
                    "dark" -> {
                        setInt(R.id.searchContainer, "setBackgroundResource", R.drawable.bg_widget_bar_dark)
                        setTextColor(R.id.tvClockSearchHint, Color.parseColor("#9DA2AB"))
                        setTextColor(R.id.tvClockWidgetBadge, Color.parseColor("#A8C7FA"))
                    }
                    "translucent" -> {
                        setInt(R.id.searchContainer, "setBackgroundResource", R.drawable.bg_widget_bar_translucent)
                        setTextColor(R.id.tvClockSearchHint, Color.parseColor("#E0E0E0"))
                        setTextColor(R.id.tvClockWidgetBadge, Color.parseColor("#FFFFFF"))
                    }
                    else -> { // "light"
                        setInt(R.id.searchContainer, "setBackgroundResource", R.drawable.bg_widget_bar_light)
                        setTextColor(R.id.tvClockSearchHint, Color.parseColor("#3C4043"))
                        setTextColor(R.id.tvClockWidgetBadge, Color.parseColor("#1A73E8"))
                    }
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
}
