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
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private MicArrayUtils micArrayUtils;
    private boolean isConnected = false;
    private Socket socket;
    private OutputStream outputStream;
    private String SERVER_IP;  // retrieve local IP address
    private static final int SERVER_PORT = 5000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        SERVER_IP = getLocalIpAddress(); // dynamically assign local IP
        Log.d(TAG, "Local IP address: " + SERVER_IP);

        micArrayUtils = new MicArrayUtils(this.getApplicationContext(), 16000, 16, 1024);
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
                        // Try to reconnect if there's an error
                        reconnectToServer();
                    }
                }
            }
        });

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isConnected) {
                    Snackbar.make(view, "Reconnecting to server...", Snackbar.LENGTH_SHORT).show();
                    reconnectToServer();
                } else {
                    Snackbar.make(view, "Already connected and streaming", Snackbar.LENGTH_SHORT).show();
                }
            }
        });

        // Start streaming automatically when the app launches
        startStreaming();
        micArrayUtils.startRecord();
    }

    private void startStreaming() {
        new Thread(() -> {
            try {
                socket = new Socket(SERVER_IP, SERVER_PORT);
                outputStream = socket.getOutputStream();
                isConnected = true;
                Log.d(TAG, "Connected to server");
                runOnUiThread(() -> {
                    Snackbar.make(findViewById(R.id.fab), "Connected to server", Snackbar.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error connecting to server", e);
                isConnected = false;
                runOnUiThread(() -> {
                    Snackbar.make(findViewById(R.id.fab), "Connection failed: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                });
                // Try again after a delay
                try {
                    Thread.sleep(5000);
                    if (!isConnected) {
                        runOnUiThread(() -> startStreaming());
                    }
                } catch (InterruptedException ie) {
                    Log.e(TAG, "Sleep interrupted", ie);
                }
            }
        }).start();
    }

    private void reconnectToServer() {
        stopStreaming();
        startStreaming();
    }

    private void stopStreaming() {
        try {
            isConnected = false;
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
            if (socket != null) {
                socket.close();
                socket = null;
            }
            Log.d(TAG, "Disconnected from server");
        } catch (Exception e) {
            Log.e(TAG, "Error closing connection", e);
        }
    }

    private String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;
    }
    @Override
    protected void onDestroy() {
        micArrayUtils.stopRecord();
        stopStreaming();
        super.onDestroy();
    }
}