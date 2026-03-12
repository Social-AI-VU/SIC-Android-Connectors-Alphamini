package com.example.alphamini.camera;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class CameraActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final int REQ_CAMERA = 100;
    private static final String TAG = "CameraActivity";
    private static final int SERVER_PORT = 6001;
    private static final String ACTION_STOP = "com.example.alphamini.camera.ACTION_STOP";

    private Camera camera;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;

    private Socket socket;
    private DataOutputStream dataOutputStream;
    private boolean isConnected = false;
    private String SERVER_IP;

    // Background sender thread and frame queue to avoid NetworkOnMainThreadException
    private Thread senderThread;
    private BlockingQueue<FramePacket> frameQueue;

    // Configurable parameters passed via intent extras (with sensible defaults)
    private int frameStride = 1;
    private int queueSize = 1;
    private int jpegQuality = 60;
    private int targetWidth = 0;
    private int targetHeight = 0;
    private float scaleFactor = 1.0f;
    private int frameCounter = 0;

    // Broadcast receiver to allow external controllers (e.g. Termux/SIC) to request shutdown.
    private BroadcastReceiver stopReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // If this activity was launched with a control intent requesting shutdown,
        // honour it immediately and avoid setting up any camera or networking.
        if (handleControlIntent(getIntent())) {
            return;
        }

        // Register a broadcast receiver for external stop requests.
        stopReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) {
                    return;
                }
                if (ACTION_STOP.equals(intent.getAction())) {
                    Log.i(TAG, "Received ACTION_STOP broadcast, shutting down CameraActivity");
                    stopStreaming();
                    if (camera != null) {
                        try {
                            camera.stopPreview();
                        } catch (RuntimeException ignored) {
                        }
                        camera.release();
                        camera = null;
                    }
                    finish();
                }
            }
        };
        registerReceiver(stopReceiver, new IntentFilter(ACTION_STOP));

        surfaceView = new SurfaceView(this);
        setContentView(surfaceView);

        // Read optional configuration extras from the launching intent
        if (getIntent() != null) {
            jpegQuality = getIntent().getIntExtra("jpeg_quality", jpegQuality);
            targetWidth = getIntent().getIntExtra("target_width", targetWidth);
            targetHeight = getIntent().getIntExtra("target_height", targetHeight);
            int scaleInt = getIntent().getIntExtra("scale_factor", 10000); // 1.0 * 10000
            scaleFactor = scaleInt / 10000f;
        }

        if (frameStride < 1) {
            frameStride = 1;
        }
        if (queueSize < 1) {
            queueSize = 1;
        }
        if (jpegQuality < 0) {
            jpegQuality = 0;
        } else if (jpegQuality > 100) {
            jpegQuality = 100;
        }
        if (scaleFactor <= 0.0f) {
            scaleFactor = 1.0f;
        }

        frameQueue = new LinkedBlockingQueue<>(queueSize);

        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }

        // Always use localhost; MiniCameraSensor is running in Termux on the same device.
        SERVER_IP = "127.0.0.1";

        startStreaming();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        // Handle control intents sent to an existing instance (e.g. from Termux via "am start").
        if (handleControlIntent(intent)) {
            return;
        }
    }

    /**
     * Handle control extras sent via the launching intent.
     *
     * Currently supports:
     *  - stop_activity=true : cleanly stop streaming, release camera, and finish the activity.
     *
     * @param intent The intent used to launch or re-launch this activity.
     * @return true if the intent requested an immediate shutdown and was handled.
     */
    private boolean handleControlIntent(Intent intent) {
        if (intent == null) {
            return false;
        }

        boolean shouldStop = intent.getBooleanExtra("stop_activity", false);
        if (!shouldStop) {
            return false;
        }

        Log.i(TAG, "Received stop_activity control intent, shutting down CameraActivity");

        // Clean shutdown path invoked from an external controller (e.g. SIC MiniCameraSensor).
        stopStreaming();
        if (camera != null) {
            try {
                camera.stopPreview();
            } catch (RuntimeException ignored) {
            }
            camera.release();
            camera = null;
        }
        finish();
        return true;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            camera = Camera.open();
            Camera.Parameters params = camera.getParameters();

            // Determine base/default preview size
            Camera.Size base = params.getPreferredPreviewSizeForVideo();
            List<Camera.Size> sizes = params.getSupportedPreviewSizes();
            if (base == null && sizes != null && !sizes.isEmpty()) {
                // Fallback: pick the first supported size as the base
                base = sizes.get(0);
            }

            Camera.Size chosen = base;

            if (sizes != null && !sizes.isEmpty() && base != null) {
                int desiredWidth = base.width;
                int desiredHeight = base.height;

                // If a specific resolution was requested, use that as desired size
                if (targetWidth > 0 && targetHeight > 0) {
                    desiredWidth = targetWidth;
                    desiredHeight = targetHeight;
                }

                // Apply fractional scaling to the desired size, preserving aspect ratio
                if (scaleFactor != 1.0f) {
                    desiredWidth = Math.max(1, Math.round(desiredWidth * scaleFactor));
                    desiredHeight = Math.max(1, Math.round(desiredHeight * scaleFactor));
                }

                // Find the supported size closest to the desired size
                int bestDiff = Integer.MAX_VALUE;
                for (Camera.Size s : sizes) {
                    int diff = Math.abs(s.width - desiredWidth) + Math.abs(s.height - desiredHeight);
                    if (diff < bestDiff) {
                        bestDiff = diff;
                        chosen = s;
                    }
                }

                params.setPreviewSize(chosen.width, chosen.height);
                camera.setParameters(params);
            }

            camera.setPreviewDisplay(holder);
            // Attach a preview callback to stream frames over TCP
            camera.setPreviewCallback(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    sendFrame(data, camera);
                }
            });
            camera.startPreview();
        } catch (IOException | RuntimeException e) {
            Toast.makeText(this, "Unable to open camera: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            Log.e(TAG, "Unable to open camera", e);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // You can adjust camera parameters here if needed.
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }

        stopStreaming();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (stopReceiver != null) {
            try {
                unregisterReceiver(stopReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            stopReceiver = null;
        }
        if (camera != null) {
            camera.release();
            camera = null;
        }
        stopStreaming();
    }

    private void startStreaming() {
        new Thread(() -> {
            try {
                socket = new Socket(SERVER_IP, SERVER_PORT);
                dataOutputStream = new DataOutputStream(socket.getOutputStream());
                isConnected = true;

                // Start background sender thread that pulls frames from the queue and writes to socket
                senderThread = new Thread(() -> {
                    while (isConnected && !Thread.currentThread().isInterrupted()) {
                        try {
                            FramePacket packet = frameQueue.take();
                            if (dataOutputStream == null) {
                                continue;
                            }
                            // Write 8-byte capture timestamp (ms since epoch) followed by 4-byte length and JPEG bytes
                            dataOutputStream.writeLong(packet.timestampMs);
                            dataOutputStream.writeInt(packet.jpegBytes.length);
                            dataOutputStream.write(packet.jpegBytes);
                            dataOutputStream.flush();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } catch (Exception e) {
                            Log.e(TAG, "Sender thread error, reconnecting", e);
                            isConnected = false;
                            stopStreaming();
                            startStreaming();
                            break;
                        }
                    }
                });
                senderThread.start();
            } catch (Exception e) {
                isConnected = false;
                Log.e(TAG, "Socket connect failed", e);
            }
        }).start();
    }

    private void stopStreaming() {
        isConnected = false;

        if (senderThread != null) {
            senderThread.interrupt();
            senderThread = null;
        }

        frameQueue.clear();

        try {
            if (dataOutputStream != null) {
                dataOutputStream.close();
                dataOutputStream = null;
            }
            if (socket != null) {
                socket.close();
                socket = null;
            }
        } catch (IOException ignored) {
        }
    }

    private void sendFrame(byte[] data, Camera camera) {
        if (!isConnected) {
            return;
        }

        try {
            // Optional frame stride to reduce load: send every Nth preview frame
            frameCounter++;
            if (frameCounter % frameStride != 0) {
                return;
            }

            Camera.Parameters params = camera.getParameters();
            Camera.Size size = params.getPreviewSize();
            int width = size.width;
            int height = size.height;
            int format = params.getPreviewFormat();

            if (format != ImageFormat.NV21 && format != ImageFormat.YUY2
                    && format != ImageFormat.NV16) {
                // Unsupported format for YuvImage; skip this frame
                return;
            }

            YuvImage yuvImage = new YuvImage(data, format, width, height, null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // Configurable JPEG quality to trade off size vs. fidelity
            yuvImage.compressToJpeg(new Rect(0, 0, width, height), jpegQuality, baos);
            byte[] jpegBytes = baos.toByteArray();

            // Enqueue frame for background sender thread; drop if queue is full
            FramePacket packet = new FramePacket(System.currentTimeMillis(), jpegBytes);
            frameQueue.offer(packet);
        } catch (Exception e) {
            Log.e(TAG, "Error preparing frame, reconnecting", e);
            isConnected = false;
            stopStreaming();
            startStreaming();
        }
    }

    // Simple container for a single frame and its capture timestamp
    private static class FramePacket {
        final long timestampMs;
        final byte[] jpegBytes;

        FramePacket(long timestampMs, byte[] jpegBytes) {
            this.timestampMs = timestampMs;
            this.jpegBytes = jpegBytes;
        }
    }
}
