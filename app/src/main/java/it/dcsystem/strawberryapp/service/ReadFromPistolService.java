package it.dcsystem.strawberryapp.service;

/**
 * Created by Scugnixxo on 08/11/15.
 */


import android.app.Service;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;

import it.dcsystem.strawberryapp.activity.MainActivity;
import it.dcsystem.strawberryapp.handler.ReadFromPistolHandler;
import it.dcsystem.strawberryapp.socket.ReadFromPistolThread;


public class ReadFromPistolService extends Service {


    private static ReadFromPistolThread multiT;
    Handler updateConversationHandler;
    private BluetoothSocket mmSocket;


    public static void salvaScansione(final String text) {


        final String msg = text.split("\n")[0];
        MainActivity.runOnUI(new Runnable() {
            @Override
            public void run() {


                MainActivity.insertIntoList(msg);
            }
        });

    }


    @Override
    public IBinder onBind(Intent intent) {

        return null;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);

        mmSocket = MainActivity.getSocket();

        this.updateConversationHandler = new ReadFromPistolHandler();
        multiT = new ReadFromPistolThread(updateConversationHandler, mmSocket);
        multiT.start();
    }

    @Override
    public void onRebind(Intent intent) {


        mmSocket = MainActivity.getSocket();

        if (multiT == null || multiT.isInterrupted()) {
            multiT = new ReadFromPistolThread(updateConversationHandler, mmSocket);
            multiT.start();
        }
    }

    @Override
    public void onDestroy() {
        MainActivity.resetService();
        multiT.cancel();
        super.onDestroy();
    }

}