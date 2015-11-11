package it.dcsystem.strawberryapp.activity;

import android.app.Activity;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.hardware.Camera;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import it.dcsystem.strawberryapp.R;
import it.dcsystem.strawberryapp.database.helper.GestioneDB;
import it.dcsystem.strawberryapp.service.ReadFromPistolService;
import it.dcsystem.strawberryapp.zxing.IntentIntegrator;
import it.dcsystem.strawberryapp.zxing.IntentResult;


/**
 * Created by Scugnixxo on 17/10/15.
 */
public class MainActivity extends Activity {

    private final static int LONGTIME = 3500;
    public static Handler UIHandler;
    private static TextView resultTxt;
    private static Handler myHandler;
    private static List<String> scansioni;
    private static Integer scanCount;
    private static ProgressDialog progress;
    private static Button qrButton;
    private static Button pistolButton;
    private static Button sendButton;
    private static GestioneDB gdb;
    private static BluetoothSocket mmSocket;
    private static Boolean checkServiceOn;
    private static ArrayAdapter<String> adapter;
    private static LinkedList<String> scansioniList;
    private static SimpleDateFormat sdf;
    private static Ringtone r;
    private static Intent serviceIntent;

    static {
        UIHandler = new Handler(Looper.getMainLooper());
    }

    private final Runnable finish = new Runnable() {
        public void run() {

            MainActivity.this.finish();
        }
    };
    private Intent toAssociateIntent;
    private IntentIntegrator scanInt;
    private int front;
    private BluetoothAdapter btAdapter;
    private BluetoothDevice mmDevice;
    private ListView lista;
    private NotificationManager notificationManager;

    /**
     * Metodo per riavviare il servizio di scansione con pistola
     */
    public static void resetService() {
        checkServiceOn = false;
    }

    /**
     * Metodo che inserisci i risultati della scansione con la pistola
     *
     * @param text
     */
    public static void insertIntoList(String text) {
        boolean restartScan = false;
        //aumento il contatore delle scansioni e inserisco il risultato nella lista scansioni
        scanCount++;
        final String scanResult = text;
        if (scanCount == 1 && !scanResult.startsWith("A")) {
            myHandler.post(new Runnable() {
                @Override
                public void run() {
                    resultTxt.setText("ERRORE SCANSIONE: SCANSIONA UN CODICE 'A'");
                }
            });

            restartScan = true;
        } else if (scanCount == 2 && !scanResult.startsWith("B")) {
            myHandler.post(new Runnable() {
                @Override
                public void run() {
                    resultTxt.setText("ERRORE SCANSIONE: SCANSIONA UN CODICE 'B'");
                }
            });
            restartScan = true;
        }
        if (restartScan) {
            try {

                r.play();
            } catch (Exception e) {
                e.printStackTrace();
            }
            scanCount--;

        } else {
            scansioni.add(scanResult);

            myHandler.post(new Runnable() {
                @Override
                public void run() {
                    resultTxt.setText("HAI SCANSIONATO: " + scanResult);
                    qrButton.setEnabled(true);
                    qrButton.setClickable(true);
                    pistolButton.setEnabled(true);
                    pistolButton.setClickable(true);
                }
            });
            //se ho scansionato due codici, posso procedere con la nuova scansione
            if (scanCount == 2) {
                String lastScan = scansioniList.getLast();

                lastScan += " | " + scanResult + " | " + sdf.format(new Date());
                scansioniList.removeLast();
                scansioniList.addLast(lastScan);


                saveResult();
            } else {
                scansioniList.addLast(scanResult);

            }
            adapter.notifyDataSetChanged();
        }
    }

    /**
     * Metodo che permette al servizio di modifcare la grafica della main activity
     *
     * @param runnable
     */
    public static void runOnUI(Runnable runnable) {
        UIHandler.post(runnable);
    }

    /**
     * Metodo per salvare i risultati della scansione nel database
     */
    private static void saveResult() {
        // Salvo le scansioni all'interno del database

        new AsyncTask<Void, Void, String>() {

            @Override
            protected void onPreExecute() {
                progress.show();
            }

            @Override
            protected String doInBackground(Void... params) {
                //memorizzo le scansioni in db
                if (gdb.inserisciScansioni(scansioni.get(0), scansioni.get(1))) {
                    scanCount = 0;
                    return null;
                } else {
                    scanCount = 0;
                    return "Errore nel salvataggio della scansione: RIPROVA!";
                }
            }

            @Override
            protected void onPostExecute(String msg) {
                if (progress != null && progress.isShowing()) progress.dismiss();
                if (msg == null) {
                    String ultima = gdb.readLastTimeStamp();
                    msg = "REGISTRAZIONI SCANSIONI EFFETTUATE CORRETTAMENTE: " + ultima;
                }
                myHandler = new Handler();
                final String finalMsg = msg;
                myHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        resultTxt.setText(finalMsg);
                        qrButton.setEnabled(true);
                        qrButton.setClickable(true);
                        pistolButton.setEnabled(true);
                        pistolButton.setClickable(true);
                        sendButton.setEnabled(true);
                        sendButton.setClickable(true);

                    }
                }, LONGTIME + 500);
            }
        }.execute();
    }

    /**
     * Getter del socket della pistola
     *
     * @return
     */
    public static BluetoothSocket getSocket() {
        return mmSocket;
    }

    @Override
    /**
     * creazione dell'attività e inizializzazione delle variabili e delle intent
     *
     */
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        resultTxt = (TextView) findViewById(R.id.mainWelcomeText);
        sendButton = (Button) findViewById(R.id.main_sendButton);
        qrButton = (Button) findViewById(R.id.main_qrButton);
        pistolButton = (Button) findViewById(R.id.main_pistolButton);
        lista = (ListView) findViewById(R.id.main_lista);
        scansioniList = new LinkedList<>();

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, scansioniList);
        lista.setAdapter(adapter);

        sendButton.setEnabled(false);
        sendButton.setClickable(false);
        qrButton.setEnabled(true);
        qrButton.setClickable(true);
        pistolButton.setEnabled(true);
        pistolButton.setClickable(true);

        sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");

        progress = new ProgressDialog(this);
        progress.setMessage("ATTENDERE..");
        progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progress.setMax(100);
        progress.setCancelable(false);
        myHandler = new Handler();

        //autorizzo il thread principale ad aprire canali di comunicazioni internet
        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                    .permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        //creo il database
        gdb = GestioneDB.getInstance(getApplicationContext());
        scanCount = 0;
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        r = RingtoneManager.getRingtone(getApplicationContext(), notification);
    }

    /**
     * Metodo che esegue l'attività di scansione del qrCode
     *
     * @param v
     */
    public void scanButton(View v) {
        startScan();
    }


    /**
     * Metodo che esegue l'attività di scansione del barcode con la pistola
     *
     * @param v
     * @throws IOException
     */
    public void scanBarPistolButton(View v) throws IOException {
        if (pistolButton.getText().equals("Pistola")) {
            //disabilito i pulsanti
            qrButton.setEnabled(false);
            qrButton.setClickable(false);
            pistolButton.setEnabled(false);
            pistolButton.setClickable(false);
            sendButton.setEnabled(false);
            sendButton.setClickable(false);
            //inizializzo la lista delle scansioni
            if (scanCount == 0)
                scansioni = new ArrayList<>();


            btAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice bd = null;
            if (btAdapter.isEnabled()) {
                //trovo la pistola
                Iterator<BluetoothDevice> bdIterator = btAdapter.getBondedDevices().iterator();
                while (bdIterator.hasNext()) {
                    BluetoothDevice device = bdIterator.next();
                    if (device.getName().startsWith("CT10")) {
                        bd = device;
                        break;
                    }
                }
                if (bd != null) {

                    final BluetoothDevice finalBd = bd;
                    new AsyncTask<Void, Void, String>() {

                        @Override
                        protected void onPreExecute() {

                            progress.show();
                        }

                        @Override
                        protected String doInBackground(Void... params) {
                            BluetoothSocket tmp = null;
                            mmDevice = finalBd;


                            try {

                                tmp = mmDevice.createRfcommSocketToServiceRecord(mmDevice.getUuids()[0].getUuid());

                            } catch (Exception e) {
                                return "error";
                            }
                            mmSocket = tmp;
                            return null;
                        }

                        @Override
                        protected void onPostExecute(String msg) {

                            if (msg != null) {
                                showToastMsg("Impossibile collegarsi alla pistola bluetooth");

                            } else {

                                btAdapter.cancelDiscovery();

                                try {

                                    mmSocket.connect();

                                    if (progress != null && progress.isShowing())
                                        progress.dismiss();
                                    //devo dire di lanciare il servizio di ascolto
                                    myHandler.post(new Runnable() {
                                        @Override
                                        public void run() {

                                            Notification.Builder notiB = new Notification.Builder(MainActivity.this);
                                            notiB.setSmallIcon(R.mipmap.ic_blue_on).setContentTitle("BLUETOOTH CONNESSO").setContentText("PISTOLA CONNESSA");
                                            @SuppressWarnings("deprecation")
                                            Notification noti = notiB.getNotification();
                                            noti.flags |= Notification.FLAG_NO_CLEAR;
                                            notificationManager.notify(0, noti);

                                            showToastMsg("Collegamento alla pistola eseguito!");

                                            pistolButton.setText("Disattiva Pistola");
                                            pistolButton.setEnabled(true);
                                            pistolButton.setClickable(true);
                                            qrButton.setEnabled(true);
                                            qrButton.setClickable(true);
                                            startService();
                                        }
                                    });

                                } catch (Exception connectException) {
                                    if (progress != null && progress.isShowing())
                                        progress.dismiss();
                                    try {
                                        mmSocket.close();
                                        myHandler.post(new Runnable() {
                                            @Override
                                            public void run() {

                                                showToastMsg("Impossibile collegarsi alla pistola bluetooth");
                                                pistolButton.setText("Pistola");
                                                pistolButton.setEnabled(true);
                                                pistolButton.setClickable(true);
                                                qrButton.setEnabled(true);
                                                qrButton.setClickable(true);

                                            }
                                        });

                                    } catch (Exception closeException) {
                                        myHandler.post(new Runnable() {
                                            @Override
                                            public void run() {

                                                showToastMsg("Impossibile collegarsi alla pistola bluetooth");
                                                pistolButton.setText("Pistola");
                                                qrButton.setEnabled(true);
                                                qrButton.setClickable(true);
                                                pistolButton.setEnabled(true);
                                                pistolButton.setClickable(true);

                                            }
                                        });
                                    }

                                }
                            }
                        }

                    }.execute();


                }
            } else {
                showToastMsg("Impossibile collegarsi alla pistola bluetooth");
                pistolButton.setText("Pistola");
                qrButton.setEnabled(true);
                qrButton.setClickable(true);
                pistolButton.setEnabled(true);
                pistolButton.setClickable(true);
            }

        } else {

            stopService();
            pistolButton.setText("Pistola");
            qrButton.setEnabled(true);
            qrButton.setClickable(true);
            pistolButton.setEnabled(true);
            pistolButton.setClickable(true);
            showToastMsg("Pistola Scollegata");
        }
    }

    /**
     * Metodo che esegue l'attività di invio del file;
     *
     * @param v
     */
    public void sendFileButton(View v) {

        //task asincrono per l'esecuzione del comando di invio del file
        new AsyncTask<Void, Void, String>() {

            @Override
            protected void onPreExecute() {
                progress.show();
            }

            @Override
            protected String doInBackground(Void... params) {

                if (gdb.sendFile()) {

                    return null;
                } else {

                    return "Errore nell'invio del file delle scansioni: RIPROVA!";
                }


            }

            @Override
            protected void onPostExecute(String msg) {
                if (progress != null && progress.isShowing()) progress.dismiss();

                if (msg == null) {

                    msg = "INVIO DATI AL SERVER COMPLETATO!\nPROCEDERE CON UNA NUOVA SCANSIONE";
                    scansioniList.clear();
                }
                //mostro il messaggio a video
                showToastMsg(msg);
                //riabilito i pulsanti e il msg di benvenuto
                myHandler = new Handler();
                myHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {

                        resultTxt.setText("PROCEDI CON UNA SCANSIONE");
                        qrButton.setEnabled(true);
                        qrButton.setClickable(true);
                        sendButton.setEnabled(true);
                        sendButton.setClickable(true);
                    }
                }
                        , LONGTIME + 500);


            }


        }.execute();
    }

    @Override
    /**
     * metodo che elabora le informazioni scansionate
     */
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        toAssociateIntent = null;
        IntentResult scanningRes = null;
        if (resultCode != RESULT_CANCELED) {

            scanningRes = IntentIntegrator.parseActivityResult(requestCode,
                    resultCode, intent);

        }
        if (scanningRes != null) {
            boolean restartScan = false;
            //aumento il contatore delle scansioni e inserisco il risultato nella lista scansioni
            scanCount++;
            final String scanResult = scanningRes.getContents();
            if (scanCount == 1 && !scanResult.startsWith("A")) {
                showToastMsg("ERRORE SCANSIONE: SCANSIONE UN CODICE 'A'");
                restartScan = true;
            } else if (scanCount == 2 && !scanResult.startsWith("B")) {
                showToastMsg("ERRORE SCANSIONE: SCANSIONE UN CODICE 'B'");
                restartScan = true;
            }
            if (restartScan) {
                try {
                    Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                    Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                    r.play();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                scanCount--;
               /* myHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startScan();
                    }
                }, LONGTIME + 5);*/
            } else {
                scansioni.add(scanResult);

                myHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        resultTxt.setText("HAI SCANSIONATO: " + scanResult);
                        qrButton.setEnabled(true);
                        qrButton.setClickable(true);
                        pistolButton.setEnabled(true);
                        pistolButton.setClickable(true);
                    }
                });
                //se ho scansionato due codici, posso procedere con la nuova scansione
                if (scanCount == 2) {
                    String lastScan = scansioniList.getLast();

                    lastScan += " | " + scanResult + " | " + sdf.format(new Date());
                    scansioniList.removeLast();
                    scansioniList.addLast(lastScan);

                    saveResult();
                } else {
                    scansioniList.addLast(scanResult);
                  /*  myHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            startScan();
                        }
                    }, LONGTIME - 1500);*/
                }
            }
        } else {

            //erore gestito dalla pagina della main
            resultTxt.setText("ERRORE: NESSUNA SCANSIONE EFFETTUATA");
            qrButton.setEnabled(true);
            qrButton.setClickable(true);
            pistolButton.setEnabled(true);
            pistolButton.setClickable(true);
            //verifico se ho scansionato due codici corretti
           /* if (scanCount < 2)
                myHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startScan();
                    }
                }, LONGTIME - 1500);*/


        }


    }


    @Override
    public void onResume() {
        super.onResume();
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onBackPressed() {

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.menu_main, menu);

        menu.add(1, 1, 1, "CHIUDI APP");

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        showToastMsg("ARRIVEDERCI");
        notificationManager.cancel(0);
        myHandler.postDelayed(finish, LONGTIME + 100);
        return true;


    }

    /**
     * Metodo privato per iniziare l'attività di scansione da fotocamera
     */
    private void startScan() {
        //disabilito i pulsanti
        qrButton.setEnabled(false);
        qrButton.setClickable(false);
        pistolButton.setEnabled(false);
        pistolButton.setClickable(false);
        sendButton.setEnabled(false);
        sendButton.setClickable(false);
        //inizializzo la lista delle scansioni
        if (scanCount == 0)
            scansioni = new ArrayList<>();

        //inizializzo la fotocamera e lancio la scansione
        scanInt = new IntentIntegrator(this);
        front = this.selectRearCamera();
        scanInt.initiateScan(front);
    }

    /**
     * Metodo per visualizzare un toast con un messaggio
     *
     * @param message il messaggio da visualizzare
     */

    private void showToastMsg(String message) {

        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.custom_toast_layout, (ViewGroup) findViewById(R.id.toast_layout_root));
        layout.setBackgroundColor(getResources().getColor(R.color.white));
        ImageView image = (ImageView) layout.findViewById(R.id.toastImage);
        image.setImageDrawable(getResources().getDrawable(android.R.drawable.ic_menu_info_details));

        TextView text = (TextView) layout.findViewById(R.id.toastText);
        text.setText(message);
        text.setTextColor(getResources().getColor(R.color.black));
        Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
        toast.setDuration(Toast.LENGTH_LONG);


        toast.setView(layout);
        toast.show();
        final Dialog overlayDialog = new Dialog(this, android.R.style.Theme_Panel);
        overlayDialog.setCancelable(false);
        overlayDialog.show();
        myHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (overlayDialog != null && overlayDialog.isShowing())
                    overlayDialog.dismiss();
            }
        }, LONGTIME);


    }

    /**
     * Metodo per selezionare la fotocamera di ambiente durante la scansione
     *
     * @return l'intero che identifica la fotocomara selezionata
     */
    private int selectRearCamera() {
        int result = -1;


        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                result = i;
            }

        }

        return result;
    }

    /**
     * Metodo per avviare il servizio per la scansione tramite pistola
     */
    private void startService() {
        if (checkServiceOn == null || !checkServiceOn) {
            serviceIntent = new Intent(this, ReadFromPistolService.class);

            startService(serviceIntent);

            checkServiceOn = true;
        }
    }

    private void stopService() {
        if (checkServiceOn == null || checkServiceOn) {


            stopService(serviceIntent);
            Notification.Builder notiB = new Notification.Builder(MainActivity.this);
            notiB.setSmallIcon(R.mipmap.ic_blue_off).setContentTitle("BLUETOOTH DISCONESSO").setContentText("PISTOLA DISCONNESSA");
            @SuppressWarnings("deprecation")
            Notification noti = notiB.getNotification();
            noti.flags |= Notification.FLAG_NO_CLEAR;
            notificationManager.notify(0, noti);
            checkServiceOn = false;
        }
    }
}