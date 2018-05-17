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

    class Conf{
        static final String HOST_IP = "ip";
    }

    static final String TAG = "Main";
    static final int LOG_WIDTH = 100;

    Properties properties;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            loadProperties();
        } catch (IOException e) {
            log(Log.getStackTraceString(e));
            System.exit(-1);
        } catch (IllegalAccessException e) {
            log("Missing config: " + e.getMessage());
            System.exit(-1);
        }
    }

    void loadProperties() throws IOException, IllegalAccessException {
        InputStream configs = getResources().openRawResource(R.raw.cloudy);
        properties = new Properties();
        properties.load(configs);
        if (!(properties.containsKey(Conf.HOST_IP))) {
            throw new IllegalAccessException(Conf.HOST_IP);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {

        Thread networkThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(properties.getProperty(Conf.HOST_IP));
                    URLConnection conn = url.openConnection();
                    // TODO: move this to own class with getters
                    BufferedInputStream bis =
                            new BufferedInputStream(conn.getInputStream());
                } catch (MalformedURLException e) {
                    log(Log.getStackTraceString(e));
                } catch (IOException e) {
                    log(Log.getStackTraceString(e));
                }
            }
        });
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

    // Can log long messages with line breaks
    static void log(String msg) {
        for (int i=0; i<=msg.length(); i+=LOG_WIDTH)
            Log.d(TAG, msg.substring(i, Math.min(msg.length(), i+LOG_WIDTH)));
    }

}
