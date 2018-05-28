package com.insertcoin.cloudygamethinclient;

import android.content.Context;
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
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;

public class MainActivity extends AppCompatActivity
        implements TextureView.SurfaceTextureListener, SensorEventListener{

    static final String TAG = "Main";
    static final String AVC = MediaFormat.MIMETYPE_VIDEO_AVC;
    static final float GYRO_THRESHOLD = 0.05f;
    static final float GYRO_SCALE = 0.2f;

    Conf configs;
    H264StreamThread streamThread;
    DecodeTask decodeTask;
    MediaCodec decoder;
    TextureView texView;
    SensorManager manager;
    Sensor gyro;
    float[] gyroVals;
    float[] mousePos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        texView = findViewById(R.id.textureView);
        texView.setSurfaceTextureListener(this);
        try {
            configs = this.new Conf();
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            System.exit(-1);
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Missing config: " + e.getMessage());
            System.exit(-1);
        }

        log("starting...");
        GameSignal sendJoin = new GameSignal(configs, GameSignal.Cmd.JOIN);
        Thread joinThread = new Thread(sendJoin);
        joinThread.start();
        streamThread = new H264StreamThread(configs);
        streamThread.start();

        gyroVals = new float[]{0, 0, 0};
        mousePos = new float[]{configs.width / 2, configs.height / 2};
        manager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        gyro = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        manager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture tex, int w, int h) {
        while (streamThread == null || !(streamThread.headersReady())) {
            sleep(100);
        }

        decodeTask = new DecodeTask();
        decodeTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture tex, int w, int h) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        quitGame();
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
    }

    @Override
    public void onBackPressed() {
        quitGame();
    }

    // Quit game, clean up and stop app
    public void quitGame() {
        GameSignal sendQuit = new GameSignal(configs, GameSignal.Cmd.QUIT);
        Thread quitThread = new Thread(sendQuit);
        quitThread.start();
        try {
            quitThread.join();
        } catch (InterruptedException e) {
            loge(e);
        }
        decodeTask.stopped = true;
        while (!decodeTask.done)
            sleep(100);
        System.exit(0);
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent)
    {
        int action = motionEvent.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                int keyCode = GameCtrl.Key.MOUSE_BTN;
                int event = GameCtrl.Event.KEY_DOWN.ordinal();
                GameCtrl sendCtrl = new GameCtrl(configs, keyCode, event);
                Thread ctrlThread = new Thread(sendCtrl);
                ctrlThread.start();
                return true;
            }

            case MotionEvent.ACTION_UP: { // curly brackets prevent compiler err
                int keyCode = GameCtrl.Key.MOUSE_BTN;
                int event = GameCtrl.Event.KEY_UP.ordinal();
                GameCtrl sendCtrl = new GameCtrl(configs, keyCode, event);
                Thread ctrlThread = new Thread(sendCtrl);
                ctrlThread.start();
                return true;
            }

            default:
                return true;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        Sensor sensor = sensorEvent.sensor;
        if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            float[] diff = minus(sensorEvent.values, gyroVals);

            if (Math.abs(diff[0]) > GYRO_THRESHOLD ||
                    Math.abs(diff[1]) > GYRO_THRESHOLD) {

                double x_mov = -GYRO_SCALE * diff[0] * configs.width;
                double y_mov = GYRO_SCALE * diff[1] * configs.height;
                int[] mouseMov = {(int) x_mov, (int) y_mov};
                mousePos[0] -= x_mov;
                mousePos[1] += y_mov;

                GameCtrl sendCtrl = new GameCtrl(configs, mouseMov, mousePos);
                Thread ctrlThread = new Thread(sendCtrl);
                ctrlThread.start();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private class DecodeTask extends AsyncTask<Void, Void, Void> {
        boolean stopped = false;
        boolean done = false;
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
                loge(e);
            }

            decoder.setCallback(new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(
                        @NonNull MediaCodec mc, int i) {
                    inputIndices.add(i);
                }

                @Override
                public void onOutputBufferAvailable(@NonNull MediaCodec mc,
                        int i, @NonNull MediaCodec.BufferInfo bufferInfo) {
                    decoder.releaseOutputBuffer(i, true);
                }

                @Override
                public void onError(@NonNull MediaCodec mediaCodec,
                                    @NonNull MediaCodec.CodecException e) {
                    loge(e);
                }

                @Override
                public void onOutputFormatChanged(
                        @NonNull MediaCodec mc, @NonNull MediaFormat mf) {
                }
            });

            Surface surface = new Surface(texView.getSurfaceTexture());
            decoder.configure(format, surface, null, 0);
            decoder.start();
            while (!stopped) {
                try {
                    frame = streamThread.nextPacket();
                    int i = inputIndices.take();
                    ByteBuffer inputBuffer = decoder.getInputBuffer(i);
                    inputBuffer.put(frame);
                    decoder.queueInputBuffer(i, 0, frame.length, 0, 0);
                } catch (IOException e) {
                    log("Could not get next packet");
                    loge(e);
                } catch (InterruptedException e) {
                    log("Input buffer not available");
                    loge(e);
                }
            }
            decoder.stop();
            decoder.release();
            streamThread.close();
            log("streaming clean up successful!");
            done = true;
            return null;
        }
    }

    /* Helper methods */

    // Log with config control
    void log(String msg) {
        if (configs.showLog)
            Log.d(TAG, msg);
    }

    void loge(Exception e) {
        if (configs.showLog)
            Log.e(TAG, Log.getStackTraceString(e));
    }

    // Less verbose sleep function
    void sleep(int millisec) {
        try {
            Thread.sleep(millisec);
        } catch (InterruptedException e) {
            loge(e);
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
        private static final String CTRL_ID = "ctrlId";
        private static final String GAME_ID = "gameId";
        private static final String SESSION_ID = "sessionId";
        private static final String VERSION = "version";
        private static final String SHOW_LOG = "showLog";
        private static final String WIDTH = "width";
        private static final String HEIGHT = "height";

        // properties
        public String ip = "";
        public int streamPort0 = 30000;
        public int signalPort = 55556;
        public int ctrlPort = 55555;
        public int ctrlId = 0;
        public int gameId = 1;
        public int sessionId = 1;
        public int version = 0;
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
            if (prop.containsKey(CTRL_ID))
                ctrlId = Integer.parseInt(prop.getProperty(CTRL_ID));
            if (prop.containsKey(GAME_ID))
                gameId = Integer.parseInt(prop.getProperty(GAME_ID));
            if (prop.containsKey(SESSION_ID))
                sessionId = Integer.parseInt(prop.getProperty(SESSION_ID));
            if (prop.containsKey(VERSION))
                version = Integer.parseInt(prop.getProperty(VERSION));
            if (prop.containsKey(SHOW_LOG))
                showLog = Boolean.parseBoolean(prop.getProperty(SHOW_LOG));
            if (prop.containsKey(WIDTH))
                width = Integer.parseInt(prop.getProperty(WIDTH));
            if (prop.containsKey(HEIGHT))
                height = Integer.parseInt(prop.getProperty(HEIGHT));
        }
    }
}
