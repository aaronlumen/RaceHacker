# Session Handoff — 2026-09-05

Written so a fresh Claude session (after this conversation is cleared) can pick up
exactly where this one left off, without re-deriving any of this from git log archaeology.
Read this first, then the docs it points to.

## Repo state right now

`main` is up to date with everything below merged in (`git log --oneline -20` shows all
of it). No open PRs, no uncommitted work, nothing stashed. Signed release `v1.0.0` exists
on GitHub (https://github.com/aaronlumen/RaceHacker/releases/tag/v1.0.0) — **v1.0.1 was
requested but not yet cut**, see "Immediate next step" below.

Physical test device: Pixel 6, serial `19191FDF60058J`, connected via USB. Standing rule
for this device, self-imposed after an earlier incident this session where a blind
tap/text-input almost sent something unintended: **on-device verification is read-only
only** — `adb exec-out screencap`, `adb logcat`, `adb shell pidof`/`am start` on exported
activities only. Never tap, swipe, or type on the device programmatically. This has real
consequences (see "Known gap in verification" below) — don't relax it without the user
explicitly saying so in the moment.

## What shipped this session, in order

1. **README rewrite, LICENSE, doc cleanup** (PR #3, earlier context not in this doc)
2. **Ace voice assistant** — mute/pitch/test-speech, connect-to-X disambiguation, shift
   lights, G-force gauge, narration correlations (PR #5)
3. **Release signing** — real keystore at `~/.android/racehacker-release.jks` +
   `~/.android/racehacker-keystore.properties` (outside repo, gitignored), wired into
   `app/build.gradle`'s `signingConfigs`. README got a download badge pointing at
   GitHub Releases' `/latest/download/RaceHacker.apk` (PR #6, tag `v1.0.0`)
4. **MAF/MAP/STFT/LTFT/O2 gauges + narration, AMOLED burn-in, configurable alarm
   thresholds, History tab** (PR #7) — see [FEATURE_IDEAS.md](FEATURE_IDEAS.md) and
   [SENSOR_DIAGNOSTICS.md](SENSOR_DIAGNOSTICS.md) for what's now ✅ vs. still 🔲
5. **SURINA header color fix** — was `#444444` (invisible), now `@color/racing_orange` (PR #8)
6. **Response learning** — CarHackerKit now persists PID/ECU/UDS-service discovery via
   Room (`com.carhacker.kit.knowledge`), keyed by VIN, instead of losing it on
   disconnect. New `S3_VISION.md` captures a large 12-idea product brainstorm session
   (PR #9) — **read this doc, it's the single largest source of "what's next"**
7. **S3 Diagnostic Intelligence, Phase 1** — the SCAN→IDENTIFY→DISCOVER MODULES→READ
   CODES→COLLECT LIVE DATA→CORRELATE→DIAGNOSE pipeline, rule-based (not LLM-backed),
   real correlation only for P0171/P0174 lean codes so far, honest "no rule yet" for
   everything else. New `DiagnosticIntelligenceEngine.kt` + a "Diagnose" button in
   CarHackerKit (PR #10)
8. **Crash fix** — `BottomNavigationView` hard-caps at 5 items; step 4 above pushed it
   to 6 (added a History tab), which threw `IllegalArgumentException` at inflation and
   crashed the app the instant "ENTER RACING" was tapped. History is no longer a
   bottom-nav tab — it's a small icon button in the Dashboard header + the existing
   "open history" voice command (PR #11)

## Known gap in verification — read this before trusting "compiles and installs"

**The bottom-nav crash (step 8) shipped in step 4 and sat on `main` undetected for
several turns.** It only surfaced when the user actually tapped "ENTER RACING" — which
this session's own read-only-verification rule prevents doing. Every "compiled clean,
installed, launched without crashing" claim earlier in this session only verified up to
whatever screen the app lands on *without* a tap past that point (usually
`ModeSelectActivity`). That is a real, demonstrated blind spot, not a hypothetical one.

**Concretely, this means:** any change touching a screen reachable only after a tap
(Racing mode's Dashboard/Diagnostics/ECU Flash/Tuning/History/Settings, CarHackerKit's
Workshop screen, the new Diagnose/View Knowledge buttons) has **not actually been
exercised at runtime this session** — only compiled, installed, and structurally
reasoned about. Ask the user to tap through and confirm before assuming any of it works
live, or ask them to relax the no-taps rule for a specific verification pass if that's
genuinely needed.

## Immediate next step: cut v1.0.1

User asked for this (`"release v1.0.1?"`) right before the crash was discovered — paused
mid-way to fix the crash first (releasing a build where Racing mode 100%-crashes on
entry would have been bad). Now that the crash is fixed, the release should actually
happen. Steps (same pattern as v1.0.0, see git log for the original commit that set up
signing):

```bash
# bump version in app/build.gradle: versionCode 1 -> 2, versionName "1.0.0" -> "1.0.1"
./gradlew :app:assembleRelease
/home/toor/Android/Sdk/build-tools/35.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk  # confirm real key, not debug
gh release create v1.0.1 --repo aaronlumen/RaceHacker --target main --title "RaceHacker v1.0.1" --notes "..." app/build/outputs/apk/release/app-release.apk
```

Worth listing the crash fix prominently in the release notes since it's the most
user-visible thing in this batch.

## Open thread: user's Bluetooth OBD adapter won't connect

Real-world bug report from the user, mid-diagnosis when this session got interrupted by
the crash discovery above. Established so far via a clarifying question and adb logcat
capture:

- **Symptom**: the adapter *does* show up in the app's device list (so it's likely
  already bonded at the OS level) — connection **fails** when tapping Connect, not
  during discovery.
- **Suspect code**: `ObdConnectionService.connectSocket()` in
  `app/src/main/java/xyz/surina/racehacker/services/ObdConnectionService.java` — tries
  standard SPP-UUID RFCOMM first, falls back to a raw channel-1 socket via reflection
  (`createRfcommSocketChannel1()`) if that fails. This fallback was added earlier this
  session specifically for adapters that don't advertise SDP records properly, and **has
  never been verified against the user's real hardware**.
- **In-flight, not yet done**: logcat was cleared and the user was asked to tap Connect
  again so the exact exception could be captured live, but the crash discovery
  interrupted this before the user reported back. **Next step: ask the user to tap
  Connect again, then run `adb logcat -d | grep -A5 "Connection failed"` (or just check
  `ObdConnectionService`'s tag) to see the real exception** — don't re-guess from code
  reading alone when live capture is one tap away.
- **User's own offer, still available**: enable Developer Options → "Enable Bluetooth
  HCI snoop log", connect successfully via Torque (a working reference app), then
  `adb pull` the resulting `btsnoop_hci.log` to compare exactly what Torque does
  differently at the protocol level. Good next step if the logcat exception alone
  doesn't explain the failure.
- Also separately fixed this session, unrelated to the above: `SettingsFragment.scanForDevices()`
  only ever listed already-bonded devices (`getBondedDevices()`) — it never called
  `BluetoothAdapter.startDiscovery()` to find genuinely new/unpaired devices. **This
  was diagnosed but the fix itself was not yet written** when the conversation moved on
  to the "shows devices but fails to connect" report (which turned out to be the user's
  actual, more specific symptom). Worth doing anyway for anyone whose adapter isn't
  already paired — real discovery via a `BroadcastReceiver` on `BluetoothDevice.ACTION_FOUND`.

## The idea backlog — where to look for "what's next"

Three docs, in the order to check them:

1. **[S3_VISION.md](S3_VISION.md)** — the big one. 12 numbered feature ideas plus a
   signature "§0" workflow tying them together, gathered in one brainstorming session
   right before Diagnostic Intelligence Phase 1 (step 7 above) got built. Task #7 in
   this session's task list ("Synchronized multi-signal timeline with tap-to-inspect")
   is S3_VISION.md's §1/§2/§3 and is the most natural next build after Phase 1 of
   Diagnostic Intelligence, since GPS+multi-signal capture feeds both the timeline *and*
   a richer COLLECT LIVE DATA stage.
2. **[FEATURE_IDEAS.md](FEATURE_IDEAS.md)** — smaller, more tactical items: units
   toggle, performance testing, GPS+map (no API key needed for GPS *data*, only for
   *rendering* a map — this was a real correction made mid-session), custom PID/formula
   engine (S3_VISION.md §7 has since fleshed this out further), share-with-mechanic,
   homescreen widgets.
3. **[SENSOR_DIAGNOSTICS.md](SENSOR_DIAGNOSTICS.md)** — still-🔲 sensors (Exhaust Temp,
   APP, downstream O2, Engine Load, Knock Retard, Fuel Rail Pressure, Commanded
   Equivalence Ratio, EVAP Purge, EGR, VVT, Transmission Temp) and the standing
   vehicle-aware-thresholds architecture gap (today's warning/critical numbers are
   global constants, not tied to the selected `VehicleProfile`).

Also: **[DIAGNOSTIC_PLATFORM_VISION.md](DIAGNOSTIC_PLATFORM_VISION.md)** has the
"Response learning" design (Phase 1 built this session; Phase 2, syncing to a
webserver, deliberately not started — needs a real endpoint and real consent/
anonymization design first) and the professional-scan-tool backlog split by
engine-exists-no-UI vs. genuinely-new.

## Things explicitly NOT done, with reasons (don't rebuild without checking these first)

- **GPS + map view** — GPS *data capture* needs no API key (just location permission,
  already granted); a Maps API key is only needed to *render* a map, which is a smaller
  piece of this than originally assumed.
- **Custom PID/formula engine** — flagged in FEATURE_IDEAS.md as "the single biggest
  idea," fleshed out further in S3_VISION.md §7 with a real formula-language spec. Still
  not started — genuinely large.
- **Bidirectional Active Tests / Special Functions** (S3_VISION.md §5/§6) — real safety
  tier above passive reading; needs the same prominent confirmation treatment as ECU
  flash, not a bare button. Not started.
- **PDF Vehicle Health Report, cloud/account layer, PID pack marketplace/monetization,
  motorcycle-first-class mode, ECU tuning read/write risk-tier split** — all documented
  in S3_VISION.md, none started.
- **Pires OBD-Java API migration** — `PIRES_API_INTEGRATION.md`'s own "Next Steps" say
  to migrate standard gauges to the Pires library; this never happened.
  `MainActivity`/`DashboardFragment` still read gauges through the original custom
  `ObdConnectionService`. Given that code is now well-exercised and stable, this is a
  refactor-for-its-own-sake candidate, not an urgent gap — don't attempt it without the
  user asking specifically.

## Working conventions established this session (keep following these)

- Commit messages: multi-paragraph body explaining what/why, ending with `Dedicated to
  David and Andrew.`, blank line, `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`,
  `Claude-Session:` line.
- Branch per PR, off latest `origin/main` (not off another feature branch, even one that
  was just merged — this session made that mistake twice and had to rename/re-branch).
  Never commit directly to `main`.
- `gh pr merge` gets blocked by a permission classifier unpredictably — sometimes it
  works immediately, sometimes it needs the user to merge manually via the GitHub UI.
  Not a code problem, just retry once and ask the user if it's still blocked.
- Every doc update this session cross-links to the others rather than duplicating
  content — check FEATURE_IDEAS.md/SENSOR_DIAGNOSTICS.md/DIAGNOSTIC_PLATFORM_VISION.md/
  S3_VISION.md for existing coverage before writing a new idea down as if it were novel.
