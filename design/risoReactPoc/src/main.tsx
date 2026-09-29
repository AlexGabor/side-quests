// Interactive side by side: the Compose snapshot (`./gradlew :design:risoRecorder:snapshots`,
// desktop, 2×, copied in by build.sh when it exists) on the left, the React components on the right.
import React, { useState } from "react";
import { createRoot } from "react-dom/client";
import { Ink, RisoEffects } from "./riso/Ink";
import { Button, ButtonGroup, content, Paper, typography } from "./riso/components";

function Column({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
      <div style={{ font: "12px system-ui", color: "#8a8272" }}>{label}</div>
      {children}
    </div>
  );
}

function App() {
  const [effects, setEffects] = useState(true);
  const [texture, setTexture] = useState(false);
  const [side, setSide] = useState<"On" | "Off">("On");
  const [count, setCount] = useState(0);
  return (
    <RisoEffects.Provider value={effects}>
      <Paper texture={texture} style={{ minHeight: "100vh", padding: 24, boxSizing: "border-box" }}>
        <div style={{ font: "13px system-ui", display: "flex", gap: 16, marginBottom: 16 }}>
          <label><input type="checkbox" checked={effects} onChange={(e) => setEffects(e.target.checked)} /> effects</label>
          <label><input type="checkbox" checked={texture} onChange={(e) => setTexture(e.target.checked)} /> paper.design texture</label>
        </div>
        <div style={{ display: "flex", flexWrap: "wrap", gap: 32, alignItems: "flex-start" }}>
          <Column label="Compose (snapshots/desktop.png)">
            {/* 1440 css px wide is 1:1 dp; the frame crops it to the top of the page. */}
            <div style={{ width: 300, height: 450, overflow: "hidden" }}>
              <img src="compose.png" alt="Compose snapshot missing: run ./gradlew :design:risoRecorder:snapshots" style={{ width: 1440, display: "block" }} />
            </div>
          </Column>
          <Column label="React">
            <div style={{ width: 300, display: "flex", flexDirection: "column", gap: 16, alignItems: "flex-start", paddingTop: 12 }}>
              <Ink inks={["vintageBlack"]} style={{ ...typography.heading1, color: content }}>Riso sheet</Ink>
              <Button text={count ? `Print ${count}` : "Print"} onClick={() => setCount(count + 1)} />
              <ButtonGroup items={["On", "Off"]} selected={side} onSelect={setSide} />
            </div>
          </Column>
        </div>
      </Paper>
    </RisoEffects.Provider>
  );
}

createRoot(document.getElementById("root")!).render(<App />);
