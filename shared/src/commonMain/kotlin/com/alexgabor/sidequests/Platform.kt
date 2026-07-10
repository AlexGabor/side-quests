package com.alexgabor.sidequests

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform