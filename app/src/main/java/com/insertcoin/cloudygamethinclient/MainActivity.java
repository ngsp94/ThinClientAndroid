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
        texView = (TextureView) findViewById(R.id.textureView);
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
        streamThread = new H264StreamThread(configs);
        streamThread.start();
    }

    private class DecodeTask extends AsyncTask<Void, Void, Void> {
        byte[] frame;
        boolean consumed = false;

        @Override
        protected Void doInBackground(Void... voids) {

            MediaFormat format = MediaFormat.createVideoFormat(
                    AVC, configs.width, configs.height);
            format.setByteBuffer("csd-0", streamThread.getSPS());
            format.setByteBuffer("csd-1", streamThread.getPPS());

            try {
                decoder = MediaCodec.createDecoderByType(AVC);
            } catch (IOException e) {
                e.printStackTrace();
            }

            boolean done = false;

            try {
                frame = streamThread.nextPacket();
            } catch (IOException e) {
                log(Log.getStackTraceString(e));
            }

            decoder.setCallback(new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(@NonNull MediaCodec mc, int i) {
                    log("Input buffer: " + i);
                    ByteBuffer inputBuffer = decoder.getInputBuffer(i);
                    while (consumed) { // wait for next frame to be ready
                        sleep(5);
                    }
                    inputBuffer.put(frame);
                    decoder.queueInputBuffer(i, 0, frame.length, 0, 0);
                    consumed = true;
                }

                @Override
                public void onOutputBufferAvailable(@NonNull MediaCodec mc, int i,
                                                    @NonNull MediaCodec.BufferInfo bufferInfo) {
                    log("Output buffer: " + i);
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
            while (!done) {
                if (consumed) {
                    try {
                        frame = streamThread.nextPacket();
                        consumed = false;
                    } catch (IOException e) {
                        log(Log.getStackTraceString(e));
                    }
                } else {
                    sleep(10);
                }
            }

            decoder.stop();
            decoder.release();
            streamThread.close();
            return null;
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture tex, int w, int h) {
        log("surfacetex ready");
        while (streamThread == null || !(streamThread.headersReady())) {
            sleep(100);
        }

        decodeTask = new DecodeTask();
        decodeTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

    }

    /* Helper methods */

    // log with config control
    void log(String msg) {
        if (configs.showLog)
            Log.d(TAG, msg);
    }

    // less verbose sleep function
    void sleep(int millisec) {
        try {
            Thread.sleep(millisec);
        } catch (InterruptedException e) {
            log(Log.getStackTraceString(e));
        }
    }

    /* Configurations for this app */
    class Conf{

        // keys
        private static final String IP = "ip";
        private static final String SHOW_LOG = "showLog";
        private static final String WIDTH = "width";
        private static final String HEIGHT = "height";

        // properties
        public String ip = "";
        public boolean showLog = false;
        public int width = 480;
        public int height = 320;

        Properties properties;

        public Conf() throws IOException, IllegalAccessException {
            InputStream configs = getResources().openRawResource(R.raw.cloudy);
            properties = new Properties();
            properties.load(configs);
            if (!(properties.containsKey(IP)))
                throw new IllegalAccessException(IP);

            ip = properties.getProperty(IP);
            showLog = Boolean.parseBoolean(properties.getProperty(SHOW_LOG));

            if (properties.containsKey(WIDTH))
                width = Integer.parseInt(properties.getProperty(WIDTH));
            if (properties.containsKey(HEIGHT))
                height = Integer.parseInt(properties.getProperty(HEIGHT));
        }
    }
}
