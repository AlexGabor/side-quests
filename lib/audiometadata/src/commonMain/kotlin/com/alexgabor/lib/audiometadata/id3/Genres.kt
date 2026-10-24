package com.alexgabor.lib.audiometadata.id3

/** ID3v1 genre names by index: the 80 standard ones, then the Winamp extensions. */
internal object Genres {

    fun name(index: Int): String? = NAMES.getOrNull(index)

    /**
     * An ID3v2 `TCON` value: `(n)` or `n` is a genre index, `(n)Refinement` prefers the refinement,
     * `(RX)` and `(CR)` are Remix and Cover, and `((` escapes a literal parenthesis.
     */
    fun resolve(value: String): String? {
        val s = value.trim()
        if (s.isEmpty()) return null
        if (s.startsWith("((")) return s.substring(1)
        s.toIntOrNull()?.let { return name(it) ?: s }
        val match = REFERENCE.matchEntire(s) ?: return s
        val (code, rest) = match.destructured
        if (rest.isNotBlank()) return resolve(rest)
        return when (code) {
            "RX" -> "Remix"
            "CR" -> "Cover"
            else -> code.toIntOrNull()?.let(::name)
        }
    }

    private val REFERENCE = Regex("""\((\d+|RX|CR)\)(.*)""", RegexOption.DOT_MATCHES_ALL)

    private val NAMES = listOf(
        "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop", "Jazz", "Metal",
        "New Age", "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae", "Rock", "Techno", "Industrial",
        "Alternative", "Ska", "Death Metal", "Pranks", "Soundtrack", "Euro-Techno", "Ambient", "Trip-Hop",
        "Vocal", "Jazz+Funk", "Fusion", "Trance", "Classical", "Instrumental", "Acid", "House", "Game",
        "Sound Clip", "Gospel", "Noise", "Alternative Rock", "Bass", "Soul", "Punk", "Space", "Meditative",
        "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic", "Darkwave", "Techno-Industrial",
        "Electronic", "Pop-Folk", "Eurodance", "Dream", "Southern Rock", "Comedy", "Cult", "Gangsta",
        "Top 40", "Christian Rap", "Pop/Funk", "Jungle", "Native American", "Cabaret", "New Wave",
        "Psychedelic", "Rave", "Showtunes", "Trailer", "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz", "Polka",
        "Retro", "Musical", "Rock & Roll", "Hard Rock",
        // Winamp extensions
        "Folk", "Folk-Rock", "National Folk", "Swing", "Fast Fusion", "Bebop", "Latin", "Revival", "Celtic",
        "Bluegrass", "Avantgarde", "Gothic Rock", "Progressive Rock", "Psychedelic Rock", "Symphonic Rock",
        "Slow Rock", "Big Band", "Chorus", "Easy Listening", "Acoustic", "Humour", "Speech", "Chanson",
        "Opera", "Chamber Music", "Sonata", "Symphony", "Booty Bass", "Primus", "Porn Groove", "Satire",
        "Slow Jam", "Club", "Tango", "Samba", "Folklore", "Ballad", "Power Ballad", "Rhythmic Soul",
        "Freestyle", "Duet", "Punk Rock", "Drum Solo", "A Cappella", "Euro-House", "Dance Hall", "Goa",
        "Drum & Bass", "Club-House", "Hardcore Techno", "Terror", "Indie", "BritPop", "Afro-Punk",
        "Polsk Punk", "Beat", "Christian Gangsta Rap", "Heavy Metal", "Black Metal", "Crossover",
        "Contemporary Christian", "Christian Rock", "Merengue", "Salsa", "Thrash Metal", "Anime", "JPop",
        "Synthpop", "Abstract", "Art Rock", "Baroque", "Bhangra", "Big Beat", "Breakbeat", "Chillout",
        "Downtempo", "Dub", "EBM", "Eclectic", "Electro", "Electroclash", "Emo", "Experimental", "Garage",
        "Global", "IDM", "Illbient", "Industro-Goth", "Jam Band", "Krautrock", "Leftfield", "Lounge",
        "Math Rock", "New Romantic", "Nu-Breakz", "Post-Punk", "Post-Rock", "Psytrance", "Shoegaze",
        "Space Rock", "Trop Rock", "World Music", "Neoclassical", "Audiobook", "Audio Theatre",
        "Neue Deutsche Welle", "Podcast", "Indie Rock", "G-Funk", "Dubstep", "Garage Rock", "Psybient",
    )
}
