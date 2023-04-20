package com.example.mobilesensingproject

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import androidx.core.app.ActivityCompat
import java.io.FileInputStream
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