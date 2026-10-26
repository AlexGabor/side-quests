package com.alexgabor.bookplate

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.alexgabor.bookplate.core.player.ACTION_SHOW_PLAYING

/**
 * Passes [activity]'s media-notification intents to this [ShowPlaying]: the one it launched with (unless it is
 * being recreated, which already showed it) and each new one.
 */
fun ShowPlaying.follow(activity: ComponentActivity, savedInstanceState: Bundle?) {
    if (savedInstanceState == null) handle(activity.intent)
    activity.addOnNewIntentListener { handle(it) }
}

private fun ShowPlaying.handle(intent: Intent?) {
    if (intent?.action == ACTION_SHOW_PLAYING) request()
}
