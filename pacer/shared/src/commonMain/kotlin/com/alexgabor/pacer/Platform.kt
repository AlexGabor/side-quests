package com.alexgabor.pacer

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform