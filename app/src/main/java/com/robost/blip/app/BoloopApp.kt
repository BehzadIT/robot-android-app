package com.robost.blip.app

import android.app.Application
import com.robost.blip.app.di.networkModule
import com.robost.blip.app.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin

class BoloopApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Koin or any other dependency injection framework here
        startKoin {
            androidContext(this@BoloopApp)
            modules(viewModelModule, networkModule)
        }
    }
}
