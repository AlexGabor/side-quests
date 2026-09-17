# Design system: Riso

[design/riso](../../design/riso) is a custom Compose theme modeled on risograph printing: a paper surface, ink passes with visible misregistration. Every app and tool uses it. Visual overview in the [root README](../../README.md#riso).

The effect is implemented with a custom shader.

- Apps and features use Riso components and `RisoTheme.*` values, never Material directly. Only `design/riso` itself may use Material internally.
- Read theme values through `RisoTheme.colors / typography / dimens / shapes / press`; the backing `Local*` composition locals are `internal`.
- `effectsEnabled` switches the shader print effects off.
- `design/riso` depends on nothing else in the repo.
- A new component goes in `components/` when two screens need it or it defines the look of a control; one-off layout stays in the feature.

## Platform source sets

The shader work is split by how each platform reaches Skia, configured and explained here: [design/riso/build.gradle.kts](../../design/riso/build.gradle.kts):

```
commonMain
├── androidMain            android.graphics RuntimeShader
└── skikoMain              org.jetbrains.skia (JVM, iOS, wasmJs)
    ├── bakedPaperMain     paper baked into a texture once (JVM, iOS)
    │   └── metalBakeMain  baked on the GPU via Metal (iOS); JVM bakes on a raster surface
    └── inlinePaperMain    paper computed per frame (wasmJs, no offscreen GPU surface)
```

- Shared Skia code goes in `skikoMain`, not duplicated per target.
