package com.alexgabor.lib.appstateurl

import com.alexgabor.lib.launch.LaunchParameters
import kotlinx.coroutines.flow.StateFlow

/**
 * The address of what is on screen, where the platform has one the user can see — the page url on
 * the web, and nothing at all anywhere else.
 *
 * Written in the same key/value shape the app is launched with ([LaunchParameters]), so that
 * whatever is put here can be read back by the same code that reads a deep link.
 *
 * Writes merge: the keys given are written over the ones already there and the rest are left
 * alone. That is what lets two parts of the app own different keys without knowing about each
 * other — one writing what is on a screen while another writes which screen it is.
 */
interface AppUrl {

    /** What the address holds now, and again each time the user goes back or forward. */
    val parameters: StateFlow<LaunchParameters>

    /** Writes [parameters] over the current address, in place. */
    fun replace(parameters: LaunchParameters)

    /** The same, but as somewhere to come back to — a history entry of its own. */
    fun push(parameters: LaunchParameters)

    /**
     * Returns to the entry before the last [push], if this address has one of its own to return
     * to — it does not walk out of whatever the app was opened from.
     *
     * True when it was taken, and then [parameters] arrives at the older address in its own time,
     * the same way it would if the user had pressed Back. False when there is nowhere to go, and
     * the caller is the one that has to answer for going back.
     */
    fun back(): Boolean
}
