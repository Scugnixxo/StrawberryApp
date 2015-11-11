package it.dcsystem.strawberryapp.socket;

/**
 * Created by Scugnixxo on 08/11/15.
 */

import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.io.IOException;

public class ReadFromPistolThread extends Thread {

    private BluetoothSocket mmSocket;
    private Handler handler;


    public ReadFromPistolThread(Handler updateConversationHandler, BluetoothSocket mmSocket) {
        handler = updateConversationHandler;
        this.mmSocket = mmSocket;
    }

    @Override
    public void run() {


        try {

            while (!Thread.currentThread().isInterrupted()) {

                byte[] mess = new byte[1024];

                mmSocket.getInputStream().read(mess);
                String mmsg = null;
                mmsg = new String(mess, "UTF-8");
                Message msg = handler.obtainMessage();
                Bundle b = new Bundle();
                if (mmsg != null) {
                    b.putString("msg", mmsg);
                } else {
                    b.putString("error", "errore");
                }
                msg.setData(b);
                handler.sendMessage(msg);
            }

        } catch (Exception ignored) {
            Message msg = handler.obtainMessage();
            Bundle b = new Bundle();
            b.putString("error", "errore");
            msg.setData(b);
            handler.sendMessage(msg);
        }


    }


    /* Call this from the main activity to shutdown the connection */
    public void cancel() {
        try {
            mmSocket.close();
        } catch (IOException e) {
        }
    }


}