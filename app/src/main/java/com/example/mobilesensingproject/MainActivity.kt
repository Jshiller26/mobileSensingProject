package com.example.mobilesensingproject

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    lateinit var recordButton: Button
    lateinit var stopButton: Button
    lateinit var playButton: Button
    lateinit var mr: MediaRecorder
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Audio recordings will get saved to /genreGuesser
        var storagePath = Environment.getExternalStorageDirectory().toString()+"/genreGuesser.mp3"
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
        recordButton.isEnabled = true

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