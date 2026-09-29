import React, { createContext, useCallback, useContext, useEffect, useId, useLayoutEffect, useMemo, useRef, useState } from "react";
import { flushSync } from "react-dom";
import { drum, InkName, MAX_DENSITY, MIN_TRANSMITTANCE, PAPER, Press, rgb, separationRows } from "./press";
import { deviceScale, dotTile, noiseTile, SURFACE_SCALE, surfaceTile, Tile } from "./tiles";

export const RisoEffects = createContext(true);

/**
 * Which copy of an `Ink`'s subtree a `Knockout` is rendering in: the pass itself, where it must not
 * draw (so the fill runs on underneath, as Compose leaves a knockout out of the pass's artwork), or
 * the punch laid over the pass, where it draws in the paper colour.
 */
const KnockoutHost = createContext<{ mode: "print"; register: () => () => void } | { mode: "punch" } | null>(null);

interface Layout {
  // In the offset parent, for the punch copy.
  left: number;
  top: number;
  width: number;
  height: number;
  // On the page, for anchoring the screen, the mottle and the sheet's surface.
  pageX: number;
  pageY: number;
}

const sameLayout = (a: Layout | null, b: Layout) =>
  !!a && (Object.keys(b) as (keyof Layout)[]).every((k) => Math.abs(a[k] - b[k]) < 0.01);

function useSurface() {
  const [tile, setTile] = useState<Tile | null>(null);
  useEffect(() => {
    let live = true;
    surfaceTile().then((t) => live && setTile(t), (e) => console.warn("riso: no surface", e));
    return () => {
      live = false;
    };
  }, []);
  return tile;
}

/**
 * One pass through the press for this subtree, on up to three drums. The DOM version of
 * `Modifier.risoInk`: an SVG filter does INK_PASS_SKSL's work, and `mix-blend-mode: multiply`
 * lays the pass onto the paper.
 *
 * Unlike Compose, passes can't nest (a CSS filter always applies to its whole subtree), so a
 * component names every drum it prints on, once, at its root.
 */
export function Ink({
  inks,
  offsetScale = 1,
  style,
  children,
  ...rest
}: {
  inks: InkName[];
  offsetScale?: number;
} & React.HTMLAttributes<HTMLDivElement>) {
  const effects = useContext(RisoEffects);
  const id = "riso" + useId().replace(/[^a-zA-Z0-9]/g, "");
  const ref = useRef<HTMLDivElement>(null);
  const [layout, setLayout] = useState<Layout | null>(null);
  const [knockouts, setKnockouts] = useState(0);
  const surface = useSurface();

  const register = useCallback(() => {
    setKnockouts((n) => n + 1);
    return () => setKnockouts((n) => n - 1);
  }, []);
  const host = useMemo(() => ({ mode: "print" as const, register }), [register]);

  // Fractional throughout: offsetWidth and friends round to whole pixels, and a punch copy even a
  // fraction narrower than its text wraps it.
  const measure = useCallback(() => {
    const el = ref.current;
    if (!el) return;
    const r = el.getBoundingClientRect();
    const parent = el.offsetParent as HTMLElement | null;
    const p = parent?.getBoundingClientRect();
    const next = {
      left: p ? r.left - p.left - parent!.clientLeft + parent!.scrollLeft : r.left + window.scrollX,
      top: p ? r.top - p.top - parent!.clientTop + parent!.scrollTop : r.top + window.scrollY,
      width: r.width,
      height: r.height,
      pageX: r.left + window.scrollX,
      pageY: r.top + window.scrollY,
    };
    setLayout((prev) => (sameLayout(prev, next) ? prev : next));
  }, []);

  // After every render, before the browser paints: new children can change the size, and a layout
  // effect's state update is rendered synchronously, so the punch and the filter never show a frame
  // laid out for the old content. Settles in one extra render at most (an equal layout bails out).
  useLayoutEffect(() => {
    if (effects) measure();
  });

  // Size changes that don't come from this component rendering (the window, a parent). Observer
  // callbacks run before paint too, so their update is flushed there rather than a frame later.
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el || !effects) return;
    const sync = () => flushSync(measure);
    const ro = new ResizeObserver(sync);
    ro.observe(el);
    ro.observe(document.body);
    window.addEventListener("resize", sync);
    return () => {
      ro.disconnect();
      window.removeEventListener("resize", sync);
    };
  }, [effects, measure]);

  const drums = useMemo(() => [...new Set(inks)].slice(0, 3).map(drum), [inks.join()]);
  const rows = useMemo(() => separationRows(drums.map((d) => d.ink)), [drums]);

  if (!effects) {
    return (
      <div ref={ref} style={style} {...rest}>
        {children}
      </div>
    );
  }

  const box = layout ?? { left: 0, top: 0, width: 0, height: 0, pageX: 0, pageY: 0 };
  const margin = 8 + 3 * Math.abs(offsetScale);
  const region = { x: -margin, y: -margin, width: box.width + 2 * margin, height: box.height + 2 * margin };
  const paper = rgb(PAPER);
  const floor = -Math.log(1 - Press.tolerance);
  // D(x) / MAX_DENSITY per channel, so it fits the 0..1 a filter can carry.
  const densityTable = (ch: number) =>
    Array.from({ length: 256 }, (_, i) => {
      const t = Math.min(1, Math.max(0.02, i / 255 / paper[ch]));
      return (Math.max(-Math.log(t) - floor, 0) / MAX_DENSITY).toFixed(4);
    }).join(" ");
  const noise = noiseTile();

  return (
    <>
      <svg width="0" height="0" style={{ position: "absolute" }} aria-hidden>
        <filter id={id} filterUnits="userSpaceOnUse" primitiveUnits="userSpaceOnUse" {...region} colorInterpolationFilters="sRGB">
          <feComponentTransfer in="SourceGraphic" result="density">
            <feFuncR type="table" tableValues={densityTable(0)} />
            <feFuncG type="table" tableValues={densityTable(1)} />
            <feFuncB type="table" tableValues={densityTable(2)} />
          </feComponentTransfer>
          {drums.map((d, i) => {
            const row = rows[i].map((v) => v * MAX_DENSITY);
            const dots = dotTile(d);
            const w = Math.min(0.45, Math.max(0.06, 2 / dots.dotSize));
            const slip = d.registration.map((v) => v * offsetScale);
            return (
              <React.Fragment key={d.name}>
                <feColorMatrix
                  in="density"
                  type="matrix"
                  values={`0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  ${row[0]} ${row[1]} ${row[2]} 0 0`}
                  result={`sep${i}`}
                />
                <feComposite in={`sep${i}`} in2="SourceAlpha" operator="in" result={`raw${i}`} />
                {surface && Press.warp ? (
                  <>
                    {/* The sheet pushes the ink around: read the artwork displaced by the surface
                        where this drum lands on the page, as INK_PASS_SKSL does. */}
                    {tiled(`surface${i}`, surface, box.pageX + slip[0], box.pageY + slip[1], region)}
                    <feDisplacementMap
                      in={`raw${i}`}
                      in2={`surface${i}`}
                      scale={SURFACE_SCALE}
                      xChannelSelector="R"
                      yChannelSelector="G"
                      result={`warped${i}`}
                    />
                  </>
                ) : (
                  <feOffset in={`raw${i}`} result={`warped${i}`} />
                )}
                <feComponentTransfer in={`warped${i}`} result={`cov${i}`}>
                  <feFuncA type="gamma" exponent={1 / (1 + Press.spread)} />
                </feComponentTransfer>
                {tiled(`dots${i}`, dots, box.pageX, box.pageY, region)}
                {/* `mix(c, screened, u_screen)`: linear in c and the field, so still one arithmetic. */}
                <feComposite
                  in={`cov${i}`}
                  in2={`dots${i}`}
                  operator="arithmetic"
                  k1={0}
                  k2={Press.screen * ((1 + 2 * w) / w) + (1 - Press.screen)}
                  k3={Press.screen * (-1 / w)}
                  k4={Press.screen * -0.5}
                  result={`screened${i}`}
                />
                {tiled(`noise${i}`, noise, box.pageX + d.slot * 37, box.pageY + d.slot * 53, region)}
                <feComposite in={`screened${i}`} in2={`noise${i}`} operator="arithmetic" k1={1} result={`printed${i}`} />
                {/* u_ink is the ink's transmittance, floored like every other density in the maths. */}
                <feFlood floodColor={`rgb(${d.ink.map((v) => Math.max(v, MIN_TRANSMITTANCE) * 255).join(",")})`} result={`ink${i}`} />
                <feComposite in={`ink${i}`} in2={`printed${i}`} operator="in" result={`laid${i}`} />
                {throwDrum(`laid${i}`, `drum${i}`, slip)}
                {i > 0 && <feBlend in={`drum${i}`} in2={i === 1 ? "drum0" : `stack${i - 1}`} mode="multiply" result={`stack${i}`} />}
              </React.Fragment>
            );
          })}
        </filter>
      </svg>
      <div ref={ref} style={{ ...style, filter: `url(#${id})`, mixBlendMode: "multiply" }} {...rest}>
        <KnockoutHost.Provider value={host}>{children}</KnockoutHost.Provider>
      </div>
      {knockouts > 0 && layout && (
        // The punch: the same subtree, laid out identically on top of the pass, showing nothing
        // but its knockouts. Pinned to the page, so one hole serves every drum.
        <div
          aria-hidden
          style={{
            ...style,
            position: "absolute",
            left: layout.left,
            top: layout.top,
            width: layout.width,
            height: layout.height,
            margin: 0,
            boxSizing: "border-box",
            visibility: "hidden",
            pointerEvents: "none",
            userSelect: "none",
          }}
        >
          <KnockoutHost.Provider value={{ mode: "punch" }}>{children}</KnockoutHost.Provider>
        </div>
      )}
    </>
  );
}

/**
 * Moves a finished pass by the drum's throw, as Compose moves the pass layer: whole device pixels
 * with feOffset, the rest with a bilinear 2×2 kernel. feOffset alone snaps a fractional throw and
 * smears an extra part-column of ink where Compose's layer resamples to a soft edge.
 */
function throwDrum(input: string, result: string, [x, y]: number[]) {
  const r = deviceScale();
  const px = x * r, py = y * r;
  if (!Press.subpixel) return <feOffset in={input} dx={x} dy={y} result={result} />;
  const nx = Math.floor(px), ny = Math.floor(py);
  const fx = px - nx, fy = py - ny;
  // result(x, y) = Σ src(x − 1 + j, y − 1 + i) · K[1 − i][1 − j]  (target 1,1; kernel rotated 180°)
  const k = [(1 - fx) * (1 - fy), fx * (1 - fy), (1 - fx) * fy, fx * fy].map((v) => v.toFixed(5)).join(" ");
  return (
    <>
      <feOffset in={input} dx={nx / r} dy={ny / r} result={`${result}Whole`} />
      <feConvolveMatrix in={`${result}Whole`} order="2" kernelMatrix={k} divisor="1" preserveAlpha="false" edgeMode="none" result={result} />
    </>
  );
}

/**
 * Lays [tile] across [region] so that one copy starts at a multiple of its period from the page's
 * origin. feTile only repeats the part of its input inside the filter region, so a tile that can't
 * fit whole in the region twice over is laid as merged copies instead.
 */
function tiled(result: string, tile: Tile, pageX: number, pageY: number, region: { x: number; y: number; width: number; height: number }) {
  const L = tile.size;
  const mod = (v: number, m: number) => ((v % m) + m) % m;
  // Whole device pixels in the filter's own space: the element can sit at a fractional position on
  // the page, and a copy between pixels is resampled, which leaves a faint seam where copies meet.
  const r = deviceScale();
  const snap = (v: number) => Math.round(v * r) / r;
  const ax = snap(region.x + mod(-region.x - pageX, L));
  const ay = snap(region.y + mod(-region.y - pageY, L));
  if (region.width >= 2 * L && region.height >= 2 * L) {
    return (
      <>
        <feImage href={tile.url} x={ax} y={ay} width={L} height={L} preserveAspectRatio="none" imageRendering="pixelated" result={`${result}Cell`} />
        <feTile in={`${result}Cell`} {...region} result={result} />
      </>
    );
  }
  const copies: [number, number][] = [];
  for (let x = ax - L; x < region.x + region.width; x += L)
    for (let y = ay - L; y < region.y + region.height; y += L) if (x + L > region.x && y + L > region.y) copies.push([x, y]);
  return (
    <>
      {copies.map(([x, y], k) => (
        <feImage key={k} href={tile.url} x={x} y={y} width={L} height={L} preserveAspectRatio="none" imageRendering="pixelated" result={`${result}Copy${k}`} />
      ))}
      <feMerge {...region} result={result}>
        {copies.map((_, k) => (
          <feMergeNode key={k} in={`${result}Copy${k}`} />
        ))}
      </feMerge>
    </>
  );
}

/** `Modifier.risoKnockout()`: cut this subtree out of the enclosing pass, pinned to the sheet. */
export function Knockout({ children }: { children: React.ReactNode }) {
  const effects = useContext(RisoEffects);
  const host = useContext(KnockoutHost);
  const register = host?.mode === "print" ? host.register : undefined;
  useEffect(() => register?.(), [register]);
  if (!effects || !host) return <span style={{ color: PAPER }}>{children}</span>;
  if (host.mode === "print") return <span style={{ color: "transparent" }}>{children}</span>;
  return <span style={{ color: PAPER, visibility: "visible" }}>{children}</span>;
}
