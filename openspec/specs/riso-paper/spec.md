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

### Requirement: Ink fades by laying down less of it
Inked content SHALL be able to fade by coverage rather than by transparency. A fade of `a` applied to a region SHALL make every pass inside it lay down the ink it would have laid at `a` times the coverage, as though the artwork itself had been drawn that much fainter. The ink SHALL keep its full strength where it lands, so a screened region prints smaller dots rather than paler ones, and the stock between them SHALL be the stock, not a wash of ink over it. A fade of `1` SHALL print exactly what no fade prints; a fade of `0` SHALL lay down no ink at all. Values outside `0..1` SHALL be clamped.

#### Scenario: Halfway through a fade
- **WHEN** screened artwork is printed at a fade of `0.5`
- **THEN** its dots are smaller than at full strength
- **AND** the ink within a dot is as dark as it is at full strength
- **AND** the paper between the dots shows the stock unchanged

#### Scenario: Faded to nothing
- **WHEN** inked content is printed at a fade of `0`
- **THEN** nothing is laid down for it, and the sheet shows what it would show with the content absent

#### Scenario: Fade of one
- **WHEN** inked content is printed at a fade of `1`
- **THEN** the result matches the same content printed with no fade at all

### Requirement: A fade reaches every pass beneath it
A fade SHALL apply to all inked content inside the region it is applied to, including passes nested within other passes, and SHALL NOT affect anything outside that region. Nested fades SHALL multiply, so a region at `0.5` inside a region at `0.5` prints at `0.25`. A knockout inside a fading region SHALL keep cutting its hole in full: what thins is the ink laid down, not the frisket that holds it off the sheet.

#### Scenario: A component that owns its own ink
- **WHEN** a fade is applied around a component that prints on drums of its own
- **THEN** that component's ink fades with it

#### Scenario: Nested fades
- **WHEN** a region at a fade of `0.5` contains a region at a fade of `0.5`
- **THEN** the inner region prints at a coverage of `0.25`

#### Scenario: Neighbour outside the fade
- **WHEN** a fade is applied to one composable
- **THEN** inked content beside it prints unchanged

#### Scenario: Reversed-out artwork while fading
- **WHEN** a pass containing a knockout is printed at a partial fade
- **THEN** the knocked-out region shows bare stock, not partly inked stock
- **AND** the ink around it is thinner than at full strength

### Requirement: Content the press does not print can take the fade as transparency
A fade thins ink, and content that is not printed with Riso ink has no ink to thin — an image or a plain fill inside a fading region SHALL therefore be unaffected by the fade unless it asks for it. Such content SHALL be able to take the enclosing fade as plain transparency instead, so that a region holding both inked and un-inked content can disappear as one. Content that asks for it outside any fade SHALL be drawn unchanged. Inked content SHALL NOT ask for it: the fade would then apply twice, once as coverage and once as transparency.

With effects disabled a fade is already plain transparency over everything inside it, so asking for it SHALL do nothing rather than fade that content a second time.

#### Scenario: Photo in a fading region
- **WHEN** an image inside a fading region takes the fade as transparency
- **THEN** it is drawn at that alpha, reaching nothing when the fade reaches nothing

#### Scenario: Photo that does not ask
- **WHEN** an image inside a fading region does not take the fade
- **THEN** it is drawn at full strength however far the fade has gone

#### Scenario: Asking outside any fade
- **WHEN** content takes the fade as transparency with no enclosing fade
- **THEN** it is drawn unchanged

#### Scenario: Effects off
- **WHEN** Riso effects are turned off and content inside a fading region takes the fade as transparency
- **THEN** it is drawn at the fade's alpha once, not twice

### Requirement: Content fades in and out of the layout
The design system SHALL offer showing and hiding content as that dissolve. Content being shown SHALL fade in from no ink; content being hidden SHALL fade out to none. While either is in progress the content SHALL stay composed and keep its place in the layout, so nothing jumps part-way through. Once hidden, it SHALL take up no space and SHALL NOT be composed. Content on its way out SHALL NOT respond to pointer input, so a control that is nearly gone cannot be activated.

#### Scenario: Hiding
- **WHEN** content shown this way is hidden
- **THEN** its ink thins away to nothing
- **AND** its space in the layout is released only once the dissolve has finished

#### Scenario: Showing
- **WHEN** hidden content is shown
- **THEN** it takes its place in the layout and its ink comes up from nothing to full strength

#### Scenario: Tapping content that is leaving
- **WHEN** a button inside content that is fading out is tapped
- **THEN** nothing is activated

#### Scenario: Settled out of sight
- **WHEN** the fade out has finished
- **THEN** the content occupies no space and is not composed

### Requirement: Fading with effects disabled
With Riso effects disabled there is no coverage to thin, so a fade SHALL instead show the content at that alpha in its authored colours, and hiding SHALL still end with the content gone from the layout.

#### Scenario: Effects off, halfway through
- **WHEN** Riso effects are turned off and content is shown at a fade of `0.5`
- **THEN** it is drawn in its own colours at half opacity

#### Scenario: Effects off, hidden
- **WHEN** Riso effects are turned off and content shown this way is hidden
- **THEN** it ends up absent from the layout, as it does with effects on

### Requirement: Fades linger around half ink
The design system SHALL offer a crossfade easing shared by its crossfades that runs fast, slows through half ink, and runs fast again, so the half-dotted print in between stays on screen long enough to be seen. The ink SHALL keep moving throughout: neither side SHALL hold still at any value between its start and its end. Content fading out SHALL run from full ink to none over 0–175 ms, passing half ink at about 88 ms. Content fading in SHALL lay no ink until 100 ms, then run to full ink by 275 ms, passing half ink at about 188 ms. A fade that is interrupted SHALL continue from the ink it had reached, not jump back to its starting value.

#### Scenario: Lingering through the middle
- **WHEN** content fading out passes half ink
- **THEN** its ink changes more slowly than it did at the start of the fade
- **AND** it is still changing

#### Scenario: Never still
- **WHEN** the ink of either side is sampled at any two moments between its start and its end
- **THEN** the later sample is further along than the earlier one

#### Scenario: Overlap
- **WHEN** a crossfade is 140 ms in
- **THEN** both the outgoing and the incoming content lay down some ink, and neither is at full strength

#### Scenario: Settled
- **WHEN** a crossfade has run for 275 ms
- **THEN** the incoming content prints at full ink and the outgoing content lays down none

### Requirement: Content crossfades as a dissolve
The design system SHALL offer swapping one piece of content for another as a dissolve, with the outgoing content's ink thinning away and the incoming content's ink coming up, both following the crossfade easing. Both SHALL be composed and stacked in the same place while the crossfade runs. The outgoing content SHALL NOT respond to pointer input. Once the crossfade settles, only the current content SHALL be composed. Content shown on first composition SHALL appear at full ink, without fading in. Returning to content that is still fading out SHALL bring that same content back, not compose a second copy of it.

#### Scenario: Swapping
- **WHEN** the target of a crossfade changes
- **THEN** the old content thins away as the new content prints in, in the same place

#### Scenario: Tapping the outgoing content
- **WHEN** a button in content that is crossfading out is tapped
- **THEN** nothing is activated

#### Scenario: Settled crossfade
- **WHEN** the crossfade has finished
- **THEN** only the current content is composed

#### Scenario: First appearance
- **WHEN** a crossfade is first composed
- **THEN** its content prints at full ink immediately

#### Scenario: Changing back mid-way
- **WHEN** the target changes from A to B and back to A before the crossfade settles
- **THEN** A's content fades back in from the ink it had reached, and only one A is composed

#### Scenario: Effects off
- **WHEN** Riso effects are turned off and content crossfades
- **THEN** each side is drawn in its own colours at the fade's alpha, and the outgoing content still leaves the composition once settled
