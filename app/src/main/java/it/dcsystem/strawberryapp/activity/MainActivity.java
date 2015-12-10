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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
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
     * Metodo che inserisci in db i risultati della scansione con la pistola
     *
     * @param text
     */
    public static void insertIntoList(String text) {
        boolean restartScan = false;
        //verifico se il contatore è a zero
        if (scanCount == 0)
            scansioni = new ArrayList<>();
        //aumento il contatore delle scansioni e inserisco il risultato nella lista scansioni
        scanCount++;
        //verifico la correttezza delle scansioni
        if (scanCount == 1 && !text.startsWith("A")) {
            myHandler.post(new Runnable() {
                @Override
                public void run() {
                    resultTxt.setText("ERRORE SCANSIONE: SCANSIONA UN CODICE 'A'");
                }
            });

            restartScan = true;
        } else if (scanCount == 2 && !text.startsWith("B")) {
            myHandler.post(new Runnable() {
                @Override
                public void run() {
                    resultTxt.setText("ERRORE SCANSIONE: SCANSIONA UN CODICE 'B'");
                }
            });
            restartScan = true;
        }
        //se la scansione non è corretta eseguo la notifica di errore
        if (restartScan) {
            try {
                r.play();
            } catch (Exception e) {
                e.printStackTrace();
            }
            //ripristino il contatore alla situazione precedente
            scanCount--;

        } else {

            scansioni.add(text);
            //mostro a video il risultato della scansione
            myHandler.post(new Runnable() {
                @Override
                public void run() {
                    resultTxt.setText("HAI SCANSIONATO: " + scansioni.get(scansioni.size() - 1));
                    qrButton.setEnabled(true);
                    qrButton.setClickable(true);
                    pistolButton.setEnabled(true);
                    pistolButton.setClickable(true);
                }
            });
            //se ho scansionato due codici, posso procedere con la nuova scansione
            if (scanCount == 2) {
                String lastScan = scansioniList.getLast();
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy kk:mm:ss");
                lastScan += " | " + text + " | " + sdf.format(new Date());
                scansioniList.removeLast();
                scansioniList.addLast(lastScan);
                saveResult();
            } else {
                scansioniList.addLast(text);
            }
            //aggiorno a video la lista delle scansioni
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

        //inizializzo i pulsanti
        sendButton.setEnabled(false);
        sendButton.setClickable(false);
        qrButton.setEnabled(true);
        qrButton.setClickable(true);
        pistolButton.setEnabled(true);
        pistolButton.setClickable(true);


        //inizializzo la barra di avanzamento
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
        //inizializzo la lista
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, scansioniList);
        lista.setAdapter(adapter);                                                             //creo il database
        gdb = GestioneDB.getInstance(getApplicationContext());

        //carico ciò che c'è salvato nel database;
        restoreDb();

        scanCount = 0;

        //creazione della notifica di allarme
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        r = RingtoneManager.getRingtone(getApplicationContext(), notification);


    }

    /**
     * Metodo che permette di recuperare le scansioni dal db, in fase di primo caricamento dell'activity
     */
    private void restoreDb() {
        new AsyncTask<Void, Void, LinkedList<String>>() {

            @Override
            protected void onPreExecute() {
                progress.show();
            }

            @Override
            protected LinkedList<String> doInBackground(Void... params) {
                //carico la lista dal database
                LinkedList<String> tableResult = gdb.loadLastDB();
                return tableResult;

            }

            @Override
            protected void onPostExecute(final LinkedList<String> result) {
                if (progress != null && progress.isShowing()) progress.dismiss();
                if (result == null) {

                } else {
                    myHandler = new Handler();
                    final LinkedList<String> finalResult = result;
                    //aggiorno la grafica
                    UIHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            scansioniList.addAll(finalResult);
                            adapter.notifyDataSetChanged();
                            if (scansioniList.size() > 0) {
                                sendButton.setEnabled(true);
                                sendButton.setClickable(true);
                            }

                        }
                    });
                }
            }
        }.execute();


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
     * Metodo che abilita la scansione tramite pistola
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

            //recupero le info sul bluetooth
            btAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice bd = null;
            if (btAdapter.isEnabled()) {
                //trovo la pistola
                Iterator<BluetoothDevice> bdIterator = btAdapter.getBondedDevices().iterator();
                //trovo all'interno dei dispositivi associati, la pistola
                while (bdIterator.hasNext()) {
                    BluetoothDevice device = bdIterator.next();
                    if (device.getName().startsWith("CT10")) {
                        bd = device;
                        break;
                    }
                }
                if (bd != null) {

                    final BluetoothDevice finalBd = bd;
                    //metodo asincrono di connessione alla pistola
                    //creo un socket di comunicazione con la pistola
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
                                //creazione del socket alla pistola.
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
                                    //connesione al socket
                                    mmSocket.connect();

                                    if (progress != null && progress.isShowing())
                                        progress.dismiss();
                                    //devo dire di lanciare il servizio di ascolto
                                    myHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            //icona di avvenuta connesione
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
            //scollegare la pistola e terminare il servizio
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
                //procedo con l'ivio del file
                if (gdb.sendFile()) {

                    try {
                        //invio una richiesta ad un url, per eseguire uno script php
                        URL phpUrl = new URL("http://51.254.131.133/noschese/load_data.php");
                        URLConnection urlCon = phpUrl.openConnection();
                        BufferedReader br = new BufferedReader(
                                new InputStreamReader(
                                        urlCon.getInputStream()));
                        String line;

                        while ((line = br.readLine()) != null) {

                        }
                        br.close();
                        if (!line.startsWith("Loaded"))
                            return "ATTENZIO CARICAMENTO DATI NON RIUSCITO\nContattare l'amministratore!";

                    } catch (Exception e) {
                        return "ATTENZIO CARICAMENTO DATI NON RIUSCITO\nContattare l'amministratore!";
                    }
                    return null;
                } else {
                    return "Errore nell'invio del file delle scansioni: RIPROVA!";
                }


            }

            @Override
            protected void onPostExecute(String msg) {
                if (progress != null && progress.isShowing()) progress.dismiss();
                //invio avvenuto, creo il messaggio da visualizzare a video
                if (msg == null) {
                    msg = "INVIO DATI AL SERVER COMPLETATO!\nPROCEDERE CON UNA NUOVA SCANSIONE";
                    //resetto la lista dei codici scansinati
                    scansioniList.clear();
                }
                //mostro il messaggio a video
                showToastMsg(msg);
                //riabilito i pulsanti e il msg di benvenuto
                myHandler = new Handler();
                //visualizzo a video che posso procedere con la scansione
                myHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        resultTxt.setText("PROCEDI CON UNA SCANSIONE");
                        //riabilito i tasti
                        qrButton.setEnabled(true);
                        qrButton.setClickable(true);
                        pistolButton.setEnabled(true);
                        pistolButton.setClickable(true);
                        sendButton.setEnabled(true);
                        sendButton.setClickable(true);
                        //aggiorno la lista a video
                        adapter.notifyDataSetChanged();
                    }
                }, LONGTIME + 500);
            }
        }.execute();
    }

    @Override
    /**
     * metodo che elabora le informazioni scansionate tramite la fotocamera
     */
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        toAssociateIntent = null;
        IntentResult scanningRes = null;
        //verifico se il risulatato della scansione esista
        if (resultCode != RESULT_CANCELED) {

            scanningRes = IntentIntegrator.parseActivityResult(requestCode,
                    resultCode, intent);

        }
        if (scanningRes != null) {
            boolean restartScan = false;
            //aumento il contatore delle scansioni e inserisco il risultato nella lista scansioni
            scanCount++;
            final String scanResult = scanningRes.getContents();
            //verifico che la scansione sia corretta
            if (scanCount == 1 && !scanResult.startsWith("A")) {
                showToastMsg("ERRORE SCANSIONE: SCANSIONE UN CODICE 'A'");
                restartScan = true;
            } else if (scanCount == 2 && !scanResult.startsWith("B")) {
                showToastMsg("ERRORE SCANSIONE: SCANSIONE UN CODICE 'B'");
                restartScan = true;
            }
            //verifico se ho avuto un errore di scansione, ed eseguo l'allarme
            if (restartScan) {
                try {
                    Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                    Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                    r.play();
                } catch (Exception e) {

                }
                //diminuisco di uno il contatore delle scansioni corrette effettuate
                scanCount--;
            } else {
                scansioni.add(scanResult);
                //notifico a video che ho scansionato un codice
                myHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        resultTxt.setText("HAI SCANSIONATO: " + scanResult);
                        //riabilito i pulsanti
                        qrButton.setEnabled(true);
                        qrButton.setClickable(true);
                        pistolButton.setEnabled(true);
                        pistolButton.setClickable(true);
                    }
                });
                //se ho scansionato due codici, posso procedere con la nuova scansione
                if (scanCount == 2) {
                    //stampo a video le due scansioni, compresa l'orario di inserimento
                    String lastScan = scansioniList.getLast();
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy kk:mm:ss");
                    lastScan += " | " + scanResult + " | " + sdf.format(new Date());
                    scansioniList.removeLast();
                    scansioniList.addLast(lastScan);

                    saveResult();
                } else {
                    //aggiungo l'ultima scansione alla lista visibile sullo schermo
                    scansioniList.addLast(scanResult);
                }
                //aggiornamento della visualizzazione della lista delle scansioni
                UIHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        adapter.notifyDataSetChanged();
                    }
                });

            }
        } else {

            //erore gestito dalla pagina della main
            resultTxt.setText("ERRORE: NESSUNA SCANSIONE EFFETTUATA");
            //riabilito i pulsanti
            qrButton.setEnabled(true);
            qrButton.setClickable(true);
            pistolButton.setEnabled(true);
            pistolButton.setClickable(true);


        }


    }

    @Override
    /**
     * Ogni qual volta un utente tocca un area dello schermo
     * disattivo la notifica sonora di errore se essa è in riproduzione
     */
    public void onUserInteraction() {
        if (r != null && r.isPlaying())
            r.stop();

    }

    @Override
    /**
     * Se l'activity viene riesumata, aggiorno la lista delle scansioni
     */
    public void onResume() {
        super.onResume();
        adapter.notifyDataSetChanged();
    }

    @Override
    /**
     * Override del metodo onBackPressed, così da non far accadere nulla in caso di pressione del tasto back
     */
    public void onBackPressed() {

    }

    @Override
    /**
     * Metodo per creare le voci di menu
     */
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.menu_main, menu);

        menu.add(1, 1, 1, "CHIUDI APP");

        return true;
    }

    @Override
    /**
     * Metodo per selezionare la voce di menu opzioni
     */
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

    /**
     * Metodo per terminare il servizio di scansione tramite pistola
     */
    private void stopService() {
        if (checkServiceOn == null || checkServiceOn) {

            //fermo il servizio
            stopService(serviceIntent);
            //cambio l'icona di notifica, bluetooth scollegato
            Notification.Builder notiB = new Notification.Builder(MainActivity.this);
            notiB.setSmallIcon(R.mipmap.ic_blue_off).setContentTitle("BLUETOOTH DISCONESSO").setContentText("PISTOLA DISCONNESSA");
            @SuppressWarnings("deprecation")
            Notification noti = notiB.getNotification();
            noti.flags |= Notification.FLAG_NO_CLEAR;
            notificationManager.notify(0, noti);
            //setto a false la variabile che verifica lo stato del servizio
            checkServiceOn = false;
        }
    }
}