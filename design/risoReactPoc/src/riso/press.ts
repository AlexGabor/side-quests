// Port of the colour maths in design/riso: palette, drum slots, Beer–Lambert overprint and the
// least-squares separation rows (Separation.kt).

export const PAPER = "#EFEBE1";

export const Inks = {
  fluorescentPink: "#FF48B0",
  red: "#FF665E",
  orange: "#FF6C2F",
  yellow: "#FFE800",
  green: "#00A95C",
  teal: "#00838A",
  aqua: "#5EC8E5",
  blue: "#0078BF",
  federalBlue: "#3D5588",
  purple: "#765BA7",
  burgundy: "#914E72",
  vintageBlack: "#383226",
} as const;

export type InkName = keyof typeof Inks;
const SLOTS = Object.keys(Inks) as InkName[];

export const Press = {
  screen: 1,
  dotSize: 1.5, // css px ≈ dp
  mottle: 0.22,
  mottleSize: 8,
  grain: 0.12,
  grainSize: 0.75,
  spread: 0.15,
  tolerance: 0.01,
  // Debug switches for taking the filter apart; not part of Compose's Press.
  warp: 1,
  subpixel: 1,
};

export const MIN_TRANSMITTANCE = 0.02;
export const MAX_DENSITY = -Math.log(MIN_TRANSMITTANCE);

export type Rgb = [number, number, number];

export function rgb(hex: string): Rgb {
  const n = parseInt(hex.slice(1, 7), 16);
  return [(n >> 16) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
}

export function hex([r, g, b]: Rgb): string {
  const c = (v: number) => Math.round(Math.min(1, Math.max(0, v)) * 255).toString(16).padStart(2, "0");
  return `#${c(r)}${c(g)}${c(b)}`;
}

const density = (c: Rgb): Rgb => c.map((v) => -Math.log(Math.max(v, MIN_TRANSMITTANCE))) as Rgb;
const dot3 = (a: Rgb, b: Rgb) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];

/** `risoOverprint`: the colour to draw so a pass on these drums lays down these coverages. */
export function overprint(inks: [InkName, number][], paper = PAPER): string {
  const out = rgb(paper);
  for (const [name, c] of inks) {
    const ink = rgb(Inks[name]);
    for (let ch = 0; ch < 3; ch++) out[ch] *= Math.max(ink[ch], MIN_TRANSMITTANCE) ** c;
  }
  return hex(out);
}

/** `separationRows`: ink i's coverage is `row[i] · D(pixel / paper)`. */
export function separationRows(inks: Rgb[]): Rgb[] {
  const d = inks.map(density);
  const n = d.length;
  const m = d.map((a) => d.map((b) => dot3(a, b)));
  let trace = 0;
  for (let i = 0; i < n; i++) trace += m[i][i];
  for (let i = 0; i < n; i++) m[i][i] += (1e-3 * trace) / n;
  const inv = invert(m);
  if (!inv) return inks.map(() => [0, 0, 0]);
  return inv.map((row) => [0, 1, 2].map((ch) => row.reduce((s, v, j) => s + v * d[j][ch], 0)) as Rgb);
}

function invert(a: number[][]): number[][] | null {
  const n = a.length;
  const w = a.map((row, i) => [...row, ...row.map((_, j) => (i === j ? 1 : 0))]);
  for (let col = 0; col < n; col++) {
    let p = col;
    for (let r = col + 1; r < n; r++) if (Math.abs(w[r][col]) > Math.abs(w[p][col])) p = r;
    if (Math.abs(w[p][col]) < 1e-6) return null;
    [w[col], w[p]] = [w[p], w[col]];
    const s = w[col][col];
    for (let k = 0; k < 2 * n; k++) w[col][k] /= s;
    for (let r = 0; r < n; r++) {
      if (r === col) continue;
      const f = w[r][col];
      for (let k = 0; k < 2 * n; k++) w[r][k] -= f * w[col][k];
    }
  }
  return w.map((row) => row.slice(n));
}

export interface Drum {
  name: InkName;
  slot: number;
  ink: Rgb;
  /** css px at offsetScale 1. */
  registration: [number, number];
  /** Degrees, as `risoInkForSlot` sets it; the dot tile picks the nearest angle that repeats. */
  screenAngle: number;
}

/** `risoInkForSlot`. */
export function drum(name: InkName): Drum {
  const slot = SLOTS.indexOf(name);
  return {
    name,
    slot,
    ink: rgb(Inks[name]),
    registration: [3 * Math.cos(slot * 2.4), 3 * Math.sin(slot * 2.4)],
    screenAngle: (15 + slot * 37.5) % 90,
  };
}
