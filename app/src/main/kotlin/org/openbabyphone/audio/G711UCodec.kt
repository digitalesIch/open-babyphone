/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Taken from https://android.googlesource.com/platform/external/nist-sip/+/6f95fdeab4481188b6260041b41d1db12b101266/src/com/android/sip/media/G711UCodec.java
 * Adopted to the needs of the Child Monitor project.
 */
package org.openbabyphone.audio

/**
 * G.711 codec. This class provides u-law conversion.
 */
class G711UCodec {
    fun decode(b16: ShortArray, ulaw: ByteArray, count: Int, offset: Int): Int {
        var i = 0
        var j = offset
        while (i < count) {
            b16[i] = table8to16[ulaw[j].toInt() and 0xFF]
            i++
            j++
        }
        return count
    }

    fun encode(b16: ShortArray, count: Int, b8: ByteArray, offset: Int): Int {
        var i = 0
        var j = offset
        while (i < count) {
            b8[j] = table13to8[b16[i].toInt() shr 4 and 0x1FFF]
            i++
            j++
        }
        return count
    }

    companion object {
        // s00000001wxyz...s000wxyz
        // s0000001wxyza...s001wxyz
        // s000001wxyzab...s010wxyz
        // s00001wxyzabc...s011wxyz
        // s0001wxyzabcd...s100wxyz
        // s001wxyzabcde...s101wxyz
        // s01wxyzabcdef...s110wxyz
        // s1wxyzabcdefg...s111wxyz
        private val table13to8 = ByteArray(8192)
        private val table8to16 = ShortArray(256)

        init {
            // b13 --> b8
            var p = 1
            var q = 0
            while (p <= 0x80) {
                var i = 0
                var j = (p shl 4) - 0x10
                while (i < 16) {
                    val v = i + q xor 0x7F
                    val value1 = v.toByte()
                    val value2 = (v + 128).toByte()
                    var m = j
                    val e = j + p
                    while (m < e) {
                        table13to8[m] = value1
                        table13to8[8191 - m] = value2
                        m++
                    }
                    i++
                    j += p
                }
                p = p shl 1
                q += 0x10
            }
            // b8 --> b16
            for (r in 0..7) {
                var i = 0
                var m = r shl 4
                while (i < 16) {
                    val v = (i + 0x10 shl r) - 0x10 shl 3
                    table8to16[m xor 0x7F] = v.toShort()
                    table8to16[(m xor 0x7F) + 128] = (65536 - v).toShort()
                    i++
                    m++
                }
            }
        }
    }
}