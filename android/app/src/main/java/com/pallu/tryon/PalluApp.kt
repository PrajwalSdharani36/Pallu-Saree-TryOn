package com.pallu.tryon

import android.app.Application
import com.pallu.tryon.data.AppContainer

class PalluApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
