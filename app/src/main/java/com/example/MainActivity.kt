package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DownloadViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle initial incoming sharing link from other applications
        var sharedUrl = ""
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val extraText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            sharedUrl = extractUrl(extraText)
        }

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    PanoramaDownloaderApp(
                        viewModel = viewModel,
                        sharedUrl = sharedUrl,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val extraText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val url = extractUrl(extraText)
            if (url.isNotEmpty()) {
                viewModel.parseUrl(url)
            }
        }
    }

    private fun extractUrl(text: String): String {
        val urlRegex = """(https?://\S+)""".toRegex()
        val match = urlRegex.find(text)
        return match?.value ?: text
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanoramaDownloaderApp(
    viewModel: DownloadViewModel,
    sharedUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var inputUrl by remember { mutableStateOf("") }
    val uiState by viewModel.state.collectAsState()
    val selectedQuality by viewModel.quality.collectAsState()

    // Automatically fill in and request if sharedUrl is received
    LaunchedEffect(sharedUrl) {
        if (sharedUrl.isNotEmpty()) {
            inputUrl = sharedUrl
            viewModel.parseUrl(sharedUrl)
        }
    }

    val spaceGrotesk = FontFamily.SansSerif

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A), // Deep Slate space dark
                        Color(0xFF020617)  // Ultra Dark space deep
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // App Heading Area
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22D3EE).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFF22D3EE).copy(alpha = 0.40f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = "Map Icon",
                        tint = Color(0xFF22D3EE),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Panorama Downloader",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontFamily = spaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "Equirectangular 360° stitcher & tagger",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontFamily = spaceGrotesk
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Link input field and paste button
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Paste Google Earth Link",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = spaceGrotesk
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Supports both official vehicle tours and user-uploaded photo spheres.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontFamily = spaceGrotesk
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = inputUrl,
                            onValueChange = { inputUrl = it },
                            placeholder = {
                                Text(
                                    "https://earth.google.com/web/... or https://earth.app.goo.gl/...",
                                    color = Color(0xFF64748B),
                                    fontSize = 12.sp
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A),
                                focusedBorderColor = Color(0xFF22D3EE),
                                unfocusedBorderColor = Color(0xFF334155)
                            ),
                            trailingIcon = {
                                if (inputUrl.isNotEmpty()) {
                                    IconButton(onClick = { inputUrl = "" }) {
                                        Text("✕", color = Color(0xFF94A3B8))
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val item = clipboard.primaryClip?.getItemAt(0)
                                if (item != null) {
                                    inputUrl = item.text.toString()
                                    Toast.makeText(context, "Link pasted!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Clipboard empty", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF334155))
                                .border(1.dp, Color(0xFF475569), RoundedCornerShape(10.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste Clipboard",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { viewModel.parseUrl(inputUrl) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Load Link",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Analyze Link",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Active Panel
            when (val state = uiState) {
                is DownloadViewModel.UIState.Idle -> {
                    // Beautiful empty tutorial guide
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF334155).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "How To Use",
                                tint = Color(0xFF22D3EE).copy(alpha = 0.7f),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "How to download Google Earth 360°s",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                fontFamily = spaceGrotesk
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            val tutorialSteps = listOf(
                                "Open the Google Earth app on your device",
                                "Navigate to any Street View or Photo Sphere",
                                "Tap the 'Share Link' option in Google Earth",
                                "Choose 'Panorama Downloader' inside the share sheet, or copy-paste the link directly here!"
                            )
                            
                            tutorialSteps.forEachIndexed { i, step ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "${i + 1}.",
                                        color = Color(0xFF22D3EE),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.width(20.dp)
                                    )
                                    Text(
                                        text = step,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp,
                                        fontFamily = spaceGrotesk,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }

                is DownloadViewModel.UIState.Resolving -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = Color(0xFF22D3EE))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Resolving link redirects & decoding Google Earth parameters...",
                                color = Color.White,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                fontFamily = spaceGrotesk
                            )
                        }
                    }
                }

                is DownloadViewModel.UIState.Resolved -> {
                    // Main workspace
                    val loc = state.location
                    
                    // Pre-calculate Preview Image URL
                    val previewUrl = if (loc.isUserUploaded) {
                        "https://lh3.googleusercontent.com/p/${loc.panoId}=w600-h300-n"
                    } else {
                        "https://streetviewpixels-pa.googleapis.com/v1/tile?cb_client=maps_sv.tactile&panoid=${loc.panoId}&x=0&y=0&zoom=1"
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Card with photo preview and details
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                        ) {
                            Column {
                                // Cover Photo Sphere preview
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .background(Color(0xFF0F172A))
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(previewUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Panorama Preview",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    
                                    // Pano Tag badge
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(12.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (loc.isUserUploaded) Color(0xFFEC4899) else Color(0xFF06B6D4)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (loc.isUserUploaded) "User Upload" else "Official Sphere",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Details column
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = loc.name,
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = spaceGrotesk
                                    )
                                    
                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Display metadata parameters
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("COORDINATES", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.LocationOn,
                                                    contentDescription = "GPS",
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "${"%.5f".format(loc.latitude)}, ${"%.5f".format(loc.longitude)}",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }

                                        Column {
                                            Text("PANORAMA ID", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = loc.panoId.take(12) + "...",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Quality selection card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Select Download Quality",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = spaceGrotesk
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Higher quality produces details but requires more data & processing.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontFamily = spaceGrotesk
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Button segmented option
                                SingleChoiceSegmentedButtonRow(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val levels = listOf(2 to "Medium", 3 to "High", 4 to "Ultra")
                                    levels.forEachIndexed { index, (lvl, label) ->
                                        SegmentedButton(
                                            selected = selectedQuality == lvl,
                                            onClick = { viewModel.setQuality(lvl) },
                                            shape = SegmentedButtonDefaults.itemShape(index = index, count = levels.size),
                                            colors = SegmentedButtonDefaults.colors(
                                                activeContainerColor = Color(0xFF0284C7),
                                                activeContentColor = Color.White,
                                                inactiveContainerColor = Color(0xFF0F172A),
                                                inactiveContentColor = Color(0xFF94A3B8)
                                            )
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(text = label, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                val suffix = when (lvl) {
                                                    2 -> "2K"
                                                    3 -> "4K"
                                                    4 -> "8K"
                                                    else -> ""
                                                }
                                                Text(text = suffix, fontSize = 9.sp, color = if (selectedQuality == lvl) Color.White else Color(0xFF64748B))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Main Action Button
                        Button(
                            onClick = { viewModel.downloadPanorama(context, loc) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)), // Emerald Green
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Download",
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "STITCH & DOWNLOAD 360°",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 0.5.sp,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Reset Button
                        Button(
                            onClick = { viewModel.resetState() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Clear and Load Another", color = Color(0xFF94A3B8))
                        }
                    }
                }

                is DownloadViewModel.UIState.Downloading -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                progress = { state.progress },
                                color = Color(0xFF10B981),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = state.status,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                fontFamily = spaceGrotesk
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = Color(0xFF22D3EE),
                                trackColor = Color(0xFF334155)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${(state.progress * 100).toInt()}% Stitched",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontFamily = spaceGrotesk
                            )
                        }
                    }
                }

                is DownloadViewModel.UIState.Success -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF065F46)), // Dark Forest Success Green
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF047857), RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "STITCHING COMPLETE!",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = spaceGrotesk
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Successfully saved equirectangular JPEG of ${state.title} containing 360° viewer tags & GPS EXIF coordinate headers inside your device gallery!",
                                color = Color(0xFFD1FAE5),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                fontFamily = spaceGrotesk,
                                lineHeight = 16.sp
                            )
                            
                            Spacer(modifier = Modifier.height(20.dp))

                            // Action button to open Gallery or View
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse("content://media/external/images/media"), "image/jpeg")
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Saved to Pictures/Panoramas!", Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "View Photo",
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open Device Gallery", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = { viewModel.resetState() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Stitch Another Location", color = Color(0xFFD1FAE5))
                            }
                        }
                    }
                }

                is DownloadViewModel.UIState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)), // Dark Crimson Red Error
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF991B1B), RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = Color(0xFFFCA5A5),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Problem Stitching Panorama",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = spaceGrotesk
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = state.message,
                                color = Color(0xFFFEE2E2),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                fontFamily = spaceGrotesk,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.resetState() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Dismiss", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
