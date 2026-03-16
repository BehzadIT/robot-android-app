package com.bizi.roboapp.app

import android.app.Application
import com.bizi.roboapp.app.di.networkModule
import com.bizi.roboapp.app.di.viewModelModule
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
