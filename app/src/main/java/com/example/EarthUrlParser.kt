package com.example

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder

object EarthUrlParser {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    data class ParsedLocation(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val isUserUploaded: Boolean,
        val panoId: String,
        val rawData: String
    )

    suspend fun resolveAndParse(url: String): ParsedLocation? = withContext(Dispatchers.IO) {
        var finalUrl = url
        // If it looks like a shortened URL (goo.gl, etc.), follow it!
        if (url.contains("goo.gl") || url.contains("page.link") || url.length < 50) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .head() // Use HEAD request to find the redirected URL quickly
                    .build()
                client.newCall(request).execute().use { response ->
                    val resolved = response.request.url.toString()
                    Log.d("EarthUrlParser", "Short URL resolved from $url to $resolved")
                    finalUrl = resolved
                }
            } catch (e: Exception) {
                // If HEAD fails, try full GET
                try {
                    val request = Request.Builder().url(url).build()
                    client.newCall(request).execute().use { response ->
                        finalUrl = response.request.url.toString()
                    }
                } catch (ex: Exception) {
                    Log.e("EarthUrlParser", "Failed to follow redirect: ${ex.message}", ex)
                }
            }
        }
        return@withContext parse(finalUrl)
    }

    fun parse(url: String): ParsedLocation? {
        val decodedUrl = try {
            URLDecoder.decode(url, "UTF-8")
        } catch (e: Exception) {
            url
        }

        // 1. Extract name from path if available
        // E.g. ".../search/Tatacoa+Desert/@..."
        val nameRegex = """/search/([^/@]+)""".toRegex()
        val nameMatch = nameRegex.find(decodedUrl)
        val rawName = nameMatch?.groupValues?.get(1)?.replace("+", " ") ?: "Shared Panorama"
        val name = try {
            URLDecoder.decode(rawName, "UTF-8").trim()
        } catch (e: Exception) {
            rawName.trim()
        }

        // 2. Extract Lat & Lng
        // E.g. "@3.23076369,-75.16766361"
        val coordsRegex = """@(-?\d+\.\d+),(-?\d+\.\d+)""".toRegex()
        val coordsMatch = coordsRegex.find(decodedUrl)
        val latitude = coordsMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val longitude = coordsMatch?.groupValues?.get(2)?.toDoubleOrNull() ?: 0.0

        // 3. Extract the "data" parameter, which contains our protobuf base64
        val dataRegex = """data=([^/&]+)""".toRegex()
        val dataMatch = dataRegex.find(decodedUrl)
        val rawDataString = dataMatch?.groupValues?.get(1) ?: ""

        var panoId = ""
        var isUserUploaded = false

        if (rawDataString.isNotEmpty()) {
            try {
                // Replace characters to be Base64-safe
                val cleanedData = rawDataString.replace('_', '/').replace('-', '+')
                val decodedBytes = try {
                    android.util.Base64.decode(cleanedData, android.util.Base64.DEFAULT)
                } catch (e: Exception) {
                    android.util.Base64.decode(rawDataString, android.util.Base64.DEFAULT)
                }

                val extractedStrings = extractAsciiStrings(decodedBytes)
                Log.d("EarthUrlParser", "Extracted ASCII strings count: ${extractedStrings.size}")
                
                // Let's filter candidates that could be panoIds.
                // 1. Official panoIds are 22 chars
                // 2. User-uploaded (CIHM... or similar) can be 23 chars (or 21-22-23)
                // Filter out names and place coordinates
                val candidates = extractedStrings.filter {
                    val len = it.length
                    len in 21..24 && !it.startsWith("0x") && !it.startsWith("%0x") && !it.contains(" ")
                }

                // Prefer user uploaded (typically 23 chars, starts with CIHM etc.)
                val userUploadedPano = candidates.find { it.startsWith("CIHM") || it.startsWith("AF1Qip") }
                if (userUploadedPano != null) {
                    panoId = userUploadedPano
                    isUserUploaded = true
                } else {
                    // Try to guess by length
                    val officialPano = candidates.find { it.length == 22 }
                    if (officialPano != null) {
                        panoId = officialPano
                        isUserUploaded = false
                    } else if (candidates.isNotEmpty()) {
                        panoId = candidates.first()
                        isUserUploaded = panoId.length == 23 || panoId.startsWith("CIHM")
                    }
                }
            } catch (e: Exception) {
                Log.e("EarthUrlParser", "Base64 parsing failed: ${e.message}", e)
            }
        }

        // Search in URL itself as fallback
        if (panoId.isEmpty()) {
            val ciHMatch = """CIHM[a-zA-Z0-9_\-]+""".toRegex().find(decodedUrl)
            if (ciHMatch != null) {
                panoId = ciHMatch.value
                isUserUploaded = true
            } else {
                val af1Match = """AF1Qip[a-zA-Z0-9_\-]+""".toRegex().find(decodedUrl)
                if (af1Match != null) {
                    panoId = af1Match.value
                    isUserUploaded = true
                }
            }
        }

        if (panoId.isEmpty()) {
            return null
        }

        return ParsedLocation(
            name = name,
            latitude = latitude,
            longitude = longitude,
            isUserUploaded = isUserUploaded,
            panoId = panoId,
            rawData = rawDataString
        )
    }

    private fun extractAsciiStrings(bytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        val current = java.lang.StringBuilder()
        for (b in bytes) {
            val c = b.toInt().toChar()
            if (b in 32..126) { // Printable ASCII
                current.append(c)
            } else {
                if (current.length >= 10) {
                    result.add(current.toString())
                }
                current.setLength(0)
            }
        }
        if (current.length >= 10) {
            result.add(current.toString())
        }
        return result
    }
}
