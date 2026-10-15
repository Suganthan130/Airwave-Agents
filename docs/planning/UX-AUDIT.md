# UX Audit — Airwave Agents (full product)

> Produced by `/android-ux-audit`. Covers all 8 Android transmitter screens + the web
> listener app. Findings are severity-ranked; each fix is shippable as written and cites the
> heuristic / law / pattern it comes from. `file:line` points at the exact spot.

**Scope audited**
- Android (`integrations/transmiter/`): HomeScreen, LoginScreen, SignUpScreen, MainActivity,
  AiStudioScreen, MixerScreen, EqualizerScreen, LibraryScreen, SettingsScreen — judged against
  the shared design system (`theme/Color.kt`, `Type.kt`, `Theme.kt`, `components/Common.kt`).
- Web (`apps/web/`): App, RadioPlayer, ChatBox, Header, ProfileModal, StationDirectoryModal,
  RjAnnouncementBanner — judged against `tailwind.config.js` + `index.css`.

**Inferred intent** — *confirm before acting on P1s.*
- **Transmitter (broadcaster):** operate a live stream on a phone, often one-handed, and stay
  live 24/7 without accidental dead air.
- **Web (listener):** open the page (usually on a phone), press play, hear the live station,
  see what's on air, chat with the AI host.

---

## Summary

| Severity | Count | Dominant theme |
|---|---|---|
| **P1** | 6 | **False success / silent failure** — the UI says it worked while the real system is silent, off-air, or broken |
| **P2** | 24 | Transient toast errors with no way forward · unconfirmed destructive actions · missing a11y names · sub-48dp targets · missing loading/disabled states |
| **P3** | 39 | Loading/empty conflation (no skeletons) · status-by-color-alone · process-death state loss · missing undo · touch targets |
| **P4** | 20 | Hardcoded hex vs tokens · off-4dp-grid spacing · emoji-as-icons · duplicated components · unused design tokens |

---

## The one theme that matters most: *false success*

Five of the six P1s are the same bug wearing different clothes — **a success/positive state
shown while the underlying operation failed or never completed.** For a "stay live 24/7"
product this is the single most damaging class, because it hides exactly the failures the
product exists to prevent.

| Surface | Says | Reality |
|---|---|---|
| HomeScreen | silently reverts to "Go Live" | the station just dropped **off air** mid-show |
| AiStudioScreen | card stuck "Voicing…", button swallows taps | "Play Now" is **permanently dead** after first use |
| SettingsScreen | Toast "Settings saved" | invalid listener URL **silently discarded**; listeners can't tune in |
| AiStudioScreen | Toast "Hiru News ticker refreshed" | the refresh **failed** |
| RadioPlayer (web) | green "Live Broadcast In Sync · playing in real time" | **autoplay was blocked**; the user hears nothing |

Fix pattern for all of them: **drive status from the real signal** (connection state, playback
`onPlaying`, operation result) — never from a timer, an unconditional toast, or an unreset
flag — and **never render a positive state unless the operation actually succeeded.**

Two cross-cutting product decisions also surfaced on both platforms and are worth a deliberate
call: **no dark theme** (`Theme.kt:9/28` forces light + disables dynamic color; web sets
`darkMode:"class"` at `tailwind.config.js:7` but ships no dark theme) — a glare problem for a
late-night studio shift; and **the type identity never renders** (`Type.kt` leaves the scale at
M3 defaults; web configures Inter but never loads the font), which flattens visual character.

---

# P1 — Critical (UX / safety)

### P1-1 · HomeScreen · Stopping the live broadcast has no confirmation — one mis-tap = dead air
**Where:** `HomeScreen.kt:863–874`
**Problem:** When `connState != 2` the card shows a red **Stop** wired straight to
`engine.stopBroadcast()` — no dialog, no undo. A single accidental tap on a one-handed phone
takes a 24/7 station off the air. Worse, Stop occupies the *same* primary slot the blue **Go
Live** button sat in (`852–862`), so a broadcaster double-tapping to start can land the second
tap on Stop the instant `connState` flips.
**Fix:** Gate `stopBroadcast()` behind an `AlertDialog` — confirm "Stop broadcast" (red) vs.
default-focused "Keep broadcasting", body "Your listeners will hear silence." Don't render Stop
in the same geometric position as Go Live.
**Cite:** Destructive Actions pattern; Nielsen N5 (error prevention).

### P1-2 · HomeScreen · Dropped connection goes silently off-air (surfaced only by keyword match)
**Where:** `HomeScreen.kt:843–849` (error render), `:863` (silent revert)
**Problem:** The error line renders only if `connState == 2 && connMessage` contains
"error/fail/refused/unreach". Any other drop (or blank message) silently swaps the UI back to
**Go Live** with zero indication the broadcaster just went OFF AIR mid-show. No "Reconnecting…"
state despite `StreamConfig.autoReconnect`, and no offline state at all.
**Fix:** Drive status from `connState` + a typed engine error, not substring matching. Add a
persistent "⚠ Connection lost — reconnecting / Off air" banner with Retry when a live session
drops; add an offline state when there's no network while live.
**Cite:** Error States pattern; Nielsen N1; checklist §3 (Error/Offline).

### P1-3 · AiStudioScreen · "Play Now" permanently dies after first use, silently
**Where:** `AiStudioScreen.kt:98, 151–157` (guard at `:152`)
**Problem:** `broadcastingId` is set in `playNewsNow()` but **never reset** (unlike
`enqueuingId`/`generatingId`). Because `playNewsNow` opens with `if (broadcastingId != null) return`,
the first "Play Now" tap leaves it non-null forever — every later "Play Now" is silently
swallowed and the originating card is stuck showing "Voicing…" indefinitely. The broadcaster's
primary interrupt action becomes a dead button mid-show; only leaving and returning recovers it.
**Fix:** Reset `broadcastingId = null` on completion — give `onBroadcastNewsNow` a completion
callback (or mirror the enqueue timeout pattern as a floor), and clear + surface an error on
failure.
**Cite:** State Completeness (Success + Error unhandled); Nielsen N1; Error States.

### P1-4 · SettingsScreen · "Save" reports success after silently discarding an invalid listener URL
**Where:** `SettingsScreen.kt:408–410, 428, 472–477`
**Problem:** `effectiveStream = publicListeningUrl.takeIf { isValidPublicStreamUrl } ?: … ?: ""`.
A malformed public stream URL is silently dropped to the old value or `""`, then the Toast says
"Settings saved." The broadcaster believes listeners can tune in; the public station page is
actually broken, with no error anywhere — the stated worst case behind a false success.
**Fix:** Validate on blur; if invalid, inline error under the field (`DangerRed` + icon) and
**block** the save ("Fix the stream URL before saving"). Never discard typed input while
reporting success.
**Cite:** Nielsen N1 + N5; Error States; Postel's Law.

### P1-5 · Auth · Forgot-password is a dead end — no account recovery
**Where:** `MainActivity.kt:160` (`onForgotPassword = { toast("Password reset is coming soon.") }`)
**Problem:** A broadcaster who forgot their password has zero way back in — the only recovery
control shows a transient "coming soon" toast. That's "error state with no way forward" on the
single most important recovery path of the whole app.
**Fix:** Wire to Firebase `sendPasswordResetEmail(email)` using the typed email (validate first;
focus the field if empty); show an in-card "Reset link sent to {email}". If it genuinely can't
ship now, **hide** the link rather than lead nowhere.
**Cite:** Nielsen N9; checklist §States/Error; flows.md (auth recovery).

### P1-6 · RadioPlayer (web) · UI claims "Live Broadcast In Sync — playing in real time" while silent
**Where:** `RadioPlayer.tsx:56–103` (sync timer) → `407–468` (footer) + `118–145` (autoplay)
**Problem:** The 20s sync countdown runs on a bare `setInterval` keyed only on
`radioStatus.isLive` — not tied to real playback. When autoplay is blocked (the most common real
failure — `.catch` at `:139` only logs), `syncSeconds` still ticks to 0, the badge flips to
"LIVE ON AIR", and the footer shows green "…playing in real time · 0s BUFFER LAG • LIVE" while
the user hears nothing and was never told to tap play. Same false-positive if `onError` fires
after the timer hit 0.
**Fix:** Drive "in sync" from `isPlaying && !isBuffering`, not the timer. On autoplay reject, set
a `needsGesture` state and render **"Tap ▶ to start listening"** instead of the green panel. On
`onError`, keep the red alert and never show "in sync".
**Cite:** Nielsen N1; Autosave pattern ("never show Saved for a failed write"); Error States.

---

# P2 — Major

## HomeScreen
### P2-1 · Go Live / Stop have no loading or disabled state during the connect transition
**Where:** `HomeScreen.kt:852–862`, `564–582` · **Problem:** `goLive()` fires `startBroadcast()`
with no in-flight guard and no spinner; during the 2→0 window the user can double-fire start and
nothing on the button confirms the tap. Violates the project's own "disable the triggering
control while work is in flight." · **Fix:** While `connState == 0`, show a disabled
"Connecting…" button with a spinner; ignore repeat taps. · **Cite:** Disabled Buttons / Loading
States System; CLAUDE.md "Handle every state".

### P2-2 · Critical broadcast errors are Toast-only and offer no way forward
**Where:** `HomeScreen.kt:566–573, 177, 1664, 1703` · **Problem:** Go-live blockers ("Add your
Icecast server in Settings…") and failures are transient Toasts — easily missed, poorly
announced, and the "go to Settings" copy gives no way to actually get there. · **Fix:** Inline
banner naming the missing field **+ an "Open Settings" button** that sets `tab = SETTINGS`;
failures become a snackbar with Retry. · **Cite:** Error States; Nielsen N9.

### P2-3 · Icon-only transport + reorder controls have null content descriptions
**Where:** `HomeScreen.kt:2021–2028, 1180–1185` · **Problem:** Skip-prev/next and the primary
56dp play/pause pass `contentDescription = null` — invisible to TalkBack during a live show. ·
**Fix:** Give each a `stringResource` description (toggle "Play"/"Pause" with state). · **Cite:**
WCAG 1.1.1; accessibility ref.

### P2-4 · Primary interactive controls below 48×48dp
**Where:** `HomeScreen.kt:2026` (transport 32dp), `1808, 1821–1823` (queue move 32dp) ·
**Problem:** 32dp targets on a live console invite mis-taps; adjacent reorder arrows reorder the
wrong item on a slip. · **Fix:** Wrap the 20dp icon in a ≥48dp clickable box. · **Cite:** Fitts's
Law; WCAG 2.5.8.

### P2-5 · Flash News draft text lost on rotation / process death
**Where:** `HomeScreen.kt:516–519, 318, 510–515` · **Problem:** The broadcast survives rotation
(service-backed), but `showFlashNewsDialog`/`newsHeadline`/`newsContent` use `remember`, not
`rememberSaveable` — rotate or hit process death and the typed bulletin is gone. · **Fix:**
`rememberSaveable` for those fields. · **Cite:** android-platform; checklist §3/§10.

### P2-6 · System Back unhandled when the Equalizer takes over the screen
**Where:** `HomeScreen.kt:311–314` · **Problem:** `showEqualizer` swaps the whole UI via early
`return` with no `BackHandler`; hardware/gesture Back likely pops the app instead of returning
to the Mixer. · **Fix:** `BackHandler(enabled = showEqualizer) { showEqualizer = false }`. ·
**Cite:** android-platform (Back vs Up); Nielsen N3.

## Auth
### P2-7 · Server/network auth errors are a Toast, not in the form
**Where:** `MainActivity.kt:99, 127, 146` vs inline error `LoginScreen.kt:186` /
`SignUpScreen.kt:192` · **Problem:** Client validation renders inline, but "Incorrect email or
password," "email already exists," and network errors fire as a transient bottom Toast — two
error channels, with the most important failure on the weaker one. · **Fix:** Hoist
`errorMessage: String?` into auth state and render it in the screen's existing inline slot. ·
**Cite:** Nielsen N1 + N9.

### P2-8 · Form errors aren't announced to TalkBack
**Where:** `LoginScreen.kt:186–193`, `SignUpScreen.kt:192–199` · **Problem:** The error `Text`
carries no live-region/error semantics; a TalkBack user taps "Log in", hears nothing, and
doesn't know why it failed. · **Fix:** `Modifier.semantics { liveRegion = Assertive }` on the
error, `isError = true` + error semantics on the offending field. · **Cite:** checklist §Forms;
accessibility ref.

### P2-9 · No autofill hints, no IME action / focus progression
**Where:** `AppTextField` `LoginScreen.kt:290–322`, `SignUpScreen.kt:322–354` · **Problem:** No
autofill content types (password managers can't fill/save → weaker passwords); no `imeAction`,
so the 4-field signup shows no Next/Done and won't advance or submit. · **Fix:** Per-field
`contentType` (Email/Username/Password/NewPassword/PersonFullName); `imeAction = Next` on all but
last, `Done` on last wired to submit via `keyboardActions`. · **Cite:** checklist §Forms; Jakob's
Law.

### P2-10 · System Back on Sign-Up exits the app and discards the typed form
**Where:** `MainActivity.kt:41, 154–175` (navigation is a `var screen` `when`, no `BackHandler`)
· **Problem:** From LOGIN → "Sign up", Back leaves the app and drops a half-filled registration;
only the in-screen "Log in" link returns. · **Fix:**
`BackHandler(enabled = screen == SIGN_UP) { screen = LOGIN }` (`rememberSaveable` already keeps
the fields). · **Cite:** Nielsen N3; android-platform.

### P2-11 · One combined error string isn't placed next to the offending field
**Where:** `SignUpScreen.kt:204–214`, `LoginScreen.kt:200–205` · **Problem:** "Passwords don't
match"/"Enter your full name" appears at the bottom of a 4-field card, not beside the field; on
tap, focus doesn't move to the first blocker. · **Fix:** Per-field error under each field; on
failed submit `requestFocus()` the first blocking field. · **Cite:** checklist §Forms; Form Field
States; Disabled Buttons.

## AI Studio / Mixer / Equalizer
### P2-12 · AiStudio · Interrupting the live broadcast is a one-tap, unconfirmed, prominent action
**Where:** `AiStudioScreen.kt:562–576` · **Problem:** "▶ Play Now" is the dominant blue button
(weight 1.3f) on every news card and immediately interrupts the live stream — the stated worst
case — with no confirmation and a label that doesn't say it cuts into what's playing. · **Fix:**
Rename "Interrupt & Play"/"Break In"; add a lightweight confirm or snackbar-undo window; demote
it relative to the non-disruptive "+ Up Next". · **Cite:** Skill 5 §3; Destructive Actions;
Fitts's Law.

### P2-13 · AiStudio · Ticker refresh reports success even when it failed
**Where:** `AiStudioScreen.kt:381–395` · **Problem:** `HiruNewsManager.refresh(...)` is followed
by an unconditional `toast("Hiru News ticker refreshed")` — a failed refresh still claims
success. · **Fix:** Return a result from `refresh`; toast success only on success, a
human-readable error + retry on failure. · **Cite:** Autosave pattern; State Completeness;
Nielsen N1.

## Library / Settings
### P2-14 · Settings · Split-brain save model risks diverging the live broadcast config
**Where:** `SettingsScreen.kt:112–138` (continuous autosave + per-keystroke `applyToEngine`) vs
`382–431` (manual "Save to cloud" re-applying `StreamConfig.*`) · **Problem:** Two persistence
paths run independently; edits auto-apply to the engine even if the user never presses Save,
while `StreamConfig.*` is only written on the button — the two can diverge, so what's live may
not match what's shown. · **Fix:** One model — autosave local prefs, make `applyToEngine`
authoritative, let "Save to cloud" only push already-applied state to Firestore; remove the
duplicate block. · **Cite:** Nielsen N4; Autosave.

### P2-15 · Settings · Stream source password persisted in plaintext SharedPreferences
**Where:** `SettingsScreen.kt:87, 117–136` · **Problem:** The Icecast/Shoutcast source password
is written to SharedPreferences in cleartext. (Correctly kept out of the Firestore map,
`434–453`.) Trips CLAUDE.md's "keep clients secret-free." · **Fix:** `EncryptedSharedPreferences`
or Android Keystore; keep the password out of any log/crash payload. · **Cite:** CLAUDE.md hard
rule; accessibility.md security mapping.

### P2-16 · Settings · No inline validation on host/port; errors never appear next to fields
**Where:** `SettingsScreen.kt:298–300` · **Problem:** Host accepts anything; Port is
numeric-only, never range-checked (1–65535). Invalid config surfaces only as a failed "Go live"
later, far from the field. · **Fix:** Validate on blur, error below the offending field (color +
icon + message), go live-validating after first error. · **Cite:** Form Validation Timing; Form
Field States.

### P2-17 · Library · "Remove from library" is destructive with no undo
**Where:** `LibraryScreen.kt:412` · **Problem:** Placement/label are correct (overflow,
verb-labeled, red), but delete is instant and recovery means re-importing + re-ordering. ·
**Fix:** Snackbar "Removed '<title>' · Undo" holding the item ~5s before committing. · **Cite:**
Undo UX; Destructive Actions.

### P2-18 · Library · No loading/skeleton — empty state flashes before the playlist loads
**Where:** `LibraryScreen.kt:94–96, 294–365` · **Problem:** `Playlist.initialize` runs in a
`LaunchedEffect`; until it populates, "No music tracks yet" + CTAs paint first, then get
replaced — a false "empty" on every cold open. · **Fix:** Track `isLoading`; render `TrackRow`
skeletons while loading; show empty only after load. · **Cite:** Loading States System; Skeleton
Loading.

### P2-19 · Library · "Now playing" status conveyed by color alone
**Where:** `LibraryScreen.kt:521, 536` · **Problem:** The only marker of the current track is an
`OnAirTeal` border/tint — invisible to colorblind users and TalkBack. · **Fix:** Add a "● Now
playing" pill/label (or `stateDescription` semantics) on the current row. · **Cite:**
accessibility (status never by color alone); list-row anatomy.

## Web listener app
### P2-20 · Header · Profile dropdown dismiss depends on `onMouseLeave` (Hover Trap)
**Where:** `Header.tsx:185–189` · **Problem:** The menu only auto-closes via `onMouseLeave`,
which never fires on touch; on a phone it can only be closed by re-tapping the avatar and stays
stuck open over content. · **Fix:** Remove hover dismiss; add a click-away backdrop / outside-tap
listener, close on select/Esc. · **Cite:** Hover Trap (the #1 mobile-web pitfall); Jakob's Law.

### P2-21 · RadioPlayer · Core transport controls have no accessible names
**Where:** `RadioPlayer.tsx:318–335, 339–348, 349–360, 364–370` · **Problem:** Play/pause, mute,
volume range, settings are icon-only with `title` but no `aria-label`; the primary action reads
as "button". · **Fix:** `aria-label` on each (toggle Play/Pause with state); range gets
`aria-label="Volume"` + `aria-valuetext`. · **Cite:** WCAG 4.1.2; accessibility ref.

### P2-22 · App · Sign-in failure uses a raw `alert()`, and cancelled popup is treated as an error
**Where:** `App.tsx:329–336` · **Problem:** `alert("Sign-in failed: " + err.message)` is an
unstyled blocking raw-message dialog; closing the Google popup (benign) hits the same path. ·
**Fix:** Inline styled banner mapped to cause; treat `auth/popup-closed-by-user` /
`cancelled-popup-request` as a no-op. · **Cite:** CLAUDE.md "Handle every state"; Error States.

### P2-23 · ProfileModal · Country default `"LK"` desyncs the select from the preview and stores mixed formats
**Where:** `ProfileModal.tsx:23, 126–146` · **Problem:** `country` initializes to code `"LK"` but
`<option value={c.name}>` uses names — no option matches, so the select shows the *first*
country while the preview says Sri Lanka; untouched, `"LK"` is saved, but picking saves the full
name → two formats in Firestore. · **Fix:** Default to a value that exists (prefer canonical
`code`) and store one form consistently. · **Cite:** Nielsen N6; Form Field States.

### P2-24 · RadioPlayer · Raw Icecast stream-URL editing exposed to listeners
**Where:** `RadioPlayer.tsx:364–370, 471–509` · **Problem:** The settings gear lets any listener
edit/replace the station's stream URL — an internal transmitter detail that can break their own
playback with no recovery. Data-model field surfaced as UI. · **Fix:** Remove the URL editor from
the listener surface (stream comes from the selected station's Firestore doc); keep it behind a
dev/query-param flag only. · **Cite:** Intent clarity §1.3; Simplicity First.

---

# P3 — Medium / polish

## HomeScreen
- **P3-1 · "On air" timer runs during CONNECTING** — `HomeScreen.kt:840`; show the timer only
  when `connState == 1`. *(Nielsen N1)*
- **P3-2 · Footer hardcodes "Icecast · 128 kbps"**, contradicting the real metric above —
  `:924–925` vs `:841`; bind to `StreamConfig.bitrate`/real server type or remove. *(Nielsen N1)*
- **P3-3 · Status-badge palette is ad-hoc hex, not tokens** — `:1887–1894, 971, 978, 1872–1873,
  1994`; add semantic tokens (`WarnAmber`, `InfoIndigo`, reuse `SuccessGreen`). *(material3; §6)*
- **P3-4 · `CardSurface` re-implements the shared `SectionCard`** — `:1976–1987`; delete and use
  `SectionCard`. *(checklist §6)*
- **P3-5 · Remove-from-queue deletes instantly with no undo** — `:1519–1530`; snackbar Undo.
  *(Undo UX)*
- **P3-6 · Emoji used as product icons inconsistently with the vector set** — `:951, 975,
  1030/1044/1063, 1247/1263, 1390, 1877, 1888–1893, 1937–1964`; standardize on `ic_*` vectors for
  controls. *(Icon Design Rules; lineup test)*
- **P3-7 · No skeleton — chat feed flashes empty before data arrives** — `:1424–1456`; brief
  skeleton until first snapshot. *(Skeleton Loading)*

## Auth
- **P3-8 · Stale error persists while the user fixes the field** — `LoginScreen.kt:134/162`,
  SignUp `onValueChange`; clear/re-validate once a field has errored. *(Form Validation Timing)*
- **P3-9 · Text fields have no error state** — `AppTextField` `LoginScreen.kt:290` /
  `SignUpScreen.kt:322`; add `isError` + error colors + trailing icon (color **and** icon **and**
  message). *(Form Field States)*
- **P3-10 · Email check accepts invalid addresses** — `LoginScreen.kt:202` / `SignUpScreen.kt:210`
  (`!contains("@") || !contains(".")`); use `Patterns.EMAIL_ADDRESS`. *(Postel's Law)*
- **P3-11 · No `imePadding()` — keyboard can cover the confirm-password field** —
  `LoginScreen.kt:82–86` / `SignUpScreen.kt:86–90`; add `Modifier.imePadding()` + confirm
  `adjustResize`. *(WCAG 2.4.11)*
- **P3-12 · Duplicated field/label/divider components across both screens** — `AppTextField`,
  `FieldLabel`, `DividerWithText` in `LoginScreen.kt:280–336` & `SignUpScreen.kt:312–368`; extend
  `Common.LabeledTextField` (trailing/isError/imeAction/contentType) and delete the copies.
  *(checklist §6)*

## AI Studio / Mixer / Equalizer
- **P3-13 · Equalizer · Band & enhancer sliders stay interactive while EQ is bypassed** —
  `EqualizerScreen.kt:117–128, 158–182`; dim + inline "EQ is off — turn it on to apply" (keep
  reachable). *(Skill 5 §4; Nielsen N1)*
- **P3-14 · Equalizer · Selecting a preset wipes a custom curve with no undo** — `:146–154`;
  snackbar Undo when overwriting non-flat bands. *(Undo UX; Nielsen N3)*
- **P3-15 · Mixer · Level meter signals clipping by color only, no TalkBack semantics** —
  `MixerScreen.kt:211–235`; add "PEAK"/"CLIP" text cue + `contentDescription`/liveRegion.
  *(§7; WCAG 1.4.1)*
- **P3-16 · Equalizer · Preset chips below 48dp** — `:188–200` (~34dp);
  `minimumInteractiveComponentSize()`. *(Fitts's Law; WCAG 2.5.8)*
- **P3-17 · AiStudio · No skeleton for first paint of the news list** — `:400–425`; 2–3 shimmer
  cards shaped like `NewsItemCard`. *(Skeleton Loading)*

## Library / Settings
- **P3-18 · Library · No progress during IO metadata read on add** — `LibraryScreen.kt:107–111`;
  per-item "Adding…" row or top progress bar. *(Doherty Threshold)*
- **P3-19 · Library · Search query lost on rotation/process death** — `:87` (`remember`);
  `rememberSaveable`. *(android-platform)*
- **P3-20 · Library · Touch targets below 48dp** — `:581–584` (More 40dp), filter tabs
  `:220–230` (~36dp); pad to ≥48dp. *(WCAG 2.5.5)*
- **P3-21 · Library · Row actions use an AlertDialog instead of a bottom sheet** — `:397–418`;
  Material bottom sheet (keeps list visible, drag-to-dismiss). *(Bottom Sheets)*
- **P3-22 · Library · Toast feedback can't carry undo and isn't lifecycle-aware** — `:168, 170,
  261, 430, 449, 473`; move to `SnackbarHost` (enables P2-17 undo). *(Undo UX)*
- **P3-23 · Settings · Sign out has no confirmation and sits at the top, not a danger zone** —
  `SettingsScreen.kt:204–206`; if live, confirm "You're live — sign out and stop broadcasting?";
  move to a bottom danger zone. *(Destructive Actions; Nielsen N3)*
- **P3-24 · Settings · Primary "Save to cloud" scrolls away mid-form** — `:382`; pin in a
  persistent bottom bar. *(Fitts's Law; §2)*
- **P3-25 · Settings · Password field has no show/hide toggle** — `:309`; add a trailing
  visibility toggle to `LabeledTextField`. *(Form Field States)*
- **P3-26 · Settings · Copy/Share buttons under 48dp** — `:263, 282` (42dp); 48dp min. *(WCAG
  2.5.5)*
- **P3-27 · Settings · Continuous autosave has no success/failure feedback** — `:112–138`
  (`SettingsStore.save` can throw silently); surface autosave failures. *(Autosave)*

## Web listener app
- **P3-28 · Loading state conflated with empty state** — `ChatBox.tsx:110–119`,
  `StationDirectoryModal.tsx:48–55`, `App.tsx:540–556`; per-listener `loaded` flag + skeleton.
  *(Loading States System; Empty States)*
- **P3-29 · Modals have no Esc/backdrop close and no focus trap** — `ProfileModal.tsx:79–81`,
  `StationDirectoryModal.tsx:22–24`; close on Esc + overlay, trap focus, restore on close,
  `role="dialog"`+`aria-modal`. *(Nielsen N3; WCAG 2.4.3)*
- **P3-30 · Pervasive sub-12px text** — `text-[10px]/[11px]` across `App.tsx:505/611/642`,
  `Header.tsx:92/177`, `RadioPlayer.tsx:275/424`, `ChatBox.tsx:94/153`; floor essential text at
  ~12–13px. *(legibility minimums)*
- **P3-31 · Multiple touch targets below 44px** — mute `RadioPlayer.tsx:339`, dismiss X
  `RjAnnouncementBanner.tsx:46` / `StationDirectoryModal.tsx:38`, Copy-Link `Header.tsx:94`, chat
  voice `ChatBox.tsx:215`; pad to ≥44×44. *(Fitts's Law; WCAG 2.5.8)*
- **P3-32 · Defined `brand` tokens unused; many competing accents** — `tailwind.config.js:9–33`
  vs raw blue/indigo/purple/emerald/amber/red in components; route primary through `brand-*`, one
  accent per surface, strict semantic roles (red=on-air only). *(Von Restorff; §6)*
- **P3-33 · Inter is configured but never loaded** — `tailwind.config.js:35–44`, no font link in
  `index.html`; load Inter or drop it from the stack. *(Visual Character — type axis)*
- **P3-34 · No offline indicator** — `App.tsx` (no `navigator.onLine`), `RadioPlayer.tsx:239–243`;
  listen to `online`/`offline`, show "You're offline — reconnecting", distinguish from a
  station-side error. *(State Completeness; Error States)*
- **P3-35 · Header logo fallback never renders on image error** — `Header.tsx:62–72` (`only:flex`
  never matches); toggle fallback with state. *(broken-media state)*
- **P3-36 · Sign-out and profile-read failures are silent** — `App.tsx:338–345, 134–138`; toast
  on sign-out failure; retry + minimal authed profile on profile-read error. *(Nielsen N1/N9)*
- **P3-37 · Static marketing cards crowd live content on mobile** — `App.tsx:602–657`; collapse
  into one "About this station" disclosure or move below the fold. *(Information Hierarchy; de-AI
  landing)*
- **P3-38 · RJ voice banner can claim "Playing Voice" when autoplay is blocked** —
  `RadioPlayer.tsx:159–162`, `RjAnnouncementBanner.tsx:36–45`; flip label on `onplaying`, show
  "Tap to hear" on reject. *(Nielsen N1)*

## Cross-platform product decision
- **P3-39 · No dark theme on either client** — Android `Theme.kt:9/28` forces light + disables
  dynamic color; web `tailwind.config.js:7` sets `darkMode:"class"` with no theme shipped. A
  glare problem for a late-night studio shift. Decide deliberately: ship a dark theme or document
  the light-only decision and drop the dead web flag. *(material3; Visual Character)*

---

# P4 — Minor visual inconsistency

## HomeScreen
- **P4-1 · Hardcoded `sp` off the type scale** — `:1778` (`10.sp`), ad-hoc `letterSpacing = 1.sp`
  `:1092/1309/1371`; use a `labelSmall`/caption token.
- **P4-2 · `Color(0xFFF59E0B)` amber** — `:1994`; introduce a `WarnAmber` token.

## Auth
- **P4-3 · Hardcoded shadow/white hex** — card shadow `Color(0x140F172A)` `LoginScreen.kt:115` /
  `SignUpScreen.kt:119`; button `Color.White` `:214/:223`; promote shadow tint to a token, use
  `SurfaceWhite`/`onPrimary`.
- **P4-4 · Name field has no word capitalization** — `SignUpScreen.kt:136–141`; add
  `KeyboardCapitalization.Words`.
- **P4-5 · Unused imports** (`Box`, `Icon`) in both auth screens — cleanup (flagged, not deleted
  per surgical-change rule).

## AI Studio / Mixer / Equalizer
- **P4-6 · AiStudio · `CardSurface` re-implements `SectionCard`** — `AiStudioScreen.kt:582–594`;
  delete, use `SectionCard`.
- **P4-7 · Mixer · Hardcoded amber hex** — `MixerScreen.kt:223` (`0xFFF59E0B`); add `WarnAmber`
  token (shared with P4-2).
- **P4-8 · AiStudio · Off-4dp-grid spacing** — `:236, 271` (14.dp) and the `6.dp` spacers; snap
  to 12/16dp.
- **P4-9 · Mixer · Inconsistent engine-apply path across channels** — `:153` vs `:157/162` (some
  channels double-apply); pick the effect-driven path.
- **P4-10 · Equalizer · Chip radius inconsistent with the shared pill** — `:195`
  (`RoundedCornerShape(50)`) vs `SegmentedPills` 8dp; standardize.

## Library / Settings
- **P4-11 · Library · Hardcoded hex equal to a token** — `LibraryScreen.kt:250` (`0xFFE2E8F0` ==
  `BorderLight`); use the token.
- **P4-12 · Library · Ad-hoc type size** — `:555` (`10.sp` over `labelSmall`); use the scale.
- **P4-13 · Library · Re-implemented components** — filter tabs `:205–263` duplicate
  `SegmentedPills`; search field `:266–291` copies `Common.kt` colors; extend the shared ones.
- **P4-14 · Settings · Emoji used as icons** — `:267, 286` ("📋","📤"); use `ic_*` vectors.
- **P4-15 · Settings · Port field lacks a placeholder/default hint** — `:300`; add
  `placeholder = "8000"`.

## Web listener app
- **P4-16 · ChatBox · `maxLength={280}` with no counter** — `ChatBox.tsx:306`; show a counter
  near the limit.
- **P4-17 · `darkMode:"class"` set but no dark theme/toggle** — `tailwind.config.js:7` (see P3-39);
  implement or remove.
- **P4-18 · ProfileModal · Cancel stays enabled during save** — `ProfileModal.tsx:150–158`;
  disable while `saving`.
- **P4-19 · Clipboard writes have no catch/fallback** — `App.tsx:520`, `Header.tsx:51`; wrap in
  try/catch with a "Copy failed" fallback.
- **P4-20 · ChatBox · Misleading "cooldown" label with no implementation** — `ChatBox.tsx:261`
  (`lastChatAt` tracked in `App.tsx:369–372` but no cooldown UI); implement a visible countdown or
  drop the dead affordance and map the rate-limit error.

---

## Coverage checklist

| Area | Verdict |
|---|---|
| Intent clarity | **Pass** — both the console and the listener goals are obvious; one exception: the exposed stream-URL editor on web (P2-24). |
| Hierarchy | **Mostly pass** — primary CTAs are clear; gaps are thumb-zone placement (Home Stop P1-1, Settings save P3-24) and web marketing crowding live content (P3-37). |
| States (loading/empty/partial/error/success/offline) | **Fail** — the dominant failure. All 6 P1s plus loading/empty conflation (no skeletons) and missing offline paths. |
| Forms | **Partial** — strong plumbing (AuthErrors, LoadingOverlay, rememberSaveable), weak on field-level errors, autofill/IME, and inline validation (Auth P2-7..11, Settings P2-16). |
| Feedback & affordance | **Fail** — unconfirmed destructive actions (P1-1, P2-12), toast-only errors, missing undo, hover-trap menu (P2-20). |
| Design-system consistency | **Partial** — tokens are centralized but drift is real: duplicated cards, hardcoded hex, emoji-as-icons, unused web `brand` tokens. |
| Accessibility | **Partial** — the systemic weak spots: icon-only controls unnamed, sub-48/44 targets, status-by-color-alone, TalkBack error announcement. |
| Visual character | **Partial** — recognizable radio products, but the type identity never renders (M3 defaults / Inter unloaded) and web spreads across too many accents. |

---

### Suggested fix order
1. **The 6 P1s first** — they're all the same *false-success* class; fixing status-from-real-signal
   on each surface removes the dead-air / silent-failure risk the product exists to prevent.
2. **Destructive-action confirmations + undo** (P1-1, P2-12, P2-17, P3-14, P3-23) — cheap, high safety payoff.
3. **The a11y sweep** (icon names, 48/44dp targets, color+label status, TalkBack announcements) — mostly mechanical, applies identically across screens.
4. **Design-system consolidation** (delete the duplicate `CardSurface`s, tokenize the stray hex/amber, load Inter, route web through `brand-*`) — one pass, lifts every screen.
