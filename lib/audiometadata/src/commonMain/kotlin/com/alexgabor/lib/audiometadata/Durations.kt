package com.alexgabor.lib.audiometadata

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/** [value] ÷ [timescale], as whole seconds plus the remainder so 64-bit values can't overflow. */
internal fun durationOf(value: Long?, timescale: Long?): Duration? {
    if (value == null || timescale == null || value <= 0 || timescale <= 0) return null
    return (value / timescale).seconds + (value % timescale * 1_000_000_000 / timescale).nanoseconds
}
