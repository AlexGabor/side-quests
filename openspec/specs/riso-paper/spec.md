# riso-paper Specification

## Purpose
Defines how the Riso paper renders beneath an app's content and how ink printed on it behaves: what the stock looks like, what is and is not printed, how coloured and nested papers work, and what holds on every platform.

## Requirements

### Requirement: Paper renders beneath content
A composable with the Riso paper applied SHALL show the paper's stock colour and surface behind its content. Content SHALL be drawn over the paper, not under it.

#### Scenario: Empty paper
- **WHEN** the paper is applied to a screen that draws nothing else
- **THEN** the screen shows the stock colour with its surface texture across its full bounds

#### Scenario: Transparent stock
- **WHEN** the paper is applied with no stock (`RisoPaper.None`)
- **THEN** nothing is painted behind the content

### Requirement: Only inked content is printed
Content inside a paper that is not printed with Riso ink SHALL appear exactly as authored. It SHALL NOT be tinted by the stock, shaded by the surface or displaced by it. No opt-out modifier SHALL be needed for images or other plain content.

#### Scenario: Photo on paper
- **WHEN** an image is drawn inside a paper without Riso ink
- **THEN** its pixels appear unchanged, in their own colours and position

#### Scenario: Plain fill on paper
- **WHEN** a solid-colour background is drawn inside a paper without Riso ink
- **THEN** it shows its authored colour, not that colour multiplied by the stock

### Requirement: Ink prints onto the stock
Content printed with Riso ink inside a paper SHALL appear as the stock seen through the ink: the stock colour multiplied by the ink's transmittance, per channel. Areas a drum lays no ink on SHALL show the stock unchanged, with no seam at the edge of the inked content.

#### Scenario: Solid ink on default stock
- **WHEN** a region is filled with an ink's printed colour (`ink.onRisoPaper()`) and printed on that ink's drum on the default stock, at a solid screen cell
- **THEN** the result matches `ink.onRisoPaper()` within 2/255 per channel

#### Scenario: Edge of inked content
- **WHEN** an inked composable's bounds end on bare paper
- **THEN** no visible line, halo or colour step appears at those bounds

### Requirement: Ink separates against its own sheet
Ink SHALL decide how much of each drum to lay down by comparing the artwork against the stock colour of the nearest enclosing paper. Ink outside any paper SHALL compare against the theme's paper colour.

#### Scenario: Artwork in the stock colour
- **WHEN** artwork drawn in a coloured paper's own stock colour is printed with Riso ink on that paper
- **THEN** no ink is laid down for it

#### Scenario: Ink outside a paper
- **WHEN** Riso ink is used with no enclosing paper
- **THEN** it separates against the theme's paper colour

### Requirement: Ink prints the same through an isolating layer
An ink pass SHALL print the same result whether it composites directly onto its sheet or into a layer an ancestor put between the two. Content that an ancestor draws into a layer of its own — a scroll container stretching at its end, a fade, a predictive-back transition, any explicitly offscreen layer — SHALL NOT lose its stock or gain a flat fill where no ink was laid.

#### Scenario: Overscroll stretch
- **WHEN** a list printed with Riso ink is pulled past its end on Android and the stretch is held
- **THEN** the content keeps its stock behind it, with nothing filled in where no drum reached
- **AND** it prints as it did before the stretch began

#### Scenario: Isolated layer around inked content
- **WHEN** inked content is drawn inside a layer that composites offscreen
- **THEN** bare paper still shows through wherever no ink was laid
- **AND** the inked artwork prints its ink on that stock

### Requirement: A pass leaves nothing where no ink was laid
Where a drum lays no ink, a pass SHALL leave what is behind it untouched rather than covering it. On a sheet that paints no stock (`RisoPaper.None`), what comes off the press SHALL therefore be the inks alone, with the areas no drum reached left clear for whatever the print is composited over.

#### Scenario: Print on an unpainted sheet
- **WHEN** artwork is printed with Riso ink on `RisoPaper.None` and the result is composited over a background
- **THEN** the background shows wherever no drum reached
- **AND** the ink prints over it where the drums did reach

### Requirement: A knockout cuts one hole for every drum
A knockout SHALL take ink back off the pass enclosing it, so the region it covers shows the bare stock. The hole SHALL land at the same place on the sheet for every drum, however far off register each drum lands, so reversed-out artwork is not doubled. Riso ink used inside a knockout SHALL print on that bare stock.

#### Scenario: Reversed-out type on a filled shape
- **WHEN** a knockout is placed inside a shape printed on two or more drums
- **THEN** the region it covers shows the stock, as though nothing were printed there
- **AND** the hole's edges are where the knockout was laid out, not offset per drum
- **AND** the surrounding artwork still prints on every drum

#### Scenario: Ink inside a knockout
- **WHEN** content printed with Riso ink is placed inside a knockout
- **THEN** it prints on the bare stock the knockout exposed, rather than on the enclosing pass's ink

### Requirement: Surface warps inked artwork
The paper's surface SHALL displace inked artwork by a small amount that varies with the surface: at most 1dp with default surface parameters, and the same physical amount at any layout size. It SHALL leave un-inked content in place. A stock with no roughness and no fiber SHALL displace nothing.

#### Scenario: Flat stock
- **WHEN** ink is printed on a paper with roughness 0 and fiber 0
- **THEN** the inked artwork lands exactly where it was drawn, apart from the drum's registration offset

### Requirement: Surface scale is independent of layout size
The paper surface SHALL have a fixed physical scale in density-independent pixels. Resizing a paper SHALL reveal more or less of the surface, never stretch it. Resizing SHALL NOT cause the surface to be regenerated.

#### Scenario: Window resize on desktop
- **WHEN** a desktop window showing a paper is resized
- **THEN** the grain keeps its size throughout the resize and is never stretched
- **AND** frames keep up with the resize

### Requirement: Coloured papers
Any stock colour SHALL be usable. Changing a paper's colours, or the strength of its contrast, roughness, fiber or fade, SHALL take effect on the next frame without regenerating the surface.

#### Scenario: Animating the stock colour
- **WHEN** a paper's stock colour is animated from cream to pink
- **THEN** every frame shows the intermediate colour with the same surface texture
- **AND** no frame shows a missing or stretched surface

### Requirement: Nested papers
A paper inside another paper SHALL paint its own stock over the outer one within its bounds. Ink inside it SHALL print onto, and separate against, the inner stock.

#### Scenario: Pink card on cream page
- **WHEN** a card applies a pink paper inside a screen-level cream paper, and prints text with Riso ink
- **THEN** the card shows pink stock and the text prints onto pink
- **AND** outside the card, the cream stock is unchanged

### Requirement: Surface appears without flashing
Until a paper's surface texture is available, the paper SHALL show its stock as a flat colour of the same average brightness. When the surface arrives, it SHALL replace the flat stock without a change in overall colour and without a blank frame.

#### Scenario: First frame with a custom surface
- **WHEN** a paper with a non-default surface is shown for the first time in the process
- **THEN** the first frame shows the flat stock colour
- **AND** the surface texture appears later without the stock's colour shifting

#### Scenario: Default stock
- **WHEN** a paper with the default surface is shown at a standard screen density
- **THEN** the surface texture is available without generating it at runtime

### Requirement: Surface readiness can be awaited
Code that captures single frames SHALL be able to tell whether a stock's surface is ready at the current density. It SHALL become ready once the surface has landed, or once loading has given up (the stock then stays flat). It SHALL always be ready for a stock with no relief and with effects disabled.

#### Scenario: Exporting an icon
- **WHEN** a tool captures a frame of a paper whose surface is still loading
- **THEN** it can wait until the surface is ready, and the capture shows the surface

### Requirement: Consistent across platforms
The paper SHALL look the same on Android, iOS, desktop (JVM) and web (wasmJs) for the same stock, surface parameters and density, up to 8-bit rounding. No platform SHALL recompute the surface on every frame.

#### Scenario: Same stock on web and Android
- **WHEN** the default paper is rendered at the same density on web and on Android
- **THEN** the surfaces match up to 8-bit rounding

### Requirement: Effects disabled
With Riso effects disabled, the paper SHALL paint only its flat stock colour (nothing for `RisoPaper.None`), and ink SHALL draw content in its authored colours. No surface SHALL be generated or loaded.

#### Scenario: Effects off
- **WHEN** Riso effects are turned off
- **THEN** screens show the flat stock colour with content in its own colours and in register
