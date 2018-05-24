package com.insertcoin.cloudygamethinclient;

import android.util.Log;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

// To send game action to server using UDP
public class UDPRunnable implements Runnable {
    static final String TAG = "UDPRunnable";

    byte[] data;
    DatagramSocket socket;
    InetAddress ip;
    DatagramPacket packet;
    MainActivity.Conf configs;

    public UDPRunnable(MainActivity.Conf conf, byte[] data) {
        configs = conf;
        this.data = data;
        try {
        } catch (Exception e) {
            loge(Log.getStackTraceString(e));
        }
    }

    @Override
    public void run() {
        try {
            socket = new DatagramSocket();
            ip = InetAddress.getByName(configs.ip);
            int port = configs.streamPort0;
            packet = new DatagramPacket(data, data.length, ip, port);
            socket.send(packet);
            log("sent packet!");
        } catch (Exception e) {
            loge(Log.getStackTraceString(e));
        }
    }

    /* Helper Methods */

    // Log with config control
    void log(String msg) {
        if (configs.showLog)
            Log.d(TAG, msg);
    }

    void loge(String msg) {
        if (configs.showLog)
            Log.e(TAG, msg);
    }
}
