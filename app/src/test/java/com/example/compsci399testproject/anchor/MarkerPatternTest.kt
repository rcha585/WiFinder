package com.example.compsci399testproject.anchor

import com.example.compsci399testproject.anchor.session.MarkerPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MarkerPatternTest {
    @Test fun matchesAnchorWebHashVector() {
        assertEquals(3_535_589_556L, MarkerPattern.fnv1a("ABCD1234:A").toLong())
    }

    @Test fun anchorPatternsAreDistinct() {
        assertFalse(MarkerPattern.bits("ABCD1234", "A").contentEquals(MarkerPattern.bits("ABCD1234", "B")))
    }
}
