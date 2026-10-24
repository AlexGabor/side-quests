## 1. Module

- [x] 1.1 Add `lib/audiometadata` with `sidequests.mobile.library`, namespace `com.alexgabor.lib.audiometadata`, `kotlin-test` for `commonTest`, and include it in `settings.gradle.kts`

## 2. Foundation

- [x] 2.1 `ByteSource`, `ByteArraySource`, and an internal bounds-checked `Reader`
- [x] 2.2 Result model: `AudioFormat`, `AudioMetadata`, `AudioTags`, `Cover` (content equality), `Chapter`, and an internal `MetadataBuilder` (trimming, first-value-wins setters)
- [x] 2.3 `AudioMetadataReader.detect`/`read`: skip ID3v2 tags, probe signatures, dispatch to a `FormatParser`, catch exceptions and return the partial result

## 3. MP3

- [x] 3.1 `Id3v2` frame reader: 2.2/2.3/2.4 headers, extended header, whole-tag and per-frame unsynchronisation, 2.4 frame flags, non-syncsafe size fallback, padding, bounded sizes, 2.2→2.3 ID mapping
- [x] 3.2 Text decoding (ISO-8859-1, UTF-16 BOM, UTF-16BE, UTF-8) and text-frame mapping, incl. `TXXX` narrator, genre resolution, year, `n/m` numbers
- [x] 3.3 `APIC`/`PIC` cover selection and MIME normalisation/sniffing
- [x] 3.4 `CHAP`/`CTOC` parsing and chapter tree building (root selection, nesting, ordering, cycles, end fix-up)
- [x] 3.5 `Id3v1`/1.1 and the genre table, as fallback for missing fields
- [x] 3.6 MPEG frame header, first-frame scan, Xing/Info, VBRI and CBR duration

## 4. Fixtures and tests

- [x] 4.1 `scripts/generate-fixtures.sh` (ffmpeg) and the generated files in `src/jvmTest/resources/fixtures/mp3/`
- [x] 4.2 `commonTest` builders (`Id3Builder`, MPEG frame helpers, `CountingSource`)
- [x] 4.3 Tests: detection; each ID3 version; each text encoding; each mapped frame; APIC/PIC; CHAP/CTOC flat and nested, cycles, unordered; ID3v1/1.1 and precedence; unsynchronisation; extended header; Xing, Info, VBRI and CBR durations; bounded reads on a 100 MB virtual source
- [x] 4.4 Malformed input: truncated tag, overrunning frame size, garbage frames, huge declared sizes, failing source, random fuzz bytes — partial results, no throw
- [x] 4.5 `jvmTest` fixture tests: each ffmpeg clip parses to its expected tags, cover, chapters and duration

## 5. Verification

Platforms, as given for this milestone: JVM tests, plus compiling Android and iOS.

- [x] 5.1 `./gradlew :lib:audiometadata:jvmTest` passes
- [x] 5.2 `./gradlew :lib:audiometadata:compileAndroidMain :lib:audiometadata:compileKotlinIosSimulatorArm64 :lib:audiometadata:compileKotlinIosArm64`
- [x] 5.3 Bookplate and Pacer still build: `:bookplate:androidApp:assembleDebug`, `:bookplate:sharedApp:jvmTest`, `:pacer:androidApp:assembleDebug`
