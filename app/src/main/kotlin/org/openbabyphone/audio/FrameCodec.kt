/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Open Babyphone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Open Babyphone. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openbabyphone.audio

import org.openbabyphone.CryptoHelper
import android.util.Log

object FrameCodec {
    private const val TAG = "FrameCodec"
    const val HEADER_SIZE = 11
    const val AUTH_TAG_SIZE = 16
    const val FLAG_AUDIO = 0x01.toByte()
    const val FLAG_HEARTBEAT = 0x02.toByte()

    data class DecodedFrame(
        val seqNum: Int,
        val timestampMs: Int,
        val ulawData: ByteArray,
        val isHeartbeat: Boolean
    )

    fun encodeFrame(ulawData: ByteArray, seqNum: Int, timestampMs: Int, key: ByteArray?, sessionId: ByteArray): ByteArray {
        val isHeartbeat = ulawData.isEmpty()
        val flags = if (isHeartbeat) FLAG_HEARTBEAT else FLAG_AUDIO
        
        if (key != null && !isHeartbeat) {
            val encrypted = CryptoHelper.encryptChunk(ulawData, key, sessionId, seqNum.toLong())
            val frameSize = HEADER_SIZE + encrypted.size
            val frame = ByteArray(frameSize)
            
            var offset = 0
            frame[offset++] = flags
            frame[offset++] = ((seqNum ushr 24) and 0xFF).toByte()
            frame[offset++] = ((seqNum ushr 16) and 0xFF).toByte()
            frame[offset++] = ((seqNum ushr 8) and 0xFF).toByte()
            frame[offset++] = (seqNum and 0xFF).toByte()
            frame[offset++] = ((timestampMs ushr 24) and 0xFF).toByte()
            frame[offset++] = ((timestampMs ushr 16) and 0xFF).toByte()
            frame[offset++] = ((timestampMs ushr 8) and 0xFF).toByte()
            frame[offset++] = (timestampMs and 0xFF).toByte()
            frame[offset++] = ((encrypted.size ushr 8) and 0xFF).toByte()
            frame[offset++] = (encrypted.size and 0xFF).toByte()
            System.arraycopy(encrypted, 0, frame, offset, encrypted.size)
            
            return frame
        } else {
            val payloadSize = ulawData.size
            val frameSize = HEADER_SIZE + payloadSize
            val frame = ByteArray(frameSize)
            
            var offset = 0
            frame[offset++] = flags
            frame[offset++] = ((seqNum ushr 24) and 0xFF).toByte()
            frame[offset++] = ((seqNum ushr 16) and 0xFF).toByte()
            frame[offset++] = ((seqNum ushr 8) and 0xFF).toByte()
            frame[offset++] = (seqNum and 0xFF).toByte()
            frame[offset++] = ((timestampMs ushr 24) and 0xFF).toByte()
            frame[offset++] = ((timestampMs ushr 16) and 0xFF).toByte()
            frame[offset++] = ((timestampMs ushr 8) and 0xFF).toByte()
            frame[offset++] = (timestampMs and 0xFF).toByte()
            frame[offset++] = ((payloadSize ushr 8) and 0xFF).toByte()
            frame[offset++] = (payloadSize and 0xFF).toByte()
            System.arraycopy(ulawData, 0, frame, offset, payloadSize)
            
            return frame
        }
    }

    fun encodeHeartbeat(seqNum: Int, timestampMs: Int): ByteArray {
        val frame = ByteArray(HEADER_SIZE)
        
        frame[0] = FLAG_HEARTBEAT
        frame[1] = ((seqNum ushr 24) and 0xFF).toByte()
        frame[2] = ((seqNum ushr 16) and 0xFF).toByte()
        frame[3] = ((seqNum ushr 8) and 0xFF).toByte()
        frame[4] = (seqNum and 0xFF).toByte()
        frame[5] = ((timestampMs ushr 24) and 0xFF).toByte()
        frame[6] = ((timestampMs ushr 16) and 0xFF).toByte()
        frame[7] = ((timestampMs ushr 8) and 0xFF).toByte()
        frame[8] = (timestampMs and 0xFF).toByte()
        frame[9] = 0x00
        frame[10] = 0x00
        
        return frame
    }

    fun decodeFrame(header: FrameHeader, payload: ByteArray, key: ByteArray?, sessionId: ByteArray): DecodedFrame? {
        val isHeartbeat = header.flags == FLAG_HEARTBEAT
        
        if (isHeartbeat) {
            return DecodedFrame(header.seqNum, header.timestampMs, byteArrayOf(), true)
        }
        
        val ulawData: ByteArray
        if (key != null) {
            val decrypted = CryptoHelper.decryptChunk(payload, key, sessionId, header.seqNum.toLong())
                ?: run {
                    Log.e(TAG, "Decryption failed for frame ${header.seqNum}")
                    return null
                }
            ulawData = decrypted
        } else {
            ulawData = payload
        }
        
        return DecodedFrame(header.seqNum, header.timestampMs, ulawData, false)
    }
}
