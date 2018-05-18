package com.insertcoin.cloudygamethinclient;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

public class H264StreamThread extends Thread {

    static final String TAG = "H264StreamThread";
    static final int LOG_LEN = 80;
    static final byte[] START_CODE = {0, 0, 0, 1}; // H.264 start code

    BufferedInputStream stream = null;
    MainActivity.Conf configs;

    public H264StreamThread(MainActivity.Conf conf) {
        configs = conf;
    }

    public void run() {
        try {
            URLConnection conn = (new URL(configs.ip)).openConnection();
            stream = new BufferedInputStream(conn.getInputStream());
            byte[] pkt = nextPacket();
            while (true) {
                if (pkt != null)
                    log(pkt);
                pkt = nextPacket();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private byte[] nextPacket() throws IOException {

        ByteArrayOutputStream packet = null;

        while (packet == null) {
            byte readBuf[] = new byte[START_CODE.length];
            // TODO: check if ok to omit bytearrayoutputstream size
            packet = new ByteArrayOutputStream();
            packet.write(START_CODE);

            int head = 0; // use pointer to simulate circular buffer
            stream.read(readBuf, 0, START_CODE.length);

            // throw away packets starting with start code
            if (equals(readBuf, START_CODE, head)) {
                packet = null;
                continue;
            }

            while (stream.available() > 0) {
                if (equals(readBuf, START_CODE, head))
                    return packet.toByteArray();
                else {
                    packet.write(readBuf[head]);
                    readBuf[head] = (byte) stream.read();
                }
                head = (head + 1) % START_CODE.length;
            }
        }
        return packet.toByteArray();
    }

    /* Helper Methods */

    // Check equivalence of all elements, with an offset on the first array
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
            for (int i = 0; i <= msg.length(); i += 100)
                Log.d(TAG, msg.substring(i, Math.min(msg.length(), i+LOG_LEN)));
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
