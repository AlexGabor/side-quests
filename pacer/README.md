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
- **Distance presets:** one tap to 5K, 10K, half marathon (21.0975 km) or marathon (42.195 km)
  — or 5 and 10 miles when miles are selected.
- **Live results:** the computed value updates while you drag, not only when you let go.
- **Kilometers or miles:** switching units doesn't change the run itself.
- **Precise readout:** the rulers snap to hundredths and whole seconds, but the cards show
  distance to the ten-thousandth and pace and time to the hundredth of a second, without
  trailing zeros.
- **Out-of-range values:** a value beyond the end of its ruler is kept and shown exactly; the ruler
  parks at its end.
- **Shareable links:** the URL tracks the current run, for example
  `https://pacer.alexgabor.com/?distance=21.1&pace=5:00&metric=time&unit=kilometers`. Opening
  the link restores that run. Android also handles `pacer://` links.
- **Share:** on Android and iOS, the share button sends the three values as the cards show them,
  plus a link that opens the same run.
