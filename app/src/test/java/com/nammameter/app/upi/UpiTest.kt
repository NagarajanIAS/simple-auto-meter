package com.nammameter.app.upi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class UpiTest {
    @Test
    fun `valid and invalid VPAs`() {
        listOf("ravi@okaxis", "ravi.kumar-1@ybl", "9876543210@paytm", "a_b@upi").forEach {
            assertTrue(it, Upi.isValidVpa(it))
        }
        listOf("", "ravi", "@okaxis", "ravi@", "ravi@@ok", "ra vi@ok", "ravi@ok axis", "r@1bank", "x@y").forEach {
            assertFalse(it, Upi.isValidVpa(it))
        }
    }

    @Test
    fun `pay uri with amount`() {
        val uri = Upi.payUri("ravi@okaxis", "Ravi Kumar", BigDecimal("70"), "Auto fare 28-10-2026 14:05")
        assertEquals(
            "upi://pay?pa=ravi@okaxis&pn=Ravi%20Kumar&am=70.00&cu=INR&tn=Auto%20fare%2028-10-2026%2014%3A05",
            uri,
        )
    }

    @Test
    fun `static pay uri has no amount`() {
        assertEquals("upi://pay?pa=ravi@okaxis&pn=Ravi&cu=INR", Upi.payUri("ravi@okaxis", "Ravi", null, null))
    }

    @Test
    fun `tamil payee name is percent encoded`() {
        val uri = Upi.payUri("a@ybl", "ரவி", BigDecimal("12.5"), null)
        assertTrue(uri.contains("pn=%E0%AE%B0%E0%AE%B5%E0%AE%BF"))
        assertTrue(uri.contains("am=12.50"))
    }

    @Test
    fun `pin hashing`() {
        assertTrue(PinHasher.isValidPin("0123"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("12a4"))
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("4321", salt)
        assertTrue(PinHasher.verify("4321", salt, hash))
        assertFalse(PinHasher.verify("4322", salt, hash))
    }
}
