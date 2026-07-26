package com.voicememos.aicalendar

import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.voicememos.aicalendar.ui.CalendarApp
import com.voicememos.aicalendar.ui.CalendarViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: CalendarViewModel by viewModels {
        (application as ShengliApplication).container.calendarViewModelFactory()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalendarApp(viewModel = viewModel)
        }
        handleExternalEvent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalEvent(intent)
    }

    private fun handleExternalEvent(intent: Intent) {
        val customUri = intent.getStringExtra(CalendarContract.EXTRA_CUSTOM_APP_URI)
            ?: intent.dataString.takeIf { intent.action == Intent.ACTION_VIEW }
        customUri?.let {
            viewModel.openExternalEvent(it)
            setResult(RESULT_OK)
        }
    }
}
