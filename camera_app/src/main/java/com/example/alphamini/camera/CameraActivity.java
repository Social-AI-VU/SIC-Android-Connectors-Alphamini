package com.example.alphamini.camera;

import android.Manifest;
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

    private Camera camera;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;

    private Socket socket;
    private DataOutputStream dataOutputStream;
    private boolean isConnected = false;
    private String SERVER_IP;

    // Background sender thread and frame queue to avoid NetworkOnMainThreadException
    private Thread senderThread;
    private final BlockingQueue<byte[]> frameQueue = new LinkedBlockingQueue<>(3);
    private int frameCounter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        surfaceView = new SurfaceView(this);
        setContentView(surfaceView);

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
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            camera = Camera.open();
            Camera.Parameters params = camera.getParameters();

            // Choose a smaller preview size to reduce bandwidth and CPU
            Camera.Size chosen = params.getPreferredPreviewSizeForVideo();
            List<Camera.Size> sizes = params.getSupportedPreviewSizes();
            if (sizes != null && !sizes.isEmpty()) {
                // Fallback: pick the smallest available size
                if (chosen == null) {
                    chosen = sizes.get(0);
                    for (Camera.Size s : sizes) {
                        if (s.width * s.height < chosen.width * chosen.height) {
                            chosen = s;
                        }
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
                            byte[] jpegBytes = frameQueue.take();
                            if (dataOutputStream == null) {
                                continue;
                            }
                            dataOutputStream.writeInt(jpegBytes.length);
                            dataOutputStream.write(jpegBytes);
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
            // Drop 2 out of every 3 frames to reduce load
            frameCounter++;
            if (frameCounter % 3 != 0) {
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
            // Lower JPEG quality to reduce size and latency
            yuvImage.compressToJpeg(new Rect(0, 0, width, height), 60, baos);
            byte[] jpegBytes = baos.toByteArray();

            // Enqueue frame for background sender thread; drop if queue is full
            frameQueue.offer(jpegBytes);
        } catch (Exception e) {
            Log.e(TAG, "Error preparing frame, reconnecting", e);
            isConnected = false;
            stopStreaming();
            startStreaming();
        }
    }
}

