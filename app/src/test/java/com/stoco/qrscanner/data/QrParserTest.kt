package com.stoco.qrscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrParserTest {
    @Test fun parsesSample() {
        val r = QrParser.parse("T65N6557936;A84041A99B6410F0;A840410000000100;6AC8054E1F8B88F9FB915D8C8B9CEAF1")
        assertEquals(
            ParseResult.Ok("T65N6557936", "A84041A99B6410F0", "A840410000000100", "6AC8054E1F8B88F9FB915D8C8B9CEAF1"), r,
        )
    }

    @Test fun normalizesCaseAndWhitespace() {
        val r = QrParser.parse(" T1 ; a84041a99b6410f0;a840410000000100;6ac8054e1f8b88f9fb915d8c8b9ceaf1;\n")
        assertTrue(r is ParseResult.Ok)
        assertEquals("A84041A99B6410F0", (r as ParseResult.Ok).devEui)
    }

    @Test fun rejectsWrongFieldCount() {
        assertTrue(QrParser.parse("https://example.com") is ParseResult.Invalid)
    }

    @Test fun rejectsBadHex() {
        assertTrue(QrParser.parse("PN;A84041A99B6410FZ;A840410000000100;6AC8054E1F8B88F9FB915D8C8B9CEAF1") is ParseResult.Invalid)
        assertTrue(QrParser.parse("PN;A84041A99B6410F0;A840410000000100;6AC8054E") is ParseResult.Invalid)
    }
}
