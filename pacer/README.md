# Pacer

A running pace calculator built for adjusting values, not typing them.

## Motivation

Popular online pace calculators work like forms: enter two of distance, pace and time, press
calculate, read the third. Trying another value means going back, clearing a field and typing it
again. Pacer shows all three as scrollable rulers. Move one and the computed value updates
immediately.

Try it at [pacer.alexgabor.com](https://pacer.alexgabor.com).

## Features

- **Distance, pace and time:** choose which one is computed; drag the rulers for the other two.
- **Distance presets:** one tap to 5K, 10K, half marathon or marathon — or 5 and 10 miles when
  miles are selected.
- **Live results:** the computed value updates while you drag, not only when you let go.
- **Kilometers or miles:** switching units doesn't change the run itself.
- **Out-of-range values:** a value beyond the end of its ruler is kept exactly and marked with `>` or `<`.
- **Shareable links:** the URL tracks the current run, for example
  `https://pacer.alexgabor.com/?distance=21.10&pace=5:00&metric=time&unit=kilometers`. Opening
  the link restores that run. Android also handles `pacer://` links.
- **Share:** on Android and iOS, the share button sends the three values as the cards show them,
  plus a link that opens the same run.
