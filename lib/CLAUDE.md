# lib

- Modules here are shared between apps and must stay app agnostic: no dependency on any `<app>/` module and no app-specific names.
- Package mirrors the path: `lib/<name>/<sub>` uses `com.alexgabor.lib.<name>.<sub>`, matching the Gradle `namespace`.
- May be split into `api`, `impl` and `test`; see [modules.md](../docs/architecture/modules.md).
