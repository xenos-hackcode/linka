package com.example.linka

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val joinDateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

/** Reached via the icon beside Settings on the role picker. Purely local (see
 * ProfileStore.kt) - there's no account backend, so this is what this device knows
 * about its own user, not a shareable public profile. */
@Composable
fun ProfileScreen(
    themeColor: Color,
    isDark: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var displayName by remember { mutableStateOf(ProfileStore.displayName(context)) }
    var bio by remember { mutableStateOf(ProfileStore.bio(context)) }
    var pictureVersion by remember { mutableIntStateOf(0) }
    var country by remember { mutableStateOf<String?>(null) }
    val joinDateMs = remember { ProfileStore.joinDateMs(context) }
    val stats = remember(pictureVersion) { computeProfileStats(context) }

    LaunchedEffect(Unit) {
        country = withContext(Dispatchers.IO) { bestEffortCountry(context) }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes != null) {
                withContext(Dispatchers.IO) { ProfileStore.setProfilePicture(context, bytes) }
                pictureVersion++
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF060911) else Color(0xFFEFEFEF))
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, bottom = 24.dp, top = 40.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "◀",
                color = themeColor,
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp)
            )
            Text(
                text = "PROFILE",
                color = themeColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(96.dp)
                .clip(CircleShape)
                .background(Color.Gray.copy(alpha = 0.2f))
                .clickable { pickImage.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            val hasPicture = remember(pictureVersion) { ProfileStore.hasProfilePicture(context) }
            if (hasPicture) {
                val bitmap = remember(pictureVersion) {
                    BitmapFactory.decodeFile(ProfileStore.profilePicturePath(context))
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Profile picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                }
            } else {
                Text("+", color = themeColor, fontSize = 28.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Tap to change photo",
            color = Color.Gray,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it; ProfileStore.setDisplayName(context, it) },
            label = { Text("Display name", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it; ProfileStore.setBio(context, it) },
            label = { Text("Bio", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(18.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            ProfileFact(label = "Location", value = country ?: "Unknown", modifier = Modifier.weight(1f), isDark = isDark)
            ProfileFact(
                label = "Joined",
                value = joinDateFormat.format(Date(joinDateMs)),
                modifier = Modifier.weight(1f),
                isDark = isDark
            )
        }
        Spacer(Modifier.height(24.dp))

        Text(
            text = "LEVEL ${stats.level}",
            color = themeColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { stats.xpIntoLevel / stats.xpPerLevel.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = themeColor,
            trackColor = Color.Gray.copy(alpha = 0.2f)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${stats.xpIntoLevel} / ${stats.xpPerLevel} XP to next level (${stats.xp} XP total)",
            color = Color.Gray,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(24.dp))

        Text(
            text = "STATS",
            color = Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            ProfileFact(label = "Times used", value = "${stats.totalSessions}", modifier = Modifier.weight(1f), isDark = isDark)
            ProfileFact(label = "As controller", value = "${stats.timesAsController}", modifier = Modifier.weight(1f), isDark = isDark)
            ProfileFact(label = "As controllee", value = "${stats.timesAsControllee}", modifier = Modifier.weight(1f), isDark = isDark)
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            ProfileFact(label = "Requests made", value = "${stats.requestsCreated}", modifier = Modifier.weight(1f), isDark = isDark)
            ProfileFact(label = "Accepted", value = "${stats.requestsAccepted}", modifier = Modifier.weight(1f), isDark = isDark)
            ProfileFact(label = "Denied", value = "${stats.requestsDenied}", modifier = Modifier.weight(1f), isDark = isDark)
        }
    }
}

@Composable
private fun ProfileFact(label: String, value: String, modifier: Modifier = Modifier, isDark: Boolean) {
    Column(modifier = modifier) {
        Text(
            text = value,
            color = if (isDark) Color.White else Color.Black,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
