package com.alexgabor.bookplate

import android.content.Intent
import com.alexgabor.bookplate.core.player.ACTION_SHOW_PLAYING

/** Whether [intent] is the media notification's: show the playing book, through [App]'s `showPlaying`. */
fun isShowPlaying(intent: Intent?): Boolean = intent?.action == ACTION_SHOW_PLAYING
