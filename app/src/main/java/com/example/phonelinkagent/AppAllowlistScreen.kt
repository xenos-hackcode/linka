package com.example.phonelinkagent

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val THEME_COLOR = Color(0xFF00E5A0)

private data class InstalledApp(val packageName: String, val label: String, val icon: Bitmap?)

private fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val bmp = Bitmap.createBitmap(
        intrinsicWidth.coerceAtLeast(1),
        intrinsicHeight.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp
}

/**
 * Checklist of launchable apps, checked = shared with the controller (the default -
 * see AppAllowlistStore). Writes through immediately on toggle, no separate save step,
 * since this is meant to be changeable anytime, not a form to submit.
 *
 * Used two ways: as a mandatory one-time onboarding step right before the pairing/QR
 * screen (onContinue non-null, so there's an obvious way to proceed forward), and
 * later as an anytime-reachable settings screen via "MANAGE SHARED APPS" (onContinue
 * null - back is the only exit). The "◀" always means back, never "proceed" - those
 * are different actions and need separate callbacks, not one shared one.
 */
@Composable
fun AppAllowlistScreen(
    onBack: () -> Unit,
    onContinue: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var blocked by remember { mutableStateOf(AppAllowlistStore.blockedPackages(context)) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.Default) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .map { info ->
                    InstalledApp(
                        packageName = info.activityInfo.packageName,
                        label = info.loadLabel(pm).toString(),
                        icon = runCatching { info.loadIcon(pm).toBitmap() }.getOrNull()
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }
        apps = loaded
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060911))
            .systemBarsPadding()
            .padding(start = 18.dp, end = 18.dp, bottom = 18.dp, top = 40.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "◀",
                color = THEME_COLOR,
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp)
            )
            Text(
                text = "SHARED APPS",
                color = THEME_COLOR,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = (if (onContinue != null) "Before you link with anyone: choose what's shared. " else "") +
                "Uncheck an app to stop sharing it with whoever's linked to this phone - " +
                "their screen goes black while you're in it, and nothing you do there can be " +
                "seen or controlled remotely. Changeable anytime from this screen.",
            color = Color.Gray,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(16.dp))

        if (loading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Loading apps...", color = Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(apps, key = { it.packageName }) { app ->
                    val isChecked = !blocked.contains(app.packageName)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                AppAllowlistStore.setBlocked(context, app.packageName, isChecked)
                                blocked = AppAllowlistStore.blockedPackages(context)
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        val icon = app.icon
                        if (icon != null) {
                            Image(
                                bitmap = icon.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp)
                            )
                        } else {
                            Spacer(Modifier.size(32.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = app.label,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = THEME_COLOR)
                        )
                    }
                }
            }
        }

        if (onContinue != null) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = THEME_COLOR),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("CONTINUE", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }
    }
}
