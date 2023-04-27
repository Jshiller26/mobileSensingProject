package com.example.mobilesensingproject

import android.media.AudioFormat
import android.media.AudioRecord
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioProcessor(private val sampleRate: Int, private val desiredSampleRate: Int, private val desiredDuration: Int) {
    private val shortBufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    private val floatBufferSize = shortBufferSize / 2
    private val desiredFloatBufferSize = desiredSampleRate * desiredDuration

    fun process(audioData: ByteArray): FloatArray {
        val shortBuffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val floatBuffer = FloatArray(floatBufferSize)
        val processedBuffer = FloatArray(desiredFloatBufferSize)

        // Downsample to desired sample rate
        val downsampleRatio = sampleRate.toFloat() / desiredSampleRate.toFloat()
        var bufferIndex = 0
        var processedIndex = 0
        while (bufferIndex < shortBuffer.limit() && processedIndex < processedBuffer.size) {
            val originalIndex = (bufferIndex * downsampleRatio).toInt()
            floatBuffer[bufferIndex % floatBufferSize] = shortBuffer[originalIndex].toFloat() / 32767.0f
            if (bufferIndex % floatBufferSize == floatBufferSize - 1) {
                // Normalize audio data to range [-1, 1]
                val mean = floatBuffer.average()
                val stdDev = Math.sqrt(floatBuffer.map { (it - mean) * (it - mean) }.average().toDouble())
                for (j in 0 until floatBufferSize) {
                    processedBuffer[processedIndex + j] = (floatBuffer[j] - mean).toFloat() / stdDev.toFloat()
                }
                processedIndex += floatBufferSize
            }
            bufferIndex++
        }

        return processedBuffer
    }

}
