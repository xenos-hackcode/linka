package com.example.xenoscontroller

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Chooser shown once the user has picked the Controller role - was the app's root
 * screen back when this was its own standalone app; now nested one level under the
 * top-level role picker in the merged app's MainActivity. */
@Composable
fun ControllerHomeScreen(
    themeColor: Color,
    isDark: Boolean,
    onOpenLaptop: () -> Unit,
    onOpenPhone: () -> Unit,
    onSwitchRole: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF050505) else Color(0xFFF5F5F5))
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "CONTROLLER",
                color = themeColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "⇄ SWITCH ROLE",
                color = Color.Gray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable(onClick = onSwitchRole)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "View and control a PC or phone that's already running the matching " +
                    "Linka agent role. This only ever connects OUT to something else - it " +
                    "never captures or controls this device.",
            color = Color.Gray,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(24.dp))

        listOf("LINK TO PC" to onOpenLaptop, "LINK TO PHONE" to onOpenPhone).forEach { (label, action) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Gray.copy(alpha = 0.1f))
                    .clickable(onClick = action)
                    .padding(16.dp)
            ) {
                Text(
                    text = label,
                    color = if (isDark) Color.White else Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                Text(">", color = themeColor, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }
    }
}
