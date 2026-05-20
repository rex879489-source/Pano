package com.example

import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder
import java.util.Base64

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testUrlParsing() {
    val userUploadedUrl = "https://earth.app.goo.gl/?apn=com.google.earth&isi=293622097&ius=googleearth&link=https%3a%2f%2fearth.google.com%2fweb%2fsearch%2fTatacoa%2bDesert%2f%403.23076369,-75.16766361,435.10333638a,0d,60y,360h,85t,0r%2fdata%3dCn0aTxJJCiUweDhlM2I5MjFjODY2NDQyNzk6MHgxMTlhOGE3Y2M5YzljMWE5Gej5LgDo2wlAIbRwWYXNylLAKg5UYXRhY29hIERlc2VydBgBIAEiJgokCanDOjoywk1AEafDOjoywk3AGYUB2qA15UhAIcG0_uCC_0jAQgIIASIbChdDSUhNMG9nS0VJQ0FnSUNHMnE2ZXR3RRAFQgIIAEoNCP___________wEQAA"
    val officialUrl = "https://earth.app.goo.gl/?apn=com.google.earth&isi=293622097&ius=googleearth&link=https%3a%2f%2fearth.google.com%2fweb%2fsearch%2fTatacoa%2bDesert%2f%403.23373613,-75.16672196,451.72148079a,0d,60y,0h,85t,0r%2fdata%3dCn0aTxJJCiUweDhlM2I5MjFjODY2NDQyNzk6MHgxMTlhOGE3Y2M5YzljMWE5Gej5LgDo2wlAIbRwWYXNylLAKg5UYXRhY29hIERlc2VydBgBIAEiJgokCanDOjoywk1AEafDOjoywk3AGYUB2qA15UhAIcG0_uCC_0jAQgIIASIaChY3NWQ0MHRpYzhORXh3ZVVRRVhhQ2pREAJCAggASg0I____________ARAA"

    println("=== USER UPLOADED URL PARSING ===")
    analyzeUrl(userUploadedUrl)

    println("=== OFFICIAL URL PARSING ===")
    analyzeUrl(officialUrl)
  }

  private fun analyzeUrl(url: String) {
    val decodedUrl = URLDecoder.decode(url, "UTF-8")
    println("Decoded URL: $decodedUrl")

    val dataRegex = """data=([^/&]+)""".toRegex()
    val dataMatch = dataRegex.find(decodedUrl)
    val rawDataString = dataMatch?.groupValues?.get(1) ?: ""
    println("data parameter string: $rawDataString")

    if (rawDataString.isNotEmpty()) {
      try {
        // Try both standard and url-safe Base64
        val decodedBytes = try {
          Base64.getUrlDecoder().decode(rawDataString)
        } catch (e: Exception) {
          Base64.getDecoder().decode(rawDataString)
        }
        println("Decoded bytes length: ${decodedBytes.size}")

        // Let's print out readable ASCII strings of length >= 10
        val extractedStrings = mutableListOf<String>()
        val current = java.lang.StringBuilder()
        for (b in decodedBytes) {
          val c = b.toInt().toChar()
          if (b in 32..126) {
            current.append(c)
          } else {
            if (current.length >= 10) {
              extractedStrings.add(current.toString())
            }
            current.setLength(0)
          }
        }
        if (current.length >= 10) {
          extractedStrings.add(current.toString())
        }
        println("Extracted ASCII strings ($extractedStrings)")

        // Let's also print standard hex representation of the whole payload
        val hexString = decodedBytes.joinToString("") { "%02x".format(it) }
        println("Hex payload: $hexString")

        // Check if we can find typical Pano IDs like 75d40tic8NExweUQEXaCjQ inside
        val expectedPano = "75d40tic8NExweUQEXaCjQ"
        val expectedHex = expectedPano.map { "%02x".format(it.code) }.joinToString("")
        if (hexString.contains(expectedHex)) {
          println("FOUND OFFICIAL PANOID IN HEX PAYLOAD!")
        }
      } catch (e: Exception) {
        println("Base64 decode failed: ${e.message}")
      }
    }
  }
}
