package com.example.linka

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private val THEME_COLOR = Color(0xFF00E5A0)

/** Plays a bundled res/raw video in-app (VideoView, with its default play/pause/seek
 * controller) instead of handing off to whatever external video app the device has -
 * that's the whole point of this screen (see TutorialScreen.kt, which is its only
 * caller). Only for bundled clips: an external hosted link (TutorialContent.videoUrl)
 * isn't a raw video stream VideoView can play (typically a YouTube/Drive page), so
 * that case still opens externally instead of routing through here. */
@Composable
fun VideoPlayerScreen(rawRes: Int, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(Uri.parse("android.resource://${ctx.packageName}/$rawRes"))
                    setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                    setOnPreparedListener { it.start() }
                }
            }
        )
        Text(
            text = "◀ BACK",
            color = THEME_COLOR,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.TopStart)
                .systemBarsPadding()
                .padding(16.dp)
                .clickable(onClick = onBack)
        )
    }
}
