package org.magnus.bt300headmouse;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String PREFS = "bt300-headmouse";
    private static final String DEFAULT_HOST = "192.168.0.12";
    private static final int DEFAULT_PORT = 39500;
    private static final long SEND_INTERVAL_NS = 20000000L;

    private SensorManager sensorManager;
    private Sensor sensor;
    private HandlerThread senderThread;
    private Handler sender;
    private DatagramSocket socket;
    private InetAddress targetAddress;
    private int targetPort = DEFAULT_PORT;
    private boolean running = false;
    private long lastSendNs = 0;
    private long sequence = 0;
    private float centerYaw = 0;
    private float centerPitch = 0;
    private float centerRoll = 0;
    private float lastYaw = 0;
    private float lastPitch = 0;
    private float lastRoll = 0;

    private EditText hostEdit;
    private EditText portEdit;
    private TextView status;
    private TextView poseView;
    private Button startButton;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if (sensor == null) {
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
        buildUi();
    }

    private void buildUi() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        status = new TextView(this);
        status.setTextSize(18);
        status.setText(sensor == null ? "No rotation vector sensor" : "Ready: " + sensor.getName());
        root.addView(status, fillWrap());

        hostEdit = new EditText(this);
        hostEdit.setSingleLine(true);
        hostEdit.setHint("Debian host");
        hostEdit.setText(prefs.getString("host", DEFAULT_HOST));
        root.addView(hostEdit, fillWrap());

        portEdit = new EditText(this);
        portEdit.setSingleLine(true);
        portEdit.setHint("UDP port");
        portEdit.setText(String.valueOf(prefs.getInt("port", DEFAULT_PORT)));
        root.addView(portEdit, fillWrap());

        startButton = new Button(this);
        startButton.setText("Start");
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (running) {
                    stopSending();
                } else {
                    startSending();
                }
            }
        });
        root.addView(startButton, fillWrap());

        Button recenter = new Button(this);
        recenter.setText("Recenter");
        recenter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                centerYaw = lastYaw;
                centerPitch = lastPitch;
                centerRoll = lastRoll;
                sequence = 0;
            }
        });
        root.addView(recenter, fillWrap());

        poseView = new TextView(this);
        poseView.setTextSize(16);
        poseView.setText("raw yaw 0.0  pitch 0.0  roll 0.0");
        root.addView(poseView, fillWrap());

        setContentView(root);
    }

    private LinearLayout.LayoutParams fillWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private void startSending() {
        if (sensor == null) {
            status.setText("No usable rotation vector sensor");
            return;
        }
        try {
            String host = hostEdit.getText().toString().trim();
            int port = Integer.parseInt(portEdit.getText().toString().trim());
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString("host", host)
                    .putInt("port", port)
                    .apply();
            targetAddress = InetAddress.getByName(host);
            targetPort = port;
            socket = new DatagramSocket();
            senderThread = new HandlerThread("udp-sender");
            senderThread.start();
            sender = new Handler(senderThread.getLooper());
            running = true;
            sequence = 0;
            centerYaw = lastYaw;
            centerPitch = lastPitch;
            centerRoll = lastRoll;
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
            startButton.setText("Stop");
            status.setText("Sending to " + host + ":" + port);
        } catch (Exception e) {
            status.setText("Start failed: " + e.getMessage());
            stopSending();
        }
    }

    private void stopSending() {
        running = false;
        sensorManager.unregisterListener(this);
        if (senderThread != null) {
            senderThread.quitSafely();
            senderThread = null;
        }
        if (socket != null) {
            socket.close();
            socket = null;
        }
        startButton.setText("Start");
        status.setText(sensor == null ? "No rotation vector sensor" : "Stopped");
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopSending();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!running || event.sensor.getType() != sensor.getType()) {
            return;
        }
        if (event.timestamp - lastSendNs < SEND_INTERVAL_NS) {
            return;
        }
        lastSendNs = event.timestamp;

        float[] matrix = new float[9];
        float[] orientation = new float[3];
        SensorManager.getRotationMatrixFromVector(matrix, event.values);
        SensorManager.getOrientation(matrix, orientation);

        lastYaw = (float) Math.toDegrees(orientation[0]);
        lastPitch = (float) Math.toDegrees(orientation[1]);
        lastRoll = (float) Math.toDegrees(orientation[2]);

        final float yaw = angleDelta(lastYaw, centerYaw);
        final float pitch = lastPitch - centerPitch;
        final float roll = lastRoll - centerRoll;
        final long seq = sequence++;

        poseView.setText(String.format(Locale.US,
                "raw yaw %.1f  pitch %.1f  roll %.1f", yaw, pitch, roll));
        sendPose(seq, yaw, pitch, roll);
    }

    private float angleDelta(float value, float center) {
        float delta = value - center;
        while (delta > 180) delta -= 360;
        while (delta < -180) delta += 360;
        return delta;
    }

    private void sendPose(final long seq, final float yaw, final float pitch, final float roll) {
        if (sender == null || socket == null || targetAddress == null) {
            return;
        }
        sender.post(new Runnable() {
            @Override
            public void run() {
                try {
                    String payload = String.format(Locale.US,
                            "{\"seq\":%d,\"yaw\":%.4f,\"pitch\":%.4f,\"roll\":%.4f}",
                            seq, yaw, pitch, roll);
                    byte[] bytes = payload.getBytes(UTF8);
                    DatagramPacket packet = new DatagramPacket(
                            bytes, bytes.length, targetAddress, targetPort);
                    socket.send(packet);
                } catch (Exception ignored) {
                }
            }
        });
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
