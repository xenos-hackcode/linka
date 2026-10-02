# Linka — Feature Spec & Play Store Compliance Plan

Status: draft, for alignment before implementation. Reflects the current codebase
(`controller`, `agent`, `laptop_agent`) plus the new features described 2026-08-14.

## 1. Terminology

| Product term | Maps to existing code |
|---|---|
| Controller | `controller` Android module (already handles phone + laptop targets) |
| Controllee | `agent` Android module + `laptop_agent` (Windows, Python) — today these are two separate codebases presented as one product |

Model: the **controllee owns the device** and grants access; the **controller** is the
remote operator (support person, parent, partner, etc.). This is closer to a
remote-support tool (TeamViewer/AnyDesk-style) than covert monitoring software —
important for how Play Store will classify the app. Controllee can already leave at
any time via logout or uninstall; no anti-uninstall/lockout exists or should be added.

**Decided (2026-08-14): one app, not two.** `controller` and `agent` are currently
separate installable apps (different package names/icons). Target UX is a single app
per platform with a role picker — the user chooses "I'm the controller" or "I'm the
controllee" at that moment (or switches later), rather than installing a
controller-only or controllee-only app. This applies per-platform (the Android app
does both roles; the Windows app does both roles; etc.) — it does not mean literally
one binary across all three OSes, that's the separate shared-core question in §5.
Sequencing: the in-progress `:core` wire-protocol extraction (§5) is a prerequisite,
not something this changes — merging `controller`+`agent` into one Android app with a
role picker is the natural next phase once both roles sit on top of the same `:core`
logic, since at that point it's mostly a navigation/UI merge rather than a protocol one.

## 2. Platform matrix

Target UX (confirmed): opening either Controller or Controllee shows a platform
picker — Android, iPhone, Windows — and **any platform can control any other
platform**, in any combination (iPhone→Windows, Windows→Android, Android→iPhone,
etc.), with one asymmetry: iPhone-as-controllee is view-only (2.1).

| Controller platform | Controllee platform | Status today |
|---|---|---|
| Android | Android | Exists |
| Android | Windows | Exists (remote-desktop style, in the Android controller app) |
| Android | iPhone | New — full control from Android; iPhone side is view-only, see 2.1 |
| Windows | Android | New — Windows-as-controller doesn't exist at all yet |
| Windows | Windows | New |
| Windows | iPhone | New — view-only |
| iPhone | Android | New |
| iPhone | Windows | New |
| iPhone | iPhone | New — view-only |

Note: **Windows as a controller platform is new work**, not a rename — today the
controller app is Android-only, it just happens to already support both phone and
laptop as *targets*. Nine combinations total (minus the fact that iPhone-as-controllee
is always view-only regardless of which controller drives it) is the concrete reason
the shared-core rewrite (agreed below, §5) is worth it over hand-building each
platform pair separately: one core session/protocol/mode-logic layer (lock/retransfer/
dark modes, chat, domain) with a thin platform-specific UI + input-injection shim per
OS, rather than duplicating that logic three or nine times over.

### 2.1 Why iOS controllee is view-only

Needs to be stated explicitly in the App Store review notes, not just known
internally. Apple does not expose an entitlement to third-party App Store apps that
allows injecting taps/gestures/text into other apps — the closest equivalent to
Android's Accessibility Service is only granted for MDM/enterprise device management,
which requires an Apple Business/enterprise agreement Linka doesn't have. Building
full remote control on iOS would require either (a) private APIs, which is an
automatic rejection, or (b) an enterprise MDM profile, which is a different
distribution model entirely (not App Store, requires organizational enrollment).
Screen mirroring via ReplayKit's broadcast extension is the actual public, sanctioned
API surface available, which is why iOS controllee is scoped to view-only rather than
matching Android/Windows's feature set. This should be stated plainly in both the App
Store review notes and in-app copy so the reduced functionality reads as an
intentional platform constraint, not a bug or a hidden/incomplete feature.

## 3. Feature areas

### 3.1 App access control (controllee-defined allowlist)
**Status: not yet built** — still spec-only as of 2026-08-15. Controllee chooses which
apps the controller may interact with, changeable at any time (a live setting, not a
one-time install-time choice). Apps not on the allowlist are either non-interactive or
shown blacked-out in the controller's view. This is an extension of the existing
screen-share/remote-control pipeline — needs an app-identity signal on the controllee
side (foreground app / window title) that the controller-side renderer checks before
forwarding input or rendering the frame.

### 3.2 Per-account/app modes
**Status: not yet built.** Generalizes to **any** account-style app the controllee
flags, not just Gmail — Gmail below is just the running example, not a special case.
Applies to specific accounts or apps that the controllee wants
to handle differently than a flat allow/deny:

- **Lock mode** — controllee marks specific accounts as off-limits to the controller.
  A password gate is shown **only on the controller's side**; the controllee's own use
  of the account is unaffected. The controllee never needs to know the password —
  it exists purely to stop the controller from entering that account, not to stop the
  controllee.
- **Retransfer mode** — controller can add their own account/app instance that
  *appears* to be running on the controllee's device from the controller's view, but
  is fully sandboxed: nothing the controller does in it touches the real controllee
  device or data.
- **Dark mode / black mode** — if the controller taps into an account/app the
  controllee has set to dark mode, the controller's screen goes black and inactive.
  Controllee side is unaffected and unaware anything happened.

All three are controllee-side privacy protections *against* the controller, not the
other direction — keep this framing consistent, it's what keeps the product out of
stalkerware territory.

### 3.3 Communication
- **Paired DM** — controller ↔ their own controllee. Always available, no gating.
- **Global chat** — all app users can message each other.
- **Group chat** — many-to-many communication, like a persistent group.

**Age gating (agreed):** controllee accounts flagged as minors are restricted to
paired DM only. Global chat and group chat require an adult-verified account. This is
required for Play Store's Child Safety Standards / CSAE policy — an open
stranger-messaging surface reachable by minor accounts is close to a guaranteed
rejection (or worse, a ban after the fact) if it's ever able to reach a minor account.

**Rate limiting (agreed):** daily send limits — a per-account "token" budget capping
messages sent per day — on global/group chat. This is standard anti-spam/anti-abuse
hygiene expected by reviewers and needed regardless of Play policy.

### 3.4 Domain — access-request marketplace
Users can post a request asking another user to share access to a specific app/account,
with a stated reason; other users can respond and fulfill it. Kept open to all users
per your call, but:

- **Age gating (agreed):** minor-flagged controllee accounts do not participate —
  same reasoning as global chat. This is the single riskiest feature in the app if it
  reaches a minor account: "share access to your account, here's my reason" is
  structurally identical to a phishing/social-engineering pitch.
- **Rate limiting (agreed):** a daily cap on requests postable/answerable per account,
  to blunt spam and mass credential-harvesting attempts.
- Still needs, before launch: a report/block mechanism on both requests and responding
  users, and some abuse-review process — reviewers will look for this on any feature
  that brokers account access between strangers, gated or not.

### 3.5 Age verification

Determines whether an account is gated out of global chat / group chat / domain
marketplace per 3.3–3.4. Multiple paths, user picks one:

1. **Government ID upload** — strongest, most friction. Best done via a third-party
   ID-verification vendor (e.g. Persona, Veriff, Incode) rather than building
   ID-document parsing in-house — they carry the compliance burden for document data
   handling/retention.
2. **Parent-linked account** — a parent/guardian account vouches for the minor
   account and controls what it can access (ties naturally into the controller/
   controllee relationship already in the product — the parent could literally be the
   controller).
3. **Self-declared + photo check** — user states their age, then takes a photo/selfie
   for an age-estimation pass. If the estimated age doesn't match the declared age:
   send a warning on first mismatch; a second mismatch escalates to a **device ban**.

Escalation detail (device ban): intent is that a banned device can't just reinstall
and make a new account — needs a new physical phone. Two caveats worth knowing before
this gets built as a hard guarantee:
- Fully reliable identifiers are limited — factory reset defeats some device
  fingerprinting. Play Integrity API (Android) / DeviceCheck (iOS) attestation gets a
  reasonably strong, hard-to-spoof device signal that survives uninstall/reinstall,
  though not a factory reset. Treat "new phone required" as the practical outcome for
  the overwhelming majority of users, not a mathematical guarantee against a
  determined adversary.
- Facial age-estimation is a sensitive data category on its own, especially applied to
  a photo that might turn out to belong to a minor — retention policy matters a lot
  here. Recommend routing this through a dedicated age-assurance vendor (Yoti, Persona,
  Veriff all offer this) that returns a pass/fail or estimated range rather than
  storing/processing the photo ourselves — keeps the sensitive biometric handling and
  its compliance obligations off Linka's own infrastructure.

### 3.6 Windows dual-session "share mode" (decided 2026-08-16, Phase 1 shipped, Phase 2 deferred)
Idea: the physical user and a remote controller both use the same Windows PC at the
same time, without fighting over one screen/mouse. Real multi-session Remote Desktop
(true OS-level session isolation) needs Windows Server/Enterprise RDS licensing, not
something available by default on whatever PC someone installs Linka on - ruled out.
The unofficial concurrent-RDP-session patches (e.g. RDPWrap) were also ruled out -
unofficial, break across Windows updates, sit in a gray area against Microsoft's terms.

**Decided approach:** a virtual-display-driver setup - the physical user keeps their
real screen/mouse/keyboard; the remote controller is pointed at a second, virtual
monitor instead. Not true hardware-level isolation (Windows still has one system mouse
cursor, so both moving the mouse at the exact same instant will collide) but workable
for normal use where each person stays on their own screen. Recommended driver:
[VirtualDrivers/Virtual-Display-Driver](https://github.com/VirtualDrivers/Virtual-Display-Driver)
(MIT license, signed, no test-signing mode needed on x64).

- **Phase 1 (shipped 2026-08-16):** `laptop_agent/agent.py` can now target *any*
  monitor, not just the primary - `python agent.py --choose-monitor` lists available
  displays and remembers the choice (`monitor_index` in config.json); coordinate
  mapping was fixed to offset by the chosen monitor's position (previously assumed
  origin (0,0), silently wrong for anything but the primary display).
- **Phase 2 (not started - user action, not a build task):** install the virtual
  display driver above, then run `--choose-monitor` and pick the new virtual display.
  Nothing in the codebase blocks this; it just hasn't been done yet.

## 4. Play Store compliance checklist

- Accessibility Service use (gesture injection, `agent` module) must be justified in
  Play's Permissions Declaration Form as remote-support functionality — this is an
  existing hurdle independent of the new features, worth doing early since it can
  gate the whole release.
- No covert/hidden operation: controller presence should be visibly indicated on the
  controllee device while a session is active (persistent notification at minimum).
  Current uninstall/logout escape hatch is good — keep it, don't add anything that
  narrows it later (e.g. don't let "lock mode" or dark mode be reachable in a way that
  restricts the controllee's own exit paths).
- Global chat / group chat / domain marketplace: age gating + rate limits above are
  the minimum bar; also need in-app report/block on every user-to-user surface (chat
  messages, domain posts/responses) before this ships to real users, not just before
  Play submission.
- Age verification mechanism — see 3.5 below.
- iOS controllee's reduced (view-only) functionality must be explained in the App
  Store review notes and in the in-app onboarding copy — see reasoning in section 2.
  Reviewers reject apps where a feature gap looks unexplained or hidden; stating the
  platform constraint up front heads that off.

## 5. Decisions log & next steps

**Decided: shared-core rewrite**, not separate-codebases-kept-in-sync. Given the
any-to-any platform matrix in §2 (every OS can be controller or controllee, roughly
nine platform-pair combinations), hand-maintaining separate Android/Windows/iOS
implementations of the session protocol, permission logic, mode logic (lock/
retransfer/dark), chat, and domain features would mean re-solving the same logic
three-plus times and keeping them in sync by hand. Direction instead:
- One shared core (likely Kotlin Multiplatform, since Android's existing Kotlin code
  becomes the natural base) owning: wire protocol, session/pairing state, the
  allowlist/lock/retransfer/dark mode logic, chat, and domain logic.
- Thin per-platform shells doing only what's inherently platform-specific: Android
  Accessibility Service + MediaProjection, Windows input injection + capture
  (replacing `laptop_agent`'s Python implementation), iOS ReplayKit (view-only, per
  2.1) and standard UI/view layer per OS.
- This replaces `laptop_agent` (Python) rather than keeping it — it becomes one of
  the platform shells around the shared core instead of an independent implementation.

**Decided (2026-08-14): language split — shared body, native design per platform.**
The shared core is Kotlin everywhere (Android and Windows compile it directly; iOS
consumes it via a Kotlin/Native-built framework). UI is **not** shared — each platform
keeps its own native toolkit: Compose (Kotlin) on Android, SwiftUI on iOS, a native
Windows UI to replace `laptop_agent`'s current Python one. Two things ruled out along
the way, worth keeping on record so they don't get re-proposed by accident:
- **Swift cannot cover Android** — it's Apple's toolkit, tied to Xcode/Apple's
  runtime, with no production-viable path to render Swift UI on Android.
- **A single shared UI in Kotlin across all three (Compose Multiplatform, including
  its iOS target) was considered and explicitly declined** — it would technically
  work, but iOS Compose Multiplatform is less mature than native SwiftUI, and three
  separate native UIs was preferred over that bet. If this trade-off ever needs
  revisiting (e.g. UI maintenance across three codebases becomes the bottleneck),
  Compose Multiplatform is the fallback option, not Swift-everywhere (impossible) or
  Kotlin-everywhere via any path other than Compose Multiplatform.

Next step: this spec is far enough along to turn into a real implementation plan —
shared-core architecture/module boundaries, data model, backend endpoint changes on
the external elene-backend, wire protocol changes, and per-platform shell work. Say
the word when you want to move into that.
