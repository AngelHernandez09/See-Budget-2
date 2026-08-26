package com.seebudget.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform