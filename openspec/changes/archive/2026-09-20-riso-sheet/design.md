# Design

## Context

How the current pipeline works is documented in [docs/architecture/riso-paper-and-ink.md](../../../docs/architecture/riso-paper-and-ink.md), and the motivation is in `proposal.md`. The facts that shape this design are:

- Each ink pass already outputs opaque `mix(1, ink, coverage)` and composites with Multiply (§6.6). Paper and ink therefore share no state except the pixels of the paper's layer (§7).
- The paper's sheet shader computes `S·(1 − c + c·T) + T·c·(1 − σ)`. Inside an ink pass, `c = 1`, so this reduces to `S·T + T·(1 − σ)`. Skia's Multiply of an opaque source `T` over a destination `S` with opacity `σ` gives `T·(1 − σ) + T·S`, which is the same expression. **So painting the stock first and letting the passes multiply onto it reproduces today's output for inked content exactly.**
- The surface is expensive to compute: about 100 dependent noise reads per pixel. Only Android and iOS can bake it on the GPU. The JVM bakes on a CPU raster, and wasm computes it every frame.
- `design/riso` may depend on nothing else in the repo, including `lib/coroutine/dispatchers`.

## Goals / Non-Goals

**Goals:**
- Content inside the paper is left alone. The only link between paper and ink is an explicit node lookup.
- A full-screen paper costs one cached tile draw per frame on every platform.
- The surface is baked per surface shape and density, never per layout size, colour or strength.
- There is one paper code path on all four platforms.

**Non-Goals:**
- Single-pass ink (one layer per ink node instead of one per drum). That is a separate change.
- Opaque, lighter-than-paper inks such as white or metallic.
- Changing Pacer's per-platform effects defaults.
- Matching today's surface pattern pixel for pixel. The look is re-calibrated (D3).

## Decisions

### D1. The paper is a background plus a traversable node

`Modifier.risoPaper(paper)` becomes a `ModifierNodeElement` for `RisoSheetNode`. That node is a `DrawModifierNode`, a `TraversableNode` (with key `RisoSheetKey`) and a `GlobalPositionAwareModifierNode`.

- **`draw()`** paints the stock with a `ShaderBrush` over its bounds and then calls `drawContent()`. There is no `graphicsLayer`, no render effect and no offscreen buffer.
- **Ink finds its sheet.** In `onAttach`, `RisoPassNode` finds its nearest sheet with `traverseAncestors(RisoSheetKey)`, which is the same mechanism it already uses for nesting. From the sheet it reads:
  - the stock colour, used for separation in place of `RisoTheme.colors.paper`
  - the surface shader and its parameters, used for the warp
  - the sheet's coordinates, which give the pass its position within the sheet

*Alternative considered:* a `RisoSheet { }` composable that provides a `CompositionLocal`. It was rejected because it forces an API change on every call site and still needs a node to know the sheet's position. A modifier can't provide a `CompositionLocal`, but node traversal already solves the lookup.

*Alternative considered:* keeping the render effect with a cheaper shader. It was rejected because the full-screen offscreen buffer and the "everything is transmittance" contract are exactly what this change removes.

### D2. The surface is split into baked shape and draw-time strength

Today every parameter goes into the bake. The surface is a linear combination of three raw fields, with the strengths applied afterwards (`PAPER_SURFACE_SKSL`):

```
fade  = clamp(8·(u_fade · fadeFbm)³, 0, 1)
fiber = ½·u_fiber·(fiberGrad − 1) · mix(1, ½, fade)
rough = roughDiff · mix(1, ½, fade)
normal, normalImage, res  ← linear in (u_roughness·rough, fiber), then the lighting uses u_contrast
```

Only the three raw fields are baked:

| Texture     | Channels                         | Depends on                           | Period                                                          |
|-------------|----------------------------------|--------------------------------------|-----------------------------------------------------------------|
| fine tile   | r = `roughDiff`, g = `fiberGrad` | `fiberSize`, `scale`, density bucket | `P_fine` dp                                                     |
| coarse tile | r = `fadeFbm`                    | `scale`, `seed`, density bucket      | `P_coarse` dp, co-prime with `P_fine`, stored at 1/8 resolution |

A shared SkSL function, `sheetSurface(sheetPx) → (normalImage, res)`, rebuilds the surface from two texture reads plus arithmetic. Both the stock brush and the ink pass use it, so they can't disagree.

- **Colours, `contrast`, `roughness`, `fiber` and `fade` become uniforms.** Changing them never re-bakes, which covers the "Coloured papers" requirement.
- **Repetition is only partly hidden by the co-prime periods.** The fine detail repeats every `P_fine`, and the fade modulating it repeats every `P_coarse`. In practice the default `fade` (0.5) barely modulates the detail: at 4K the high-passed grain correlates at 0.99 one `P_fine` apart. What keeps the repeat from being seen is the default stock's low contrast (see Risks).

*Alternative considered:* one full-surface tile. It is simpler, but the fade would repeat visibly at the tile size, and every strength change would re-bake.

### D3. The noise is periodic and anchored in dp

- **Anchored to the sheet in dp.** Surface coordinates are `sheetPx / density`, measured from the sheet's top-left. That replaces today's layer-relative `patternUV`, so the grain has a physical size and no longer depends on the layout.
- **Periodic value noise.** Lattice lookups wrap modulo the period in lattice cells, and each octave's frequency multiplier is an integer (2, replacing 1.99 and 2.1).
- **Fiber rotation that stays periodic.** The per-octave rotation of 0.7 rad breaks periodicity. It is replaced by the integer lattice map `(x, y) → (x + y, y − x)`, which is a 45° rotation with a √2 scale that maps the lattice onto itself.
- **Re-calibration.** The pattern changes, so default strengths are re-tuned in `risoRecorder` against today's `docs/*.webp` for a similar overall look.

### D4. Tiles use density buckets

Tiles are baked at `bucket = ceil(density)` (1, 2, 3 or 4) and sampled with `REPEAT` tiling and linear filtering, scaled by `density / bucket`. A 2.625× phone therefore uses the 3× tile, minified slightly. That way the shipped 2× and 3× tiles cover nearly every device, and runtime bakes are rare.

### D5. Where tiles come from, in order

1. **In-memory cache:** an LRU keyed by `TileKey(kind, spatial params, bucket)`. The Skia lock-free LRU is reused and extended to Android.
2. **Shipped resource:** `composeResources/files/riso/tiles/v<SURFACE_VERSION>/<key>.png`, as lossless PNG. They are generated for the default stock at 1×, 2× and 3× plus its coarse tile (about 1.7 MB in total) by `./gradlew :design:riso:bakeTiles`. That task lives in `design/riso`, not `risoRecorder`, because it needs the library's internal bake.
3. **Disk cache:** the same PNGs, under the platform cache directory. Android uses `context.cacheDir`, read through `LocalContext` in `risoPaper`. iOS uses `NSCachesDirectory`. The JVM uses the OS cache directory (`~/Library/Caches`, `$XDG_CACHE_HOME` or `%LOCALAPPDATA%`) plus `sidequests-riso/`. Web has no disk tier. Files sit under a `v<SURFACE_VERSION>/` folder, and that constant is bumped whenever the surface shader changes, so stale files are never read.
4. **Bake:** it runs on a single-threaded dispatcher. The GPU is used on Android (`HardwareRenderer`, as today) and iOS (its own Metal context, as today), and a raster surface on the JVM and wasm.

- **While a tile is loading,** the sheet draws the flat stock at the surface's mean lighting. The shader runs with a flat-surface flag, so the only visual change when the tile arrives is the grain appearing, never a colour shift.
- **Consistency guard:** a JVM test re-bakes the default tiles and compares them with the shipped files. The build fails if they drift, which would mean the shader changed without regenerating the resources.

*Alternative considered:* owning a GPU context per desktop OS (Metal through JNA, D3D12, EGL) and on web (WebGL2). It was rejected for now. With tiles, a raster bake is small, rare and mostly replaced by shipped resources, so four platform backends aren't worth maintaining. It stays open as a macOS-only optimisation if needed.

### D6. How ink uses the sheet

- **Separation:** `InkPassSpec.paper` comes from the sheet's stock (`colorFront` composited over `colorBack` at mean lighting). The theme paper is used only when there is no sheet.
- **Warp:** the ink shader gains the surface children and uniforms from D2. It reads its content at `fragCoord + warpDp · density · normalImage(sheetPos)`, where `sheetPos = fragCoord + origin − sheetOrigin`.
  - `warpDp` is 8, calibrated to what the ported shader's 2%-of-layer warp gave on a phone-width sheet. The default stock's maximum displacement is 0.53dp.
  - The warped read is clamped to the recorded artwork. Pass clips are **not** grown: a sub-dp displacement at a node's own edge is invisible, and growing the clip would mean recording outside the node's bounds.
  - `u_paperWarp` gating (flat stock) is kept.
- **Compositing is unchanged:** Multiply, Offscreen, one layer per drum. The passes now multiply onto the painted stock rather than onto a transparent layer that is post-processed later.

### D7. Source sets

```
commonMain            sheet node, tile keys, shaders, cache policy
├── androidMain       RuntimeShader brush, HardwareRenderer bake, cacheDir
└── skikoMain         RuntimeShaderBuilder brush, raster/GPU bake driver, PaperNoiseShader
    ├── metalBakeMain   (ios) Metal bake surface, NSCachesDirectory
    └── rasterBakeMain  (jvm, wasmJs) raster bake surface; the JVM adds its OS cache dir
```

`inlinePaperMain` and `bakedPaperMain` are removed, and their shared code moves up into `skikoMain`.

### D8. Removals

- `risograph/region/*` is deleted: `risoBypass`, `RisoBypassRect`, `RisoRegionHost` and `bypassSksl`. The region host existed only to serve bypass.
- `SheetEffect` is deleted, because there is no render effect left to cache.
- The paper's settle delay, the stand-in and the per-size bake keys are deleted.

### D9. Waiting for tiles

A tile lands on a background thread. The first implementation exposed it as snapshot state written by the loader. On Android, a composition that read that state was never invalidated when the loader wrote it (seen as Stamp's export hanging), and creating the state in the global snapshot instead crashed with "created after the snapshot was taken". So a `Tile` is a plain `CompletableDeferred`, and whoever needs one awaits it on its own main-thread scope:
- `RisoSheetNode` awaits on its node `coroutineScope`, then invalidates itself and its registered ink dependents.
- The new public `@Composable RisoPaper.isSurfaceReady()` uses `remember { mutableStateOf }` plus a `LaunchedEffect`.

`isSurfaceReady()` exists for code that captures single frames. Stamp waits on it before every export capture, because tiles now load asynchronously where the old Android bake was synchronous. A tile whose every source fails settles as null, and the sheet stays flat for good rather than leaving callers waiting.

## Risks / Trade-offs

- **[Visible repetition]** of the fine tile on large screens. Measured: the detail repeats every 256dp almost exactly, because the default fade barely modulates it. At the default stock's contrast (grain std about 2 levels of 255) it is not visible at 4K. A custom stock with high `contrast` and very different front and back colours could show it. → If that is ever needed: a larger `P_fine`, or offsetting the fine tile per coarse cell at draw time (texture bombing).
- **[8-bit precision]** of the raw fields could band in `res` at high contrast. → Keep the ±½ LSB dither and measure the value ranges to set the encoding scale. If banding persists, use RGBA_F16 where supported (Android `Bitmap.Config.RGBA_F16`, Skia `ColorType.RGBA_F16`).
- **[Visual change for un-inked content]** Pacer or Stamp content that relied on being multiplied by the stock changes colour. That includes `onRisoPaper()` backgrounds outside `risoInk` and Stamp's `drawBehind { White }` under a paper. → Audit every screen in tasks, and compare `risoRecorder` output before and after.
- **[Main-thread bake on web]** wasm has no background thread, so a custom-surface raster bake blocks the page for a moment. → Default tiles are shipped. The web bake yields between row bands (`yield()` every N rows) so frames keep rendering.
- **[Bigger ink shader]** The ink pass gains two texture reads and the surface arithmetic. That is small next to the full-screen sheet pass it replaces, and it only runs over inked regions.
- **[Sheet lookup across nested sheets]** A pass must use the nearest sheet, and its coordinates can change without the pass itself redrawing. → The sheet's position is read at draw time through `LayoutCoordinates`, as `origin` already is.
- **[Architecture deviation]** The bake uses a `Dispatchers.Default`-derived single-threaded dispatcher, because `design/riso` can't depend on `lib/coroutine/dispatchers`. → Record this in `docs/architecture/known-deviations.md`.

## Migration Plan

1. Land D2 and D3 (the tileable surface and the bake pipeline) behind the existing `risoPaper` render effect first. The effect samples the tiles instead of `u_paperMap`. This validates the pattern and the caching on all platforms with no API change.
2. Switch `risoPaper` to the background plus node model (D1), and move the warp into ink (D6).
3. Remove bypass and the old paper paths, and migrate Stamp.
4. Regenerate the shipped tiles and `docs/*.webp`, and update the docs.

Rollback is `git revert` of the change. There is no persisted data except disk-cache files, which are versioned and ignored once stale.

## Open Questions

- Resolved: `P_fine` = 256dp, `P_coarse` = 1400dp at 4dp per pixel, `warpDp` = 8, encoding `rough·0.8 + 0.5` and `fiber / 6` (measured ranges ±0.37 and up to 4.3).
- Resolved: the 4× bucket is not shipped. Devices above 3× bake their tile once on the GPU and cache it.
