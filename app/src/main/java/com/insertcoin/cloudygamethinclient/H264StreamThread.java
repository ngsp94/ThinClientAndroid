package com.insertcoin.cloudygamethinclient;

import android.net.wifi.WifiConfiguration;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;

public class H264StreamThread extends Thread {

    static final String TAG = "H264StreamThread";
    static final int LOG_LEN = 200;
    static final byte[] START_CODE = {0, 0, 0, 1}; // H.264 start code
    static final byte SPS_CODE = 0x67;
    static final byte PPS_CODE = 0x68;

    BufferedInputStream stream = null;
    MainActivity.Conf configs;
    ByteBuffer sps = null;
    ByteBuffer pps = null;

    public H264StreamThread(MainActivity.Conf conf) {
        configs = conf;
    }

    public boolean headersReady() {
        return ((sps != null) && (pps != null));
    }

    public ByteBuffer getSPS() {
        return sps;
    }

    public ByteBuffer getPPS() {
        return pps;
    }

    public void close() {
        try {
            stream.close();
        } catch (IOException e) {
            loge(Log.getStackTraceString(e));
        }
    }

    public void run() {
        // use HTTP

        try {
            URL url = new URL("http", configs.ip, configs.streamPort0, "");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            int response = conn.getResponseCode();
            /*
            while (response != HttpURLConnection.HTTP_OK) {
                log("Response: " + response);
                sleep(100);
                conn = (HttpURLConnection) url.openConnection();
                response = conn.getResponseCode();
            }
            */

            stream = new BufferedInputStream(conn.getInputStream());
            do {
                byte[] packet = nextPacket();
                if (packet[START_CODE.length] == SPS_CODE) {
                    sps = ByteBuffer.wrap(packet);
                } else if (packet[START_CODE.length] == PPS_CODE) {
                    pps = ByteBuffer.wrap(packet);
                }
            } while (sps == null || pps == null);
        } catch (IOException e) {
            loge(Log.getStackTraceString(e));
        }
    }

    public byte[] nextPacket() throws IOException {
        ByteArrayOutputStream packet = null;

        while (packet == null) {
            byte readBuf[] = new byte[START_CODE.length];
            packet = new ByteArrayOutputStream();
            packet.write(START_CODE);

            int head = 0; // use pointer to simulate circular buffer
            stream.read(readBuf, 0, START_CODE.length);

            // throw away packets starting with start code
            if (equals(readBuf, START_CODE, head)) {
                packet = null;
                continue;
            }

            while (true) {
                if (equals(readBuf, START_CODE, head)) {
                    return packet.toByteArray();
                } else {
                    packet.write(readBuf[head]);
                    readBuf[head] = (byte) stream.read(); // blocking call
                }
                head = (head + 1) % START_CODE.length;
            }
        }
        return null; // should never reach this state because of while (true)
    }

    /* Helper Methods */

    // Check equivalence of elements, with an offset on a
    static boolean equals(byte[] a, byte[] b, int offset) {
        for (int i=0; i<a.length; i++) {
            if (a[(i + offset) % a.length] != b[i])
                return false;
        }
        return true;
    }

    // Can log long messages with line breaks
    void log(String msg) {
        if (configs.showLog) {
            for (int i = 0; i <= msg.length(); i += LOG_LEN)
                Log.d(TAG, msg.substring(i, Math.min(msg.length(), i+LOG_LEN)));
        }
    }

    void loge(String msg) {
        if (configs.showLog) {
            for (int i = 0; i <= msg.length(); i += LOG_LEN)
                Log.e(TAG, msg.substring(i, Math.min(msg.length(), i + LOG_LEN)));
        }
    }

    // Less verbose sleep function
    void sleep(int millisec) {
        try {
            Thread.sleep(millisec);
        } catch (InterruptedException e) {
            log(Log.getStackTraceString(e));
        }
    }

    // Log packets of bytes directly
    void log(byte[] arr) {
        StringBuilder sb = new StringBuilder();
        for (byte b : arr)
            sb.append(String.format("%02X ", b));
        log(sb.toString());
    }
}
