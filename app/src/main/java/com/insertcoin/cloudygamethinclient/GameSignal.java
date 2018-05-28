package com.insertcoin.cloudygamethinclient;

import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.PrintWriter;
import java.net.Socket;

public class GameSignal implements Runnable {
    static final String TAG = "GameSignal";

    JSONObject signal;
    MainActivity.Conf conf;
    Cmd cmd;

    enum Cmd {JOIN, QUIT};

    GameSignal(MainActivity.Conf conf, Cmd cmd) {
        this.conf = conf;
        this.cmd = cmd;
    }

    @Override
    public void run() {
        try {
            makeSignal();
            Socket socket = new Socket(conf.ip, conf.signalPort); // TCP
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            out.println(signal.toString());
            out.close();
            socket.close();
        } catch (Exception e) {
            loge(e);
        }
    }

    private void makeSignal() throws JSONException {
        signal = new JSONObject();
        if (cmd == Cmd.JOIN) {
            signal.put("command", "join");
            signal.put("controller", conf.ctrlId);
            signal.put("streaming_port", conf.streamPort0 + conf.ctrlId);
            signal.put("streaming_ip", conf.ip);
            signal.put("game_id", conf.gameId);
            signal.put("game_session_id", conf.sessionId);
        } else { // QUIT
            signal.put("command", "quit");
            signal.put("controller", conf.ctrlId);
        }
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
}
