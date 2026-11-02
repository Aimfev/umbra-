package com.umbra.client

import android.content.Context

data class Mod(val id: String, val name: String, val desc: String)

val MODS = listOf(
    Mod("clock", "Clock", "Time on screen"),
    Mod("battery", "Battery", "Charge level"),
    Mod("ram", "Memory", "Free RAM"),
    Mod("temp", "Temperature", "Battery temperature"),
    Mod("ping", "Ping", "Latency to the internet"),
    Mod("crosshair", "Crosshair", "Dot in the screen center"),
    Mod("awake", "Keep awake", "Stops the screen from sleeping")
)

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("umbra", 0)
    fun on(c: Context, id: String) = p(c).getBoolean(id, false)
    fun set(c: Context, id: String, v: Boolean) = p(c).edit().putBoolean(id, v).apply()
    fun size(c: Context) = p(c).getInt("size", 56)
    fun setSize(c: Context, v: Int) = p(c).edit().putInt("size", v).apply()
}
