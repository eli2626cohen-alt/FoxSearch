package com.lockfox.search.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.lockfox.search.MainActivity
import com.lockfox.search.R

class FoxSearchWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE_STYLE = "com.lockfox.search.ACTION_TOGGLE_WIDGET_STYLE"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, FoxSearchWidget::class.java))
            for (id in ids) {
                updateAppWidget(context, appWidgetManager, id)
            }
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences("fox_search_prefs", Context.MODE_PRIVATE)
            val style = prefs.getString("widget_style", "light") ?: "light"

            val searchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val searchPendingIntent = PendingIntent.getActivity(
                context,
                0,
                searchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val toggleIntent = Intent(context, FoxSearchWidget::class.java).apply {
                action = ACTION_TOGGLE_STYLE
            }
            val togglePendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val views = RemoteViews(context.packageName, R.layout.widget_search_bar).apply {
                setOnClickPendingIntent(R.id.widgetContainer, searchPendingIntent)
                setOnClickPendingIntent(R.id.tvWidgetBadge, togglePendingIntent)

                when (style) {
                    "dark" -> {
                        setInt(R.id.widgetContainer, "setBackgroundResource", R.drawable.bg_widget_bar_dark)
                        setTextColor(R.id.tvWidgetSearchHint, Color.parseColor("#9DA2AB"))
                        setTextColor(R.id.tvWidgetBadge, Color.parseColor("#A8C7FA"))
                    }
                    "translucent" -> {
                        setInt(R.id.widgetContainer, "setBackgroundResource", R.drawable.bg_widget_bar_translucent)
                        setTextColor(R.id.tvWidgetSearchHint, Color.parseColor("#E0E0E0"))
                        setTextColor(R.id.tvWidgetBadge, Color.parseColor("#FFFFFF"))
                    }
                    else -> { // "light"
                        setInt(R.id.widgetContainer, "setBackgroundResource", R.drawable.bg_widget_bar_light)
                        setTextColor(R.id.tvWidgetSearchHint, Color.parseColor("#3C4043"))
                        setTextColor(R.id.tvWidgetBadge, Color.parseColor("#1A73E8"))
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

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_STYLE) {
            val prefs = context.getSharedPreferences("fox_search_prefs", Context.MODE_PRIVATE)
            val current = prefs.getString("widget_style", "light") ?: "light"
            val next = when (current) {
                "light" -> "dark"
                "dark" -> "translucent"
                else -> "light"
            }
            prefs.edit().putString("widget_style", next).apply()
            updateAllWidgets(context)
            FoxClockSearchWidget.updateAllWidgets(context)
        }
    }
}
