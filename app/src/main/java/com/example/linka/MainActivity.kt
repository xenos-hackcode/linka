package com.example.linka

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RequestPage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.phonelinkagent.AppAllowlistScreen
import com.example.phonelinkagent.AppAllowlistStore
import com.example.phonelinkagent.PhoneLinkAccessibilityService
import com.example.phonelinkagent.PhoneLinkAgentScreen
import com.example.phonelinkagent.PhoneLinkAgentStatus
import com.example.phonelinkagent.ScreenCaptureService
import com.example.linka.backend.AuthScreen
import com.example.linka.backend.BackendClient
import com.example.linka.backend.BackendDmMessage
import com.example.linka.backend.BackendGlobalMessage
import com.example.linka.backend.BackendGroupMessage
import com.example.linka.backend.BackendRequest
import com.example.linka.backend.BackendResult
import com.example.linka.backend.BackendSession
import com.example.linka.backend.BackendWebSocket
import com.example.linka.backend.UserProfileScreen
import com.example.xenoscontroller.ControllerHomeScreen
import com.example.xenoscontroller.LaptopControlScreen
import com.example.xenoscontroller.PhoneControlScreen
import kotlinx.coroutines.launch

private const val CONTROLLER_PREFS = "xenos_controller_prefs"
private const val AGENT_PREFS = "phone_link_agent_prefs"
private val THEME_COLOR = Color(0xFF00E5A0)

private enum class Role { CONTROLLER, CONTROLLEE }

private fun BackendRequest.toDomainRequest(): DomainRequest = DomainRequest(
    id = id,
    summary = summary,
    reason = reason,
    timestampMs = createdAt,
    status = RequestStatus.valueOf(status)
)

private fun BackendGlobalMessage.toChatEntry(myUserId: String?): ChatEntry =
    ChatEntry(text, fromMe = userId == myUserId, timestampMs = createdAt, senderId = userId, senderUsername = username)

private fun BackendGroupMessage.toChatEntry(myUserId: String?): ChatEntry =
    ChatEntry(text, fromMe = userId == myUserId, timestampMs = createdAt, senderId = userId, senderUsername = username)

private fun BackendDmMessage.toChatEntry(myUserId: String?): ChatEntry =
    ChatEntry(text, fromMe = senderId == myUserId, timestampMs = createdAt)

/** Single entry point for both roles - the user picks Controller or Controllee each
 * time the app is opened fresh. Owns both ActivityResultContracts (QR scan, used by
 * the Controller role; MediaProjection consent, used by the Controllee role) since an
 * Activity can only register these on itself. Everything below the role picker reuses
 * the same screens/clients the two formerly-separate apps already had - see
 * ControllerHomeScreen/LaptopControlScreen/PhoneControlScreen (com.example.xenoscontroller)
 * and PhoneLinkAgentScreen (com.example.phonelinkagent). */
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_RESUME_CAPTURE = "resume_capture"
    }

    // ---- Controllee role state (moved from the old agent MainActivity) ----

    private var pendingResumeCapture = false
    private val accessibilityOnState = mutableStateOf(false)
    private val batteryUnrestrictedState = mutableStateOf(false)
    private val notificationPermissionGrantedState = mutableStateOf(false)

    private fun isBatteryUnrestricted(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun isNotificationPermissionGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    // Settings' Notifications toggle owns this - an Activity has to be the one to
    // register/launch a runtime permission request, same reasoning as the QR scan and
    // screen-capture launchers above.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPermissionGrantedState.value = granted
        NotificationPrefs.setNotificationsEnabled(this, granted)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // No runtime permission below API 33 - the manifest declaration is enough.
            NotificationPrefs.setNotificationsEnabled(this, true)
            notificationPermissionGrantedState.value = true
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Session-time only, per the location decision in PLANNING.md: asked the first time
    // either role reaches an actual live session, never blocks anything if denied - a
    // denial just means bestEffortCountry() keeps returning null and the activity log
    // shows "unknown" for that side. Once per app process, not once per session.
    private var locationPermissionRequestedThisProcess = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op - LocationUtil.bestEffortCountry() re-checks the grant itself when used */ }

    private fun requestLocationPermissionIfNeeded() {
        if (locationPermissionRequestedThisProcess) return
        locationPermissionRequestedThisProcess = true
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val svcIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
            }
            ContextCompat.startForegroundService(this, svcIntent)
            PhoneLinkAccessibilityService.instance?.onCaptureStarted()
            // Deliberately triggered here, not from the controllee's agentStatus change -
            // that fires in the same click handler as beginScreenCapture() below, and two
            // system permission dialogs launched back-to-back race each other (the second
            // launch() call can get silently dropped while this one is still transitioning
            // to the foreground, permanently burning the "already asked" guard with no
            // dialog ever shown). This callback only runs once that transition is fully
            // resolved and the Activity is genuinely resumed again, so it's safe here.
            requestLocationPermissionIfNeeded()
        } else {
            PhoneLinkAccessibilityService.instance?.onCaptureDenied()
        }
    }

    private fun beginScreenCapture() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        runCatching { screenCaptureLauncher.launch(mpm.createScreenCaptureIntent()) }
            .onFailure { PhoneLinkAccessibilityService.instance?.onCaptureDenied() }
    }

    private fun agentPrefs() = getSharedPreferences(AGENT_PREFS, MODE_PRIVATE)
    private fun loadDisclosureAcknowledged(): Boolean = agentPrefs().getBoolean("disclosure_ack", false)
    private fun saveDisclosureAcknowledged() { agentPrefs().edit().putBoolean("disclosure_ack", true).apply() }

    private fun loadOrCreateAgentToken(): String {
        val existing = agentPrefs().getString("token", null)
        if (existing != null && existing.length >= 16) return existing
        val fresh = generateAgentToken()
        agentPrefs().edit().putString("token", fresh).apply()
        return fresh
    }

    private fun regenerateAgentToken(): String {
        val fresh = generateAgentToken()
        agentPrefs().edit().putString("token", fresh).apply()
        return fresh
    }

    private fun generateAgentToken(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ---- Controller role state (moved from the old controller MainActivity) ----

    private var qrScanResultCallback: ((String) -> Unit)? = null

    private val qrScanLauncher = registerForActivityResult(
        com.journeyapps.barcodescanner.ScanContract()
    ) { result ->
        val text = result.contents
        val callback = qrScanResultCallback
        qrScanResultCallback = null
        if (!text.isNullOrBlank()) callback?.invoke(text)
    }

    private fun launchQrScan(onResult: (String) -> Unit) {
        qrScanResultCallback = onResult
        runCatching {
            qrScanLauncher.launch(
                com.journeyapps.barcodescanner.ScanOptions()
                    .setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
                    .setBeepEnabled(false)
                    .setOrientationLocked(false)
            )
        }.onFailure { qrScanResultCallback = null }
    }

    private fun controllerPrefs() = getSharedPreferences(CONTROLLER_PREFS, MODE_PRIVATE)
    private fun loadLaptopToken(): String? = controllerPrefs().getString("laptop_token", null)
    private fun saveLaptopToken(token: String) { controllerPrefs().edit().putString("laptop_token", token).apply() }
    private fun forgetLaptopToken() { controllerPrefs().edit().remove("laptop_token").apply() }
    private fun loadPhoneToken(): String? = controllerPrefs().getString("phone_token", null)
    private fun savePhoneToken(token: String) { controllerPrefs().edit().putString("phone_token", token).apply() }
    private fun forgetPhoneToken() { controllerPrefs().edit().remove("phone_token").apply() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingResumeCapture = intent?.getBooleanExtra(EXTRA_RESUME_CAPTURE, false) == true

        setContent {
            val isDark = isSystemInDarkTheme()
            // 0=Link (existing role flow), 1=Request, 2=Global, 3=Groups - see
            // PLANNING.md, the "UI shell only" tab set added 2026-08-15.
            var selectedTab by rememberSaveable { mutableStateOf(0) }
            var role by rememberSaveable { mutableStateOf<Role?>(null) }
            var showSettings by rememberSaveable { mutableStateOf(false) }
            var showActivityLog by rememberSaveable { mutableStateOf(false) }
            var showProfile by rememberSaveable { mutableStateOf(false) }
            // Auto-true the very first time the app is ever opened (see TutorialPrefs) -
            // re-openable anytime after from Settings > Activity Log > Tutorial.
            var showTutorial by rememberSaveable { mutableStateOf(!TutorialPrefs.hasSeenTutorial(this@MainActivity)) }

            // Request/Global/Groups tab state - backed by the real backend now (see
            // backend/BackendClient.kt, backend/BackendWebSocket.kt). Link/pairing
            // needs none of this - it's untouched, secured by its own tokens.
            var backendToken by rememberSaveable { mutableStateOf(BackendSession.token(this@MainActivity)) }
            var backendUserId by rememberSaveable { mutableStateOf(BackendSession.userId(this@MainActivity)) }
            var backendUsername by rememberSaveable { mutableStateOf(BackendSession.username(this@MainActivity)) }
            val myRequests = remember { mutableStateListOf<DomainRequest>() }
            val respondRequests = remember { mutableStateListOf<DomainRequest>() }
            val acceptedRequests = remember { mutableStateListOf<DomainRequest>() }
            val globalMessages = remember { mutableStateListOf<ChatEntry>() }
            val groups = remember { mutableStateListOf<LocalGroup>() }
            val discoverableGroups = remember { mutableStateListOf<LocalGroup>() }
            val groupMessages = remember { mutableStateMapOf<String, MutableList<ChatEntry>>() }
            // Tapping a message's sender (see ChatScreen's onSenderClick) opens their
            // profile; CHAT from there opens a DM thread; BLOCK hides their messages
            // from global/group chat on this client and rejects DMs both ways on the
            // server (see server/src/routes/dms.ts).
            var viewingProfileUserId by rememberSaveable { mutableStateOf<String?>(null) }
            var openDmUserId by rememberSaveable { mutableStateOf<String?>(null) }
            var openDmUsername by rememberSaveable { mutableStateOf<String?>(null) }
            val dmMessages = remember { mutableStateMapOf<String, MutableList<ChatEntry>>() }
            val blockedUserIds = remember { mutableStateListOf<String>() }
            val backendScope = rememberCoroutineScope()
            val onBackendAuthenticated: (String, String, String) -> Unit = { newToken, userId, username ->
                BackendSession.save(this@MainActivity, newToken, userId, username)
                backendToken = newToken
                backendUserId = userId
                backendUsername = username
            }
            val onBackendSignOut: () -> Unit = {
                BackendSession.clear(this@MainActivity)
                backendToken = null
                backendUserId = null
                backendUsername = null
                myRequests.clear(); respondRequests.clear(); acceptedRequests.clear()
                globalMessages.clear(); groups.clear(); discoverableGroups.clear(); groupMessages.clear()
                viewingProfileUserId = null; openDmUserId = null; openDmUsername = null
                dmMessages.clear(); blockedUserIds.clear()
            }

            // Controllee-role state
            var disclosureAck by remember { mutableStateOf(loadDisclosureAcknowledged()) }
            val accessibilityOn by accessibilityOnState
            val batteryUnrestricted by batteryUnrestrictedState
            val notificationPermissionGranted by notificationPermissionGrantedState
            var agentToken by remember { mutableStateOf(loadOrCreateAgentToken()) }
            var agentStatus by remember {
                mutableStateOf(PhoneLinkAccessibilityService.instance?.currentStatus() ?: PhoneLinkAgentStatus.IDLE)
            }
            var showAppAllowlist by rememberSaveable { mutableStateOf(false) }
            var appAllowlistReviewed by remember { mutableStateOf(AppAllowlistStore.hasReviewed(this@MainActivity)) }
            var showChat by rememberSaveable { mutableStateOf(false) }
            // In-memory only - lost on disconnect/app restart, no backend to persist
            // against (see PLANNING.md §3.3, ChatEntry doc comment).
            val chatMessages = remember { mutableStateListOf<ChatEntry>() }

            // Controller-role state
            var controllerScreen by rememberSaveable { mutableStateOf("home") }
            var laptopToken by rememberSaveable { mutableStateOf(loadLaptopToken()) }
            var phoneToken by rememberSaveable { mutableStateOf(loadPhoneToken()) }

            // System/gesture back button, made to match whatever on-screen "◀"/"switch
            // role" affordance is currently visible - without this, back just exits
            // the app instead of navigating, everywhere in it (found live 2026-08-15).
            // null (role picker, the true root) intentionally falls through to the
            // default behavior, which exits - that's the one place exiting is correct.
            val backAction: (() -> Unit)? = when {
                openDmUserId != null -> { { openDmUserId = null; openDmUsername = null } }
                viewingProfileUserId != null -> { { viewingProfileUserId = null } }
                selectedTab != 0 -> { { selectedTab = 0 } }
                role == null && showProfile -> { { showProfile = false } }
                role == null && showActivityLog -> { { showActivityLog = false } }
                role == null && showSettings -> { { showSettings = false } }
                role == Role.CONTROLLER && controllerScreen != "home" -> { { controllerScreen = "home" } }
                role == Role.CONTROLLER -> { { role = null } }
                role == Role.CONTROLLEE && showAppAllowlist -> { { showAppAllowlist = false } }
                role == Role.CONTROLLEE -> { { role = null } }
                else -> null
            }
            BackHandler(enabled = backAction != null) { backAction?.invoke() }
            // Declared AFTER the centralized handler above - Compose's back dispatcher
            // gives priority to the most-recently-registered enabled BackHandler, so
            // this one (registered later) correctly wins and closes chat first while
            // it's open, without needing to touch the centralized backAction logic.
            BackHandler(enabled = showChat) { showChat = false }
            // Declared last of all three, same reasoning as showChat's handler above -
            // the tutorial overlay sits on top of literally everything else, so its
            // back handler must win over all of them while it's open.
            BackHandler(enabled = showTutorial) {
                TutorialPrefs.markTutorialSeen(this@MainActivity)
                showTutorial = false
            }

            DisposableEffect(Unit) {
                PhoneLinkAccessibilityService.instance?.onStatusChanged = { agentStatus = it }
                PhoneLinkAccessibilityService.instance?.onChatMessage = { text ->
                    chatMessages.add(ChatEntry(text, fromMe = false, timestampMs = System.currentTimeMillis()))
                }
                agentStatus = PhoneLinkAccessibilityService.instance?.currentStatus() ?: PhoneLinkAgentStatus.IDLE
                if (pendingResumeCapture && agentStatus == PhoneLinkAgentStatus.PAUSED) {
                    pendingResumeCapture = false
                    beginScreenCapture()
                    role = Role.CONTROLLEE
                }
                onDispose {
                    PhoneLinkAccessibilityService.instance?.onStatusChanged = null
                    PhoneLinkAccessibilityService.instance?.onChatMessage = null
                }
            }

            // Centralized trigger for the location permission (see requestLocationPermissionIfNeeded)
            // for the controller role - fires once, the first time it reaches a live phone/
            // laptop session. The controllee-role trigger lives in screenCaptureLauncher's
            // callback instead (see its comment) - not here, since firing it from a Compose
            // state change that happens in the same click handler as the screen-capture
            // consent dialog races that dialog and can silently no-op.
            val controllerInLiveSession = role == Role.CONTROLLER && (controllerScreen == "laptop" || controllerScreen == "phone")
            LaunchedEffect(controllerInLiveSession) {
                if (controllerInLiveSession) requestLocationPermissionIfNeeded()
            }

            // Initial load for the three backend-backed tabs, once signed in - each
            // screen also gets live updates over WebSocket below, so this only needs
            // to run once per sign-in, not on every tab switch.
            LaunchedEffect(backendToken) {
                val token = backendToken ?: return@LaunchedEffect
                (BackendClient.myRequests(token) as? BackendResult.Success)?.value?.let { list ->
                    myRequests.clear(); myRequests.addAll(list.map { it.toDomainRequest() })
                }
                (BackendClient.browseRequests(token) as? BackendResult.Success)?.value?.let { list ->
                    respondRequests.clear(); respondRequests.addAll(list.map { it.toDomainRequest() })
                }
                (BackendClient.acceptedRequests(token) as? BackendResult.Success)?.value?.let { list ->
                    acceptedRequests.clear(); acceptedRequests.addAll(list.map { it.toDomainRequest() })
                }
                (BackendClient.globalMessages(token) as? BackendResult.Success)?.value?.let { list ->
                    globalMessages.clear(); globalMessages.addAll(list.map { it.toChatEntry(backendUserId) })
                }
                (BackendClient.listGroups(token) as? BackendResult.Success)?.value?.let { list ->
                    groups.clear(); groups.addAll(list.map { LocalGroup(it.id, it.name) })
                    list.forEach { group ->
                        launch {
                            (BackendClient.groupMessages(token, group.id) as? BackendResult.Success)?.value?.let { msgs ->
                                groupMessages[group.id] = msgs.map { it.toChatEntry(backendUserId) }.toMutableStateList()
                            }
                        }
                    }
                }
                (BackendClient.discoverGroups(token) as? BackendResult.Success)?.value?.let { list ->
                    discoverableGroups.clear(); discoverableGroups.addAll(list.map { LocalGroup(it.id, it.name) })
                }
                (BackendClient.listBlocks(token) as? BackendResult.Success)?.value?.let { list ->
                    blockedUserIds.clear(); blockedUserIds.addAll(list)
                }
            }

            // Live push (see backend/BackendWebSocket.kt) - keeps the three tabs above
            // updating in real time while open, on top of the one-time load above.
            DisposableEffect(backendToken) {
                val token = backendToken
                if (token == null) return@DisposableEffect onDispose {}
                val ws = BackendWebSocket(token)
                ws.onGlobalMessage = { msg -> globalMessages.add(msg.toChatEntry(backendUserId)) }
                ws.onGroupMessage = { msg ->
                    groupMessages.getOrPut(msg.groupId) { mutableStateListOf() }.add(msg.toChatEntry(backendUserId))
                }
                ws.onRequestCreated = { req ->
                    if (req.requesterId == backendUserId) {
                        myRequests.add(0, req.toDomainRequest())
                    } else {
                        respondRequests.add(0, req.toDomainRequest())
                    }
                }
                ws.onRequestUpdated = { req ->
                    val index = myRequests.indexOfFirst { it.id == req.id }
                    if (index != -1) myRequests[index] = req.toDomainRequest()
                }
                ws.onDmMessage = { msg ->
                    dmMessages.getOrPut(msg.senderId) { mutableStateListOf() }.add(msg.toChatEntry(backendUserId))
                }
                ws.connect()
                onDispose { ws.disconnect() }
            }

            MaterialTheme {
              if (showTutorial) {
                TutorialScreen(
                    isDark = isDark,
                    // True only for the auto-open on first launch - once marked seen
                    // (below, on close), a Settings > Tutorial replay always reads false
                    // here and gets the ordinary "◀ back" framing instead of the
                    // first-run CTA, with no separate state needed to track which case
                    // this is.
                    isFirstRun = !TutorialPrefs.hasSeenTutorial(this@MainActivity),
                    onClose = {
                        TutorialPrefs.markTutorialSeen(this@MainActivity)
                        showTutorial = false
                    }
                )
              } else {
                Scaffold(
                    containerColor = Color(0xFF050505),
                    bottomBar = {
                        NavigationBar(
                            containerColor = Color(0xFF0A0E14),
                            contentColor = Color.White
                        ) {
                            listOf(
                                Icons.Filled.Link to "Link",
                                Icons.Filled.RequestPage to "Request",
                                Icons.Filled.Public to "Global",
                                Icons.Filled.Groups to "Groups"
                            ).forEachIndexed { index, (vector, label) ->
                                NavigationBarItem(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    icon = { Icon(vector, contentDescription = label) },
                                    label = { Text(label, fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = THEME_COLOR,
                                        selectedTextColor = THEME_COLOR,
                                        unselectedIconColor = Color.Gray,
                                        unselectedTextColor = Color.Gray,
                                        indicatorColor = THEME_COLOR.copy(alpha = 0.15f)
                                    )
                                )
                            }
                        }
                    }
                ) { scaffoldPadding ->
                Surface(
                    modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
                    color = Color(0xFF050505)
                ) {
                    val dmUserId = openDmUserId
                    val profileUserId = viewingProfileUserId
                    if (dmUserId != null && backendToken != null) {
                        ChatScreen(
                            themeColor = THEME_COLOR,
                            isDark = isDark,
                            messages = dmMessages[dmUserId] ?: emptyList(),
                            onSend = { text ->
                                val token = backendToken!!
                                backendScope.launch {
                                    when (val result = BackendClient.sendDm(token, dmUserId, text)) {
                                        is BackendResult.Success ->
                                            dmMessages.getOrPut(dmUserId) { mutableStateListOf() }.add(result.value.toChatEntry(backendUserId))
                                        is BackendResult.Failure ->
                                            Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onBack = { openDmUserId = null; openDmUsername = null },
                            title = openDmUsername?.uppercase() ?: "CHAT"
                        )
                    } else if (profileUserId != null && backendToken != null) {
                        UserProfileScreen(
                            themeColor = THEME_COLOR,
                            isDark = isDark,
                            token = backendToken!!,
                            userId = profileUserId,
                            isBlocked = profileUserId in blockedUserIds,
                            onBack = { viewingProfileUserId = null },
                            onOpenChat = { userId, username ->
                                viewingProfileUserId = null
                                openDmUserId = userId
                                openDmUsername = username
                                backendScope.launch {
                                    val token = backendToken ?: return@launch
                                    (BackendClient.dmMessages(token, userId) as? BackendResult.Success)?.value?.let { list ->
                                        dmMessages[userId] = list.map { it.toChatEntry(backendUserId) }.toMutableStateList()
                                    }
                                }
                            },
                            onBlocked = { userId -> if (userId !in blockedUserIds) blockedUserIds.add(userId) },
                            onUnblocked = { userId -> blockedUserIds.remove(userId) },
                            onGroupCreated = { groupId, groupName ->
                                groups.add(LocalGroup(groupId, groupName))
                                groupMessages[groupId] = mutableStateListOf()
                                viewingProfileUserId = null
                                selectedTab = 3
                            }
                        )
                    } else when (selectedTab) {
                        1 -> BackendGatedContent(
                            themeColor = THEME_COLOR,
                            isDark = isDark,
                            token = backendToken,
                            username = backendUsername,
                            onAuthenticated = onBackendAuthenticated,
                            onSignOut = onBackendSignOut
                        ) { token ->
                            RequestTabScreen(
                                themeColor = THEME_COLOR,
                                isDark = isDark,
                                myRequests = myRequests,
                                respondRequests = respondRequests,
                                acceptedRequests = acceptedRequests,
                                myPairingToken = agentToken,
                                onAddRequest = { summary, reason ->
                                    backendScope.launch {
                                        when (val result = BackendClient.createRequest(token, summary, reason)) {
                                            is BackendResult.Success -> {
                                                myRequests.add(0, result.value.toDomainRequest())
                                                ActivityLogStore.append(
                                                    this@MainActivity,
                                                    ActivityLogEntry(
                                                        id = java.util.UUID.randomUUID().toString(),
                                                        type = ActivityType.REQUEST_CREATED,
                                                        timestampMs = System.currentTimeMillis(),
                                                        requestSummary = summary
                                                    )
                                                )
                                            }
                                            is BackendResult.Failure ->
                                                Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onRespondToRequest = { id, accepted ->
                                    backendScope.launch {
                                        val result = if (accepted) BackendClient.acceptRequest(token, id) else BackendClient.denyRequest(token, id)
                                        when (result) {
                                            is BackendResult.Success -> {
                                                respondRequests.removeAll { it.id == id }
                                                if (accepted) acceptedRequests.add(0, result.value.toDomainRequest())
                                                ActivityLogStore.append(
                                                    this@MainActivity,
                                                    ActivityLogEntry(
                                                        id = java.util.UUID.randomUUID().toString(),
                                                        type = if (accepted) ActivityType.REQUEST_ACCEPTED else ActivityType.REQUEST_DENIED,
                                                        timestampMs = System.currentTimeMillis(),
                                                        requestSummary = result.value.summary
                                                    )
                                                )
                                            }
                                            is BackendResult.Failure ->
                                                Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onCancelRequest = { id ->
                                    backendScope.launch {
                                        when (val result = BackendClient.cancelRequest(token, id)) {
                                            is BackendResult.Success -> {
                                                val index = myRequests.indexOfFirst { it.id == id }
                                                if (index != -1) myRequests[index] = result.value.toDomainRequest()
                                            }
                                            is BackendResult.Failure ->
                                                Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onAcceptedNavigateToManageApps = {
                                    selectedTab = 0
                                    role = Role.CONTROLLEE
                                    showAppAllowlist = true
                                }
                            )
                        }
                        2 -> BackendGatedContent(
                            themeColor = THEME_COLOR,
                            isDark = isDark,
                            token = backendToken,
                            username = backendUsername,
                            onAuthenticated = onBackendAuthenticated,
                            onSignOut = onBackendSignOut
                        ) { token ->
                            ChatScreen(
                                themeColor = THEME_COLOR,
                                isDark = isDark,
                                messages = globalMessages.filterNot { it.senderId in blockedUserIds },
                                onSend = { text ->
                                    backendScope.launch {
                                        when (val result = BackendClient.postGlobalMessage(token, text)) {
                                            is BackendResult.Success -> globalMessages.add(result.value.toChatEntry(backendUserId))
                                            is BackendResult.Failure ->
                                                Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                title = "GLOBAL",
                                onSenderClick = { senderId, _ -> viewingProfileUserId = senderId }
                            )
                        }
                        3 -> BackendGatedContent(
                            themeColor = THEME_COLOR,
                            isDark = isDark,
                            token = backendToken,
                            username = backendUsername,
                            onAuthenticated = onBackendAuthenticated,
                            onSignOut = onBackendSignOut
                        ) { token ->
                            GroupsTabScreen(
                                themeColor = THEME_COLOR,
                                isDark = isDark,
                                groups = groups,
                                discoverableGroups = discoverableGroups,
                                messagesFor = { id -> (groupMessages[id] ?: emptyList()).filterNot { it.senderId in blockedUserIds } },
                                onSenderClick = { senderId, _ -> viewingProfileUserId = senderId },
                                onCreateGroup = { name ->
                                    backendScope.launch {
                                        when (val result = BackendClient.createGroup(token, name)) {
                                            is BackendResult.Success -> {
                                                groups.add(LocalGroup(result.value.id, result.value.name))
                                                groupMessages[result.value.id] = mutableStateListOf()
                                            }
                                            is BackendResult.Failure ->
                                                Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onSendToGroup = { id, text ->
                                    backendScope.launch {
                                        when (val result = BackendClient.postGroupMessage(token, id, text)) {
                                            is BackendResult.Success ->
                                                groupMessages.getOrPut(id) { mutableStateListOf() }.add(result.value.toChatEntry(backendUserId))
                                            is BackendResult.Failure ->
                                                Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onJoinGroup = { id ->
                                    backendScope.launch {
                                        when (val result = BackendClient.joinGroup(token, id)) {
                                            is BackendResult.Success -> {
                                                discoverableGroups.removeAll { it.id == id }
                                                groups.add(LocalGroup(result.value.id, result.value.name))
                                                groupMessages[id] = mutableStateListOf()
                                            }
                                            is BackendResult.Failure ->
                                                Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                        else -> when (role) {
                        null -> if (showProfile) {
                            ProfileScreen(
                                themeColor = THEME_COLOR,
                                isDark = isDark,
                                onBack = { showProfile = false }
                            )
                        } else if (showActivityLog) {
                            ActivityLogScreen(
                                themeColor = THEME_COLOR,
                                isDark = isDark,
                                onBack = { showActivityLog = false }
                            )
                        } else if (showSettings) {
                            SettingsScreen(
                                isDark = isDark,
                                onBack = { showSettings = false },
                                notificationPermissionGranted = notificationPermissionGranted,
                                onRequestNotificationPermission = { requestNotificationPermission() },
                                onOpenActivityLog = { showActivityLog = true },
                                onOpenTutorial = { showTutorial = true }
                            )
                        } else RolePickerScreen(
                            isDark = isDark,
                            onPickController = { role = Role.CONTROLLER },
                            onPickControllee = { role = Role.CONTROLLEE },
                            onOpenSettings = { showSettings = true },
                            onOpenProfile = { showProfile = true }
                        )
                        Role.CONTROLLER -> when (controllerScreen) {
                            "laptop" -> LaptopControlScreen(
                                themeColor = THEME_COLOR,
                                isDark = isDark,
                                savedToken = laptopToken,
                                onSaveToken = { token -> saveLaptopToken(token); laptopToken = token },
                                onForgetToken = { forgetLaptopToken(); laptopToken = null },
                                onScanQr = { onResult -> launchQrScan(onResult) },
                                onBack = { controllerScreen = "home" }
                            )
                            "phone" -> PhoneControlScreen(
                                themeColor = THEME_COLOR,
                                isDark = isDark,
                                savedToken = phoneToken,
                                onSaveToken = { token -> savePhoneToken(token); phoneToken = token },
                                onForgetToken = { forgetPhoneToken(); phoneToken = null },
                                onScanQr = { onResult -> launchQrScan(onResult) },
                                onBack = { controllerScreen = "home" }
                            )
                            else -> ControllerHomeScreen(
                                themeColor = THEME_COLOR,
                                isDark = isDark,
                                onOpenLaptop = { controllerScreen = "laptop" },
                                onOpenPhone = { controllerScreen = "phone" },
                                onSwitchRole = { role = null }
                            )
                        }
                        Role.CONTROLLEE -> when {
                            showChat -> ChatScreen(
                                themeColor = THEME_COLOR,
                                isDark = isDark,
                                messages = chatMessages,
                                onSend = { text ->
                                    chatMessages.add(ChatEntry(text, fromMe = true, timestampMs = System.currentTimeMillis()))
                                    PhoneLinkAccessibilityService.instance?.sendChatMessage(text)
                                },
                                onBack = { showChat = false }
                            )
                            showAppAllowlist -> AppAllowlistScreen(onBack = { showAppAllowlist = false })
                            // Mandatory one-time step, right before the pairing/QR screen -
                            // only once setup (disclosure/accessibility/battery) is done and
                            // only before a session starts (never interrupts an active/paused
                            // one - status would no longer be IDLE by then).
                            disclosureAck && accessibilityOn && batteryUnrestricted &&
                                !appAllowlistReviewed && agentStatus == PhoneLinkAgentStatus.IDLE ->
                                AppAllowlistScreen(
                                    onBack = { role = null },
                                    onContinue = {
                                        AppAllowlistStore.markReviewed(this@MainActivity)
                                        appAllowlistReviewed = true
                                    }
                                )
                            else -> PhoneLinkAgentScreen(
                                isDark = isDark,
                                disclosureAcknowledged = disclosureAck,
                                onAcknowledgeDisclosure = {
                                    saveDisclosureAcknowledged()
                                    disclosureAck = true
                                },
                                accessibilityEnabled = accessibilityOn,
                                onOpenAccessibilitySettings = {
                                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                },
                                batteryUnrestricted = batteryUnrestricted,
                                onRequestBatteryUnrestricted = {
                                    runCatching {
                                        startActivity(
                                            Intent(
                                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                                Uri.parse("package:$packageName")
                                            )
                                        )
                                    }.onFailure {
                                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                    }
                                },
                                token = agentToken,
                                status = agentStatus,
                                onStart = {
                                    PhoneLinkAccessibilityService.instance?.startSession(agentToken)
                                    beginScreenCapture()
                                },
                                onResume = { beginScreenCapture() },
                                onLogOut = {
                                    PhoneLinkAccessibilityService.instance?.stopSession()
                                    agentToken = regenerateAgentToken()
                                },
                                onSwitchRole = { role = null },
                                onManageApps = { showAppAllowlist = true },
                                onOpenChat = { showChat = true }
                            )
                        }
                    }
                    }
                }
                }
              }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accessibilityOnState.value = PhoneLinkAccessibilityService.instance != null
        batteryUnrestrictedState.value = isBatteryUnrestricted()
        notificationPermissionGrantedState.value = isNotificationPermissionGranted()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_RESUME_CAPTURE, false) &&
            PhoneLinkAccessibilityService.instance?.currentStatus() == PhoneLinkAgentStatus.PAUSED
        ) {
            beginScreenCapture()
        }
    }
}

/** Gates the Request/Global/Groups tabs behind sign-in (see backend/AuthScreen.kt) -
 * shows a compact "signed in as X / sign out" bar above the real content once
 * authenticated, so switching accounts to test with is a one-tap affair. */
@Composable
private fun BackendGatedContent(
    themeColor: Color,
    isDark: Boolean,
    token: String?,
    username: String?,
    onAuthenticated: (token: String, userId: String, username: String) -> Unit,
    onSignOut: () -> Unit,
    content: @Composable (token: String) -> Unit
) {
    if (token == null) {
        AuthScreen(themeColor = themeColor, isDark = isDark, onAuthenticated = onAuthenticated)
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .systemBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SIGNED IN AS ${username?.uppercase() ?: "?"}",
                    color = Color.Gray,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "SIGN OUT",
                    color = themeColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable(onClick = onSignOut)
                )
            }
            content(token)
        }
    }
}

@Composable
private fun RolePickerScreen(
    isDark: Boolean,
    onPickController: () -> Unit,
    onPickControllee: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF050505) else Color(0xFFF5F5F5))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.Center
        ) {
        Text(
            text = "LINKA",
            color = THEME_COLOR,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "What are you doing right now?",
            color = Color.Gray,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(24.dp))

        listOf(
            "I'M THE CONTROLLER" to "View/control another paired device" to onPickController,
            "I'M THE CONTROLLEE" to "Let a paired device view/control this one" to onPickControllee
        ).forEach { (labelAndSub, action) ->
            val (label, subtitle) = labelAndSub
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Gray.copy(alpha = 0.1f))
                    .clickable(onClick = action)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        color = if (isDark) Color.White else Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    Text(">", color = THEME_COLOR, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
        }
        // Declared last (after the scrollable Column above), not first - a Box's
        // later-composed children draw AND hit-test on top of earlier ones, and
        // verticalScroll's gesture detector claims its entire fillMaxSize() bounds for
        // drag detection (not just the space its content visually occupies under
        // Center arrangement). With this row composed first, that full-screen
        // scrollable was sitting on top of it in z-order and swallowing every tap
        // here before it could reach these icons underneath - found live 2026-09-05.
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .systemBarsPadding()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(48.dp).clickable(onClick = onOpenProfile),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Filled.AccountCircle, contentDescription = "Profile", tint = Color.Gray)
            }
            Box(
                modifier = Modifier.size(48.dp).clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings", tint = Color.Gray)
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    isDark: Boolean,
    onBack: () -> Unit,
    notificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenActivityLog: () -> Unit,
    onOpenTutorial: () -> Unit
) {
    val context = LocalContext.current
    var selectedIcon by remember { mutableStateOf(AppIconSwitcher.current(context)) }
    var notificationsWanted by remember { mutableStateOf(NotificationPrefs.notificationsEnabled(context)) }
    var notifyChat by remember { mutableStateOf(NotificationPrefs.notifyChatMessages(context)) }
    var notifyRequests by remember { mutableStateOf(NotificationPrefs.notifyRequests(context)) }
    var notifySession by remember { mutableStateOf(NotificationPrefs.notifySessionStatus(context)) }
    var notifyGlobal by remember { mutableStateOf(NotificationPrefs.notifyGlobalChat(context)) }
    var notifyGroup by remember { mutableStateOf(NotificationPrefs.notifyGroupChat(context)) }
    var chatAccessGlobal by remember { mutableStateOf(NotificationPrefs.chatAccessGlobal(context)) }
    var chatAccessGroups by remember { mutableStateOf(NotificationPrefs.chatAccessGroups(context)) }
    var groupAddAccess by remember { mutableStateOf(NotificationPrefs.groupAddAccess(context)) }
    var requestAccess by remember { mutableStateOf(NotificationPrefs.requestAccess(context)) }
    val notificationsActive = notificationsWanted && notificationPermissionGranted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF050505) else Color(0xFFF5F5F5))
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
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
                text = "SETTINGS",
                color = THEME_COLOR,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(24.dp))

        Text(
            text = "APPEARANCE",
            color = Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "App icon",
            color = if (isDark) Color.White else Color.Black,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconStyle.entries.forEach { style ->
                val isSelected = style == selectedIcon
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Gray.copy(alpha = 0.08f))
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) THEME_COLOR else Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            AppIconSwitcher.switchTo(context, style)
                            selectedIcon = style
                        }
                        .padding(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = style.drawableRes),
                        contentDescription = style.label,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = style.label,
                        color = if (isDark) Color.White else Color.Black,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "May take a few seconds to update on your home screen after switching.",
            color = Color.Gray,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(Modifier.height(28.dp))
        Text(
            text = "NOTIFICATIONS",
            color = Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Notifications",
                color = if (isDark) Color.White else Color.Black,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = notificationsActive,
                onCheckedChange = { turningOn ->
                    notificationsWanted = turningOn
                    NotificationPrefs.setNotificationsEnabled(context, turningOn)
                    if (turningOn && !notificationPermissionGranted) onRequestNotificationPermission()
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = THEME_COLOR,
                    checkedTrackColor = THEME_COLOR.copy(alpha = 0.5f)
                )
            )
        }
        if (notificationsWanted && !notificationPermissionGranted) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Blocked in system settings.",
                color = Color.Gray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "OPEN NOTIFICATION SETTINGS",
                color = THEME_COLOR,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                }
            )
        }
        Spacer(Modifier.height(12.dp))
        listOf(
            Triple("Paired chat messages", notifyChat) { v: Boolean -> notifyChat = v; NotificationPrefs.setNotifyChatMessages(context, v) },
            Triple("Global chat", notifyGlobal) { v: Boolean -> notifyGlobal = v; NotificationPrefs.setNotifyGlobalChat(context, v) },
            Triple("Group chat", notifyGroup) { v: Boolean -> notifyGroup = v; NotificationPrefs.setNotifyGroupChat(context, v) },
            Triple("Access requests", notifyRequests) { v: Boolean -> notifyRequests = v; NotificationPrefs.setNotifyRequests(context, v) },
            Triple("Session status", notifySession) { v: Boolean -> notifySession = v; NotificationPrefs.setNotifySessionStatus(context, v) }
        ).forEach { (label, checked, setter) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().alpha(if (notificationsActive) 1f else 0.4f)
            ) {
                Text(
                    text = label,
                    color = if (isDark) Color.White else Color.Black,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = checked,
                    onCheckedChange = setter,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = THEME_COLOR,
                        checkedTrackColor = THEME_COLOR.copy(alpha = 0.5f)
                    )
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            text = "PRIVACY",
            color = Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Not enforced yet - there's no way for strangers to reach you until " +
                "Linka has real accounts. These set your preference for when that exists.",
            color = Color.Gray,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(14.dp))
        listOf(
            Triple("Who can chat me - Global", chatAccessGlobal) { v: AccessChoice -> chatAccessGlobal = v; NotificationPrefs.setChatAccessGlobal(context, v) },
            Triple("Who can chat me - Groups", chatAccessGroups) { v: AccessChoice -> chatAccessGroups = v; NotificationPrefs.setChatAccessGroups(context, v) },
            Triple("Who can add me to a group", groupAddAccess) { v: AccessChoice -> groupAddAccess = v; NotificationPrefs.setGroupAddAccess(context, v) },
            Triple("Who can send me requests", requestAccess) { v: AccessChoice -> requestAccess = v; NotificationPrefs.setRequestAccess(context, v) }
        ).forEach { (label, current, setter) ->
            Text(
                text = label,
                color = if (isDark) Color.White else Color.Black,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AccessChoice.entries.forEach { choice ->
                    val selected = choice == current
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) THEME_COLOR.copy(alpha = 0.18f) else Color.Gray.copy(alpha = 0.08f))
                            .border(
                                width = if (selected) 1.5.dp else 0.dp,
                                color = if (selected) THEME_COLOR else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { setter(choice) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (choice == AccessChoice.EVERYONE) "EVERYONE" else "NO ONE",
                            color = if (isDark) Color.White else Color.Black,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Spacer(Modifier.height(28.dp))
        Text(
            text = "ACTIVITY",
            color = Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Gray.copy(alpha = 0.08f))
                .clickable(onClick = onOpenActivityLog)
                .padding(14.dp)
        ) {
            Text(
                text = "View activity log",
                color = THEME_COLOR,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(28.dp))
        Text(
            text = "TUTORIAL",
            color = Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Gray.copy(alpha = 0.08f))
                .clickable(onClick = onOpenTutorial)
                .padding(14.dp)
        ) {
            Text(
                text = "Watch or read the tutorial again",
                color = THEME_COLOR,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
