package com.example

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class DownloadViewModel : ViewModel() {

    private val client = OkHttpClient.Builder().build()

    sealed interface UIState {
        object Idle : UIState
        object Resolving : UIState
        data class Resolved(val location: EarthUrlParser.ParsedLocation) : UIState
        data class Downloading(val progress: Float, val status: String) : UIState
        data class Success(val savedUri: Uri?, val title: String) : UIState
        data class Error(val message: String) : UIState
    }

    private val _state = MutableStateFlow<UIState>(UIState.Idle)
    val state: StateFlow<UIState> = _state.asStateFlow()

    // Quality setting: 2 for Medium, 3 for High, 4 for Ultra
    private val _quality = MutableStateFlow(3)
    val quality: StateFlow<Int> = _quality.asStateFlow()

    fun setQuality(zoom: Int) {
        _quality.value = zoom
    }

    fun parseUrl(url: String) {
        if (url.trim().isEmpty()) {
            _state.value = UIState.Error("Please enter or paste a valid Google Earth link.")
            return
        }

        viewModelScope.launch {
            _state.value = UIState.Resolving
            try {
                val parsed = EarthUrlParser.resolveAndParse(url)
                if (parsed != null) {
                    _state.value = UIState.Resolved(parsed)
                } else {
                    _state.value = UIState.Error("Could not extract panorama ID. Please ensure you share an official or user-uploaded Google Earth link.")
                }
            } catch (e: Exception) {
                _state.value = UIState.Error("Failed to parse link: ${e.localizedMessage}")
            }
        }
    }

    fun resetState() {
        _state.value = UIState.Idle
    }

    fun downloadPanorama(context: Context, location: EarthUrlParser.ParsedLocation) {
        val zoom = _quality.value
        viewModelScope.launch {
            try {
                _state.value = UIState.Downloading(0f, "Initializing download...")
                val resultBytes = withContext(Dispatchers.IO) {
                    if (location.isUserUploaded) {
                        downloadUserUploaded(location.panoId, zoom) { progress, text ->
                            viewModelScope.launch {
                                _state.value = UIState.Downloading(progress, text)
                            }
                        }
                    } else {
                        downloadOfficialStitched(location.panoId, zoom) { progress, text ->
                            viewModelScope.launch {
                                _state.value = UIState.Downloading(progress, text)
                            }
                        }
                    }
                }

                if (resultBytes == null || resultBytes.isEmpty()) {
                    _state.value = UIState.Error("Downloaded data was empty. Check your connection.")
                    return@launch
                }

                // Get stitched dimensions to put into GPano metadata
                // For user uploaded we can decode bounds quickly without loading full pixel array in memory
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(resultBytes, 0, resultBytes.size, options)
                val width = if (options.outWidth > 0) options.outWidth else 4096
                val height = if (options.outHeight > 0) options.outHeight else 2048

                _state.value = UIState.Downloading(0.95f, "Writing EXIF & GPano Metadata...")

                val savedUri = withContext(Dispatchers.IO) {
                    savePanoramaToGallery(
                        context = context,
                        imageBytes = resultBytes,
                        location = location,
                        width = width,
                        height = height
                    )
                }

                if (savedUri != null) {
                    _state.value = UIState.Success(savedUri, location.name)
                } else {
                    _state.value = UIState.Error("Failed to save image to the library.")
                }

            } catch (oom: OutOfMemoryError) {
                System.gc()
                _state.value = UIState.Error("Device memory exhausted stitching high-res panorama. Please try Select Medium or High quality instead!")
            } catch (e: Exception) {
                Log.e("DownloadViewModel", "Download failed: ${e.message}", e)
                _state.value = UIState.Error("Download failed: ${e.localizedMessage}")
            }
        }
    }

    private fun downloadUserUploaded(
        photoId: String,
        zoomSetting: Int,
        onProgress: (Float, String) -> Unit
    ): ByteArray? {
        // Size setting: original (s0), or scaling based on quality
        val sizeParam = when (zoomSetting) {
            2 -> "s2048"
            3 -> "s4096"
            4 -> "s8192"
            else -> "s0" // Original size
        }

        val url = "https://lh3.googleusercontent.com/p/$photoId=$sizeParam"
        Log.d("DownloadViewModel", "Downloading user uploaded panorama from $url")
        onProgress(0.1f, "Connecting to Google Photo Sphere CDN...")

        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Google CDN returned error code ${response.code}")
            }
            onProgress(0.4f, "Downloading high-resolution sphere...")
            val body = response.body ?: return null
            val bytes = body.bytes()
            onProgress(0.9f, "Finalizing download data...")
            return bytes
        }
    }

    private fun downloadOfficialStitched(
        panoId: String,
        zoom: Int,
        onProgress: (Float, String) -> Unit
    ): ByteArray? {
        val numCols = when (zoom) {
            2 -> 4
            3 -> 8
            4 -> 16
            else -> 8
        }
        val numRows = when (zoom) {
            2 -> 2
            3 -> 4
            4 -> 8
            else -> 4
        }

        val totalWidth = numCols * 512
        val totalHeight = numRows * 512
        val totalTiles = numCols * numRows

        Log.d("DownloadViewModel", "Stitching official panorama. Zoom: $zoom, Cols: $numCols, Rows: $numRows, Dim: ${totalWidth}x${totalHeight}")
        onProgress(0.05f, "Creating empty canvas (${totalWidth}x${totalHeight}px)...")

        val resultBitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        val p = android.graphics.Paint()

        var downloadedCount = 0

        for (y in 0 until numRows) {
            for (x in 0 until numCols) {
                val tileUrl = "https://streetviewpixels-pa.googleapis.com/v1/tile?cb_client=maps_sv.tactile&panoid=$panoId&x=$x&y=$y&zoom=$zoom"
                
                try {
                    val request = Request.Builder().url(tileUrl).build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyBytes = response.body?.bytes()
                            if (bodyBytes != null && bodyBytes.isNotEmpty()) {
                                val tileBitmap = BitmapFactory.decodeByteArray(bodyBytes, 0, bodyBytes.size)
                                if (tileBitmap != null) {
                                    canvas.drawBitmap(tileBitmap, (x * 512).toFloat(), (y * 512).toFloat(), p)
                                    tileBitmap.recycle()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("DownloadViewModel", "Skip offline/missing tile at x=$x, y=$y: ${e.message}")
                }

                downloadedCount++
                val pct = downloadedCount.toFloat() / totalTiles.toFloat()
                onProgress(
                    0.05f + pct * 0.85f,
                    "Downloading & joining tiles ($downloadedCount / $totalTiles)..."
                )
            }
        }

        onProgress(0.9f, "Encoding canvas to JPEG stream...")
        val baos = ByteArrayOutputStream()
        resultBitmap.compress(Bitmap.CompressFormat.JPEG, 95, baos)
        resultBitmap.recycle()
        return baos.toByteArray()
    }

    private fun savePanoramaToGallery(
        context: Context,
        imageBytes: ByteArray,
        location: EarthUrlParser.ParsedLocation,
        width: Int,
        height: Int
    ): Uri? {
        try {
            // Step 1: Write EXIF location to a temp file
            val tempFile = File.createTempFile("pano_temp", ".jpg", context.cacheDir)
            FileOutputStream(tempFile).use { fos ->
                fos.write(imageBytes)
            }

            // Write GPS to EXIF
            val exifInterface = ExifInterface(tempFile.absolutePath)
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE, dec2DMS(location.latitude))
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (location.latitude >= 0) "N" else "S")
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, dec2DMS(location.longitude))
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (location.longitude >= 0) "E" else "W")
            // Tag it as equirectangular
            exifInterface.setAttribute(ExifInterface.TAG_MODEL, "Pano Downloader 360")
            exifInterface.saveAttributes()

            // Step 2: Read tags-embellished file back to byte array
            val exifBytes = tempFile.readBytes()
            tempFile.delete()

            // Step 3: Inject GPano Metadata
            val finalBytes = injectGPanoMetadata(exifBytes, width, height)

            // Step 4: Write final bytes to MediaStore
            val contentResolver = context.contentResolver
            val displayName = "360_${location.name.replace(" ", "_")}_${System.currentTimeMillis()}.jpg"

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Panoramas")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (imageUri != null) {
                contentResolver.openOutputStream(imageUri).use { os ->
                    if (os != null) {
                        os.write(finalBytes)
                        os.flush()
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    contentResolver.update(imageUri, contentValues, null, null)
                }
                return imageUri
            }
        } catch (e: Exception) {
            Log.e("DownloadViewModel", "Error saving panorama: ${e.message}", e)
        }
        return null
    }

    private fun dec2DMS(coordinate: Double): String {
        val absCoord = Math.abs(coordinate)
        val degrees = absCoord.toInt()
        val minutesCoord = (absCoord - degrees) * 60.0
        val minutes = minutesCoord.toInt()
        val seconds = (minutesCoord - minutes) * 60.0
        return "$degrees/1,$minutes/1,${(seconds * 1000).toInt()}/1000"
    }

    private fun injectGPanoMetadata(jpegBytes: ByteArray, width: Int, height: Int): ByteArray {
        val xmpXml = """
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description rdf:about="" xmlns:GPano="http://ns.google.com/photos/1.0/panorama/">
                  <GPano:UsePanoramaViewer>True</GPano:UsePanoramaViewer>
                  <GPano:ProjectionType>equirectangular</GPano:ProjectionType>
                  <GPano:FullPanoWidthPixels>$width</GPano:FullPanoWidthPixels>
                  <GPano:FullPanoHeightPixels>$height</GPano:FullPanoHeightPixels>
                  <GPano:CroppedAreaImageWidthPixels>$width</GPano:CroppedAreaImageWidthPixels>
                  <GPano:CroppedAreaImageHeightPixels>$height</GPano:CroppedAreaImageHeightPixels>
                  <GPano:CroppedAreaLeftPixels>0</GPano:CroppedAreaLeftPixels>
                  <GPano:CroppedAreaTopPixels>0</GPano:CroppedAreaTopPixels>
                </rdf:Description>
              </rdf:RDF>
            </x:xmpmeta>
        """.trimIndent()

        val xmpSignature = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.UTF_8)
        val xmlBytes = xmpXml.toByteArray(Charsets.UTF_8)
        val payloadSize = xmpSignature.size + xmlBytes.size
        val segmentSize = payloadSize + 2 // including 2 length bytes

        val bos = ByteArrayOutputStream()
        val bis = ByteArrayInputStream(jpegBytes)

        val marker1 = bis.read()
        val marker2 = bis.read()
        if (marker1 != 0xFF || marker2 != 0xD8) {
            return jpegBytes
        }

        // SOI
        bos.write(0xFF)
        bos.write(0xD8)

        // Inject our APP1 segment
        bos.write(0xFF)
        bos.write(0xE1)
        bos.write((segmentSize ushr 8) and 0xFF)
        bos.write(segmentSize and 0xFF)
        bos.write(xmpSignature)
        bos.write(xmlBytes)

        // Mirror the other original bytes
        val buffer = ByteArray(4096)
        var bytesRead: Int
        while (bis.read(buffer).also { bytesRead = it } != -1) {
            bos.write(buffer, 0, bytesRead)
        }

        return bos.toByteArray()
    }
}
