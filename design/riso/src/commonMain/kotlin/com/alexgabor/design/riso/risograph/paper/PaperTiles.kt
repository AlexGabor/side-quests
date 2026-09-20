package com.alexgabor.design.riso.risograph.paper

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shader
import com.alexgabor.design.riso.Res
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * One tile, as the sheets that use it see it: nothing until it has landed, then a shader to repeat.
 *
 * Deliberately not snapshot state. The tile lands on a background thread, and whoever is waiting
 * for it — a sheet's draw, an ink pass, a capture — waits with [await] on its own terms and asks for
 * its own redraw, rather than relying on a state write crossing threads to reach a composition.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class Tile(val key: TileKey) {

    private val landed = CompletableDeferred<Shader?>()
    private val current = AtomicReference<Shader?>(null)
    private val started = AtomicInt(0)

    /** The tile's shader, once it has landed. Null until then — and for good if every source failed. */
    val shader: Shader? get() = current.load()

    /** Whether the tile has stopped arriving: landed, or failed every source. */
    val settled: Boolean get() = landed.isCompleted

    /** Suspends until the tile has settled, and returns its shader, or null if it never will have one. */
    suspend fun await(): Shader? = landed.await()

    internal fun startOnce(load: suspend () -> Shader?) {
        if (!started.compareAndSet(0, 1)) return
        PaperTiles.scope.launch {
            val shader = try {
                load()
            } catch (failure: Throwable) {
                println("risoPaper: could not load ${key.fileName}: $failure")
                null
            }
            current.store(shader)
            landed.complete(shader)
        }
    }
}

/**
 * Where the sheet's tiles come from, in order: this process, the tiles shipped with the library, the
 * disk cache, and failing all of those a bake — whose result goes to the disk cache for next time.
 */
internal object PaperTiles {

    /**
     * Loads and bakes run one at a time. A bake is the one expensive thing here, and letting several
     * run at once would compete with the frames the sheet is being drawn into. On the browser this is
     * the page's one thread, and the bake yields between bands of rows instead.
     *
     * [Dispatchers] is used directly because `design/riso` depends on nothing else in the repo — see
     * `docs/architecture/known-deviations.md`.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    private val tiles = LruCache<TileKey, Tile>(maxEntries = 8)

    fun get(key: TileKey, cacheDir: String?): Tile =
        tiles.getOrPut(key) { Tile(key) }.also { tile ->
            tile.startOnce { load(key, cacheDir)?.image?.tileShader() }
        }

    /** Where a tile came from, which is all that differs between the ways of getting one. */
    internal enum class Source { Shipped, Disk, Bake }

    internal class Loaded(val image: ImageBitmap, val source: Source)

    /** [key]'s tile from the first source that has it, or null if even the bake failed. */
    internal suspend fun load(key: TileKey, cacheDir: String?): Loaded? {
        shipped(key)?.let { return Loaded(it, Source.Shipped) }
        val dir = cacheDir?.let { "$it/v$SURFACE_VERSION" }
        dir?.let { readTileFile(it, key.fileName) }?.decode()?.let { return Loaded(it, Source.Disk) }
        val baked = runCatching { bake(key) }
            .onFailure { println("risoPaper: could not bake ${key.fileName}: $it") }
            .getOrNull() ?: return null
        dir?.let { writeTileFile(it, key.fileName, baked.encodePng()) }
        return Loaded(baked, Source.Bake)
    }

    /** Where the tiles that ship with the library live among its resources. */
    internal const val SHIPPED_TILES = "files/riso/tiles/v$SURFACE_VERSION"

    /** The tiles worth shipping: the default stock's, at every density a phone or a desktop reports. */
    internal val shippedKeys: List<TileKey>
        get() = listOf(1f, 2f, 3f).map { RisoPaper().fineTileKey(it) } + RisoPaper().coarseTileKey()

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun shipped(key: TileKey): ImageBitmap? {
        if (key !in shippedKeys) return null
        return runCatching { Res.readBytes("$SHIPPED_TILES/${key.fileName}") }
            .onFailure { println("risoPaper: shipped ${key.fileName} unreadable: $it") }
            .getOrNull()?.decode()
    }

    @OptIn(ExperimentalResourceApi::class)
    private fun ByteArray.decode(): ImageBitmap? =
        runCatching { decodeToImageBitmap() }.onFailure { println("risoPaper: tile undecodable: $it") }.getOrNull()

    /** Renders [key]'s tile from scratch. */
    internal suspend fun bake(key: TileKey): ImageBitmap = when (key) {
        is FineTileKey -> bakeTile(FINE_TILE_BAKE_SKSL, key.pixels) { it.setFineTileBake(key) }
        is CoarseTileKey -> bakeTile(COARSE_TILE_BAKE_SKSL, key.pixels) { it.setCoarseTileBake(key) }
    }
}

/**
 * What a sheet's surface is made of at the moment it is read: the stock, and its tiles if they have
 * landed. The ink that warps across a sheet reads the same thing, so the two always agree.
 */
internal data class SheetSurface(
    val paper: RisoPaper,
    val fine: Shader?,
    val coarse: Shader?,
) {
    /** Whether there is relief to read. A flat stock never has any, and neither does a sheet still loading. */
    val ready: Boolean get() = paper.warps && fine != null && coarse != null
}

/**
 * Bound in a tile's place until it lands. Never actually read: the surface is flat until both tiles
 * are there, but a shader will not run with a child left unbound.
 */
internal val PlaceholderTile: Shader by lazy { ImageBitmap(1, 1).tileShader() }
