package com.voicememos.aicalendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.voicememos.aicalendar.ui.CalendarApp
import com.voicememos.aicalendar.ui.CalendarViewModel
import com.voicememos.aicalendar.ui.theme.ShengliTheme

class MainActivity : ComponentActivity() {
    private val viewModel: CalendarViewModel by viewModels {
        (application as ShengliApplication).container.calendarViewModelFactory()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShengliTheme {
                CalendarApp(viewModel = viewModel)
            }
        }
    }
}
