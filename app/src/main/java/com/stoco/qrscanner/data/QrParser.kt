package com.stoco.qrscanner.data

sealed interface ParseResult {
    data class Ok(val pn: String, val devEui: String, val appEui: String, val appKey: String) : ParseResult
    data class Invalid(val reason: String) : ParseResult
}

object QrParser {
    private val hex16 = Regex("^[0-9A-F]{16}$")
    private val hex32 = Regex("^[0-9A-F]{32}$")

    /** Parses `PN;DEVEUI;APPEUI;APPKEY`. */
    fun parse(raw: String): ParseResult {
        var parts = raw.trim().split(';').map { it.trim() }
        // Tolerate a trailing separator
        if (parts.size == 5 && parts.last().isEmpty()) parts = parts.dropLast(1)
        if (parts.size != 4) return ParseResult.Invalid("Expected 4 fields, got ${parts.size}")
        val pn = parts[0]
        val devEui = parts[1].uppercase()
        val appEui = parts[2].uppercase()
        val appKey = parts[3].uppercase()
        if (pn.isEmpty()) return ParseResult.Invalid("Empty PN")
        if (!hex16.matches(devEui)) return ParseResult.Invalid("Bad DEVEUI")
        if (!hex16.matches(appEui)) return ParseResult.Invalid("Bad APPEUI")
        if (!hex32.matches(appKey)) return ParseResult.Invalid("Bad APPKEY")
        return ParseResult.Ok(pn, devEui, appEui, appKey)
    }
}
