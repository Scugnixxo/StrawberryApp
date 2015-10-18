package it.dcsystem.strawberryapp.activity;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import it.dcsystem.strawberryapp.R;
import it.dcsystem.strawberryapp.database.helper.GestioneDB;
import it.dcsystem.strawberryapp.zxing.IntentIntegrator;
import it.dcsystem.strawberryapp.zxing.IntentResult;


/**
 * Created by Scugnixxo on 17/10/15.
 */
public class MainActivity extends Activity {

    private final static int LONGTIME = 3500;

    private final Runnable finish = new Runnable() {
        public void run() {

            MainActivity.this.finish();
        }
    };


    private TextView resultTxt;
    private Intent toAssociateIntent;

    private IntentIntegrator scanInt;
    private int front;
    private Handler myHandler;
    private List<String> scansioni;
    private Integer scanCount;
    private ProgressDialog progress;
    private Button scanButton;
    private Button sendButton;
    private GestioneDB gdb;

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
        scanButton = (Button) findViewById(R.id.main_scanButton);
        sendButton.setEnabled(false);
        sendButton.setClickable(false);
        scanButton.setEnabled(true);
        scanButton.setClickable(true);
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


    }

    @Override
    /**
     * alla pressione del tasto back, impedisco la chiusura dell'attività e rieseguo la scansione qr
     */
    public void onBackPressed() {

        startScan();
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

                }
                //mostro il messaggio a video
                showToastMsg(msg);
                //riabilito i pulsanti e il msg di benvenuto
                myHandler = new Handler();
                myHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        resultTxt.setText("PROCEDI CON UNA SCANSIONE");
                        scanButton.setEnabled(true);
                        scanButton.setClickable(true);
                        sendButton.setEnabled(true);
                        sendButton.setClickable(true);
                    }
                }
                        , LONGTIME + 500);


            }


        }.execute();
    }

    /**
     * Metodo privato per iniziare l'attività di scansione
     */
    private void startScan() {
        //disabilito i pulsanti
        scanButton.setEnabled(false);
        scanButton.setClickable(false);
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

    @Override
    public void onResume() {
        super.onResume();
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
            //aumento il contatore delle scansioni e inserisco il risultato nella lista scansioni
            scanCount++;
            final String scanResult = scanningRes.getContents();
            scansioni.add(scanResult);

            myHandler.post(new Runnable() {
                @Override
                public void run() {
                    resultTxt.setText("HAI SCANSIONATO: " + scanResult);
                }
            });
            //se ho scansionato due codici, posso procedere con la nuova scansione
            if (scanCount == 2)
                this.saveResult();
            else {
                myHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startScan();
                    }
                }, LONGTIME - 1500);
            }
        } else {

            //erore gestito dalla pagina della main
            resultTxt.setText("ERRORE: NESSUNA SCANSIONE EFFETTUATA");
            //verifico se ho scansionato due codici corretti
            if (scanCount < 2)
                myHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startScan();
                    }
                }, LONGTIME - 1500);


        }


    }

    private void saveResult() {
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
                showToastMsg(msg);
                myHandler = new Handler();
                myHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        resultTxt.setText("PROCEDI CON UNA SCANSIONE");
                        scanButton.setEnabled(true);
                        scanButton.setClickable(true);
                        sendButton.setEnabled(true);
                        sendButton.setClickable(true);
                    }
                }
                        , LONGTIME + 500);


            }


        }.execute();


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
        myHandler.postDelayed(finish, LONGTIME + 100);
        return true;


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

}