package com.example.mobilesensingproject

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
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
            val (results, labelMap) = runYamNet(audioBuffer)
            val topResultPair = getTopResult(results, labelMap)
            val label = topResultPair.second

            // Display guess in label
            Toast.makeText(this, "Genre guess: $label", Toast.LENGTH_SHORT).show()

            recordButton.isEnabled = true
            stopButton.isEnabled = false
            mr.release()
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
    private fun runYamNet(audioBuffer: ByteArray): Pair<Array<FloatArray>, Array<String>> {
        // Load the label file
        val labelFile = assets.open("yamnet_class_map.csv")
        val labelLines = labelFile.bufferedReader().readLines()
        val labelMap = labelLines.map { line -> line.split(",")[1] }.toTypedArray()

        // Run the yamNet model with interpreter
        val yamNet = Interpreter(yamNetModel)

        // Get tensor models shape and size
        val inputShape = yamNet.getInputTensor(0).shape()
        val inputLength = inputShape[0]

        // Convert the ByteArray object to a ShortBuffer object
        val shortBuffer = ByteBuffer.wrap(audioBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        // Normalize the values in the ShortBuffer to the range [-1, 1]
        val normalizationFactor = Short.MAX_VALUE.toFloat()
        val normalizedBuffer = FloatBuffer.allocate(shortBuffer.capacity())
        for (i in 0 until shortBuffer.capacity()) {
            normalizedBuffer.put(i, shortBuffer.get(i) / normalizationFactor)
        }

        // Convert the normalized ShortBuffer to a ByteBuffer object
        val inputBuffer = ByteBuffer.allocateDirect(4096).order(ByteOrder.nativeOrder())
        val floatBuffer = inputBuffer.asFloatBuffer()
        floatBuffer.put(normalizedBuffer)

        // Run yamNet model using input buffer and store on the output buffer
        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 1024), DataType.FLOAT32)
        yamNet.run(inputBuffer, outputBuffer.buffer)

        // store results on array of float arrays
        val results = Array(outputBuffer.floatArray.size) { FloatArray(1) }
        for (i in results.indices) {
            results[i][0] = outputBuffer.floatArray[i]
        }

        // Return the results and label map
        return Pair(results, labelMap)
    }

    private fun getTopResult(results: Array<FloatArray>, labelMap: Array<String>): Pair<Float, String> {
        // Get the index of the maximum value in the results array
        val maxIndex = results.indices.maxByOrNull { results[it][0] } ?: -1

        // Get the label corresponding to the maximum index
        val label = labelMap[maxIndex]

        // Return the result at the maximum index along with the corresponding label
        // Will return confidence score and label of sound playing
        return Pair(results[maxIndex][0], label ?: "Unknown")
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