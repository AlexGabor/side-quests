# Riso paper and ink

How [design/riso](../../design/riso) turns ordinary Compose drawing into something that looks risograph-printed: the **paper** (a sheet with a surface) and the **ink** (one pass per drum, off register, screened and mottled). This doc covers the rendering pipeline, the maths, the platform split and the invariants to keep. For the public API as a user sees it, read [design/riso/README.md](../../design/riso/README.md). For the rules, read [design-system.md](design-system.md). The behaviour contract is [openspec/specs/riso-paper](../../openspec/specs/riso-paper/spec.md).

The code is referenced by symbol name, not line number. All paths below are under `design/riso/src/<sourceSet>/kotlin/com/alexgabor/design/riso/`.

---

## 1. Mental model

A riso print is a sheet of paper that goes through the press once per ink. Each drum lays down one spot color, lands a little off register, and the inks stack subtractively where they overlap. The code copies that structure one-to-one:

| On a press                         | In code                                                                                         |
|------------------------------------|-------------------------------------------------------------------------------------------------|
| The sheet (stock color + texture)  | `Modifier.risoPaper(RisoPaper)`: `RisoSheetNode` paints the stock behind its content             |
| The sheet's texture                | two baked, repeating tiles, rebuilt into a surface by `sheetSurface` at draw time               |
| The press (drums, screen, texture) | `Press`, read from the theme (`RisoTheme.press`)                                                |
| One drum's pass                    | one offscreen `GraphicsLayer` per drum, recorded by `RisoPassNode`, shaded by `INK_PASS_SKSL`   |
| Ink on paper, inks stacking        | the pass layers composited with `BlendMode.Multiply` onto the painted stock                     |
| Misregistration                    | the pass layer's translation, `registration × offsetScale × density`                            |
| The sheet pushing the ink around   | the ink pass reading its artwork displaced by the sheet's surface                               |
| Halftone screen, mottle, speckle   | `screenDots` / `inkTexture` in `INK_PASS_SKSL`, anchored to the page                            |
| A frisket                          | `risoKnockout()`: `BlendMode.DstOut` punches into the enclosing pass                            |
| A photo tipped onto the page       | anything not inside a `risoInk`: it is drawn as authored                                        |

**Paper and ink meet in one place, explicitly.** A pass finds its sheet by walking up the tree, separates against that sheet's stock, reads the sheet's surface to warp itself, and multiplies onto the stock the sheet has already painted (§6). The sheet never post-processes its content.

---

## 2. Module layout

### Source sets

Configured, and explained, in [build.gradle.kts](../../design/riso/build.gradle.kts):

```
commonMain                  models, shader source, uniforms, sheet node, ink node, tile loader, separation
├── androidMain             android.graphics RuntimeShader / RenderEffect; tiles baked through HardwareRenderer
└── skikoMain               org.jetbrains.skia (JVM, iOS, wasmJs): InkPass, StockBrush, tile bake driver
    ├── metalBakeMain       bake surface = GPU render target on a private Metal context; tile cache in Caches (iOS)
    └── rasterBakeMain      bake surface = CPU raster (JVM, wasmJs)
jvmMain / wasmJsMain        tile cache on disk (OS cache folder) / none
```

Two questions produce this split:
1. **How do you reach Skia?** Android goes through `android.graphics`. Everything else goes through `org.jetbrains.skia`.
2. **Where does a tile bake render?** Android and iOS use the GPU. The JVM and the browser can't reach a GPU surface through Skiko, so they bake on a raster. Tiles are small and baked rarely, and the default stock's ship with the library.

### Shader source is shared

- There are no `.agsl`, `.sksl` or `.metal` files. Every shader is a Kotlin string marked `// language=AGSL`. That dialect compiles unchanged under Android's `RuntimeShader` and Skia's `RuntimeEffect`.
- Uniforms are written through `ShaderUniforms` (`risograph/ShaderUniforms.kt`). It has two adapters: `RuntimeShaderUniforms` (Android) and `RuntimeShaderBuilderUniforms` (Skia). Uniform names, their order and their coercions live next to the shader text (`setSheetSurface`, `setStock`, `setFineTileBake`, `setCoarseTileBake`, `setInkPass`). The platforms can't drift apart on them.
- Children (tiles, `u_image`) are bound by each platform's actual, since their types differ.

### Files

| Concern                           | Files (`…` = `risograph`)                                                                                  |
|-----------------------------------|-------------------------------------------------------------------------------------------------------------|
| Paper model, entry point, readiness | `commonMain/…/paper/RisoPaper.kt`                                                                        |
| Sheet node                        | `commonMain/…/paper/RisoSheetNode.kt`                                                                        |
| Tile keys, periods, versions      | `commonMain/…/paper/PaperTile.kt`                                                                            |
| Surface shaders and uniforms      | `commonMain/…/paper/PaperSurfaceShader.kt`                                                                   |
| Tile loader                       | `commonMain/…/paper/PaperTiles.kt`, `LruCache.kt`                                                            |
| Platform hooks (expect)           | `commonMain/…/paper/PaperPlatform.kt`                                                                        |
| Platform hooks (actual)           | `androidMain/…/paper/PaperPlatform.android.kt`, `skikoMain/…/paper/PaperPlatform.skiko.kt`, `{metalBakeMain,rasterBakeMain}/…/paper/PaperBakeSurface.kt`, `{jvmMain,wasmJsMain}/…/paper/TileFiles.*.kt`, `metalBakeMain/…/paper/TileFiles.ios.kt` |
| Shipped tiles                     | `commonMain/composeResources/files/riso/tiles/v<SURFACE_VERSION>/*.png`                                      |
| Palette and press                 | `commonMain/…/attributes/Colors.kt`, `attributes/Press.kt`                                                   |
| Ink colour maths                  | `commonMain/…/inks/RisoInks.kt`, `inks/RisoMix.kt`, `inks/Separation.kt`                                     |
| Ink node                          | `commonMain/…/inks/RisoInkModifier.kt`                                                                       |
| Ink shader                        | `commonMain/…/inks/InkPassShader.kt`, `inks/InkPass.kt` (expect), `{androidMain,skikoMain}/…/inks/InkPass.kt` |

---

## 3. Public API surface

| Symbol                                                  | What it does                                                                                                         |
|---------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| `RisoTheme(effectsEnabled = true) { … }`                | Provides `LocalColors`, `LocalPress` and `LocalRisoEffectsEnabled` (all internal). Read them through `RisoTheme.*`.   |
| `Modifier.risoPaper(paper: RisoPaper = RisoPaper())`    | Paints a sheet behind this node's drawing. Ink inside prints onto it.                                                 |
| `RisoPaper` / `RisoPaper.None`                          | The stock: colours plus surface parameters. `None` paints nothing and has no surface.                                |
| `RisoPaper.isSurfaceReady()` (composable)               | Whether the stock's surface has landed at the current density, or loading gave up. For code that captures a frame.   |
| `Modifier.risoInk(…)`                                   | Overloads for 1, 2 or 3 `Color`s, a `List<Color>` or a `RisoMix`, plus `offsetScale`. Prints this subtree on those drums. |
| `Modifier.risoKnockout()`                               | `risoInk(emptyList())`: cuts a hole in the enclosing pass.                                                            |
| `Press`, `RisoPress`                                    | Drum rack plus screen, mottle, grain, spread, tolerance and seed.                                                     |
| `Colors` / `RisoColors`, `Inks`, `NamedInk`             | The 12-ink palette and the stock colour `#EFEBE1`.                                                                    |
| `RisoInk`                                               | One drum: colour, registration offset (dp), screen angle (deg).                                                       |
| `RisoMix`, `RisoMix.color()`                            | Drums with authored coverages, and the colour they print to.                                                          |
| `risoOverprint(paper, inks)`, `Color.onRisoPaper(…)`    | CPU Beer–Lambert preview of printed colours.                                                                          |

### With effects disabled

With `effectsEnabled = false`, every entry point degrades without making a shader, a layer or a tile request:
- `risoPaper` becomes `background(paper.colorFront)`. For `RisoPaper.None` that is transparent.
- `risoInk` and `risoKnockout` return the modifier unchanged.
- `RisoMix.color()` returns `unprinted`, and `isSurfaceReady()` returns `true`.

This is the path used by tests (`RootNavigationTest` wraps in `RisoTheme(effectsEnabled = false)`) and the default on desktop and web in Pacer (§7.4).

---

## 4. Paper

### 4.1 Model

`RisoPaper` is a `data class`. Its fields split by what changing them costs:

| Field        | Default            | Role                           | Changing it                     |
|--------------|--------------------|--------------------------------|---------------------------------|
| `colorFront` | `RisoColors.paper` | stock colour                   | uniform only                    |
| `colorBack`  | `RisoColors.paper` | what shows through the surface | uniform only                    |
| `contrast`   | 0.12               | relief steepness (`0..1`)      | uniform only                    |
| `roughness`  | 0.12               | roughness strength             | uniform only                    |
| `fiber`      | 0.1                | fiber strength                 | uniform only                    |
| `fade`       | 0.5                | large-scale mask strength      | uniform only                    |
| `fiberSize`  | 0.29               | fiber scale (`≥ 0.01`)         | a different fine tile           |
| `scale`      | 0.1                | pattern zoom (`0.01..4`)       | different fine and coarse tiles |
| `seed`       | 5.8                | fade offset                    | a different coarse tile         |

- `warps = roughness > 0 || fiber > 0`. A stock that doesn't warp has no surface at all: no tiles are requested, it is drawn flat, and it never displaces ink.
- `painted = colorFront.alpha > 0 || colorBack.alpha > 0`. A sheet that paints nothing (`RisoPaper.None`) draws no stock and leaves ink to separate against the theme's paper.

The shader is a port of paper.design's `PaperTexture` WebGL shader, reshaped so that it repeats (§4.2).

### 4.2 The surface as two repeating tiles

The ported surface is three raw noise fields combined linearly with strengths. Only the fields are baked, each into a tile exactly one period across:

| Tile   | Channels                                        | Key (`TileKey`)                                  | Period                     | Pixels                            |
|--------|-------------------------------------------------|--------------------------------------------------|----------------------------|-----------------------------------|
| fine   | r = `rough·0.8 + 0.5`, g = `fiberGradient / 6`  | `FineTileKey(roughCells, fiberCells, bucket)`    | `FINE_PERIOD_DP` = 256 dp  | `256 · bucket` square             |
| coarse | r = `fadeFbm`                                   | `CoarseTileKey(fadeCells, seedOffset)`           | `COARSE_PERIOD_DP` = 1400 dp | `1400 / 4` = 350 square (4 dp per px) |

- **Anchored in dp.** Surface coordinates are `sheetPx / density` from the sheet's top-left. The ported shader sized its pattern relative to the layer (`5/scale` units across its height), which stretched the grain with the window. The pattern is now held at the size it had on a sheet `PATTERN_REFERENCE_DP` = 800 dp tall.
- **Lattice cells per period are whole numbers.** `roughCells = round(256 · 0.15)`, `fiberCells = round(256 · 2/fiberSize · 5/(scale·800))`, `fadeCells = round(1400 · 0.17 · 5/(scale·800))`. Rounding nudges the scale by less than half a cell per period. Stocks whose fields round alike share tiles.
- **Periodic noise** (`FINE_TILE_FIELDS_SKSL`, `COARSE_TILE_FIELDS_SKSL`). Randoms come from a float hash of the cell index wrapped `mod period`, with no noise texture. Octaves step by exactly 2, and each octave's period doubles with it. Roughness's ridge term `sin(0.2x + 0.5y)` becomes `sin(2π(fx·x + fy·y)/period)`, with `fx, fy` rounded to whole turns per period.
- **Fiber octaves turn by a lattice map.** The per-octave rotation of 0.7 rad never comes back round. It is replaced by `M: (x, y) → (x + y, y − x)`, a 45° turn and √2 scale that maps the lattice onto itself. Octave k reads `M^k·n`, which repeats every `period · 2^⌊k/2⌋` (because `M²` is a doubling and a quarter turn). There are seven √2-steps with amplitude √0.6 each, covering the ported four doubling octaves, and `FIBER_NORM` = 0.574 brings the gradient's energy back to the original's.
- **Encoding.** The ranges were measured over a full default tile: rough ±0.37, fiber gradient up to 4.3. The encodings leave room for ±0.625 and 6. The bake adds ±½ LSB of dither (a hash, not periodic), so a period apart pixels agree to within 2 levels. Decoding recovers the fields to within about 1 level.
- **Density buckets.** Tiles are baked at `densityBucket(d) = ceil(d)`, clamped to `1..4`, and sampled scaled by `bucket / density`. A 2.625× phone uses the 3× tile, minified slightly.

**Repetition.** The fine detail repeats every 256 dp almost exactly: the high-passed grain correlates at 0.99 one period apart. The coarse fade has a different period, but at the default `fade` (0.5) it barely modulates the detail. The repeat stays invisible because the default stock's grain is about 2 levels of 255 (§4.4). A custom stock with high `contrast` and very different front and back colours could show it.

### 4.3 Rebuilding the surface: `SHEET_SURFACE_SKSL`

`sheetSurface(sheetPx, out normalImage, out res)` is shared, as source, by the stock brush and the ink pass, so the paper and the ink over it can't disagree:

```
if !ready:  normalImage = 0,  res = FLAT_LIGHTING (1/√6 ≈ 0.408)
fine   = u_fineTile.eval(sheetPx · bucket/density)
rough  = (fine.r − 0.5) / 0.8;   fiberGradient = fine.g · 6
fadeN  = u_coarseTile.eval(sheetPx / (4·density)).r
fade   = clamp(8·(u_fade·fadeN)³, 0, 1)
fiber  = ½·u_fiber·(fiberGradient − 1) · mix(1, ½, fade)
rough ·= mix(1, ½, fade)
normal      = (1.5·u_roughness·rough + fiber) · (1,1)
normalImage = (0.75·u_roughness·rough + 0.2·fiber) · (1,1)
res         = clamp(dot(normalize(normal, 9.5 − 9·u_contrast^0.1), normalize(1,2,1)), 0, 1)
```

`ready` (`u_surfaceReady`) is 1 only when both tiles are bound and the stock `warps`. `FLAT_LIGHTING` is the lighting of a surface with no relief, which is the stock's average. That is why nothing shifts in colour when the tiles arrive.

### 4.4 The stock: `STOCK_SKSL` and `RisoSheetNode`

`RisoSheetNode` (a `DrawModifierNode`, `TraversableNode` with key `RisoSheetKey`, and `GlobalPositionAwareModifierNode`) draws:

```
drawRect(ShaderBrush(StockBrush.shader(surface, density)))    // if the stock is painted
drawContent()
```

`STOCK_SKSL` is the ported sheet's stock term, premultiplied, with no content read at all:

```
σ₀ = a_f·res;   S = C_f·a_f·res + C_b·a_b·(1 − σ₀);   σ = σ₀ + a_b·(1 − σ₀);   out = (S, σ)
```

> With the default stock (`C_f = C_b = P`, both opaque), `S = P` and `σ = 1`: **the lighting cancels, and a default sheet is flat in colour.** Its surface shows only through the warp on the ink (§5.6). The shading becomes visible only when front and back differ, or when the front is translucent.

`StockBrush` is an `expect class`: a `RuntimeShader` on Android and a `RuntimeShaderBuilder` on Skia. It is re-uniformed only when `(SheetSurface, density)` changes.

There is no layer and no render effect, so there's no offscreen buffer either. An empty sheet paints its stock, which the old render effect could not do.

### 4.5 The stock ink separates against

`RisoPaper.separationColor` is the stock at `FLAT_LIGHTING`, un-premultiplied: `S/σ`. For the default stock that is exactly `colorFront`. It is null for a stock that paints nothing, and ink then falls back to the theme's paper.

### 4.6 Where tiles come from: `PaperTiles`

`PaperTiles.get(key, cacheDir)` returns a `Tile` from a process-wide `LruCache` (8 entries, a lock-free snapshot swapped under compare-and-set). The first `get` of a key starts its load once, on `PaperTiles.scope`, a single-threaded `Dispatchers.Default.limitedParallelism(1)` ([known deviation](known-deviations.md)). The sources are tried in order:

1. **Shipped:** only for `shippedKeys`, the default stock's fine tiles at 1×, 2× and 3× plus its coarse tile. They are read with `Res.readBytes("files/riso/tiles/v<SURFACE_VERSION>/…")` and decoded with Compose resources' common `decodeToImageBitmap`.
2. **Disk:** `readTileFile(cacheDir/v<SURFACE_VERSION>, fileName)`. `cacheDir` comes from `tileCacheDir()`: Android `cacheDir/riso-tiles`, iOS `Caches/riso-tiles`, the JVM's OS cache folder plus `sidequests-riso/tiles`, and `null` on the web.
3. **Bake:** `bakeTile(sksl, pixels, uniforms)`. The result is written back to disk as PNG, written aside and renamed (or written atomically on iOS).

| Platform | Bake surface                                                   | Notes                                                        |
|----------|----------------------------------------------------------------|--------------------------------------------------------------|
| Android  | `HardwareRenderer` → `ImageReader` (RGBA_8888, 2 buffers) → copy | GPU. One buffer deadlocks `syncAndDraw(waitForPresent)`.     |
| iOS      | `Surface.makeRenderTarget` on a private `DirectContext.makeMetal` | GPU, with a raster fallback. Metal has no "current context". |
| JVM      | `Surface.makeRasterN32Premul`                                  | CPU. Skiko keeps its GL context internal.                    |
| wasmJs   | `Surface.makeRasterN32Premul`                                  | CPU, on the page's only thread.                              |

The Skia driver draws in bands of 32 rows with `yield()` between them, so a raster bake on the browser's one thread lets frames through. Every bake is read back to CPU pixels, so a tile belongs to no GPU context.

A tile is a `CompletableDeferred`, not snapshot state. It **settles** once, with its shader or with null if every source failed, and stays flat for good in that case. A failure is logged with `println`.

### 4.7 Readiness and invalidation

Nothing waits on a tile by reading snapshot state written from a background thread. That turned out to be unreliable: a tile created during composition and written from the loader never invalidated the composition that read it. Instead, whoever needs a tile awaits it on its own scope:

- **`RisoSheetNode.surface(density)`** requests its tiles on every draw. If they haven't settled, it launches one wait on the node's `coroutineScope` (the main thread) and then calls `invalidateDraw()` on itself and on every registered **dependent**, the ink passes printing on it.
- **Stock changes** (`RisoSheetElement.update` → `paper` setter) redraw the sheet and its dependents the same way.
- **`isSurfaceReady()`** is `remember(fine, coarse) { mutableStateOf(settled) }` plus a `LaunchedEffect` that awaits both tiles, which is the standard main-thread pattern. Stamp waits on it before each capture.

| Change                                  | New tile | Stock re-uniformed | Ink redrawn                      |
|-----------------------------------------|----------|--------------------|----------------------------------|
| Stock colour, strengths                 | –        | ✓                  | ✓ (dependents)                   |
| `fiberSize`, `scale`, `seed`, density   | ✓        | ✓                  | ✓                                |
| Layout size                             | –        | –                  | –                                |
| Tiles land                              | –        | ✓                  | ✓ (dependents)                   |
| Ink pass moves on the page              | –        | –                  | its own effect rebuilds (`origin`, `sheetOffset`) |

---

## 5. Ink

### 5.1 Palette and slots

`Inks` holds twelve RISO stock colours in colour-wheel order:

| Slot | Ink              | Hex       | Slot | Ink          | Hex       |
|------|------------------|-----------|------|--------------|-----------|
| 0    | Fluorescent Pink | `#FF48B0` | 6    | Aqua         | `#5EC8E5` |
| 1    | Red              | `#FF665E` | 7    | Blue         | `#0078BF` |
| 2    | Orange           | `#FF6C2F` | 8    | Federal Blue | `#3D5588` |
| 3    | Yellow           | `#FFE800` | 9    | Purple       | `#765BA7` |
| 4    | Green            | `#00A95C` | 10   | Burgundy     | `#914E72` |
| 5    | Teal             | `#00838A` | 11   | Black        | `#383226` |

`Press.inks` is by default `Inks.all` mapped through `risoInkForSlot`. The **slot index** fixes each drum's character.

`Press.slotOf(color)` finds the drum for a colour. It tries an exact ARGB match first, which matters because the fluorescents sit too close to their neighbours in density for a fit to pick them. Failing that, it takes the drum with the smallest squared distance in optical density, `‖D(ink) − D(color)‖²`. A colour that no drum carries therefore still prints, on the nearest drum.

### 5.2 Registration, screen angle and phase

For slot `k` (`risoInkForSlot`, `buildDrums`):
```
registration = 3dp · (cos 2.4k, sin 2.4k)       2.4 rad ≈ 137.5°, the golden angle
screenAngle  = (15° + 37.5°·k) mod 90°
phase        = press.seed + 13.7·k              (offsets mottle and grain noise per drum)
```
- **Registration:** every drum lands exactly 3 dp off register. Stepping by the golden angle keeps successive drums pointing in well-separated directions.
- **Screen angle:** reducing mod 90° is valid because the dot field (§5.6) has 90° rotational symmetry.
- **The pass translation** is `origin + registration · offsetScale · density` px. `offsetScale = 0` prints in register, and `Button` animates it from 2 to 0 while pressed.

### 5.3 Colour maths

All of the maths works in **optical density**, `D = −ln T`, where `T ∈ [MIN_TRANSMITTANCE, 1]` per channel and `MIN_TRANSMITTANCE = 0.02`. The CPU separation, the shader and `risoOverprint` all floor at this same value. If they disagreed, the darkest colours would separate into more ink than the drums can lay down.

**Authoring (Beer–Lambert).** `risoOverprint(P, [(I₁,c₁)…])` returns `P · ∏ Iᵢ^cᵢ` per channel, and `onRisoPaper` is the one-ink case. Densities add as ink stacks, so a colour authored this way has
```
D(color / P) = Σ cᵢ · D(Iᵢ)
```

**Separation** (`separationRows`, solved on the CPU once per drum set). Coverages are recovered by least squares in density space. Let `A` be the 3×n matrix whose columns are `D(Iᵢ)`. Then
```
R = (AᵀA + λI)⁻¹ Aᵀ,    λ = 10⁻³ · tr(AᵀA) / n
coverageᵢ = Rᵢ · D(pixel / P)
```

`P` is the stock the pass prints onto: the nearest enclosing sheet's `separationColor` (§4.5), or `RisoTheme.colors.paper` outside any sheet or on a sheet that paints nothing.
- The ridge term `λ` stops near-collinear inks (two blues, say) from blowing up the inverse. The coverage is shared between them instead.
- For a well-separated palette, the round-trip error stays within about 0.5%.
- The inverse is Gauss–Jordan with partial pivoting. If the matrix is singular, every row is zero and nothing prints.
- The fit is at most 3×3, which is why `MAX_DRUMS = 3`. With three independent inks, the system is square and exact.
- **Two names that resolve to the same slot are merged** (`distinct()`). Otherwise one drum would print twice with its coverage split across two rows.

**Printing is not Beer–Lambert.** Each pass outputs `mix(1, I, c)`, a linear area mix (Murray–Davies). It does not output `I^c`. With the screen on, `c` becomes binary dots whose area fraction ≈ `c`, so the averaged colour of a tint is `1 − c(1 − I)`. That is what a real halftone does, and it is lighter than `I^c`. A colour authored with `risoOverprint` therefore **separates exactly back onto its coverages, but prints as a halftone of them**. Only solids (`c = 1`) and overprints of solids reproduce the authored colour.

### 5.4 `RisoPassNode`: one composable's run through the press

`risoInk(…)` becomes `RisoInkElement(inks, offsetScale, RisoTheme.press, RisoTheme.colors.paper)`, which becomes `RisoPassNode`. The node is a `DrawModifierNode`, a `TraversableNode` (key `RisoPassKey`) and a `GlobalPositionAwareModifierNode`.

**Attaching.** In `onAttach` the node looks up two ancestors with `traverseAncestors`: the nearest pass above it (`RisoPassKey`, §5.5) and the nearest sheet (`RisoSheetKey`). It registers with the sheet via `addDependent`, so the sheet can redraw it when its stock changes or its tiles land.

**Drawing:**
1. **Record.** `recordContent()` draws the subtree once into the `content` `GraphicsLayer`. It must use the `DrawScope` overload of `record`, because only that one retargets `drawContent()` into the layer. The node never draws its content straight onto the canvas.
2. **Resolve drums.** `resolveDrums()` caches `Drum(ink, row, registration, screenAngle, phase)` for the first three inks, deduplicated by slot. The cache is cleared when `inks` or `press` changes.
3. **Lay down.** In `layDown`, each drum `i` gets a pooled pass layer:
   - It is recorded as `drawLayer(content)` followed by `punchKnockouts(i)`.
   - `setRectOutline(visibleArtwork)` with `clip = true`. The rect is named explicitly because an implicit clip is resolved once against the layer's bounds at that time. On Skia, that left a pass clipped to nothing, or a pass that stayed at its old width after a resize.
   - `translationX/Y = origin + slip`. The translation is carried **on the layer**, because a non-SrcOver blend forces the offscreen path, which drops the canvas transform.
   - `blendMode = Multiply` and `compositingStrategy = Offscreen`. The punches need a buffer of their own.
   - `renderEffect = InkPass.effect(drum.spec(…))`. The spec carries the sheet's stock (for separation), its `SheetSurface` (for the warp), and `sheetOffset = positionInRoot + slip − sheet.positionInRoot`: where this pass's origin lands on the sheet, after the drum's throw.
4. **Nested passes** are then laid down on top: `below.forEach { it.layDown(scope, origin + originIn(this), host) }`.

**Clipping to the artwork keeps Multiply cheap.** An unclipped offscreen buffer would be sized to the whole destination, and the pass shader would run over the full screen once per drum.

### 5.5 Nesting and knockouts

**The innermost pass wins.**
- In `onAttach`, a node finds its nearest ancestor pass with `traverseAncestors(RisoPassKey)` and registers in that pass's `below` list. Nesting is decided **by the tree, not by draw order**. Draw order breaks as soon as a child re-records on its own.
- A nested node's `draw()` only records its content, so the ancestor's recording has a hole where the child was. If the ancestor isn't currently recording, the child also calls `above.invalidateDraw()`. That can't loop, because on the next frame the child is drawn inside the ancestor's recording, which asks for nothing.
- The ancestor lays down every registered child after its own drums, in its own scope, at the child's offset. The hole is in the *artwork*, not the sheet: whatever the outer pass draws behind the inner one still prints there, so the result is an overprint.

**Visible artwork.** `visibleArtwork(host)` is the child's bounds as seen by `host`, taken with `localBoundingBoxOf(…, clipBounds = true)` and intersected with the artwork:
- Scrollers and clips *between* the child and the host come back into force this way. Without that, a label scrolled out of a `LazyRow` would still print on the next pane.
- It is measured against the host, not the root. Clips above the host are already on its canvas and replay correctly when it scrolls, whereas a root-relative rect would freeze at whatever was visible when it was drawn.
- The clip is cut before the drum's throw, so a pass still bleeds past a scroller by exactly its registration error.

**Knockouts** are passes with no inks.
- The parent's `cutKnockouts(i, slip)` records the child's artwork into a per-drum punch layer (`BlendMode.DstOut`, Offscreen, clipped to the child's visible artwork), translated by `at − slip`, which cancels the pass layer's own translation and so pins the hole to the sheet: one hole for every drum, rather than one per drum with inked bands between them. The punches stay one per drum even though they all land in the same place, because each cancels a different `slip`.
- Because the pass layer itself moves by `slip`, the punch ends up at `at` on the page — the knockout's own place, for every drum. `SheetRenderTest.aKnockoutCutsOneHoleForEveryDrum` pins this: a hole that rode its drum leaves a band that one drum's hole covered and the other's did not, which comes back as single-drum ink where the stock should be.
- The hole is cut from the knockout's **own artwork's alpha**, so a knockout with nothing drawn in it cuts nothing.
- There is **one punch per drum**. A layer's translation is read when the display list replays, not when it is recorded, so a single shared punch would carry the last drum's offset into every pass.

### 5.6 The ink shader: `INK_PASS_SKSL`

There is one drum per shader invocation, with no loops over inks. `u_image` is the pass layer: the recorded content plus the punches. The source is `SHEET_SURFACE_SKSL` (§4.3) followed by the pass itself.

```
read     = u_warp > 0 ? clamp(p + u_warp · normalImage(p + u_sheetOffset), 0, u_imageSize) : p
src      = u_image.eval(read);   sheet = p + u_origin         (page coordinates)
rgb      = src.a > 0.001 ? src.rgb / src.a : 1                  (unpremultiply; empty → paper)
D        = max(−ln(clamp(rgb / u_paper, tmin, 1)) − u_paperFloor, 0)
c        = clamp(u_row · D, 0, 1) · src.a
if c > FAINT_COVERAGE (0.012):
    c = c^(1 / (1 + spread))                                    ink gain (dot growth)
    c = screenDots(c, sheet)                                    halftone
    c = inkTexture(c, sheet)                                    mottle, then grain
T        = mix(1, u_ink, c)                                     printed transmittance
a        = round₈(1 − min T)                                    rounded to the buffer first
out      = (T − (1 − a), a)                                     premultiplied
```

The steps, in order:
- **Warp.** The artwork is read displaced by the sheet's surface at the point where the ink lands, the same `sheetSurface` the stock under it is shaded by. `u_warp = WARP_DP · density` (8 dp per unit of displacement) when the sheet's surface is ready, else 0. The default stock moves ink by at most 0.53 dp. The read is clamped to the recorded artwork, and pass clips are not grown, so a sub-dp displacement at a node's own edge is cut off, which is invisible.
- **Paper floor.** `u_paperFloor = −ln(1 − tolerance)`, with `tolerance` clamped to `0..0.5`; the default is 0.01. It is *subtracted*, not thresholded, so ink fades in instead of switching on. Artwork drawn in the stock colour, and antialiased edges against it, come out unprinted.
- **Faint cutoff.** Coverage of 1.2% or less moves a channel by about 2/255, which is below the grain. It is laid flat rather than screened, which avoids popping as a colour crosses the threshold.
- **Halftone.** `q = rotate(sheet, angle)·π/dotSize` and `field = ½ − ½·cos qₓ·cos q_y`. The dots grow from the field's minima at `(m·dotSize, n·dotSize)` with `m + n` even: a checkerboard lattice with nearest-neighbour spacing `√2·dotSize` px. The threshold is softened over `w = clamp(2/dotSize, 0.06, 0.45)`, and coverage is stretched by `(1 + 2w)` so that `c = 1` fills solid. `u_screen` blends between continuous tone and dots. Because each drum screens at its own angle, overprinted tints sit side by side and mix additively, which is why pink over blue reads purple rather than navy.
- **Texture.** `c ·= 1 − mottle·(1 − fbm(sheet/mottleSize + phase))`, then `c ·= 1 − grain·hash(⌊sheet/grainSize⌋ + phase)`. Both are applied after the screen so the grain mottles solids instead of punching holes in the dots.
- **Page anchoring.** `u_origin` is the pass's `positionInRoot()`, so the screen and mottle belong to the page. Adjacent components share one dot grid, and two identical buttons don't carry identical blotches.
- **Premultiplied output.** Where a drum laid no ink, `T = 1`, so the pass comes out clear: the destination is left untouched and there is no seam where the artwork stops. Over the stock this is exactly the old opaque encoding — `dst·(1 − a) + rgb·dst = dst·T` for any `a`, because `rgb` and `1 − a` are built to add back up to `T`. What the alpha buys is the case where the pass *doesn't* land on the stock; see §6.
- **Rounding.** `a` is rounded to the 8-bit buffer before `rgb` is measured from it, so the two agree at the value actually stored. Taken from the unrounded minimum they disagree by half a level each, and the print drifts by up to 3/255 over screened and mottled edges.

**Uniform coercions** (in `setInkPass`):
- `screen`, `mottle`, `grain` and `spread` are clamped to `0..1`.
- `dotSize` is at least 1.5 px, `mottleSize` at least 1 px and `grainSize` at least 1 px, all after dp → px conversion.
- **Every uniform is written on every call.** Skiko rejects a builder with any uniform unset.

### 5.7 `InkPass` actuals

`expect class InkPass` holds one compiled shader per pass layer, and so per drum index per node:
- **Android:** `RuntimeShader` and `RenderEffect.createRuntimeShaderEffect(shader, "u_image")`.
- **Skia:** `RuntimeShaderBuilder` and `ImageFilter.makeRuntimeShader(builder, "u_image", null)`. The null input means the layer itself.
- Both bind the sheet's tiles as the `u_fineTile` and `u_coarseTile` children, or `PlaceholderTile` when there are none. A shader will not run with a child unbound, and the placeholder is never read, because the surface is gated off.

Both return the held effect when `spec == held`. `InkPassSpec` is a data class, but `row: FloatArray` compares **by identity**, which is safe because rows are built once per `Drum` and never mutated. Once the drums are resolved, only `origin` and `sheetOffset` change between frames, and only when the pass moves. A pass that is standing still reuses its effect.

---

## 6. How ink meets paper

The interface is explicit, and one-directional:

1. The sheet paints `S`, with opacity `σ`, over its bounds.
2. Content draws on top. Un-inked content is plain source-over, drawn as authored.
3. Each pass draws `mix(1, Iᵢ, cᵢ)` premultiplied (§5.6) over its clipped artwork rect with Multiply. Skia's Multiply is `src·(1 − da) + dst·(1 − sa) + src·dst`, so over an opaque stock the result is `T·(1 − σ) + T·S`, the same as an opaque pass would give. Stacked passes give `T = ∏ᵢ (1 − cᵢ(1 − Iᵢ))`.

That is exactly the old sheet shader's composite for inked pixels, `S·(1 − c + c·T) + T·c·(1 − σ)` at `c = 1`. So moving the stock under the content changed nothing about how ink prints, which `SheetRenderTest` pins:
- **Over an opaque stock** the ink multiplies the paper: `S·T`.
- **Over `RisoPaper.None`** (`σ = 0`) the pass lands on whatever is below and leaves its own transmittance `T`, now carrying alpha, so the areas no drum reached are clear rather than white. Stamp's adaptive foreground is built around the old opaque square and its `drawPacerMask` cut-back; it still works, but its exports change.
- **A knockout** removes the pass's alpha (`DstOut`), so the stock shows through unchanged.

**When the pass does not land on the stock.** Anything between a pass and its sheet that draws into a layer of its own — a list stretching at its end, a fade, a predictive-back transition, an explicit `CompositingStrategy.Offscreen` — hands the Multiply an empty buffer, where `da = 0` reduces it to `src`. An opaque pass was simply painted there, so the white a drum hands back where it laid no ink came off as a white fill over the page; that is what made Pacer's cards go white under overscroll. A premultiplied pass instead lands in that layer as ink and clear, and reaches the stock when the layer does.

That reconstruction, `S·(1 − a) + rgb`, cannot be exact for a coloured ink: keeping the Multiply exact forces `rgb = T − 1 + a`, and being exact through a layer as well would need `a = 1 − T` per channel, which one alpha cannot express. Taking the least `a` that keeps `rgb ≥ 0` minimises the shortfall on every channel at once. It is nil for a neutral ink — which is what text and rules print with — about a level for `vintageBlack`, and up to ~20/255 on the strongest channel of a saturated ink, visible only as a slight lightening while a stretch is held. `SheetRenderTest.inkInsideAnIsolatedLayerStillLeavesBarePaper` pins this.

**Worked pixel.** Take the default stock `P = #EFEBE1` and `risoInk(purple)` over a fill drawn in `purple.onRisoPaper()` (`= P·I`), with no mottle or grain:
- `D = D(P·I / P) − floor = D(I) − floor`. The separation row gives `c ≈ 0.99`, and the screen rounds it to a solid dot. The pass outputs `I`.
- Multiplied onto the stock, that gives `P · I`, the authored colour (`solidInkOnTheDefaultStockPrintsAsAuthored`, within 2 levels).
- Drawing the raw ink `purple` instead separates against `I/P`, which is lighter, so it prints at slightly less than full coverage.

**What is not ink.** A sheet inside a `risoInk` is artwork of that pass, so its stock is separated and printed like any other fill. Put sheets outside the passes that print on them.

---

## 7. Performance model

### 7.1 Per-frame cost

- **Paper:** one `drawRect` with the stock shader over the sheet: two tile reads and a few multiplies per pixel. There is no offscreen buffer, and content changes don't re-run it beyond the invalidated region. It is the same on every platform, including the web.
- **Ink:** for each ink node, `min(3, distinct drums)` offscreen buffers, each clipped to that node's visible artwork. Per pixel of each, that is two tile reads and the surface arithmetic for the warp, one content read, a `log` and a dot product, and the screen and texture work when `c > 0.012`.

### 7.2 One-off cost

- **Tiles:** usually none, because the default stock's tiles ship and load in a few milliseconds. A custom surface bakes once per shape and density: milliseconds on the GPU (Android, iOS), up to about a second on a raster (JVM, web), and after that it comes from disk (not on the web).
- **Shipped size:** about 1.7 MB of PNG (the 3× fine tile is ~1 MB).
- **Memory:** a decoded fine tile is `(256·bucket)²·4` bytes, 2.4 MB at 3×. The LRU holds up to 8 tiles.
- **Shader compile:** the stock shader once per sheet node, the bake shaders once per process on Skia (per bake on Android), and ink shaders once per pass layer.

### 7.3 Frame-to-frame reuse

| Object                     | Rebuilt when                                                                   |
|----------------------------|--------------------------------------------------------------------------------|
| Stock shader               | `(SheetSurface, density)` changes                                              |
| `InkPass` effect           | `InkPassSpec` changes: in steady state, only when the node moves on the page   |
| `Drum` list and separation | `inks` or `press` changes                                                      |
| Pass, punch, content layers | pooled per node; released in `onDetach`                                       |

### 7.4 Default effects per platform

Pacer defaults effects **on** for Android and iOS and **off** for desktop JVM and wasm (`RisoEffectsEnabledDefault` in `pacer/core/settings/impl/src/*/SettingsDefaults.*.kt`, specified in [openspec/specs/pacer-settings/spec.md](../../openspec/specs/pacer-settings/spec.md)). The paper no longer costs those platforms more than it costs mobile, so the defaults can be revisited on their own.

---

## 8. Invariants

Check these before changing anything in `risograph/`:

1. **Shaders are dual-dialect.** Every shader must compile under both Android `RuntimeShader` and Skia `RuntimeEffect` (`everyShaderCompiles` checks Skia).
2. **Every declared uniform is set on every `set*` call, and every child is bound.** Skia rejects unset uniforms. Bind `PlaceholderTile` when there is no tile.
3. **A uniform is declared exactly once per assembled shader.** `SHEET_SURFACE_SKSL` owns the surface uniforms, and anything it is prepended to must not redeclare them.
4. **The stock and the ink read the surface through `sheetSurface`,** never through their own copies.
5. **`MIN_TRANSMITTANCE` is shared** by `separationRows`, `setInkPass` and `risoOverprint`/`onRisoPaper`.
6. **Ink pass output is premultiplied, and `rgb` plus `1 − a` add back up to the transmittance.** That identity is what keeps the Multiply exact on the stock; the alpha is what keeps the pass honest when something isolates it into a layer of its own. Derive `rgb` from the *rounded* alpha, never the other way round.
7. **Nothing un-inked is touched by the paper.** The sheet paints behind its content and never reads it.
8. **Anything that changes a tile's pixels bumps `SURFACE_VERSION` and runs `./gradlew :design:riso:bakeTiles`.** `ShippedTilesTest` fails until the shipped tiles match a fresh bake. Old versions' disk-cache folders are simply never read again.
9. **Tiles are CPU-backed** (read back after the bake) and belong to no GPU context. Never CPU-raster the bake on Android or iOS.
10. **A tile is a pure function of its `TileKey`.** The lock-free cache relies on two racing loads producing interchangeable results.
11. **Waiting on tiles goes through `Tile.await()` on a main-thread scope,** never through snapshot state written by the loader (§4.7).
12. **Pass and punch translation live on the layer,** not on the canvas, and every pass gets an explicit rect outline.
13. **Nesting and sheets are found in `onAttach`** through the tree, never inferred from draw order.
14. **`effectsEnabled = false` creates no shaders, layers or tile requests** for any entry point.
15. **`design/riso` depends on nothing else in the repo.**

---

## 9. Verification and tooling

- `./gradlew :design:riso:jvmTest` runs:
  - `PaperSurfaceTest`: shaders compile; tiles repeat; decoded fields match; the encoding fits; the warp stays ≤ 1 dp; keys and buckets; the separation colour.
  - `SheetRenderTest`: ink prints as authored; no halo; artwork in the stock colour prints nothing; un-inked content is untouched; an empty sheet shows its stock; no sheet with effects off.
  - `TileLoadingTest`: bake → disk → disk; the default stock ships.
  - `ShippedTilesTest`: the shipped tiles match a fresh bake.
  - `LruCacheTest`.
- `./gradlew :design:riso:bakeTiles` regenerates the shipped tiles.
- `./gradlew :design:risoRecorder:snapshots` renders a fixed test page (type, a button, solid and overprinted inks, un-inked content, a pink sheet, a textured sheet, a card) at phone, desktop and 4K sizes, plus a full-screen textured sheet, into `design/risoRecorder/build/snapshots`. Run it on two commits to compare.
- `:design:risoDemo` (Android) hosts `RisoPrintDemo`, and `:design:risoRecorder:run` regenerates `design/riso/docs/*.webp`.
- The paper path is now the same on every platform, but the bake surface and the shader compilers differ, so check a visual change on Android or iOS **and** on a Skia raster target (JVM or wasm).

---

## 10. Known discrepancies

Places where the code, its comments or its results don't quite agree. Fix one, then remove its row.

| Where | Says / does | Actually |
|---|---|---|
| `RisoInk.screenAngle` KDoc | "Keep passes ~30 degrees apart" | the slot formula steps by 37.5° mod 90°, so some pairs land 15° apart (slots 0 and 2, pink and orange: 15° and 0°) |
| Fine tile | co-prime periods were meant to hide the repeat | at the default `fade` the repeat is nearly exact every 256 dp. It is invisible at the default contrast, but not guaranteed for high-contrast custom stocks (§4.2) |
| `androidMain/…/PaperPlatform.android.kt`, `bakeTile` | compiles a `RuntimeShader` and creates a `HardwareRenderer` per bake | Skia holds the bake effects as lazy singletons. It is harmless given how rarely Android bakes, but it is inconsistent |
