package it.dcsystem.strawberryapp.service;

/**
 * Created by Scugnixxo on 08/11/15.
 */


import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;

import it.dcsystem.strawberryapp.activity.MainActivity;
import it.dcsystem.strawberryapp.handler.ReadFromPistolHandler;
import it.dcsystem.strawberryapp.socket.ReadFromPistolThread;


public class ReadFromPistolService extends Service {


    private static android.content.Context that;

    private static Thread multiT;
    private static NotificationManager notificationManager;
    Handler updateConversationHandler;
    private BluetoothAdapter btAdapter;
    private BluetoothDevice mmDevice;
    private BluetoothSocket mmSocket;
    private boolean launchThread;


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
        launchThread = false;
        that = this;
        mmSocket = MainActivity.getSocket();

        this.updateConversationHandler = new ReadFromPistolHandler();
        multiT = new Thread(new ReadFromPistolThread(updateConversationHandler, mmSocket));
        multiT.start();
    }

    @Override
    public void onRebind(Intent intent) {

        that = this;
        mmSocket = MainActivity.getSocket();

        if (multiT == null || multiT.isInterrupted()) {
            multiT = new Thread(new ReadFromPistolThread(updateConversationHandler, mmSocket));
            multiT.start();
        }
    }

    @Override
    public void onDestroy() {
        MainActivity.resetService();
        super.onDestroy();
    }

}