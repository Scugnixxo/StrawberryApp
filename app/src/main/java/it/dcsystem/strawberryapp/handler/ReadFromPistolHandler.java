package it.dcsystem.strawberryapp.handler;

/**
 * Created by Scugnixxo on 08/11/15.
 */


import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import it.dcsystem.strawberryapp.service.ReadFromPistolService;


public class ReadFromPistolHandler extends Handler {

    @Override
    public void handleMessage(Message msg) {

        Bundle bundle = msg.getData();

        if (bundle.containsKey("msg")) {
            String value = bundle.getString("msg");
            ReadFromPistolService.salvaScansione(value);
        } else {

        }
    }
}