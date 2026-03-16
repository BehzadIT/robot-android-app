package com.bizi.roboapp.app.di

import android.util.Log
import com.bizi.roboapp.core.transport.NavigationWebSocketApi
import com.bizi.roboapp.core.transport.NavigationWebSocketApiImpl
import com.bizi.roboapp.feature.drivecontrol.ui.HomeViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.util.concurrent.TimeUnit
import org.koin.android.ext.koin.androidContext

val viewModelModule = module {
    viewModel { HomeViewModel(get()) }
}

private const val TIME_OUT = 6000L
private const val WS_PING_INTERVAL_SECONDS = 15L

val networkModule = module {
    factory<NavigationWebSocketApi> { NavigationWebSocketApiImpl(get(), androidContext()) }

    single {
        val okHttpClient = OkHttpClient.Builder()
            // Best practice with Ktor's OkHttp engine is to configure websocket
            // ping on the underlying OkHttp client, not via the Ktor plugin.
            .pingInterval(WS_PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .build()

        HttpClient(OkHttp) {
            engine {
                preconfigured = okHttpClient
            }
            install(WebSockets)
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        Log.d("HttpLogging:", message)
                    }

                }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = TIME_OUT
            }
        }
    }
}
