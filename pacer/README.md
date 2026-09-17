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
- **Live results:** the computed value updates while you drag, not only when you let go.
- **Kilometers or miles:** switching units doesn't change the run itself.
- **Out-of-range values:** a value beyond the end of its ruler is kept exactly and marked with `>` or `<`.
- **Shareable links:** the URL tracks the current run, for example
  `https://pacer.alexgabor.com/?distance=21.10&pace=5:00&metric=time&unit=kilometers`. Opening
  the link restores that run. Android also handles `pacer://` links.