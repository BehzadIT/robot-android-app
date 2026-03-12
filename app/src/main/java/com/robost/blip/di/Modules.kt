package com.robost.blip.di

import android.util.Log
import com.robost.blip.data.NavigationApi
import com.robost.blip.data.NavigationApiImpl
import com.robost.blip.data.NavigationWebSocketApi
import com.robost.blip.data.NavigationWebSocketApiImpl
import com.robost.blip.home.HomeViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.util.concurrent.TimeUnit
import org.koin.android.ext.koin.androidContext

val viewModelModule = module {
    viewModel { HomeViewModel(get(), get()) }
}

private const val TIME_OUT = 6000L
private const val WS_PING_INTERVAL_SECONDS = 15L

val networkModule = module {
    // Ktor
    factory<NavigationApi> { NavigationApiImpl(get()) }
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
            install(ContentNegotiation) {
                json()
            }
            install(WebSockets)
            //Logging
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
            //Http Response
            install(ResponseObserver) {
                onResponse { response ->
                    Log.d("HTTP status:", "${response.status.value}")
                }
            }

            // Headers
            install(DefaultRequest) {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }
        }
    }
}
