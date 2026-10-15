# UX Audit — Web Listener App (`apps/web`)

> Produced by `/android-ux-audit`. Standalone report for the listener web app. Findings are
> severity-ranked; each fix is shippable as written and cites the heuristic / law / pattern it
> comes from. `file:line` points at the exact spot.

**Scope audited:** `App.tsx` (699), `RadioPlayer.tsx` (512), `ChatBox.tsx` (330),
`Header.tsx` (260), `ProfileModal.tsx` (178), `StationDirectoryModal.tsx` (131),
`RjAnnouncementBanner.tsx` (56) — judged against `tailwind.config.js` + `index.css` + `index.html`.

**Inferred intent** — *confirm before acting on the P1.* A listener opens the page, usually on a
phone, wants to press play and hear the live station, see what's on air, and chat with the AI
host. Worst cases: audio fails/stalls silently, the browser blocks autoplay, or the UI claims
"live" while the stream is silent.

---

## Summary

| Severity | Count | Theme |
|---|---|---|
| **P1** | 1 | The player reports "in sync, playing in real time" while actually silent |
| **P2** | 5 | Hover-trap menu · unnamed transport controls · raw `alert()` errors · country-select desync · listener can edit the raw stream URL |
| **P3** | 11 | Loading/empty conflation · modals without Esc/focus-trap · sub-12px text · sub-44px targets · unused design tokens · Inter never loaded · no offline state |
| **P4** | 5 | No char counter · dead `darkMode` flag · Cancel enabled during save · clipboard no-catch · phantom "cooldown" |

**Platform note:** this is web, so hover *exists* — but the app is used heavily on mobile
browsers, so touch targets (≥44px), thumb reach, and "nothing critical behind hover on touch"
still apply. Back = browser back; modals must handle Esc / overlay-click / focus trapping.

---

# P1 — Critical

### P1-1 · RadioPlayer · UI reports "Live Broadcast In Sync — playing in real time" while silent
**Where:** `RadioPlayer.tsx:56–103` (sync timer) → `407–468` (footer) + `118–145` (autoplay)
**Problem:** The 20-second sync countdown runs on a bare `setInterval` keyed only on
`radioStatus.isLive` — it is **not** tied to real playback. When the browser blocks autoplay (the
single most common real failure — the `.catch` at `:139` only logs "Autoplay waiting for listener
interaction" and sets `isBuffering = false`), `syncSeconds` still ticks to 0. At that point the
top badge flips to "LIVE ON AIR" and the footer renders the green "Live Broadcast In Sync ·
Connected to studio stream · Live audio data playing in real time · 0s BUFFER LAG • LIVE" — while
the user hears nothing and was never told to tap play. The same false positive occurs if the
stream `onError` fires (`:239`) after the timer has already reached 0. This is the audio
equivalent of showing "Saved" on a failed write.
**Fix:** Drive the "in sync" state from the actual `onPlaying` / `isPlaying` signal, not the
timer. When autoplay rejects, set a distinct state (`needsGesture`) and render a clear
**"Tap ▶ to start listening"** prompt over/next to the play button instead of the green sync
panel. On `onError`, keep the red alert visible and never show "in sync". Never render "playing in
real time" unless `isPlaying && !isBuffering`.
**Cite:** Nielsen N1 (visibility of true system status); Autosave pattern ("never display Saved
for a write that failed"); Error States.

---

# P2 — Major

### P2-1 · Header · Profile dropdown dismiss depends on `onMouseLeave` — no touch close (Hover Trap)
**Where:** `Header.tsx:185–189`
**Problem:** The menu only auto-closes via `onMouseLeave`, which never fires on touch. On a phone,
once opened it can be closed only by re-tapping the avatar; tapping anywhere else leaves it stuck
open over the content.
**Fix:** Remove the hover dismiss. Add a full-screen click-away backdrop (or an outside-click
listener) and close on item select / Esc.
**Cite:** Hover Trap (the #1 mobile-web pitfall); Jakob's Law (menus close on outside tap).

### P2-2 · RadioPlayer · Core transport controls have no accessible names
**Where:** `RadioPlayer.tsx:318–335` (play/pause), `339–348` (mute), `349–360` (volume range),
`364–370` (settings)
**Problem:** All are icon-only with a `title` but no `aria-label`. Screen-reader users get
"button" with no action; the volume `<input type=range>` is unlabeled. The play button is the
screen's primary action.
**Fix:** Add `aria-label` ("Play live stream" / "Pause" toggled on state, "Mute", "Stream
settings"); give the range `aria-label="Volume"` and `aria-valuetext`.
**Cite:** WCAG 4.1.2 (Name/Role/Value); accessibility ref.

### P2-3 · App · Sign-in failure uses a raw `alert()`, and cancelled popup is treated as an error
**Where:** `App.tsx:329–336`
**Problem:** `alert("Sign-in failed: " + err.message)` surfaces an unstyled, blocking, raw-message
dialog. Closing the Google popup (benign cancellation) hits the same path and scolds the user with
a "failed" alert.
**Fix:** Replace with an inline styled banner/toast mapped to cause (popup blocked, network,
cancelled). Treat `auth/popup-closed-by-user` / `cancelled-popup-request` as a no-op, not an error.
**Cite:** CLAUDE.md "Handle every state" (friendly mapped errors, cancellation is benign); Error
States.

### P2-4 · ProfileModal · Country default `"LK"` desyncs the select from the preview and stores mixed formats
**Where:** `ProfileModal.tsx:23, 126–146`
**Problem:** `country` initializes to the code `"LK"`, but `<option value={c.name}>` uses country
**names**. No option value equals `"LK"`, so the native select visually shows the *first* country
in the list while the "Selected:" preview (matched by code) says Sri Lanka — a visible
contradiction. If the user never touches it, `"LK"` is saved; picking one saves the full name —
two formats in Firestore for the same field.
**Fix:** Default to a value that exists in the options and store one canonical form (prefer
`code`) consistently across app / chat / header.
**Cite:** Nielsen N6 (recognition — the control must reflect the real value); Form Field States.

### P2-5 · RadioPlayer · Raw Icecast stream-URL editing exposed to listeners
**Where:** `RadioPlayer.tsx:364–370, 471–509`
**Problem:** The settings gear lets any listener edit/replace the station's stream URL — an
internal transmitter detail a listener should never touch. It also lets them break their own
playback with no easy recovery. This is a data-model field surfaced as UI.
**Fix:** Remove the URL editor from the listener surface (the stream comes from the selected
station's Firestore doc). Keep it only behind a dev / query-param flag if needed for testing.
**Cite:** Intent clarity §1.3 ("nothing exists only because the data model has that field");
Simplicity First.

---

# P3 — Medium / polish

### P3-1 · Loading state conflated with empty state
**Where:** `ChatBox.tsx:110–119`, `StationDirectoryModal.tsx:48–55`, `App.tsx:540–556`
**Problem:** During the initial Firestore snapshot, arrays are empty, so "No on-air messages yet"
/ "No active broadcaster stations found" / "No Broadcaster Connected Yet" flash before data
arrives — the user is told there's nothing when it's still loading.
**Fix:** Track a `loaded` flag per listener; show a skeleton matching the list/row layout until
the first snapshot resolves, then the empty state.
**Cite:** Loading States System; Empty States (distinguish "nothing yet" from "not loaded").

### P3-2 · Modals have no Esc or backdrop-click close, no focus trap
**Where:** `ProfileModal.tsx:79–81`, `StationDirectoryModal.tsx:22–24`
**Problem:** Dismissible modals can't be closed with Esc or by clicking the overlay (the web
convention), and focus isn't trapped — keyboard/AT users can tab into the page behind.
**Fix:** Close on Esc and overlay click when dismissible; trap focus within the dialog; restore
focus to the trigger on close; add `role="dialog"` + `aria-modal`.
**Cite:** Nielsen N3 (user control); WCAG 2.4.3 (focus order); modal conventions.

### P3-3 · Pervasive sub-12px text harms readability
**Where:** `text-[10px]` / `text-[11px]` throughout — e.g. `App.tsx:505/611/642`,
`Header.tsx:92/177`, `RadioPlayer.tsx:275/424`, `ChatBox.tsx:94/153`
**Problem:** Core labels, metadata, and policy copy render at 10–11px — below a comfortable mobile
minimum and hard to read in sunlight / one-handed.
**Fix:** Floor body/meta text at ~12–13px; reserve 10px only for non-essential decoration.
**Cite:** Aesthetic-Usability vs legibility; readable-type minimums.

### P3-4 · Multiple touch targets below 44px
**Where:** mute `RadioPlayer.tsx:339`, dismiss X `RjAnnouncementBanner.tsx:46` /
`StationDirectoryModal.tsx:38`, inline Copy-Link `Header.tsx:94`, chat voice button
`ChatBox.tsx:215`
**Problem:** Icon buttons with `p-1.5` / tiny padding and inline text buttons fall under the
44×44px target, causing mis-taps on mobile.
**Fix:** Pad interactive icons to a ≥44×44 hit area (add `p-2.5`+ or an invisible hit box).
**Cite:** Fitts's Law; WCAG 2.5.8 (target size).

### P3-5 · Defined `brand` tokens are unused; many competing accent colors
**Where:** `tailwind.config.js:9–33` vs components using raw `blue/indigo/purple/emerald/amber/red`
**Problem:** The config defines a `brand` scale + `studio` palette, but every component hardcodes
default Tailwind hues. Blue, indigo, purple (AI), emerald (live/open), amber (buffering), red
(on-air) all compete as "accents," diluting hierarchy and identity.
**Fix:** Route primary actions through `brand-*`; pick one accent for the single most important
action per surface; assign the others strict semantic roles (red = on-air only, emerald =
connected only).
**Cite:** Visual Hierarchy / Von Restorff ("one accent on the most important action");
design-system consistency.

### P3-6 · Inter is configured but never loaded
**Where:** `tailwind.config.js:35–44`, `index.html` (no font `<link>`), `main.tsx`
**Problem:** `fontFamily.sans` leads with "Inter", but no Google Fonts link or `@fontsource`
import loads it, so the app silently falls back to system fonts — the intended type identity never
applies.
**Fix:** Load Inter (Google Fonts `<link>` or `@fontsource/inter`) or drop it from the stack to
match reality.
**Cite:** Visual Character (the type axis must actually render).

### P3-7 · No offline indicator
**Where:** `App.tsx` (no `navigator.onLine`), `RadioPlayer.tsx:239–243`
**Problem:** If the listener loses connectivity, Firestore snapshots stall silently and audio
surfaces only a generic "Stream disconnected." Nothing tells the user it's their network vs the
station.
**Fix:** Listen to `online` / `offline`; show a dismissible "You're offline — reconnecting" banner
and distinguish it from a station-side stream error.
**Cite:** State Completeness (offline); Error States.

### P3-8 · Header logo fallback never renders on image error
**Where:** `Header.tsx:62–72`
**Problem:** On `onError` the `<img>` is set to `display:none`, but the fallback
`<div className="hidden only:flex">` is not an only child (img + fallback + ping span coexist), so
`only:` never matches — a broken logo leaves blank space, no brand mark.
**Fix:** Toggle the fallback with state (`imgFailed`) instead of the `only:` trick, or swap the
`src`.
**Cite:** Empty / broken-media state handling.

### P3-9 · Sign-out and profile-read failures are silent
**Where:** `App.tsx:338–345` (sign-out), `134–138` (profile read)
**Problem:** A failed sign-out logs to console only — the user still sees themselves as signed in.
A failed profile read leaves `currentUser` null, silently appearing signed-out despite being
authenticated, with no retry.
**Fix:** Surface a toast on sign-out failure; on profile-read error show a retry affordance and
keep a minimal authed profile.
**Cite:** State Completeness (error); Nielsen N1/N9.

### P3-10 · Static marketing cards crowd live content on mobile
**Where:** `App.tsx:602–657` (AI Radio Jockey / Model / Standard cards)
**Problem:** Three static explainer cards occupy the right column; on mobile they stack directly
under the chat, pushing real interaction below a wall of promo copy.
**Fix:** Collapse into one compact "About this station" disclosure, or move below the fold; let the
live player + chat dominate.
**Cite:** Information Hierarchy (product over explainer); de-AI landing (show product, not copy).

### P3-11 · RJ voice banner can claim "Playing Voice" when autoplay is blocked
**Where:** `RadioPlayer.tsx:159–162`, `RjAnnouncementBanner.tsx:36–45`
**Problem:** `rjAudioRef.play()` may reject (no user gesture); the catch only warns, but the
banner/button already show "Playing Voice."
**Fix:** Flip the label to "Playing" only after the audio's `onplaying`; on rejection show "Tap to
hear."
**Cite:** Nielsen N1 (true status).

---

# P4 — Minor

### P4-1 · ChatBox · `maxLength={280}` with no character counter
**Where:** `ChatBox.tsx:306` · Input silently stops at 280. **Fix:** show a counter as the user
nears the limit. *(Form Field States)*

### P4-2 · Config · `darkMode:"class"` set but no dark theme or toggle exists
**Where:** `tailwind.config.js:7` · Dead config; system-dark users get forced light with no
acknowledgement. **Fix:** implement a dark theme or remove the flag.

### P4-3 · ProfileModal · Cancel stays enabled during save
**Where:** `ProfileModal.tsx:150–158` · Allows dismissing mid-write. **Fix:** disable Cancel while
`saving`.

### P4-4 · App / Header · Clipboard writes have no catch/fallback
**Where:** `App.tsx:520`, `Header.tsx:51` · `navigator.clipboard.writeText` throws on insecure
context / older browsers with no feedback. **Fix:** wrap in try/catch, fall back to select-and-copy
or show "Copy failed."

### P4-5 · ChatBox · Misleading "cooldown" label with no implementation
**Where:** `ChatBox.tsx:261` · The input block is intended as a "20-Min Cooldown Section"
(`lastChatAt` is tracked in `App.tsx:369–372`) but no cooldown UI or gating exists; a server-side
rate-limit rejection would only show a generic "Failed to send." **Fix:** implement the cooldown
with a visible countdown, or drop the dead affordance and map the rate-limit error to a specific
message.

---

## Coverage checklist

| Area | Verdict |
|---|---|
| Intent clarity | **Pass** — listener goal is clear; player and chat dominate. One exception: the exposed stream-URL editor (P2-5). |
| Hierarchy | **Partial** — good primary focus, but static marketing cards crowd live content on mobile (P3-10). |
| States (loading/empty/partial/error/success/offline) | **Fail** — the biggest gaps: the P1 false-sync, loading/empty conflation (P3-1), no offline path (P3-7). |
| Forms | **Partial** — good validation/labels; the holes are the country default (P2-4) and no modal focus/Esc (P3-2). |
| Feedback & affordance | **Partial** — play/mute a11y (P2-2) and the hover-trap menu (P2-1); raw `alert()` errors (P2-3). |
| Design-system consistency | **Fail** — defined `brand` tokens unused, Inter never loaded, many competing accents (P3-5, P3-6). |
| Visual character | **Pass** — a recognizable radio product, but tighten to one accent and actually load Inter to lift it clearly out of generic AI default. |

---

### Suggested fix order
1. **P1-1 first** — tie the player's "in sync / live" state to real playback (`isPlaying`), and add
   the autoplay-blocked "Tap ▶ to start listening" prompt. This is the one that makes a silent
   player look healthy.
2. **The mobile-web a11y + hover sweep** — P2-1 (hover-trap menu), P2-2 (transport aria-labels),
   P3-2 (modal Esc/focus-trap), P3-4 (≥44px targets), P3-3 (sub-12px text). Mostly mechanical.
3. **Error honesty** — P2-3 (`alert()` → mapped banner, cancellation benign), P3-7 (offline
   banner), P3-9 (silent sign-out/profile failures), P3-1 (skeletons instead of flashing empty).
4. **Design-system + character** — P3-5 (one accent via `brand-*`), P3-6 (load Inter), P2-5 (remove
   the listener stream-URL editor), P2-4 (fix the country default). One pass, lifts the whole app.
