## Context

The repo builds every library for Android, iOS, JVM and `wasmJs` through `sidequests.multiplatform.library`, and
compose libraries through `sidequests.compose.multiplatform.library`, which applies the former by id. Pacer ships on
all four. Bookplate ships on Android and iOS only, but its core and `lib` modules should still build for `jvm()` so
tests run as fast JVM unit tests (roadmap "Decisions already made"). Pacer's apps are the reference for the app
layout (`androidApp`, `iosApp`, `sharedApp`) and for root navigation (`design/navigation`).

## Goals / Non-Goals

**Goals:**
- A convention for Android + iOS (+ JVM for tests) libraries, compose and non-compose, without touching Pacer's
  targets.
- A Bookplate app that builds and launches on Android and iOS, with Koin, Riso, and a two-destination root
  navigation.
- Docs and a Maestro smoke flow future milestones extend.

**Non-Goals:**
- Any real feature: library, import, player, Room, document access (later milestones).
- Deep links, URLs, `LaunchParameters` mapping.
- App icons designed for Bookplate (the platform defaults are used for now).
- Release signing/publishing setup beyond what the shared Android app plugin already does.

## Decisions

### New plugins rather than an option on the existing ones
`sidequests.mobile.library` and `sidequests.compose.mobile.library`. The target set is decided in `apply()`; an
option on the existing plugins would have to be an extension read in `afterEvaluate` or a Gradle property, both of
which make target creation order-dependent and easy to get wrong. Two plugin ids are explicit in each module's
`plugins {}` block. Shared code is pulled into `common/` helpers:
- `configureMultiplatformLibrary(web: Boolean)` sets up Android, JVM, iOS, and `wasmJs` only when `web` is true,
  plus the JVM target level. `MultiplatformLibraryPlugin` calls it with `web = true`, so its behaviour is unchanged.
- `configureComposeLibrary()` holds the compose plugins and dependencies `ComposeMultiplatformLibraryPlugin` adds
  today; the compose mobile plugin applies the mobile library plugin and then calls it.

Alternative considered: making Pacer modules opt in to web. Rejected — it changes every Pacer module.

A mobile library may depend on an all-platform library (`design/riso`, `lib/*`): its targets are a subset. The
reverse is not allowed, which is fine since nothing shared depends on Bookplate.

### `sharedApp` on the compose mobile plugin
`bookplate/sharedApp` uses `sidequests.compose.mobile.library`, so it has Android, iOS and JVM. JVM exists only for
`jvmTest` (navigation tests through `runComposeUiTest`). The iOS framework is `BookplateShared`, static, like Pacer's.

### Navigation without deep links
`RootNavigation` uses `rememberDeepLinkedBackStack` from `design/navigation`, which is the documented pattern and
gives saved back stacks and the Riso crossfade. `LocalDeepLink` is never provided, so it is always empty and the
back stack starts at `listOf(BookplateDestination.Home)`. The back stack needs an `AppUrl`; `lib/appstateurl/impl`'s
`appUrlModule` binds the no-op `NoAppUrl` on Android and iOS, so nothing is written anywhere. `screenName` and
`keyForScreen` are still required by the API: `home`/`player`, and only `home` maps back to a key (Player needs a
book id). `App()` takes no `LaunchParameters`; `MainViewController()` and `MainActivity` pass nothing.

Alternative: a plain `NavBackStack` with `NavDisplay`. Rejected — it would duplicate the Riso fade decorator wiring
and diverge from the architecture doc.

### Destinations
`@Serializable sealed interface BookplateDestination : NavKey` with `data object Home` and
`data class Player(val bookId: String)`. A string id keeps the key independent of the storage choice (Room ids can
be mapped to strings later). Fixed content keys (`"home"`, `"player"`) as Pacer does.

### Placeholder screens live in `sharedApp`
`feature/home` and `feature/player` arrive in M9/M10 with real content; creating empty feature modules now adds
build surface for nothing. The placeholders are `internal` composables in `sharedApp` and are deleted when the
feature modules arrive. Home has an "Open player" control so the second destination is reachable and testable.

### Android app
`sidequests.android.app`, `applicationId = com.alexgabor.bookplate` (debug suffix `.debug` from the plugin).
Manifest has only the launcher intent filter, no `INTERNET` permission, no `launchMode` change (no links to
receive). Without an `android:icon` the system default icon is used.

### iOS app
Copied structure from `pacer/iosApp`: one target, a "Compile Kotlin Framework" script phase running
`./gradlew :bookplate:sharedApp:embedAndSignAppleFrameworkForXcode`, `-framework BookplateShared`. Removed: URL
types, entitlements (associated domains), `onOpenURL`. Bundle id `com.alexgabor.bookplate`. The asset catalog has
no app icon yet.

## Risks / Trade-offs

- [Two pairs of near-identical plugins] → the shared helpers keep the difference to the `web` flag.
- [Placeholders in `sharedApp`] → explicitly temporary; M9/M10 replace them with feature modules.
- [Koin `NoAppUrl` dependency on `lib/appstateurl/impl`] → small; it's what `design/navigation` already requires.
- [Default icons] → acceptable for M1; a Bookplate mark can come from `tools/stamp` later.
