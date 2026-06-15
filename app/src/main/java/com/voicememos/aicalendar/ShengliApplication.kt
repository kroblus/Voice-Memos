package com.voicememos.aicalendar

import android.app.Application
import com.voicememos.aicalendar.di.AppContainer

class ShengliApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
