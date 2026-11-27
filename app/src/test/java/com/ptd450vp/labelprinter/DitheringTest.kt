package com.ptd450vp.labelprinter

import com.ptd450vp.labelprinter.model.DitherMode
import com.ptd450vp.labelprinter.render.Dithering
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DitheringTest {

    @Test
    fun thresholdConvertsBlackAndWhiteWithInversion() {
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        val pixels = intArrayOf(black, white)

        val normal = Dithering.toMonochrome(pixels, 2, 1, DitherMode.THRESHOLD, threshold = 128, invert = false)
        assertTrue(normal[0])
        assertFalse(normal[1])

        val inverted = Dithering.toMonochrome(pixels, 2, 1, DitherMode.THRESHOLD, threshold = 128, invert = true)
        assertFalse(inverted[0])
        assertTrue(inverted[1])
    }

    @Test
    fun floydSteinbergProducesBalancedDotsOnMidGray() {
        val midGray = 0xFF808080.toInt()
        val pixels = IntArray(16 * 16) { midGray }
        val result = Dithering.toMonochrome(pixels, 16, 16, DitherMode.FLOYD_STEINBERG, threshold = 128)
        val blackCount = result.count { it }
        // Mid-gray should produce roughly 40%-60% black dots
        assertTrue("Expected balanced dithering, got $blackCount/256", blackCount in 90..166)
    }
}
