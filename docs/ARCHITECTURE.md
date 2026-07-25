# Controly — Architecture

This describes the app as it actually exists today, not a target/spec — see git history if you want the original planning document this replaced. Module names still use the `com.homecontrol.*` package/`HomeControl` namespace internally (the user-facing rebrand to "Controly" only touched branding, not the codebase — see the rebrand commits for why).

## 1. Architectural style

Clean-ish layering across Gradle modules, wired together with **Koin** (not Hilt — Hilt's annotation processor is JVM-only and blocked the Kotlin/Native iOS port, so the whole app was migrated to Koin first). The core rule: **a feature module can control any device without knowing what kind of device it is.** It talks to `IDevicePlugin` (an interface) and `DeviceCapabilities` (a data description) — no `if (deviceType == SAMSUNG)` branching in feature code.

| Layer | Lives in | Depends on |
|---|---|---|
| Presentation | `features/*`, `app/` | `core:model`, `core:plugin-api`, `core:data`, `core:designsystem` |
| Domain (contracts) | `core:plugin-api`, `core:model` | Kotlin stdlib + coroutines only |
| Data / Device I/O | `core:database`, `core:discovery`, `core:security`, `core:crypto`, `core:net-io`, `plugins:*` | `core:plugin-api`, `core:model` |

## 2. Actual module layout

```
Controly/
├── app/                        # Composition root: Activity, nav graph, Koin wiring, Settings/About screens
├── build-logic/convention/     # Gradle convention plugins (Kotlin/Compose/lint setup shared by every module)
├── core/
│   ├── model/                  # DeviceType, DeviceCapabilities, PairedDevice, DiscoveredDevice, RemoteKey — pure Kotlin, KMP (android + iOS targets)
│   ├── plugin-api/              # IDevicePlugin contract — KMP (android + iOS targets)
│   ├── security/                # KeystoreCipher (AES-GCM, Android Keystore-backed) used for ADB keys, pairing tokens
│   ├── crypto/                  # KMP module: ADB RSA signing / secure key storage (Android done; iOS is a Phase 5 stub — see TODOs)
│   ├── net-io/                  # KMP raw TCP socket abstraction (Ktor lacks Apple socket support, hence hand-rolled)
│   ├── discovery/                # mDNS/SSDP/UDP-broadcast scanning — emits brand-agnostic DiscoveredDevice records
│   ├── database/                # Room: PairedDeviceEntity + DAO
│   ├── data/                     # Repository implementations binding database + discovery + plugin-api together
│   └── designsystem/             # Material 3 theme/colors/typography
├── plugins/
│   ├── plugin-androidtv/        # Android TV, Google TV, AND Fire TV — all speak the same ADB-over-TCP protocol (see below)
│   ├── plugin-sonytv/            # Sony Bravia — ScalarWebAPI + IRCC-IP, PIN pairing
│   ├── plugin-samsungtv/         # Samsung — legacy plain-TCP remote protocol (port 55000), on-screen approval
│   ├── plugin-windows/           # Talks to windows-companion/ over an authenticated WebSocket
│   └── plugin-registry/          # Koin aggregator: collects every IDevicePlugin into the set feature code queries
├── features/
│   ├── feature-splash/
│   ├── feature-dashboard/
│   ├── feature-devices/          # Discovery UI, device list, pairing flow, manual "Add by IP"
│   ├── feature-remote/           # The remote-control screen (D-Pad, touchpad, keyboard, volume, quick actions)
│   └── feature-cameras/          # IP camera viewing: ONVIF-negotiated RTSP streams (Media3/ExoPlayer), multi-camera grid
├── windows-companion/            # Separate C# solution (WinForms tray app), not a Gradle module
├── ios/, iosApp/                 # Kotlin Multiplatform iOS port — early scaffold, see §5
└── docs/ARCHITECTURE.md          # this document
```

Note: `features/feature-settings` exists as a directory but is **not** wired into `settings.gradle.kts` — Settings and About screens actually live directly in `app/src/main/kotlin/com/homecontrol/app/settings` and `.../about`. There's no separate `feature-pairing` module either; pairing UI lives inside `feature-devices`.

## 3. Plugin architecture

`core/plugin-api`'s `IDevicePlugin` interface (identity, `canHandle(discovered)`, `pair()`/`connect()`/`disconnect()`, `getCapabilities()`, and control methods like `sendKey()`/`powerOn()`/`launchApp()`) is what every plugin implements and what `feature-remote` renders against generically. Adding a device type is: implement the interface in a new `plugins/plugin-<name>` module, register it in `plugin-registry`, done — no changes needed anywhere in `features/*`.

### 3.1 One plugin can (and does) cover multiple `DeviceType`s

`plugin-androidtv` handles `ANDROID_TV`, `GOOGLE_TV`, **and** `FIRE_TV` — there is no separate `plugin-firetv` module. Fire OS exposes the exact same ADB-over-TCP surface as stock Android TV, so one plugin covers all three; the only difference is branding (`defaultManufacturer()`/`defaultModel()`) and that Fire TV has no discovery broadcast of its own, so it's only ever added via the manual "Add by IP" flow. The same plugin also recognizes several other Android TV OS-licensing brands sold in the UK/EU market (TCL, Hisense, Philips, Sharp, Toshiba, JVC, Grundig, Hitachi, Bush) by name, for the same reason — see `AndroidTvPlugin.canHandle()`.

Sony is deliberately excluded from `plugin-androidtv` even though Sony Android TVs technically speak ADB too — real-hardware testing found the ADB on-screen approval dialog unreliable to get on fresh reconnects on Sony sets specifically, so Sony hardware is routed to `plugin-sonytv`'s PIN-based pairing instead, which doesn't share that failure mode.

### 3.2 Pairing strategies

Different device families use fundamentally different pairing mechanics — the contract exposes *which kind* via `PairingStrategy` rather than forcing every plugin into one UX:
- **`ON_SCREEN_APPROVAL`** — Android TV/Fire TV (ADB "Allow debugging?" + Always Allow checkbox) and Samsung (native Allow/Deny prompt).
- **`CODE_VERIFICATION`** — Sony (TV displays a PIN, typed into the app).

### 3.3 Connection liveness

Every plugin caches its live connections in memory (keyed by IP). A locked phone, a backgrounded app, or the device itself entering standby can silently kill the underlying socket with no local signal that it died — `plugin-androidtv` verifies a cached connection is actually still alive (a real round-trip probe, not just "is the object present") before reusing it, and evicts dead entries on command failure so the next `connect()` rebuilds rather than retrying a broken socket forever. `RemoteScreen` also reconnects automatically on `ON_RESUME` (screen returning to the foreground), not just on manual retry.

## 4. Security

- **ADB (Android TV/Fire TV)**: RSA-2048 keypair, generated once and reused for every device, persisted encrypted-at-rest via `core:security`'s `KeystoreCipher` (AES-256-GCM, Android Keystore-backed).
- **Windows Companion**: ECDH key exchange verified by an on-screen short-code comparison (defeats MITM via out-of-band visual confirmation, not anything sent over the wire).
- **Samsung/Sony**: token/trust persistence is handled device-side per each protocol's own native mechanism.
- No plugin has an anonymous "send commands to any device on the network" code path — every `connect()` requires a previously stored, valid credential for that specific device.

## 5. iOS (Kotlin Multiplatform) port — status

Early scaffold, not a working app yet. Done: Koin migration (prerequisite, since Hilt is JVM-only), `core:model`/`core:plugin-api` converted to real KMP modules, `core:crypto`/`core:net-io` created with iOS source sets, a `:ios` framework-export module producing `ControlyShared.framework` consumed by a minimal SwiftUI host in `iosApp/`. `MainViewController.kt` is explicitly a Phase 1 placeholder (renders static text, no real UI). Explicitly stubbed (`TODO`, Phase 5): ADB signing and secure key storage on iOS (`core/crypto/src/iosMain`). Not started: Room/database KMP port, `core:designsystem` KMP port, and porting any `features/*` or `plugins/*` modules — the current CI pipeline (`codemagic.yaml`) validates the build/signing/TestFlight pipeline itself, not real app functionality.

## 6. Not yet built

- Remote camera access when the phone isn't on the camera's LAN (planned: local/remote address fallback per camera, optional UPnP auto port-forwarding).
- Smart home platforms (Hue, TP-Link, Home Assistant bridge) — no plugin exists yet; would follow the same `IDevicePlugin` pattern.
