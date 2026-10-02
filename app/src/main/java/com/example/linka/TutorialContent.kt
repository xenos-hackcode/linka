package com.example.linka

/** One card in the tutorial (see TutorialScreen.kt). Render order within a card is:
 * title -> [videoRes] (a small inline WATCH button, for a step that needs its own
 * focused clip beyond the main tutorial video) -> [body] -> [imageRes] -> [bodyAfter].
 * [imageRes]/[videoRes] are drawable/raw resource ids, e.g. `R.drawable.tutorial_x` /
 * `R.raw.tutorial_x` - drop the file into res/drawable or res/raw and wire it in here;
 * left null until real screenshots/clips exist, in which case that part just doesn't
 * render. [bodyAfter] only makes sense paired with [imageRes] (text that continues
 * after the picture, e.g. describing what happens once you tap what it shows) - most
 * steps don't need it and leave it null. */
data class TutorialStep(
    val title: String,
    val body: String,
    val imageRes: Int? = null,
    val bodyAfter: String? = null,
    val videoRes: Int? = null,
)

/** Text content for the first-run/replayable tutorial. Editing this list is the only
 * thing needed to change what the tutorial says - TutorialScreen.kt just renders it.
 *
 * Video: bundled directly into the app as a raw resource (res/raw/tutorial_video.mp4)
 * rather than hosted externally, so it plays offline with no upload/link needed - see
 * [videoRawRes]. [videoUrl] stays as a fallback for a hosted link (YouTube/Drive/etc)
 * if the video ever moves off-device instead; only one of the two needs to be set. */
object TutorialContent {
    val videoUrl: String? = null
    val videoRawRes: Int? = R.raw.tutorial_video

    val steps: List<TutorialStep> = listOf(
        TutorialStep(
            title = "Welcome to Linka",
            body = "Linka lets one device view and control another that you've paired " +
                "it with - your phone controlling your PC, or your phone controlling " +
                "another phone. It also has a small community side: post or answer " +
                "access requests, and chat in Global or in Groups."
        ),
        TutorialStep(
            title = "Pick your role each time",
            body = "Every time you open Linka, you choose: \"I'M THE CONTROLLER\" (you're " +
                "about to view/control something else) or \"I'M THE CONTROLLEE\" (you're " +
                "letting a paired device view/control this one). Nothing happens " +
                "automatically - picking a role never starts sharing anything by itself."
        ),
        TutorialStep(
            title = "Controller: link to a PC or phone",
            body = "From the Controller side, choose LINK TO PC or LINK TO PHONE, then " +
                "enter the pairing code shown on the other device (or scan its QR code). " +
                "Once paired, you'll see that device's screen live and can tap, drag, " +
                "scroll, and type on it - this only ever connects OUT, it never exposes " +
                "your own screen."
        ),
        TutorialStep(
            title = "Controllee: let another device link to you",
            body = "From the Controllee side, you'll acknowledge what sharing means, then " +
                "turn on Accessibility Service and allow unrestricted battery use - both " +
                "are real Android permissions granted in Android's own Settings, never " +
                "silently. You'll then see your own pairing code/QR to share with " +
                "whoever should link to you. You can review or narrow which apps are " +
                "shared anytime under MANAGE SHARED APPS, and LOG OUT instantly ends the " +
                "session and invalidates the code."
        ),
        TutorialStep(
            title = "If Accessibility won't turn on",
            videoRes = R.raw.tutorial_restricted_settings,
            body = "Since Linka isn't installed from the Play Store, Android blocks the " +
                "Accessibility toggle the first time and calls it a \"Restricted setting\" " +
                "instead of just turning on. To fix it: open your phone's own Settings app " +
                "(not Linka's) > Apps > Linka. At the top left there's a three-dot ⋮ menu " +
                "like this:",
            imageRes = R.drawable.tutorial_restricted_dots,
            bodyAfter = "Tap it - a small dropdown appears saying \"Allow restricted " +
                "settings\". Tap that, confirm, then come back and turn Accessibility on " +
                "again - it'll work this time. You only need to do this once."
        ),
        TutorialStep(
            title = "Request access to something",
            body = "The REQUEST tab is a small marketplace: post what you're requesting " +
                "and why, or browse and accept/deny requests from others under RESPOND. " +
                "Once a request is accepted, the ACCEPTED tab shows a pairing QR/code so " +
                "the two of you can link directly."
        ),
        TutorialStep(
            title = "Global and Group chat",
            body = "GLOBAL is one chat everyone shares; GROUPS lets you create or join " +
                "smaller ones. Use #name to address someone specific and @all to tag " +
                "everyone. Tap a sender's name to view their profile, message them " +
                "directly, start a group with just the two of you, or block them."
        ),
        TutorialStep(
            title = "Settings, privacy, and your activity log",
            body = "Settings (top-right on the role picker) covers your app icon, " +
                "notification preferences, and who's allowed to chat/request/add you " +
                "(EVERYONE or NO ONE - your call). Every link, request, and response is " +
                "recorded in the Activity Log underneath, so you always have a record of " +
                "what happened and when."
        ),
        TutorialStep(
            title = "Come back to this anytime",
            body = "This tutorial only auto-opens once, the first time you use Linka. To " +
                "read or watch it again later: open Settings, scroll down past Activity " +
                "Log, and tap the TUTORIAL box."
        ),
    )
}
