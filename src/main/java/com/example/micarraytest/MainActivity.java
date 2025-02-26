package com.example.micarraytest;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import com.ubt.speecharray.DataCallback;
import com.ubt.speecharray.MicArrayUtils;
import java.io.OutputStream;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private MicArrayUtils micArrayUtils;
    private boolean isRecording = false;
    private Socket socket;
    private OutputStream outputStream;
    private static final String SERVER_IP = "10.0.0.107"; // Change to your PC/server IP
    private static final int SERVER_PORT = 5000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        micArrayUtils = new MicArrayUtils(this.getApplicationContext(), 16000, 16, 512);
        micArrayUtils.init();
        micArrayUtils.setDataCallback(new DataCallback() {
            @Override
            public void onAudioData(byte[] bytes) {
                Log.d(TAG, "onAudioData - bytes.length = " + bytes.length);
                if (outputStream != null) {
                    try {
                        outputStream.write(bytes);
                        outputStream.flush();
                    } catch (Exception e) {
                        Log.e(TAG, "Error sending audio data", e);
                    }
                }
            }
        });

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isRecording) {
                    startStreaming();
                    micArrayUtils.startRecord();
                    isRecording = true;
                    Snackbar.make(view, "Streaming started", Snackbar.LENGTH_SHORT).show();
                } else {
                    micArrayUtils.stopRecord();
                    stopStreaming();
                    isRecording = false;
                    Snackbar.make(view, "Streaming stopped", Snackbar.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void startStreaming() {
        new Thread(() -> {
            try {
                socket = new Socket(SERVER_IP, SERVER_PORT);
                outputStream = socket.getOutputStream();
                Log.d(TAG, "Connected to server");
            } catch (Exception e) {
                Log.e(TAG, "Error connecting to server", e);
            }
        }).start();
    }

    private void stopStreaming() {
        try {
            if (outputStream != null) {
                outputStream.close();
            }
            if (socket != null) {
                socket.close();
            }
            Log.d(TAG, "Disconnected from server");
        } catch (Exception e) {
            Log.e(TAG, "Error closing connection", e);
        }
    }
}