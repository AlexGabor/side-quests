# riso-inks Specification

## Purpose
Defines Riso's accent inks and scoped accents: `Inks.accents` and `Inks.named(name)` for choosing and looking up inks, and `RisoInks`, which prints part of the UI with another accent pair that the components inside it pick up.

## Requirements
### Requirement: Accent inks
`Inks` SHALL expose `accents`: every ink in `all` except Black, in the same order. `Inks.named(name)` SHALL return
the ink in `all` with exactly that name, or nothing when there is none.

#### Scenario: Accents leave out Black
- **WHEN** `accents` is read
- **THEN** it holds the eleven inks of `all` other than "Black", in color-wheel order

#### Scenario: Lookup by name
- **WHEN** `named("Teal")` and `named("Nope")` are called
- **THEN** the first returns the teal ink and the second returns nothing

### Requirement: Printing with other accents
`RisoInks(accent, accentVariant, content)` SHALL show `content` with `RisoTheme.colors.accent` and
`RisoTheme.colors.accentVariant` replaced by the given colors. Every other theme value SHALL stay as it was. Riso
components inside it that use the accents SHALL use the given ones.

#### Scenario: Button inside
- **WHEN** a `Button` is shown inside `RisoInks(teal, yellow)`
- **THEN** its fill mixes yellow and teal in place of fluorescent pink and purple

#### Scenario: Outside unchanged
- **WHEN** a `SmallProgress` sits next to, not inside, a `RisoInks` block
- **THEN** it is drawn in the theme's own accent

