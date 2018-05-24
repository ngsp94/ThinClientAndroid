package com.insertcoin.cloudygamethinclient;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;

public class MainActivity extends AppCompatActivity
        implements TextureView.SurfaceTextureListener{

    static final String TAG = "Main";
    static final String AVC = MediaFormat.MIMETYPE_VIDEO_AVC;

    Conf configs;
    H264StreamThread streamThread;
    DecodeTask decodeTask;
    MediaCodec decoder;
    TextureView texView;

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
        GameSignal sendJoin = new GameSignal(configs, 0, GameSignal.Cmd.JOIN);
        Thread joinThread = new Thread(sendJoin);
        joinThread.start();
        streamThread = new H264StreamThread(configs);
        streamThread.start();
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
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
        log("texture changed, decoding cancelled!");
        GameSignal sendQuit = new GameSignal(configs, 0, GameSignal.Cmd.QUIT);
        Thread quitThread = new Thread(sendQuit);
        quitThread.start();
        try {
            quitThread.join();
        } catch (InterruptedException e) {
            loge(e);
        }
        decodeTask.cancel(false);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        // This is triggered by back button, menu button etc
        // Can send quit signal here
        log("texture destroyed, decoding cancelled!");
        GameSignal sendQuit = new GameSignal(configs, 0, GameSignal.Cmd.QUIT);
        Thread quitThread = new Thread(sendQuit);
        quitThread.start();
        try {
            quitThread.join();
        } catch (InterruptedException e) {
            loge(e);
        }
        decodeTask.cancel(false);
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

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
                loge(e);
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
                    loge(e);
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
                    loge(e);
                } catch (InterruptedException e) {
                    log("Input buffer not available");
                    loge(e);
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

        // properties
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
