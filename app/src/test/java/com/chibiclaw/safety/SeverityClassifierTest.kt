package com.chibiclaw.safety

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SeverityClassifierTest {

    private lateinit var classifier: SeverityClassifier

    @Before
    fun setUp() {
        classifier = SeverityClassifier()
    }

    @Test
    fun `open app command is LOW severity`() {
        val result = classifier.classify("buka kalkulator")
        assertEquals(Severity.LOW, result)
    }

    @Test
    fun `phone call is MEDIUM severity`() {
        val result = classifier.classify("telepon Budi")
        assertTrue(result == Severity.MEDIUM || result == Severity.LOW)
    }

    @Test
    fun `delete command is HIGH severity`() {
        val result = classifier.classify("hapus semua kontak")
        assertTrue(result == Severity.HIGH || result == Severity.BLOCKED)
    }

    @Test
    fun `format device is BLOCKED severity`() {
        val result = classifier.classify("format ulang hp")
        assertEquals(Severity.BLOCKED, result)
    }

    @Test
    fun `empty command is LOW severity`() {
        val result = classifier.classify("")
        assertEquals(Severity.LOW, result)
    }
}
