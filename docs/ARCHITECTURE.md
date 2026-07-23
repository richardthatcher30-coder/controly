# HomeControl — Phase 1: Architecture

Status: **Proposed, pending approval**
Scope: Architecture, module layout, plugin contract, communication protocol, roadmap.
No implementation code is included in this phase.

---

## 1. Architectural Style

HomeControl uses **Clean Architecture** with **MVVM** presentation, split across **Gradle feature modules**, wired together with **Hilt**. The guiding rule: *inner layers never depend on outer layers, and features never depend on concrete device implementations.*

Three horizontal layers, enforced by module boundaries (not just convention):

| Layer | Lives in | Depends on | Never depends on |
|---|---|---|---|
| Presentation | `features/*` | `core:model`, `core:plugin-api`, `core:data` (interfaces), `core:ui`, `core:designsystem` | `core:database`, `core:network`, concrete `plugins:*` |
| Domain (contracts) | `core:plugin-api`, `core:data` (interfaces), `core:model` | nothing but Kotlin/Java stdlib + coroutines | Android framework where avoidable |
| Data / Device I/O | `core:database`, `core:network`, `core:discovery`, `core:security`, `plugins:*` | `core:plugin-api`, `core:model` | `features:*` |

The single most important rule in this project: **a feature module can control any device without knowing what kind of device it is.** It talks to `IDevicePlugin` (an interface) and `DeviceCapabilities` (a data description). Concrete plugins are wired in only at the `app` composition root via Hilt multibinding. This is what makes "future developers add a plugin for their own device" possible without ever touching `feature-remote` or `feature-dashboard`.

### Why not a flatter, simpler structure?

A single-module app would be faster to start but this project has two explicit long-term requirements that force modularity now:
1. **Community contributions** — a contributor adding TP-Link support should be able to build and test `plugin-tplink` without recompiling the whole app, and should get a compiler error (not a runtime surprise) if they try to reach into `feature-remote` internals.
2. **Device plugins will outnumber everything else** — by the time ONVIF, RTSP, Hue, TP-Link, and Home Assistant exist, there will be 8-10 plugin modules. Keeping them physically isolated keeps build times sane (Gradle only recompiles what changed) and keeps the dependency graph enforced by the build system rather than by discipline.

---

## 2. Complete Folder Structure (Version 0.1 scope)

```
HomeControl/
│
├── app/                                # Composition root only. No business logic.
│   └── src/main/kotlin/com/homecontrol/app/
│       ├── HomeControlApplication.kt   # @HiltAndroidApp
│       ├── MainActivity.kt             # Single-activity host, splash screen API
│       ├── navigation/                 # NavHost + top-level nav graph wiring features together
│       └── di/                         # App-level Hilt modules (binds PluginRegistry, etc.)
│
├── build-logic/                        # Gradle convention plugins (not app code)
│   └── convention/
│       ├── AndroidApplicationConventionPlugin
│       ├── AndroidFeatureConventionPlugin   # applies Compose+Hilt+lint to every feature module identically
│       ├── AndroidLibraryConventionPlugin
│       └── JvmLibraryConventionPlugin
│
├── core/
│   ├── model/          # Pure Kotlin data classes: Device, DeviceType, DeviceCapabilities,
│   │                    #   PairingState, RemoteKey, DiscoveredDevice. No Android dependency.
│   ├── common/          # Result<T>/Outcome wrapper, DispatcherProvider, base UseCase, extensions
│   ├── plugin-api/      # THE CONTRACT MODULE. IDevicePlugin, PluginRegistry, PairingStrategy,
│   │                    #   DeviceCapabilities builder. Depends only on core:model + core:common.
│   ├── security/        # Android Keystore wrapper, AES-GCM token encryption, ECDH/PAKE handshake,
│   │                    #   pairing-code generation
│   ├── network/         # Shared HTTP + WebSocket client setup (OkHttp/Ktor), TLS config
│   ├── discovery/       # mDNS (NsdManager), SSDP (UDP multicast M-SEARCH), UDP broadcast scanner.
│   │                    #   Emits raw DiscoveredDevice records — knows nothing about specific brands.
│   ├── database/        # Room: PairedDeviceEntity, DeviceTokenEntity, DAOs, migrations
│   ├── data/            # Repository IMPLEMENTATIONS: DeviceRepository, PairingRepository,
│   │                    #   RemoteControlRepository. Binds core:database + core:discovery +
│   │                    #   core:plugin-api together. This is the ONLY module allowed to depend
│   │                    #   on both persistence and plugin contracts.
│   ├── designsystem/    # Material 3 theme, color scheme (dark-first), typography, shapes, motion specs
│   ├── ui/              # Reusable composables: DeviceCard, DPad, Touchpad, StatusPill, shared animations
│   └── testing/         # Fake repositories, coroutine test rules, plugin test doubles
│
├── plugins/                            # Concrete device implementations. Each is interchangeable.
│   ├── plugin-api-registry-support/     # (folded into plugin-registry below)
│   ├── plugin-registry/                # Hilt @Multibinds aggregator: collects every IDevicePlugin
│   │                                    #   into a Set<IDevicePlugin>, exposes PluginRegistry impl.
│   │                                    #   ONLY the app module depends on this — features never do.
│   ├── plugin-androidtv/               # Android TV / Google TV (ADB-based transport)
│   ├── plugin-firetv/                  # Fire TV / Fire Stick (ADB-based transport, Amazon app IDs)
│   ├── plugin-samsungtv/               # Samsung Tizen WebSocket remote protocol
│   └── plugin-windows/                 # Talks to the C# Windows Companion over secure WebSocket
│
├── features/
│   ├── feature-splash/                 # Splash screen (Android 12+ SplashScreen API + branded animation)
│   ├── feature-dashboard/              # Home screen: device summary tiles, quick actions
│   ├── feature-devices/                # Discovery trigger UI, device list, device details screen
│   ├── feature-pairing/                # Pairing flow (code entry / on-screen approval), status states
│   ├── feature-remote/                 # The remote-control screen (D-Pad, touchpad, keyboard, volume…)
│   ├── feature-settings/               # App settings, paired device management, about/licenses
│   └── feature-cameras/                # RESERVED, empty in v0.1 — folder exists per target structure,
│                                        #   no code until the camera milestone
│
├── windows-companion/                  # Separate C# solution (Windows Service), not a Gradle module
│   └── (created in the milestone where Windows plugin is implemented)
│
├── docs/
│   ├── ARCHITECTURE.md                 # This document
│   ├── PLUGIN_PROTOCOL.md              # Wire-protocol spec per device family (section 5 below)
│   ├── PLUGIN_DEVELOPMENT_GUIDE.md     # Written once core:plugin-api is implemented
│   └── ROADMAP.md
│
├── gradle/libs.versions.toml           # Version catalog — single source of truth for all deps
├── settings.gradle.kts
├── build.gradle.kts
└── README.md
```

---

## 3. Why Each Module Exists

**`core:model`** — the vocabulary every other module speaks. Kept dependency-free (no Android imports where avoidable) so it can be unit-tested instantly and shared by plugins, data, and features without pulling in Android framework classes.

**`core:plugin-api`** — the seam of the entire system. This is what a third-party contributor implements against. It never changes for a new device type; only new modules under `plugins/` appear.

**`core:security`** — pairing and token handling is security-critical and used identically by every plugin (Android TV pairing, Samsung token storage, Windows companion handshake all reuse the same Keystore-backed encryption and PAKE handshake code). Centralizing it means one audited implementation instead of five slightly-different ones.

**`core:discovery`** — mDNS/SSDP/UDP scanning is transport-level and brand-agnostic. It answers "what's on the network and what does it claim to be," not "what is it." Matching a raw discovery record to a specific plugin happens in `core:data`, which asks each registered plugin "can you handle this?" (see §4).

**`core:data`** — the only module allowed to see both persistence (`core:database`) and device control (`core:plugin-api`). This is intentional: it's the repository layer from Clean Architecture, and it's what keeps `feature-remote` from ever importing Room or a plugin class directly.

**`plugins:plugin-registry`** — exists purely so Hilt's `@Multibinds` aggregation of "every plugin in the app" lives in one place, and so that module is the *only* thing in the dependency graph that touches every concrete plugin module. Add a new plugin module → add one line here → nothing else in the app changes.

**`features:*`** — one module per screen/flow in the spec, each independently buildable and previewable in Compose. `feature-cameras` is created empty now only because it was in the target structure; it will not be populated until the camera milestone.

**`build-logic/convention`** — guarantees every module (present and future community-contributed plugin) applies the same Kotlin, Compose, Hilt, and lint configuration. This is what stops "works on my plugin module" drift as the plugin ecosystem grows.

---

## 4. Plugin Architecture

### 4.1 The contract (conceptual — implemented in code in Phase 2)

Every device integration implements one interface, described here structurally rather than in code per your instruction to hold off on Kotlin:

```
IDevicePlugin
├── identity
│   ├── pluginId: String                     e.g. "androidtv", "samsungtv"
│   ├── displayName: String
│   └── supportedDeviceTypes: Set<DeviceType>
├── discovery matching
│   └── canHandle(discovered: DiscoveredDevice): Boolean
├── lifecycle
│   ├── pair(discovered, pairingInput): PairingResult
│   ├── connect(pairedDevice): ConnectionResult
│   └── disconnect(pairedDevice)
├── capability reporting
│   └── getCapabilities(pairedDevice): DeviceCapabilities
├── control (each is a no-op / throws UnsupportedOperation if capability is false)
│   ├── sendKey(remoteKey)
│   ├── powerOn() / powerOff()
│   ├── volumeUp() / volumeDown() / mute()
│   ├── sendText(text)
│   ├── launchApp(appId)
│   └── getInstalledApps(): List<AppInfo>
```

`DeviceCapabilities` is a plain flag set (`supportsPower`, `supportsVolume`, `supportsKeyboard`, `supportsApps`, `supportsTouchpad`, `supportsMouse`, `supportsPTZ`, `supportsWakeOnLan`, `supportsScreenMirror`, …). `feature-remote` reads this once per connected device and conditionally renders D-Pad/keyboard/volume/app-shelf UI — **no `if (deviceType == SAMSUNG)` branching ever appears in feature code.** All device-specific behavior stays inside the plugin.

### 4.2 Matching a discovered device to a plugin

1. `core:discovery` emits a raw `DiscoveredDevice` (IP, port, service records, advertised name/model if present).
2. `core:data`'s `DeviceRepository` asks every plugin in `PluginRegistry` — in Hilt-multibinding-order — `canHandle(discovered)`.
3. First (and normally only) match wins. If two plugins claim the same device (rare, e.g. a Google TV box that also answers generic SSDP), `plugin-registry` applies a priority order set at binding time.
4. Unmatched devices are still shown in `feature-devices` as "Unknown device" with no control actions, rather than being silently dropped — useful for future plugin authors debugging discovery.

### 4.3 Pairing is not one-size-fits-all — and the contract says so honestly

Different device families use fundamentally different pairing mechanics. Rather than force every plugin to fake a "6-digit code" UX, the contract exposes *which kind* of pairing a plugin needs, and `feature-pairing` renders the right flow for it:

```
PairingStrategy (sealed)
├── CodeVerification      — phone shows code entry field, code authenticates key exchange
├── OnScreenApproval       — device itself shows Allow/Deny, phone just waits/polls
└── PresharedFromCompanion — companion app displays the code (our own protocol, full control)
```

- **Android TV / Google TV / Fire TV** → `CodeVerification`, backed by Android's own wireless-debugging ("ADB pairing") mechanism, which genuinely is a 6-digit-code-authenticated TLS handshake already built into the OS. See §5.
- **Samsung Tizen TVs** → `OnScreenApproval` natively (Samsung's real remote protocol shows an on-TV Allow/Deny prompt; it has no code). We will *not* pretend it has a 6-digit code — that would be a fake UI backed by nothing. The pairing screen instead shows "Check your TV screen" with a spinner.
- **Windows Companion** → `PresharedFromCompanion`, a 6-digit code we generate ourselves and display in the companion's tray icon UI, since we own both ends of that protocol.

This is called out explicitly now so there's no surprise in Phase 2 when the pairing UI can't be visually identical for every device — it will be *consistent in structure* (progress → success/failure states, same encrypted-token storage afterward) but not *identical in mechanism*, because the underlying devices genuinely differ.

### 4.4 No duplicated code across plugins

Shared logic that would otherwise be copy-pasted across `plugin-androidtv` / `plugin-firetv` (both ADB-based) is factored into an internal shared Gradle module if duplication actually appears once implemented (e.g. `plugins:plugin-adb-common`) rather than speculatively created now — consistent with not over-engineering ahead of real code.

---

## 5. Communication Protocol Design (per device family)

| Device | Discovery | Transport | Pairing | Notes |
|---|---|---|---|---|
| **Android TV / Google TV** | mDNS: `_androidtvremote2._tcp` service, or fallback UDP probe | ADB over network (port 5555) for `input keyevent`, `am start`, `pm list packages`; TLS pairing channel on the port advertised by `_adb-tls-pairing._tcp` | Android's native wireless-debugging pairing (6-digit code, TLS, built into Android 11+) | This reuses an OS-level, already-secure mechanism instead of inventing our own for Android devices |
| **Fire TV / Fire Stick** | Same mDNS pattern where Fire OS exposes it; UDP broadcast probe fallback for older Fire OS | Same ADB transport (Fire OS is AOSP-derived) | Same ADB pairing where available; manual IP + on-device "Apps > Developer options > ADB debugging" toggle as fallback on older Fire OS that lacks wireless pairing | App launching uses Amazon package IDs (e.g. `com.amazon.tv.launcher`) instead of Google's |
| **Samsung Smart TV (Tizen)** | SSDP (Samsung TVs advertise via UPnP) | WebSocket, `wss://<ip>:8002/api/v2/channels/samsung.remote.control?name=<base64 app name>` | On-screen approval — TV displays Allow/Deny, returns a persistent token on approval, reused for all future connections | This is Samsung's real, documented remote-control channel |
| **Windows Companion** | mDNS service `_homecontrol._tcp.local.` (primary) + UDP broadcast beacon on a fixed port (fallback for networks that block mDNS) | Secure WebSocket (`wss://`), JSON message frames, TLS with a self-signed cert pinned during pairing | 6-digit code generated by the phone, shown for confirmation in the companion's tray UI (or vice versa — code shown on PC, entered on phone); code authenticates an ECDH key exchange (SPAKE2-style) so a passive network observer can't derive the session key even from the code alone | Fully our own protocol since we control both ends |

### 5.1 Discovery layer implementation notes

- **mDNS** via `NsdManager` (Android's built-in service discovery) — used for Android TV/Fire TV detection and our own Windows Companion advertisement.
- **SSDP** — a raw UDP multicast `M-SEARCH` sent to `239.255.255.250:1900`, parsing `LOCATION` header responses — used for Samsung TVs and, in the future, ONVIF cameras.
- **UDP Broadcast** — our own lightweight discovery beacon (`HOMECONTROL_DISCOVER` request / `HOMECONTROL_HERE` JSON response) as a fallback when mDNS is blocked by router configuration (common on guest networks/some mesh routers) — primarily backs up Windows Companion discovery.

All three feed into `core:discovery` as one merged `Flow<DiscoveredDevice>` so `feature-devices` never needs to know which protocol found what.

### 5.2 Token & session security

- Pairing always ends in an **encrypted long-lived token**, stored via `core:security`:
  - Key material held in Android Keystore (StrongBox-backed where the device supports it), never exported.
  - Token itself encrypted at rest with AES-GCM using a Keystore-bound key — even a rooted-device file read of the Room database yields ciphertext only.
- **Reconnect**: on app foreground / device-selected, the stored token re-authenticates a fresh session without re-running the pairing UX. If a device rejects the token (revoked, factory reset, re-paired to another controller), the plugin surfaces a `ConnectionResult.PairingRequired` and `feature-remote` routes back to `feature-pairing` automatically.
- **No anonymous control**: every `IDevicePlugin.connect()` call requires a previously stored, valid token; there is no "send commands to any device on the network" code path anywhere in the app.

---

## 6. Project Roadmap

**v0.1 — Foundations (this phase's target)**
Splash → Dashboard → Discovery → Device List → Pairing → Device Details → Remote Screen, for Android TV, Google TV, Fire TV, Samsung Smart TV, and Windows Companion.

**v0.2 — Windows Companion completion**
Full C# Windows Service: mouse/keyboard/clipboard relay, shutdown/restart/sleep, Wake-on-LAN, application launch list. (Originally spec'd as VB.NET; switched to C# so the module can be developed in VS Code with full IntelliSense/debugging via the official C# Dev Kit — VB.NET tooling outside full Visual Studio is materially weaker. Functionally identical: same .NET runtime, same Win32 APIs, same WebSocket libraries.)

**v0.3 — Hardening & polish**
Reconnect reliability, pairing edge cases (token revocation, IP change handling via mDNS re-resolve), animations pass, accessibility pass, crash reporting-free telemetry (local-only logs).

**v0.4 — Cameras**
`feature-cameras` populated: ONVIF discovery/PTZ, RTSP viewer (likely via ExoPlayer/Media3), `plugin-onvif`, `plugin-rtsp`.

**v0.5 — Smart Home**
`plugin-tplink`, `plugin-hue`, scenes/automation groundwork, `feature-scenes`.

**v0.6 — Home Assistant bridge**
`plugin-home-assistant` as a "meta-plugin" that proxies HA-exposed entities through the same `IDevicePlugin` contract.

**v0.7+ — Ecosystem**
IPTV, voice control, remote (cloud) access, plugin store / community plugin distribution model, signed community plugins.

---

## 7. Open Decisions for You to Confirm Before Phase 2

1. **Package name / namespace** — proposing `com.homecontrol.*` unless you have a preference (e.g. reverse-domain you own).
2. **Networking library** — recommend **Ktor Client** (multiplatform-friendly, coroutine-native, good WebSocket support) over raw OkHttp+Scarlet. Confirm or override.
3. **Min SDK** — spec says target Android 15+ (SDK 36); need a minSdk floor. Recommend **minSdk 26** (Android 8) for Wake-on-LAN/NSD API maturity, or **minSdk 30** if you want to drop pre-ADB-wireless-pairing devices entirely and simplify the Android TV plugin. Your call.
4. **`plugin-registry` priority/versioning scheme** — fine to finalize in Phase 2 once the contract is coded, flagging now so it's not forgotten.

---

*End of Phase 1 document. No Kotlin/VB.NET code has been generated. Awaiting your approval or requested changes before Phase 2 (Gradle project scaffolding + `core:model` + `core:plugin-api` contract code).*
