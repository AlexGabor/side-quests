// Renders build/snapshot.html the way Snapshots.kt renders its "desktop" shot: 1440×900 dp at
// density 2, so the two PNGs line up pixel for pixel.
//
//   npm run snapshot [-- <out.png> [?query]]   e.g. "?screen=0&mottle=0&grain=0" to isolate a stage
import { chromium } from "playwright";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const out = process.argv[2] ?? resolve(root, "build/snapshots/react-desktop.png");
const query = process.argv[3] ?? "";

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2 });
page.on("pageerror", (e) => console.log("pageerror:", e.message));
await page.goto("file://" + resolve(root, "build/snapshot.html") + query);
await page.evaluate(() => document.fonts.ready);
// The surface tile is decoded asynchronously; give it and the filters time to land.
await page.waitForTimeout(2000);
await page.screenshot({ path: out });
await browser.close();
console.log(out);
