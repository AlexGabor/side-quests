// Repeating tiles for the SVG filter, baked once on a canvas. Screen and texture values live in the
// alpha channel (rgb black), so feComposite arithmetic can work on alpha alone.

import { Drum, Press } from "./press";
// The default stock's surface, exactly as design/riso ships it (SURFACE_VERSION 1).
import fine1x from "../../../riso/src/commonMain/composeResources/files/riso/tiles/v1/fine-r38-f110@1x.png";
import fine2x from "../../../riso/src/commonMain/composeResources/files/riso/tiles/v1/fine-r38-f110@2x.png";
import fine3x from "../../../riso/src/commonMain/composeResources/files/riso/tiles/v1/fine-r38-f110@3x.png";

export interface Tile {
  url: string;
  /** Period in css px. */
  size: number;
}

export const deviceScale = () => Math.min(3, Math.max(1, Math.ceil(window.devicePixelRatio || 1)));

function bake(px: number, alphaAt: (x: number, y: number) => number): string {
  const canvas = document.createElement("canvas");
  canvas.width = canvas.height = px;
  const ctx = canvas.getContext("2d")!;
  const img = ctx.createImageData(px, px);
  for (let y = 0; y < px; y++)
    for (let x = 0; x < px; x++) img.data[(y * px + x) * 4 + 3] = Math.round(Math.min(1, Math.max(0, alphaAt(x, y))) * 255);
  ctx.putImageData(img, 0, 0);
  return canvas.toDataURL("image/png");
}

/**
 * A dot lattice rotated by atan(a/b) repeats in a square `k·d·√(a²+b²)` across (k = 1 when a+b is
 * even, else 2). Of the angles whose tile stays under [maxPx], take the one nearest [deg] (mod 90°,
 * which the dot field is symmetric under).
 */
function tilingAngle(deg: number, dotPx: number, maxPx = 160) {
  let best = { a: 0, b: 1, err: Infinity };
  for (let a = 0; a <= 16; a++)
    for (let b = 1; b <= 16; b++) {
      const k = (a + b) % 2 === 0 ? 1 : 2;
      if (k * dotPx * Math.hypot(a, b) > maxPx) continue;
      const d = (Math.atan2(a, b) * 180) / Math.PI;
      const err = Math.min(Math.abs(d - deg), 90 - Math.abs(d - deg));
      if (err < best.err - 1e-9) best = { a, b, err };
    }
  return best;
}

const dotCache = new Map<string, Tile & { dotSize: number; deg: number }>();

/** `screenDots`' field, `½ − ½·cos qx·cos qy`, with d nudged until the period is whole pixels. */
export function dotTile(d: Drum) {
  const r = deviceScale();
  const { a, b } = tilingAngle(d.screenAngle, Press.dotSize * r);
  const key = `${a}/${b}@${r}`;
  let tile = dotCache.get(key);
  if (!tile) {
    const k = (a + b) % 2 === 0 ? 1 : 2;
    const h = Math.hypot(a, b);
    const px = Math.max(2, Math.round(k * Press.dotSize * h * r));
    const dotPx = px / (k * h);
    const th = Math.atan2(a, b);
    const c = Math.cos(th), s = Math.sin(th);
    tile = {
      size: px / r,
      dotSize: dotPx,
      deg: (th * 180) / Math.PI,
      url: bake(px, (x, y) => {
        const qx = ((c * (x + 0.5) - s * (y + 0.5)) * Math.PI) / dotPx;
        const qy = ((s * (x + 0.5) + c * (y + 0.5)) * Math.PI) / dotPx;
        return 0.5 - 0.5 * Math.cos(qx) * Math.cos(qy);
      }),
    };
    dotCache.set(key, tile);
  }
  return tile;
}

// The shader's hash, fed a cell index wrapped mod the period so the noise repeats.
const fract = (v: number) => v - Math.floor(v);
function hash(x: number, y: number): number {
  let qx = fract(x * 0.1031), qy = fract(y * 0.1031), qz = fract(x * 0.1031);
  const d = qx * (qy + 33.33) + qy * (qz + 33.33) + qz * (qx + 33.33);
  qx += d; qy += d; qz += d;
  return fract((qx + qy) * qz);
}

function periodicValueNoise(x: number, y: number, period: number, seed: number): number {
  const ix = Math.floor(x), iy = Math.floor(y);
  const fx = x - ix, fy = y - iy;
  const ux = fx * fx * (3 - 2 * fx), uy = fy * fy * (3 - 2 * fy);
  const h = (i: number, j: number) => hash((((i % period) + period) % period) + seed, (((j % period) + period) % period) + seed);
  const top = h(ix, iy) + (h(ix + 1, iy) - h(ix, iy)) * ux;
  const bottom = h(ix, iy + 1) + (h(ix + 1, iy + 1) - h(ix, iy + 1)) * ux;
  return top + (bottom - top) * uy;
}

let noise: Tile | undefined;

/** `inkTexture`'s two factors, mottle × grain, as one repeating alpha tile. */
export function noiseTile(): Tile {
  if (noise) return noise;
  const r = deviceScale();
  const size = 128; // css px
  const px = size * r;
  const grainPx = Math.max(1, Math.round(Press.grainSize * r));
  const cells = size / Press.mottleSize; // whole cells per period, doubling per octave
  noise = {
    size,
    url: bake(px, (x, y) => {
      const u = x / px, v = y / px;
      let fbm = 0, amp = 0.6, n = cells;
      for (let o = 0; o < 3; o++, amp *= 0.5, n *= 2) fbm += amp * periodicValueNoise(u * n, v * n, n, 11.3 * o);
      fbm /= 1.05;
      const grain = hash(Math.floor(x / grainPx) + 3, Math.floor(y / grainPx) + 3);
      return (1 - Press.mottle * (1 - fbm)) * (1 - Press.grain * grain);
    }),
  };
  return noise;
}

/** Period of the paper's fine tile (`FINE_PERIOD_DP`). */
const FINE_PERIOD = 256;
/** feDisplacementMap's scale: a channel's 0..1 spans ±SURFACE_SCALE/2 css px. */
export const SURFACE_SCALE = 2;
/**
 * The default stock's `mix(1, ½, fade)`, averaged over its coarse tile (0.957). The fade repeats every
 * 1400 dp, not 256, so it can't be folded into a 256 dp tile; its mean is used instead.
 */
const MEAN_FADE_FACTOR = 0.96;

let surface: Promise<Tile> | undefined;

/**
 * How far the default stock's surface pushes the ink, as a displacement map. Built from the tile
 * Compose ships, decoded as `sheetSurface` decodes it: `WARP_DP · normalImage`, which for the
 * default stock (roughness 0.12, fiber 0.1) is `0.9·r + 0.48·g − 0.53` dp, the same along x and y.
 */
export function surfaceTile(): Promise<Tile> {
  surface ??= new Promise((resolve, reject) => {
    const r = deviceScale();
    const img = new Image();
    img.onload = () => {
      const px = img.width;
      const canvas = document.createElement("canvas");
      canvas.width = canvas.height = px;
      const ctx = canvas.getContext("2d")!;
      ctx.drawImage(img, 0, 0);
      const data = ctx.getImageData(0, 0, px, px);
      const p = data.data;
      for (let i = 0; i < p.length; i += 4) {
        const dp = MEAN_FADE_FACTOR * (0.9 * (p[i] / 255) + 0.48 * (p[i + 1] / 255) - 0.53);
        const c = Math.round((dp / SURFACE_SCALE + 0.5) * 255);
        p[i] = p[i + 1] = c;
        p[i + 2] = 0;
        p[i + 3] = 255;
      }
      ctx.putImageData(data, 0, 0);
      resolve({ size: FINE_PERIOD, url: canvas.toDataURL("image/png") });
    };
    img.onerror = reject;
    img.src = [fine1x, fine2x, fine3x][r - 1];
  });
  return surface;
}
