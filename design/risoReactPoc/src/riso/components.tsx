import React, { useContext, useEffect, useLayoutEffect, useRef, useState } from "react";
import { PaperTexture } from "@paper-design/shaders-react";
import { Ink, Knockout, RisoEffects } from "./Ink";
import { Inks, InkName, overprint, PAPER } from "./press";

export const content = Inks.vintageBlack;
const accent: InkName = "purple";
const lineWidth = 2;

/**
 * A text style as Compose renders it. Riso registers Fira Code VF without a weight, so every style
 * draws the font at wght 400; a style asking for 600 or more gets Skia's fake bold, a stroke of
 * `size × interp(9 → 1/24, 36 → 1/32)` round the outline.
 */
export function type(size: number, weight: number): React.CSSProperties {
  const t = Math.min(1, Math.max(0, (size - 9) / 27));
  const fakeBold = weight >= 600 ? size * (1 / 24 + t * (1 / 32 - 1 / 24)) : 0;
  return {
    fontFamily: "Fira Code, monospace",
    fontSize: size,
    fontWeight: 400,
    // Fira Code's ascent + descent (2400/1950 em), unrounded as Skia keeps it; Chrome's "normal"
    // rounds the ascent and descent to whole pixels each and grows every line by up to a pixel.
    lineHeight: 2400 / 1950,
    ...(fakeBold ? { WebkitTextStroke: `${fakeBold.toFixed(3)}px currentColor` } : {}),
  };
}

export const typography = {
  heading1: type(32, 700),
  heading2: type(22, 600),
  heading3: type(17, 600),
  body: type(15, 500),
};

/**
 * `Modifier.risoPaper()`: the sheet behind the content. Positioned, but with no z-index, so it
 * forms no stacking context and the passes inside can multiply onto it.
 *
 * The default stock is flat: its front and back are one colour, so the surface's lighting cancels
 * and it only shows through the ink it warps. [texture] lays paper.design's PaperTexture instead.
 */
export function Paper({ children, style, texture = false }: { children: React.ReactNode; style?: React.CSSProperties; texture?: boolean }) {
  const effects = useContext(RisoEffects);
  return (
    <div style={{ position: "relative", background: PAPER, ...style }}>
      {effects && texture && (
        <PaperTexture
          style={{ position: "absolute", inset: 0 }}
          fit="cover"
          colorBack={PAPER}
          colorPaper={PAPER}
          colorShadow="#d8d2c2"
          roughness={0.35}
          roughnessSize={0.3}
          fiber={0.3}
          fiberSize={0.3}
          folds={0}
          wrinkles={0}
          crumples={0}
          drops={0}
          distortion={0}
        />
      )}
      <div style={{ position: "relative" }}>{children}</div>
    </div>
  );
}

/** A spring-ish tween, enough for the press animation. */
function useAnimated(target: number) {
  const [value, setValue] = useState(target);
  const current = useRef(target);
  useEffect(() => {
    let frame = 0;
    const step = () => {
      current.current += (target - current.current) * 0.25;
      if (Math.abs(target - current.current) < 0.01) current.current = target;
      setValue(current.current);
      if (current.current !== target) frame = requestAnimationFrame(step);
    };
    frame = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frame);
  }, [target]);
  return value;
}

export function Button({ text, onClick }: { text: string; onClick?: () => void }) {
  const [pressed, setPressed] = useState(false);
  const offset = useAnimated(pressed ? 0 : 2);
  return (
    <Ink
      inks={["fluorescentPink", "purple"]}
      offsetScale={offset}
      role="button"
      tabIndex={0}
      onClick={onClick}
      onPointerDown={() => setPressed(true)}
      onPointerUp={() => setPressed(false)}
      onPointerLeave={() => setPressed(false)}
      style={{
        ...typography.body,
        display: "inline-block",
        cursor: "pointer",
        userSelect: "none",
        padding: "12px 24px",
        borderRadius: 12,
        // RisoMix(pink to .7, purple to .7).color()
        background: overprint([["fluorescentPink", 0.7], ["purple", 0.7]]),
      }}
    >
      <Knockout>{text}</Knockout>
    </Ink>
  );
}

/**
 * The outline and dividers, drawn where layout puts them rather than where the DOM would paint them.
 * Compose draws a divider at its fractional x ("On" is 18.46 dp wide) with antialiased edges, and
 * the screen turns the part-covered column into dots along one edge; a CSS box is snapped to whole
 * device pixels and prints dead straight. SVG geometry isn't snapped.
 */
function Rules({ group, dividers }: { group: React.RefObject<HTMLDivElement | null>; dividers: React.RefObject<(HTMLDivElement | null)[]> }) {
  const [shape, setShape] = useState<{ w: number; h: number; xs: number[] } | null>(null);
  useLayoutEffect(() => {
    const el = group.current;
    if (!el) return;
    const measure = () => {
      const r = el.getBoundingClientRect();
      const xs = (dividers.current ?? []).filter(Boolean).map((d) => d!.getBoundingClientRect().left - r.left);
      setShape({ w: r.width, h: r.height, xs });
    };
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);
  if (!shape) return null;
  const { w, h, xs } = shape;
  const half = lineWidth / 2;
  return (
    // Multiplied, so where a rule crosses the selected segment it separates as both inks and the
    // fill runs on under it, as it does under Compose's border.
    <svg width={w} height={h} style={{ position: "absolute", left: 0, top: 0, pointerEvents: "none", mixBlendMode: "multiply" }} aria-hidden>
      <rect x={half} y={half} width={w - lineWidth} height={h - lineWidth} rx={(h - lineWidth) / 2} fill="none" stroke={content} strokeWidth={lineWidth} />
      {xs.map((x, i) => (
        <rect key={i} x={x} y={0} width={lineWidth} height={h} fill={content} />
      ))}
    </svg>
  );
}

export function ButtonGroup<T extends string>({ items, selected, onSelect }: { items: T[]; selected: T; onSelect: (t: T) => void }) {
  const group = useRef<HTMLDivElement>(null);
  const dividers = useRef<(HTMLDivElement | null)[]>([]);
  return (
    <Ink inks={["vintageBlack", accent]} role="radiogroup" style={{ ...typography.body, position: "relative", borderRadius: 9999, overflow: "hidden", display: "inline-block" }}>
      <div ref={group} style={{ display: "flex" }}>
        {items.map((item, i) => (
          <React.Fragment key={item}>
            {/* Spacer(width = lineWidth): takes its room in the row; Rules draws it. */}
            {i > 0 && <div ref={(el) => void (dividers.current[i - 1] = el)} style={{ width: lineWidth, flex: "none" }} />}
            <div
              role="radio"
              aria-checked={item === selected}
              onClick={() => onSelect(item)}
              style={{
                padding: "12px 24px",
                cursor: "pointer",
                background: item === selected ? overprint([[accent, 1]]) : "transparent",
                color: item === selected ? PAPER : content,
                transition: "background 200ms, color 200ms",
              }}
            >
              {item}
            </div>
          </React.Fragment>
        ))}
      </div>
      <Rules group={group} dividers={dividers} />
    </Ink>
  );
}

export function Body({ children }: { children: React.ReactNode }) {
  return (
    <Ink inks={["vintageBlack"]} style={{ ...typography.body, color: content }}>
      {children}
    </Ink>
  );
}
