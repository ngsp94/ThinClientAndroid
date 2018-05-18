package com.insertcoin.cloudygamethinclient;

import android.graphics.SurfaceTexture;
import android.media.MediaCodecInfo;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;

public class MainActivity extends AppCompatActivity
        implements TextureView.SurfaceTextureListener{

    static final String TAG = "Main";

    public static Conf configs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            configs = this.new Conf();
        } catch (IOException e) {
            log(Log.getStackTraceString(e));
            System.exit(-1);
        } catch (IllegalAccessException e) {
            log("Missing config: " + e.getMessage());
            System.exit(-1);
        }
        H264StreamThread networkThread = new H264StreamThread(configs);
        networkThread.start();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {

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
    void log(String msg) {
        if (configs.showLog)
            Log.d(TAG, msg);
    }

    /* Configurations for this app */
    class Conf{

        // keys
        private static final String IP = "ip";
        private static final String SHOW_LOG = "showLog";

        // properties
        public String ip = "";
        public Boolean showLog = false;

        Properties properties;

        public Conf() throws IOException, IllegalAccessException {
            InputStream configs = getResources().openRawResource(R.raw.cloudy);
            properties = new Properties();
            properties.load(configs);
            if (!(properties.containsKey(IP)))
                throw new IllegalAccessException(IP);

            ip = properties.getProperty(IP);
            showLog = Boolean.parseBoolean(properties.getProperty(SHOW_LOG));
        }
    }
}
