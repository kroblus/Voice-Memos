package com.voicememos.aicalendar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.voicememos.aicalendar.MainActivity
import com.voicememos.aicalendar.R
import com.voicememos.aicalendar.data.AppDatabase
import com.voicememos.aicalendar.data.toDomain
import com.voicememos.aicalendar.domain.CalendarEvent
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class ShengliWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun updateAll(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(ComponentName(appContext, ShengliWidgetProvider::class.java))
            updateWidgets(appContext, manager, ids)
        }

        private fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
        ) {
            if (appWidgetIds.isEmpty()) return
            val nextEvent = runBlocking(Dispatchers.IO) {
                AppDatabase.get(context)
                    .calendarEventDao()
                    .nextScheduled(System.currentTimeMillis())
                    ?.toDomain()
            }
            appWidgetIds.forEach { widgetId ->
                appWidgetManager.updateAppWidget(widgetId, buildViews(context, nextEvent))
            }
        }

        private fun buildViews(context: Context, event: CalendarEvent?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_next_event)
            val openIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openIntent)
            views.setOnClickPendingIntent(R.id.widget_voice_button, openIntent)
            if (event == null) {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_empty_title))
                views.setTextViewText(R.id.widget_time, context.getString(R.string.widget_empty_subtitle))
            } else {
                views.setTextViewText(R.id.widget_title, event.title)
                views.setTextViewText(R.id.widget_time, event.formatWidgetTime())
            }
            return views
        }

        private fun CalendarEvent.formatWidgetTime(): String {
            val zone = ZoneId.systemDefault()
            val pattern = if (allDay) "M\u6708d\u65e5 E" else "M\u6708d\u65e5 E HH:mm"
            return startAt.atZone(zone).format(DateTimeFormatter.ofPattern(pattern, Locale.SIMPLIFIED_CHINESE))
        }
    }
}
