package com.alexgabor.design.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
internal sealed interface Root : NavKey {
    @Serializable
    data object Home : Root

    @Serializable
    data class Detail(val id: String) : Root
}

@Serializable
internal sealed interface Child : NavKey {
    @Serializable
    data object Overview : Child
}
