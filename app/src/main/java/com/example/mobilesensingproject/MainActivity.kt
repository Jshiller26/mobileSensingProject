package com.example.mobilesensingproject

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
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
    lateinit var yamnetModel: Interpreter
    lateinit var labelList: List<String>

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

        //Load yamnet model from the assets folder
        val yamnetModelFileDescriptor = assets.openFd("lite-model_yamnet_tflite_1.tflite")
        val inputStream = FileInputStream(yamnetModelFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = yamnetModelFileDescriptor.startOffset
        val declaredLength = yamnetModelFileDescriptor.declaredLength
        val yamnetModelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        val yamnetModelOptions = Interpreter.Options()
        yamnetModel = Interpreter(yamnetModelBuffer, yamnetModelOptions)


        // Load the YAMNet class map from the assets folder
        val labelFile = assets.open("yamnet_class_map.csv")
        labelList = labelFile.bufferedReader().readLines()

        // Define audio parameters
        val audioSource = MediaRecorder.AudioSource.MIC
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        // Initialize AudioRecord object
        val audioRecorder = AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, bufferSize)

        // Initialize audioProcessor
        val audioProcessor = AudioProcessor(sampleRate, 16000, 10)
        val audioBuffer = ByteBuffer.allocateDirect(bufferSize * 2)


        // Record audio when record button is clicked
        // Start recording when record button is clicked
        recordButton.setOnClickListener{
            audioRecorder.startRecording()
            stopButton.isEnabled = true
            recordButton.isEnabled = false
        }

        // Stop recording when stop button is clicked
        stopButton.setOnClickListener{
            audioRecorder.stop()
            audioRecorder.release()

            // Read audio data from AudioRecord object
            audioBuffer.rewind()
            audioRecorder.read(audioBuffer, bufferSize * 2)
            val audioData = ByteArray(bufferSize * 2)
            audioBuffer.rewind()
            audioBuffer.get(audioData)
            audioBuffer.clear()

            val processedData = audioProcessor.process(audioData)

            val outputBuffer = Array(20) { FloatArray(labelList.size - 1) }
            yamnetModel.run(processedData, outputBuffer)
            var maxIndex = -1
            var maxPrediction = Float.MIN_VALUE
            for (i in 0 until outputBuffer.size) {
                val prediction = outputBuffer[i]
                for (j in 0 until prediction.size) {
                    if (prediction[j] > maxPrediction) {
                        maxPrediction = prediction[j]
                        maxIndex = j
                    }
                }
            }
            val prediction = labelList[maxIndex]
            val predictedLabel = prediction.split(",")[2]
            Log.d("GenrePrediction", prediction)
            val genreTextView = findViewById<TextView>(R.id.textView2)
            genreTextView.text = predictedLabel

            // Enable record button
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