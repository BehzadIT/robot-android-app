package com.robost.blip.data

import io.ktor.client.HttpClient
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface NavigationApi {
    suspend fun drive(targetRpm: Int, targetDirection: Int)
    suspend fun stop()
}

class NavigationApiImpl(val httpClient: HttpClient) : NavigationApi {
    companion object {
        private const val BASE_URL = "http://10.0.0.186/"
    }

    override suspend fun drive(targetRpm: Int, targetDirection: Int) {
        httpClient.put("${BASE_URL}drive") {
            setBody(
                DriveRequest(
                    type = "drive",
                    targetRpm = targetRpm,
                    targetDirection = targetDirection
                )
            )
        }
    }

    override suspend fun stop() {
        httpClient.put("${BASE_URL}stop")
    }
}

@Serializable
data class DriveRequest(
    @SerialName("type") val type: String,
    @SerialName("target_rpm") val targetRpm: Int,
    @SerialName("target_direction") val targetDirection: Int
)