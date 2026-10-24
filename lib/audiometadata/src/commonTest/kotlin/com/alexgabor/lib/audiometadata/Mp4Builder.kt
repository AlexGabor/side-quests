package com.alexgabor.lib.audiometadata

/** Byte-level MP4 atoms for inputs ffmpeg can't produce. */

fun u16(v: Int) = bytesOf(v shr 8, v)

fun u64(v: Long) = u32(v ushr 32) + u32(v and 0xFFFFFFFFL)

fun concat(parts: List<ByteArray>): ByteArray {
    val out = ByteArray(parts.sumOf { it.size })
    var at = 0
    for (part in parts) {
        part.copyInto(out, at)
        at += part.size
    }
    return out
}

object Mp4 {

    fun atom(type: String, vararg payload: ByteArray): ByteArray {
        val body = concat(payload.toList())
        return u32(8L + body.size) + latin1(type) + body
    }

    /** An atom with a 64-bit size; [declaredSize] overrides it, e.g. for an `mdat` that isn't there. */
    fun atom64(type: String, payload: ByteArray, declaredSize: Long = 16L + payload.size) =
        u32(1) + latin1(type) + u64(declaredSize) + payload

    fun fullBox(type: String, version: Int, vararg body: ByteArray) = atom(type, bytesOf(version, 0, 0, 0), *body)

    fun ftyp(major: String = "M4B ", vararg compatible: String = arrayOf("isom")) =
        atom("ftyp", latin1(major), u32(0x200), *compatible.map(::latin1).toTypedArray())

    /** `mvhd` and `mdhd` share their layout up to the duration. */
    private fun timeHeader(type: String, timescale: Long, duration: Long, version: Int, tail: Int) =
        if (version == 1) fullBox(type, 1, u64(0), u64(0), u32(timescale), u64(duration), ByteArray(tail))
        else fullBox(type, 0, u32(0), u32(0), u32(timescale), u32(duration), ByteArray(tail))

    fun mvhd(timescale: Long, duration: Long, version: Int = 0) = timeHeader("mvhd", timescale, duration, version, 80)

    fun mdhd(timescale: Long, duration: Long, version: Int = 0) = timeHeader("mdhd", timescale, duration, version, 4)

    fun tkhd(id: Long, duration: Long, version: Int = 0) =
        if (version == 1) fullBox("tkhd", 1, u64(0), u64(0), u32(id), u32(0), u64(duration), ByteArray(60))
        else fullBox("tkhd", 0, u32(0), u32(0), u32(id), u32(0), u32(duration), ByteArray(60))

    fun hdlr(handler: String) = fullBox("hdlr", 0, u32(0), latin1(handler), ByteArray(12), bytesOf(0))

    /** A `trak` with [handler]; [chapters] adds `tref/chap`; [stbl] holds its sample tables. */
    fun trak(
        id: Long,
        handler: String,
        timescale: Long = 1000,
        duration: Long = 0,
        headerDuration: Long = duration,
        chapters: List<Long> = emptyList(),
        stbl: ByteArray = atom("stbl"),
        version: Int = 0,
        mdhd: Boolean = true,
    ) = atom(
        "trak",
        tkhd(id, headerDuration, version),
        if (chapters.isEmpty()) ByteArray(0) else atom("tref", atom("chap", concat(chapters.map(::u32)))),
        atom(
            "mdia",
            if (mdhd) mdhd(timescale, duration, version) else ByteArray(0),
            hdlr(handler),
            atom("minf", stbl),
        ),
    )

    fun stts(vararg runs: Pair<Int, Int>) =
        fullBox("stts", 0, u32(runs.size.toLong()), concat(runs.map { u32(it.first.toLong()) + u32(it.second.toLong()) }))

    fun stsc(vararg runs: Pair<Int, Int>) =
        fullBox("stsc", 0, u32(runs.size.toLong()), concat(runs.map { u32(it.first.toLong()) + u32(it.second.toLong()) + u32(1) }))

    fun stsz(sizes: List<Int>) = fullBox("stsz", 0, u32(0), u32(sizes.size.toLong()), concat(sizes.map { u32(it.toLong()) }))

    fun stszUniform(size: Long, count: Long) = fullBox("stsz", 0, u32(size), u32(count))

    fun stco(offsets: List<Long>) = fullBox("stco", 0, u32(offsets.size.toLong()), concat(offsets.map(::u32)))

    fun co64(offsets: List<Long>) = fullBox("co64", 0, u32(offsets.size.toLong()), concat(offsets.map(::u64)))

    /** A QuickTime text sample: 16-bit length, the text, then an `encd` atom as Apple writes it. */
    fun textSample(title: String, bytes: ByteArray = utf8(title)) = u16(bytes.size) + bytes + atom("encd", u32(0x100))

    /** Nero chapters: (start in ms, title). */
    fun chpl(vararg chapters: Pair<Long, String>, version: Int = 1) = fullBox(
        "chpl", version,
        if (version == 1) u32(0) else ByteArray(0),
        bytesOf(chapters.size),
        concat(chapters.map { (ms, title) -> u64(ms * 10_000) + bytesOf(utf8(title).size) + utf8(title) }),
    )

    /** An `ilst` item whose value is one `data` atom of [type]. */
    fun item(type: String, dataType: Int, value: ByteArray) = atom(type, atom("data", u32(dataType.toLong()), u32(0), value))

    fun text(type: String, value: String) = item(type, 1, utf8(value))

    fun number(type: String, n: Int, total: Int) = item(type, 0, u16(0) + u16(n) + u16(total) + u16(0))

    fun freeform(mean: String, name: String, value: String) = atom(
        "----",
        atom("mean", u32(0), utf8(mean)),
        atom("name", u32(0), utf8(name)),
        atom("data", u32(1), u32(0), utf8(value)),
    )

    /** `udta/meta/ilst`; [fullBox] false writes QuickTime's `meta` without version and flags. */
    fun udta(vararg items: ByteArray, fullBox: Boolean = true, extra: ByteArray = ByteArray(0)) = atom(
        "udta",
        atom("meta", if (fullBox) u32(0) else ByteArray(0), hdlr("mdir"), atom("ilst", *items)),
        extra,
    )

    /** `ftyp`, `moov`, and an `mdat` holding [samples], with the chapter track's offsets resolved. */
    fun file(moov: (mdatPayloadAt: Long) -> ByteArray, samples: ByteArray = ByteArray(0), ftyp: ByteArray = ftyp()): ByteArray {
        // `moov` comes first, so the samples' offsets depend on its size: build it once to measure.
        val size = moov(0).size
        val mdatAt = ftyp.size.toLong() + size + 8
        return ftyp + moov(mdatAt) + atom("mdat", samples)
    }

    /**
     * A book whose sound track (id 1) points at a QuickTime chapter track (id 2) with [titles], each
     * [deltaMs] long, and optionally a Nero `chpl`. [perChunk] samples per chunk, `co64` when [wide].
     */
    fun chapterBook(
        titles: List<String>,
        deltaMs: Int = 60_000,
        durationMs: Long = titles.size * deltaMs.toLong(),
        nero: List<Pair<Long, String>> = emptyList(),
        perChunk: Int = titles.size,
        wide: Boolean = false,
        referenced: Boolean = true,
    ): ByteArray {
        val samples = titles.map { textSample(it) }
        return file({ mdatAt ->
            val offsets = mutableListOf<Long>()
            var at = mdatAt
            samples.forEachIndexed { i, sample ->
                if (i % perChunk == 0) offsets += at
                at += sample.size
            }
            val stbl = atom(
                "stbl",
                stts(titles.size to deltaMs),
                stsc(1 to perChunk),
                stsz(samples.map { it.size }),
                if (wide) co64(offsets) else stco(offsets),
            )
            atom(
                "moov",
                mvhd(1000, durationMs),
                trak(1, "soun", 1000, durationMs, chapters = if (referenced) listOf(2L) else emptyList()),
                trak(2, "text", 1000, durationMs, stbl = stbl),
                if (nero.isEmpty()) ByteArray(0) else atom("udta", chpl(*nero.toTypedArray())),
            )
        }, concat(samples))
    }
}

/** Serves [parts] at their offsets and zeros elsewhere, counting what is read. */
class SparseSource(override val size: Long, private val parts: List<Pair<Long, ByteArray>>) : ByteSource {
    var bytesRead = 0L
        private set

    override fun read(position: Long, length: Int): ByteArray {
        if (position < 0 || position >= size || length <= 0) return ByteArray(0)
        val out = ByteArray(minOf(length.toLong(), size - position).toInt())
        for ((at, bytes) in parts) {
            val from = maxOf(at, position)
            val to = minOf(at + bytes.size, position + out.size)
            if (from < to) bytes.copyInto(out, (from - position).toInt(), (from - at).toInt(), (to - at).toInt())
        }
        bytesRead += out.size
        return out
    }
}

fun readMp4(bytes: ByteArray): AudioMetadata = read(bytes)?.also { check(it.format == AudioFormat.Mp4) } ?: error("not detected as MP4")
