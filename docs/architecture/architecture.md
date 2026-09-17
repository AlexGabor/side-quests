# Architecture

## UI

### State holders

- State is always saved in a state holder class, not in composable.
- State is exposed as readonly State.
- State holders must survive recreation. Use `rememberSaveable`.
- The remember function of the state holder is responsible for injecting dependencies.
- A state holder receives, when needed, a CoroutineScope in constructor. Provided by state holder's remember function using `rememberCoroutineScope`.
- Coroutines launched from state holder's coroutine scope will be canceled when it leaves the composition.

### Navigation and deep links

Navigation 3 wrapped in [design/navigation](../../design/navigation) (to become `lib/navigation`, see [known-deviations.md](known-deviations.md)).

- Launch input becomes a `DeepLink` once, at the top. `App(launch)` turns `LaunchParameters` into a `DeepLink` ([DeepLinks.kt](../../pacer/sharedApp/src/commonMain/kotlin/com/alexgabor/pacer/DeepLinks.kt)) and provides it as `LocalDeepLink`.
- Each navigation level takes its own keys and passes the rest down: `deepLink.take<MyKey>()` seeds this level's back stack, `rest<MyKey>()` is what nested displays see.
- Back stacks are `DeepLinkedBackStack`s from `rememberDeepLinkedBackStack`. A restored back stack wins over the deep link.
- Root destinations are a `@Serializable sealed interface <App>Destination : NavKey` with stable screen names, and entries use fixed content keys. See [RootNavigation.kt](../../pacer/sharedApp/src/commonMain/kotlin/com/alexgabor/pacer/RootNavigation.kt).
- Use AppUrl to interact with the browser history.


## Coroutines

### Dispatchers

- Never use `Dispatchers.*` directly. Use `CoroutineDispatchers.IO` / `Default` / `Main` from [lib/coroutine/dispatchers](../../lib/coroutine/dispatchers).
- Blocking or disk/network work (e.g. DataStore) is offloaded to `CoroutineDispatchers.IO` with `withContext` or `flowOn`. See [SettingsRepositoryImpl.kt](../../pacer/core/settings/impl/src/commonMain/kotlin/com/alexgabor/pacer/core/settings/SettingsRepositoryImpl.kt).
- Read `CoroutineDispatchers` when the work starts, not when a class is built, so tests can swap them.
- In tests, use `TestScopeRule` from [lib/coroutine/test](../../lib/coroutine/test): `install()` in `@BeforeTest`, `reset()` in `@AfterTest`, run the test body in `rule.testScope.runTest`.
