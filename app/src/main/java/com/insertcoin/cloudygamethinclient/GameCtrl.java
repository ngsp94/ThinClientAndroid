package com.insertcoin.cloudygamethinclient;

import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

public class GameCtrl implements Runnable {
    static final String TAG = "GameCtrl";

    byte[] data;
    DatagramSocket socket;
    DatagramPacket packet;
    MainActivity.Conf conf;
    Type type;
    int keyCode;
    int charCode;
    int event;
    int[] mouseMov;
    float[] mousePos;

    enum Type {NONE, KEYBOARD, MOUSE};
    enum Event {DUMMY0, DUMMY1, KEY_DOWN, KEY_UP};

    GameCtrl(MainActivity.Conf conf, int[] mov, float[] pos) {
        type = Type.MOUSE;
        this.conf = conf;
        mouseMov = mov;
        mousePos = pos;
    }

    GameCtrl(MainActivity.Conf conf, int keyCode, int event) {
        type = Type.KEYBOARD;
        this.conf = conf;
        this.keyCode = keyCode;
        // key & char only happen to be the same for the keys used here!
        this.charCode = keyCode;
        this.event = event;
    }

    @Override
    public void run() {
        try {
            socket = new DatagramSocket();
            InetAddress ip = InetAddress.getByName(conf.ip);
            makePacket();
            packet = new DatagramPacket(data, data.length, ip, conf.ctrlPort);
            socket.send(packet);
        } catch (Exception e) {
            loge(e);
        }
    }

    private void makePacket() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write((byte) conf.version);
        out.write((byte) type.ordinal());
        out.write((byte) conf.ctrlId);
        switch (type) {
            case KEYBOARD:
                out.write(toByteArray(keyCode, 2), 0, 2);
                out.write(toByteArray(charCode, 2), 0, 2);
                out.write((byte) event);
                break;
            case MOUSE:
                byte[] xMov = toByteArray(mouseMov[0], 2);
                byte[] yMov = toByteArray(mouseMov[1], 2);
                ByteBuffer xPos = ByteBuffer.allocate(4).putFloat(mousePos[0]);
                ByteBuffer yPos = ByteBuffer.allocate(4).putFloat(mousePos[1]);
                out.write(xMov, 0, 2);
                out.write(yMov, 0, 2);
                out.write(xPos.array(), 0, 4);
                out.write(yPos.array(), 0, 4);
                break;
            default:
                return; // ignore unsupported packet types
        }
        data = out.toByteArray();
    }

    /* Helper Methods */

    // Log with config control
    void log(String msg) {
        if (conf.showLog)
            Log.d(TAG, msg);
    }

    void loge(Exception e) {
        if (conf.showLog)
            Log.e(TAG, Log.getStackTraceString(e));
    }

    // Log packets of bytes directly
    void log(byte[] arr) {
        StringBuilder sb = new StringBuilder();
        for (byte b : arr)
            sb.append(String.format("%02X ", b));
        log(sb.toString());
    }

    // Convert int to byte array of length 'precision'
    public byte[] toByteArray(int num, int precision) {
        byte[] result = new byte[precision];
        // network byte order is big endian
        for (int i = precision-1; i >= 0; i--)
            result[i] = (byte) (num >> (i*8));
        return result;
    }

    class Key {
        static final int MOUSE_BTN = 1;
    }
}
