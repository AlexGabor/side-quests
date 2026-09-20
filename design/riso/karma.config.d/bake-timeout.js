// mocha gives a test two seconds by default, which is not enough to bake a paper tile here.
//
// The browser has no GPU surface skiko will hand out, so a tile is rasterized on the CPU (see
// rasterBakeMain/PaperBakeSurface.kt), and evaluating the bake shader per pixel costs roughly
// twenty times what it does natively: ~4s for the 256px fine tile that TileBakeTest bakes, where
// the JVM takes ~180ms. That is the real cost of a runtime bake on the web, and it is why the
// default stock's tiles ship with the library instead.
//
// Written by hand rather than through the Gradle DSL so that `client.args`, which carries the
// `--tests` filter, is left alone.
config.client = config.client || {};
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 60000 });
