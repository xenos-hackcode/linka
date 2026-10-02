package com.example.linka

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val THEME_COLOR = Color(0xFF00E5A0)

/** A bundled res/raw clip plays in-app (see VideoPlayerScreen.kt) - that's what
 * [playingRawRes] (in TutorialScreen below) switches to. An external hosted link
 * (TutorialContent.videoUrl, e.g. YouTube/Drive) isn't a raw video stream an in-app
 * player can just play, so that case still hands off to whatever app on the device
 * handles it. */
private fun openExternalVideo(context: android.content.Context, url: String?) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/**
 * Full-screen walkthrough. Auto-shown once on first launch (isFirstRun = true, ends
 * with a "start using Linka" CTA instead of a back arrow) and reachable anytime after
 * via Settings > Activity Log > Tutorial (isFirstRun = false, ordinary "◀" back). Text
 * content lives in TutorialContent.kt; this composable only renders it. [onClose] is
 * the single exit for both cases - the caller (MainActivity) is what actually persists
 * "seen" via TutorialPrefs, same pattern as AppAllowlistScreen's onContinue.
 */
@Composable
fun TutorialScreen(
    isDark: Boolean,
    isFirstRun: Boolean,
    onClose: () -> Unit
) {
    var playingRawRes by remember { mutableStateOf<Int?>(null) }
    val currentlyPlaying = playingRawRes
    if (currentlyPlaying != null) {
        VideoPlayerScreen(rawRes = currentlyPlaying, onBack = { playingRawRes = null })
        return
    }

    val context = LocalContext.current
    val bgColor = if (isDark) Color(0xFF050505) else Color(0xFFF5F5F5)
    val textColor = if (isDark) Color.White else Color.Black

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, bottom = 24.dp, top = 40.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isFirstRun) {
                Text(
                    text = "◀",
                    color = THEME_COLOR,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable(onClick = onClose).padding(end = 12.dp)
                )
            }
            Text(
                text = "TUTORIAL",
                color = THEME_COLOR,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isFirstRun)
                "A quick read before you dive in - about two minutes."
            else
                "Read it again anytime from here.",
            color = Color.Gray,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(20.dp))

        val videoUrl = TutorialContent.videoUrl
        val videoRawRes = TutorialContent.videoRawRes
        if (videoUrl != null || videoRawRes != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(THEME_COLOR.copy(alpha = 0.15f))
                    .clickable {
                        if (videoRawRes != null) playingRawRes = videoRawRes
                        else openExternalVideo(context, videoUrl)
                    }
                    .padding(14.dp)
            ) {
                Text(
                    text = "▶ WATCH VIDEO",
                    color = THEME_COLOR,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        TutorialContent.steps.forEachIndexed { index, step ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Gray.copy(alpha = 0.1f))
                    .padding(16.dp)
            ) {
                Text(
                    text = "${index + 1}. ${step.title}",
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(8.dp))
                step.videoRes?.let { res ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(THEME_COLOR.copy(alpha = 0.15f))
                            .clickable { playingRawRes = res }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "▶ WATCH",
                            color = THEME_COLOR,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    text = step.body,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                step.imageRes?.let { res ->
                    Spacer(Modifier.height(8.dp))
                    Image(
                        painter = painterResource(id = res),
                        contentDescription = step.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
                step.bodyAfter?.let { after ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = after,
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        if (isFirstRun) {
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = THEME_COLOR),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("GOT IT - START USING LINKA", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }
    }
}
