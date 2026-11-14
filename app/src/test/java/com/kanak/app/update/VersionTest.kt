package com.kanak.app.update

import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test
    fun `compares numeric versions rather than text`() {
        assertTrue(Version.parse("1.10")!! > Version.parse("1.2")!!)
        assertTrue(Version.parse("2.0")!! > Version.parse("1.99.99")!!)
    }

    @Test
    fun `treats omitted trailing parts as zero`() {
        assertTrue(Version.parse("1.2")!!.compareTo(Version.parse("1.2.0")!!) == 0)
    }

    @Test
    fun `rejects non numeric versions`() {
        assertTrue(Version.parse("v1.2") == null)
        assertTrue(Version.parse("1.two") == null)
    }
}
