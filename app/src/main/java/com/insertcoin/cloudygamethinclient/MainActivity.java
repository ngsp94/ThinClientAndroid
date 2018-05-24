package com.insertcoin.cloudygamethinclient;

import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;

// TODO: Implement join game with TCP!
public class MainActivity extends AppCompatActivity
        implements TextureView.SurfaceTextureListener, SensorEventListener {

    static final String TAG = "Main";
    static final String AVC = MediaFormat.MIMETYPE_VIDEO_AVC;
    static final int ACC_THRESHOLD = 2;

    Conf configs;
    H264StreamThread streamThread;
    DecodeTask decodeTask;
    MediaCodec decoder;
    TextureView texView;
    SensorManager sensorMgr;
    Sensor accelerometer;
    float[] accVals = null;
    float x_pos;
    float y_pos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        texView = findViewById(R.id.textureView);
        texView.setSurfaceTextureListener(this);
        try {
            configs = this.new Conf();
        } catch (IOException e) {
            log(Log.getStackTraceString(e));
            System.exit(-1);
        } catch (IllegalAccessException e) {
            log("Missing config: " + e.getMessage());
            System.exit(-1);
        }
        log("starting...");
        GameSignalRunnable sendJoin =
            new GameSignalRunnable(configs, 0, GameSignalRunnable.Command.JOIN);
        Thread joinThread = new Thread(sendJoin);
        joinThread.start();

        streamThread = new H264StreamThread(configs);
        streamThread.start();
        /*
        sensorMgr = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorMgr.registerListener(this, accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL);
                */
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture tex, int w, int h) {
        while (streamThread == null || !(streamThread.headersReady()))
            sleep(100);

        decodeTask = new DecodeTask();
        decodeTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) {
        log("texture changed, decoding stopped!");
        GameSignalRunnable sendQuit =
            new GameSignalRunnable(configs, 0, GameSignalRunnable.Command.QUIT);
        Thread quitThread = new Thread(sendQuit);
        quitThread.start();
        decodeTask.cancel(false);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        // This is triggered by back button, menu button etc
        log("texture destroyed, decoding stopped!");
        GameSignalRunnable sendQuit =
            new GameSignalRunnable(configs, 0, GameSignalRunnable.Command.QUIT);
        Thread quitThread = new Thread(sendQuit);
        quitThread.start();
        decodeTask.cancel(false);
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {}

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        Sensor sensor = sensorEvent.sensor;
        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if (accVals != null) {
                float[] diff = minus(sensorEvent.values, accVals);
                // send diff to server
                if (diff[1] > ACC_THRESHOLD || diff[2] > ACC_THRESHOLD) {
                    /**
                     * 8-bit  unsigned version number (0 for now)
                     * 8-bit  unsigned protocol type: keyboard(1), mouse(2)
                     * 8-bit  unsigned controller ID
                     * 16-bit signed   x-axis movement
                     * 16-bit signed   y-axis movement
                     * 32-bit signed   x-axis position
                     * 32-bit signed   y-axis position
                     */
                    ByteArrayOutputStream packet = new ByteArrayOutputStream();
                    // Note that Java has no unsigned primitives!!
                    byte version = 0;
                    byte protocol = 2;
                    byte ctrlID = 0;
                    short x_mov = (short)(diff[1] * configs.width);
                    short y_mov = (short)(diff[2] * configs.height);
                    log("acc: " + x_mov + " " + y_mov);
                    x_pos += x_mov;
                    y_pos += y_mov;
                    packet.write(version);
                    packet.write(protocol);
                    packet.write(ctrlID);
                    // network byte order is big endian
                    byte[] b_x_mov = {(byte)(x_mov >> 8), (byte)(x_mov)};
                    packet.write(b_x_mov, 0, 2);
                    byte[] b_y_mov = {(byte)(y_mov >> 8), (byte)(y_mov)};
                    packet.write(b_y_mov, 0, 2);
                    ByteBuffer b_x_pos = ByteBuffer.allocate(4);
                    b_x_pos.putFloat(x_pos);
                    packet.write(b_x_pos.array(), 0, 4);
                    ByteBuffer b_y_pos = ByteBuffer.allocate(4);
                    b_y_pos.putFloat(y_pos);
                    packet.write(b_y_pos.array(), 0, 4);
                    sendPacket(packet.toByteArray());
                }
            } else {
                accVals = new float[3];
            }
            System.arraycopy(sensorEvent.values, 0, accVals, 0, 3);
        }
    }

    private void sendPacket(byte[] data) {
        // need to do this on another thread since it's network...
        UDPRunnable sendUDP = new UDPRunnable(configs, data);
        Thread UDPThread = new Thread(sendUDP);
        UDPThread.start();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private class DecodeTask extends AsyncTask<Void, Void, Void> {
        byte[] frame;
        ArrayBlockingQueue<Integer> inputIndices = new ArrayBlockingQueue<>(30);

        @Override
        protected Void doInBackground(Void... voids) {
            MediaFormat format = MediaFormat.createVideoFormat(
                    AVC, configs.width, configs.height);
            format.setByteBuffer("csd-0", streamThread.getSPS());
            format.setByteBuffer("csd-1", streamThread.getPPS());

            try {
                decoder = MediaCodec.createDecoderByType(AVC);
                frame = streamThread.nextPacket();
            } catch (IOException e) {
                log(Log.getStackTraceString(e));
            }

            decoder.setCallback(new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(@NonNull MediaCodec mc, int i) {
                    inputIndices.add(i);
                }

                @Override
                public void onOutputBufferAvailable(@NonNull MediaCodec mc, int i,
                                                    @NonNull MediaCodec.BufferInfo bufferInfo) {
                    decoder.releaseOutputBuffer(i, true);
                }

                @Override
                public void onError(@NonNull MediaCodec mediaCodec,
                                    @NonNull MediaCodec.CodecException e) {
                    log(Log.getStackTraceString(e));
                }

                @Override
                public void onOutputFormatChanged(@NonNull MediaCodec mediaCodec,
                                                  @NonNull MediaFormat mediaFormat) {

                }
            });

            Surface surface = new Surface(texView.getSurfaceTexture());
            decoder.configure(format, surface, null, 0);
            decoder.start();
            while (!isCancelled()) {
                try {
                    frame = streamThread.nextPacket();
                    int i = inputIndices.take();
                    ByteBuffer inputBuffer = decoder.getInputBuffer(i);
                    inputBuffer.put(frame);
                    decoder.queueInputBuffer(i, 0, frame.length, 0, 0);
                } catch (IOException e) {
                    log("Could not get next packet");
                    log(Log.getStackTraceString(e));
                } catch (InterruptedException e) {
                    log("Input buffer not available");
                    log(Log.getStackTraceString(e));
                }
            }
            decoder.stop();
            decoder.release();
            streamThread.close();
            return null;
        }
    }

    /* Helper methods */

    // Log with config control
    void log(String msg) {
        if (configs.showLog)
            Log.d(TAG, msg);
    }

    // Less verbose sleep function
    void sleep(int millisec) {
        try {
            Thread.sleep(millisec);
        } catch (InterruptedException e) {
            log(Log.getStackTraceString(e));
        }
    }

    // Element-wise subtraction
    float[] minus(float[] a, float[] b) {
        float[] result = new float[a.length];
        for (int i=0; i<a.length; i++)
            result[i] = a[i] - b[i];
        return result;
    }

    /* Configurations for this app */
    class Conf{
        // keys
        private static final String IP = "ip";
        private static final String STREAM_PORT0 = "streamPort0";
        private static final String SIGNAL_PORT = "signalPort";
        private static final String CTRL_PORT = "ctrlPort";
        private static final String GAME_ID = "gameId";
        private static final String SESSION_ID = "sessionId";
        private static final String SHOW_LOG = "showLog";
        private static final String WIDTH = "width";
        private static final String HEIGHT = "height";

        // default properties
        public String ip = "";
        public int streamPort0 = 30000;
        public int signalPort = 55556;
        public int ctrlPort = 55555;
        public int gameId = 1;
        public int sessionId = 1;
        public boolean showLog = false;
        public int width = 1280;
        public int height = 720;

        Properties prop;

        public Conf() throws IOException, IllegalAccessException {
            InputStream configs = getResources().openRawResource(R.raw.cloudy);
            prop = new Properties();
            prop.load(configs);

            if (!(prop.containsKey(IP)))
                throw new IllegalAccessException(IP);
            ip = prop.getProperty(IP);

            if (prop.containsKey(STREAM_PORT0))
                streamPort0 = Integer.parseInt(prop.getProperty(STREAM_PORT0));
            if (prop.containsKey(SIGNAL_PORT))
                signalPort = Integer.parseInt(prop.getProperty(SIGNAL_PORT));
            if (prop.containsKey(CTRL_PORT))
                ctrlPort = Integer.parseInt(prop.getProperty(CTRL_PORT));
            if (prop.containsKey(GAME_ID))
                gameId = Integer.parseInt(prop.getProperty(GAME_ID));
            if (prop.containsKey(SESSION_ID))
                sessionId = Integer.parseInt(prop.getProperty(SESSION_ID));
            if (prop.containsKey(SHOW_LOG))
            showLog = Boolean.parseBoolean(prop.getProperty(SHOW_LOG));
            if (prop.containsKey(WIDTH))
                width = Integer.parseInt(prop.getProperty(WIDTH));
            if (prop.containsKey(HEIGHT))
                height = Integer.parseInt(prop.getProperty(HEIGHT));
        }
    }
}
