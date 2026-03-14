package com.robost.blip.core.transport

import com.robost.blip.core.protocol.navigation.DriveRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.put
import io.ktor.client.request.setBody

interface NavigationApi {
    suspend fun drive(targetRpm: Int, targetDirection: Int, targetAngle: Int)
    suspend fun stop()
    suspend fun recover()
}

class NavigationApiImpl(val httpClient: HttpClient) : NavigationApi {
    companion object {
        private const val BASE_URL = "http://10.0.0.186/"
    }

    override suspend fun drive(targetRpm: Int, targetDirection: Int, targetAngle: Int) {
        httpClient.put("${BASE_URL}drive") {
            setBody(
                DriveRequest(
                    type = "drive",
                    targetRpm = targetRpm,
                    targetDirection = targetDirection,
                    targetAngle = targetAngle
                )
            )
        }
    }

    override suspend fun stop() {
        httpClient.put("${BASE_URL}stop")
    }

    override suspend fun recover() {
        httpClient.put("${BASE_URL}recover")
    }
}
