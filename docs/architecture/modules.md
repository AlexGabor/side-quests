# Modules

## Top-level structure

- `build-logic/` Included build for convention plugins.
- `<app>/` Root folder for a single app, e.g. `pacer/`.
- `design/` Root folder for the design system.
- `lib/` Collection of library modules shared between apps. Must be app agnostic.
- `tools/` Root folder for developer tools.

## Convention plugins

Collection of common build configuration: 
   - platform apps, ex: AndroidApp plugin
   - library modules, ex: MultiplatformLibrary plugin

## App layout

```
<app>/
  <platform>App/                    platform entry point only
  sharedApp/                        common App and DI wiring.
  feature/<name>/                   UI feature modules
  core/<name>/{api,impl,test}/      domain logic modules
```

**Platform entry points:** 
  - Map platform input to common App input (launch parameters, deep link, etc.)
  - Call `initKoin` with the app's `sharedModule` and may add platform-specific modules.
  - Launches common App.

**Shared App:** 
  - Wires common Koin modules, binds api to impl.
  - Setup theming
  - Provide deep link and navigation context
  - Implements Navigation, although maybe it should be extracted as `feature/root`.

**Feature module:** 
  - Implements a screen or user flow.
  - Depends on `core` modules for domain logic. Only depends on `api` modules, never `impl`.

**Core module:** 
  - Implements domain logic for the app. Not shared with other apps.
  - May be split into `api`, `impl` and `test` modules

## Design system
   - One shared design system used by all apps. 

## lib modules
   - Implements app-agnostic behavior shared between apps.
   - May be split into `api`, `impl` and `test` modules.

# Module structure

## api / impl split

- A module may be split into an `api` gradle subproject, an `impl` subproject. A module may have more than one Gradle subproject implementing the api.
- An implementation module depends on the api of another module. The binding is done in the app's `sharedApp` module.
- Api modules are platform-agnostic. Platform specific code lives in the implementation module.
