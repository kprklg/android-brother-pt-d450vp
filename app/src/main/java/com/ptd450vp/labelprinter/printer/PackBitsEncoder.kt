package com.ptd450vp.labelprinter.printer

import java.io.ByteArrayOutputStream

/**
 * TIFF PackBits (RLE) encoder & decoder used by Brother P-touch Raster compression mode (`M 0x02`).
 *
 * Format:
 * - Header byte `n` in 0..127: copy the next `n + 1` literal bytes.
 * - Header byte `n` in -1..-127 (unsigned 255..129): repeat the next single byte `1 - n` times (2..128).
 * - Header byte `-128` (0x80): no-op.
 */
object PackBitsEncoder {

    fun encode(input: ByteArray): ByteArray {
        if (input.isEmpty()) return ByteArray(0)
        val out = ByteArrayOutputStream(input.size + 4)
        var i = 0
        val n = input.size

        while (i < n) {
            // Check for a repeat run of at least 2 identical bytes
            var runLen = 1
            while (i + runLen < n && runLen < 128 && input[i + runLen] == input[i]) {
                runLen++
            }

            if (runLen >= 2) {
                out.write((257 - runLen) and 0xFF)
                out.write(input[i].toInt() and 0xFF)
                i += runLen
            } else {
                // Collect literal bytes until we hit a run of >= 2 identical bytes or reach 128 bytes
                val literalStart = i
                var literalLen = 0
                while (i < n && literalLen < 128) {
                    if (i + 1 < n && input[i] == input[i + 1]) {
                        // Break out to encode the repeat run instead
                        break
                    }
                    i++
                    literalLen++
                }
                out.write((literalLen - 1) and 0xFF)
                out.write(input, literalStart, literalLen)
            }
        }
        return out.toByteArray()
    }

    fun decode(encoded: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < encoded.size) {
            val header = encoded[i++].toInt() // signed byte -128..127
            when {
                header in 0..127 -> {
                    val count = header + 1
                    require(i + count <= encoded.size) { "Truncated PackBits literal run" }
                    out.write(encoded, i, count)
                    i += count
                }
                header in -127..-1 -> {
                    val count = 1 - header
                    require(i < encoded.size) { "Truncated PackBits repeat run" }
                    val value = encoded[i++].toInt() and 0xFF
                    repeat(count) { out.write(value) }
                }
                else -> {
                    // -128 (0x80) is a no-op in TIFF PackBits
                }
            }
        }
        return out.toByteArray()
    }
}
