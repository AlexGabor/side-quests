// Mirrors SnapshotPage in design/risoRecorder/.../Snapshots.kt (plus a ButtonGroup, as rendered for
// the comparison), so the two can be diffed at the same density and position.
import React from "react";
import { createRoot } from "react-dom/client";
import { Ink } from "./riso/Ink";
import { Button, ButtonGroup, content, Paper, typography } from "./riso/components";
import { Inks, overprint, Press } from "./riso/press";

// ?screen=0&mottle=0&grain=0&spread=0 and so on, to take the pipeline apart stage by stage.
new URLSearchParams(location.search).forEach((v, k) => k in Press && ((Press as Record<string, number>)[k] = Number(v)));

function Swatch({ inks, background }: { inks: (keyof typeof Inks)[]; background: string }) {
  return <Ink inks={inks} style={{ width: 64, height: 64, background }} />;
}

function Page() {
  return (
    <Paper style={{ width: 1440, height: 900, boxSizing: "border-box", padding: 24 }}>
      <div style={{ display: "flex", flexDirection: "column", gap: 16, alignItems: "flex-start" }}>
        <Ink inks={["vintageBlack"]} style={{ ...typography.heading1, color: content }}>Riso sheet</Ink>
        <Ink inks={["vintageBlack"]} style={{ ...typography.body, color: content }}>
          Printed on the drums named, off register, screened and mottled, on a sheet with a surface of its own.
        </Ink>
        <Button text="Print" />
        <ButtonGroup items={["On", "Off"]} selected="On" onSelect={() => {}} />
        <Ink inks={["vintageBlack"]} style={{ ...typography.heading3, color: content }}>Inks</Ink>
        <div style={{ display: "flex", gap: 12 }}>
          <Swatch inks={["fluorescentPink"]} background={overprint([["fluorescentPink", 1]])} />
          <Swatch inks={["blue"]} background={overprint([["blue", 1]])} />
          <Swatch inks={["yellow"]} background={overprint([["yellow", 1]])} />
          <Swatch inks={["fluorescentPink", "blue"]} background={overprint([["fluorescentPink", 0.6], ["blue", 0.6]])} />
        </div>
      </div>
    </Paper>
  );
}

createRoot(document.getElementById("root")!).render(<Page />);
