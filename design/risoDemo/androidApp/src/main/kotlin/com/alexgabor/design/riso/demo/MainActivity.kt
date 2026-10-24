package com.alexgabor.design.riso.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * The Android host for [RisoDemo].
 *
 * The print effect is a `RuntimeShader`, which only runs under hardware rendering — Layoutlib does
 * not execute one, so the playground cannot be seen in a `@Preview` at all. It needs a device.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent { RisoDemo() }
    }
}
