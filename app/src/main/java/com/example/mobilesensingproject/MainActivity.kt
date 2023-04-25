package com.example.mobilesensingproject

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class MainActivity : AppCompatActivity() {

    lateinit var recordButton: Button
    lateinit var stopButton: Button
    lateinit var playButton: Button
    lateinit var mr: MediaRecorder
    lateinit var yamNetModel: MappedByteBuffer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Audio recordings will get saved to /genreGuesser
        var storagePath = applicationContext.filesDir.absolutePath + "/genreGuesser.mp3"
        mr = MediaRecorder()

        recordButton = findViewById(R.id.button)
        stopButton = findViewById(R.id.button2)
        playButton = findViewById(R.id.button3)

        // Initially disable buttons
        recordButton.isEnabled = false
        stopButton.isEnabled = false

        // Request permission to record audio and save audio files
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE), 111)
        else
            recordButton.isEnabled = true

        // Load the YamNet model from the assets folder
        try {
            val assetFileDescriptor = assets.openFd("lite-model_yamnet_tflite_1.tflite")
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            yamNetModel = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Record audio when record button is clicked
        recordButton.setOnClickListener{
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            // Save audio in mp3 format to "storagePath"
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(16 * 44100)
            mr.setAudioSamplingRate(44100)
            mr.setOutputFile(storagePath)
            // Start recording and enable stop button
            mr.prepare()
            mr.start()
            stopButton.isEnabled = true
            recordButton.isEnabled = false
        }

        // Stop recording when stop button is clicked
        stopButton.setOnClickListener{
            mr.stop()

            // Feed the YamNet model
            val audioBuffer = loadAudioFile(storagePath)
            val results = runYamNet(audioBuffer)
            val topResult = getTopResult(results)

            // Display guess in label
//            Toast.makeText(this, "Genre guess: ${topResult.label}", Toast.LENGTH_SHORT).show()

            recordButton.isEnabled = true
            stopButton.isEnabled = false
        }

        // Play back when play button is clicked
        playButton.setOnClickListener{
            var mp = MediaPlayer()
            mp.setDataSource(storagePath)
            mp.prepare()
            mp.start()
        }
    }
    // Audio needs to be in byteArray form for yamNet to read it
    private fun loadAudioFile(path: String): ByteArray {
        val file = File(path)
        val inputStream = FileInputStream(file)
        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var length: Int
        while (inputStream.read(buffer).also { length = it } != -1) {
            outputStream.write(buffer, 0, length)
        }
        outputStream.close()
        inputStream.close()
        return outputStream.toByteArray()
    }

    // Run the yamNet model on the converted audio and return results
    private fun runYamNet(audioBuffer: ByteArray): Array<FloatArray> {
        // Run the yamNet model with interpreter
        val yamNet = Interpreter(yamNetModel)
        // Get tensor models shape and size
        val inputShape = yamNet.getInputTensor(0).shape()
        val inputSize = inputShape[1]
        val outputSize = yamNet.getOutputTensor(0).shape()[1]

        // Allocate a direct ByteBuffer and
        // Order the bytes in native byte order and create a ShortBuffer
        val inputBuffer = ByteBuffer.allocateDirect(inputSize * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer()
        // Convert from bytes to shorts and put input onto the input buffeer
        for (i in 0 until inputSize) {
            val x = (audioBuffer[i * 2 + 1].toInt() shl 8) or (audioBuffer[i * 2].toInt() and 0xff)
            inputBuffer.put(x.toShort())
        }

        // Allocate a direct ByteBuffer and
        // Order the bytes in native byte order and create a ShortBuffer
        val outputBuffer = ByteBuffer.allocateDirect(outputSize * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        // Run yamNet model using input buffer and store on the output buffer
        yamNet.run(inputBuffer, outputBuffer)
        // store results on array of float arrays
        val results = Array(outputSize) { FloatArray(1) }
        for (i in 0 until outputSize) {
            results[i][0] = outputBuffer.get(i)
        }
        return results
    }

    private fun getTopResult(results: Array<FloatArray>): Pair<Float, String> {
        // Get the index of the maximum value in the results array
        val maxIndex = results.indices.maxByOrNull { results[it][0] } ?: -1

        // Return the result at the maximum index
        // Will return confidence score and label of sound playing
        return Pair(results[maxIndex][0], results[maxIndex][1].toString())
    }

    // If the user gives permission to record, enable recordButton
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == 111 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            recordButton.isEnabled = true
    }



}