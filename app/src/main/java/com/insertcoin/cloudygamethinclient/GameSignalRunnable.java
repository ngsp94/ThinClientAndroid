package com.insertcoin.cloudygamethinclient;

import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class GameSignalRunnable implements Runnable {
    static final String TAG = "GameSignalRunnable";

    Socket socket;
    JSONObject joinSignal;
    JSONObject quitSignal;
    MainActivity.Conf configs;
    Command command;

    enum Command {JOIN, QUIT};

    GameSignalRunnable(MainActivity.Conf conf, int ctrlId, Command command) {
        try {
            configs = conf;
            this.command = command;

            joinSignal = new JSONObject();
            joinSignal.put("command", "join");
            joinSignal.put("controller", ctrlId);
            joinSignal.put("streaming_port", conf.streamPort0 + ctrlId);
            joinSignal.put("streaming_ip", conf.ip);
            joinSignal.put("game_id", conf.gameId);
            joinSignal.put("game_session_id", conf.sessionId);

            quitSignal = new JSONObject();
            quitSignal.put("command", "quit");
            quitSignal.put("controller", ctrlId);
        } catch (JSONException e) {
            loge(Log.getStackTraceString(e));
        }
    }

    @Override
    public void run() {
        try {
            socket = new Socket(configs.ip, configs.signalPort);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    socket.getInputStream()));
            if (command == Command.JOIN)
                out.println(joinSignal.toString());
            else // QUIT
                out.println(quitSignal.toString());
            socket.close();
        } catch (IOException e) {
            loge(Log.getStackTraceString(e));
        }
    }

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