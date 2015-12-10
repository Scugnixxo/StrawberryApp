package it.dcsystem.strawberryapp.database.helper;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;

import com.opencsv.CSVWriter;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.TimeZone;

import it.dcsystem.strawberryapp.database.beans.Scansioni.ScansioniEntry;

/**
 * Created by Scugnixxo on 17/10/15.
 * <p/>
 * Classe singleton per la gestione del database
 */
public class GestioneDB {

    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 21;
    private static final String USER = "user";
    private static final String PASSWORD = "pwd";

    private static GestioneDB instance = null;
    private static DbHelper helper = null;
    private static SQLiteDatabase databaseWrite = null;
    private static SQLiteDatabase databaseRead = null;

    /**
     * Costruttore della classe
     *
     * @param context action context
     */
    private GestioneDB(Context context) {
        //inizializzazione del database sqlite
        helper = new DbHelper(context);

        databaseWrite = helper.getWritableDatabase();
        databaseRead = helper.getReadableDatabase();
    }

    /**
     * metodo per la gestione del singleton
     *
     * @param context action context
     * @return l'istanza dell'oggetto GestioneDB
     */
    public static synchronized GestioneDB getInstance(Context context) {

        if (instance == null) {
            instance = new GestioneDB(context);
        }
        return instance;
    }

    /**
     * Metodo per inserire la coppia di scansioni effettuate.
     * Oltre alle scansioni, nella riga della tabella viene inserita anche il time stamp di inserimento
     *
     * @param scansione_a la prima scansione
     * @param scansione_b la seconda scansione
     * @return un numero che rappresenta l'id della tupla inserita
     */
    public boolean inserisciScansioni(String scansione_a, String scansione_b) {
        Calendar dataAttuale = Calendar.getInstance(TimeZone.getTimeZone("Italy"), Locale.ITALY);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd kk:mm:ss");
        String dataScansione = sdf.format(dataAttuale.getTime());

        // i valori da inserire nella tabella
        ContentValues values = new ContentValues();
        values.put(ScansioniEntry.COLUMN_NAME_SCANSIONE_A, scansione_a);
        values.put(ScansioniEntry.COLUMN_NAME_SCANSIONE_B, scansione_b);
        values.put(ScansioniEntry.COLUMN_NAME_DATA_SCANSIONE, dataScansione);

        long newRowId;
        newRowId = databaseWrite.insert(ScansioniEntry.TABLE_NAME, null, values);

        return newRowId > 0;
    }

    /**
     * Metodo per leggere il time stamp dell'ultima tupla inserita
     *
     * @return una stringa contenente il timestamp
     */
    public String readLastTimeStamp() {


        String[] projection = {
                ScansioniEntry._ID,
                ScansioniEntry.COLUMN_NAME_SCANSIONE_A,
                ScansioniEntry.COLUMN_NAME_SCANSIONE_B,
                ScansioniEntry.COLUMN_NAME_DATA_SCANSIONE,
        };


        String sortOrder =
                ScansioniEntry._ID + " DESC";

        Cursor c = databaseRead.query(
                ScansioniEntry.TABLE_NAME,
                projection,
                null,
                null,
                null,
                null,
                sortOrder
        );

        c.moveToFirst();
        String data = c.getString(
                c.getColumnIndexOrThrow(ScansioniEntry.COLUMN_NAME_DATA_SCANSIONE)
        );

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        try {
            String formattata = sdf.format((sdf.parse(data)));
            data = formattata;
        } catch (Exception e) {

        }
        return data;
    }

    public LinkedList<String> loadLastDB() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        LinkedList<String> tableResult = new LinkedList<>();

        try {


            //recupero i dati della tabella dal database
            Cursor cur = databaseRead.rawQuery("SELECT * FROM scansioni", null);

            //scrivo tutto nel csv
            while (cur.moveToNext()) {
                String formattata = sdf.format((sdf.parse(cur.getString(3))));
                String row = cur.getString(1) + " | " + cur.getString(2) + " | " + formattata;
                tableResult.add(row);
            }

            cur.close();
            return tableResult;

        } catch (Exception sqlEx) {
            return null;
        }
    }

    /**
     * Metodo per esportare i dati della tabella in csv con la virgola come separatore di default
     *
     * @return true se l'operazione va a buon fine<br>false altrimenti
     */
    private boolean exportToCsv() {

        //creo il file scansioni.csv se
        File exportDir = new File(Environment.getExternalStorageDirectory(), "/Export");
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }
        File file = new File(exportDir, "scansioni.csv");
        if (file.exists())
            file.delete();
        file.setReadable(true);
        file.setWritable(true);

        try {
            file.createNewFile();
            CSVWriter csvWrite = new CSVWriter(new FileWriter(file));

            //recupero i dati della tabella dal database
            Cursor curCSV = databaseRead.rawQuery("SELECT * FROM scansioni", null);
            csvWrite.writeNext(curCSV.getColumnNames());
            //scrivo tutto nel csv
            while (curCSV.moveToNext()) {

                String arrStr[] = {curCSV.getString(0), curCSV.getString(1), curCSV.getString(2), curCSV.getString(3)};
                csvWrite.writeNext(arrStr);
            }
            csvWrite.close();
            curCSV.close();
            return true;

        } catch (Exception sqlEx) {
            return false;
        }

    }

    /**
     * Metodo per inviare ad un server FTP il file csv dei dati della tabella
     *
     * @return true se l'operazione va a buon fine<br>false altrimenti
     */
    public boolean sendFile() {
        //se la creazione del file va a buon fine, procedo al suo invio
        if (exportToCsv()) {
            FTPClient con = null;
            boolean result;
            try {
                con = new FTPClient();

                con.connect(SERVER_ADDRESS, SERVER_PORT);


                //se la connessione ha successo procedo all'invio del file
                if (con.login(USER, PASSWORD)) {
                    //ulteriore controllo di connessione
                    int reply = con.getReplyCode();
                    if (!FTPReply.isPositiveCompletion(reply)) {
                        con.disconnect();
                        return false;
                    }
                    con.enterLocalPassiveMode();
                    //seleziono il file da inviare
                    con.setFileType(FTP.BINARY_FILE_TYPE);
                    File exportDir = new File(Environment.getExternalStorageDirectory(), "/Export");
                    if (!exportDir.exists()) {
                        exportDir.mkdirs();
                    }
                    File file = new File(exportDir, "scansioni.csv");

                    FileInputStream in = new FileInputStream(file);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddkkmmss");
                    //invio il file aggiungendo il time stamp di invio
                    result = con.storeFile("scansioni" + (sdf.format(new Date())) + ".csv", in);
                    in.close();

                    con.logout();
                    con.disconnect();
                    //se l'invio ha avuto successo pulisco la tabella del database e cancello il file appena inviato
                    if (result) {
                        databaseWrite.delete(ScansioniEntry.TABLE_NAME, null, null);
                        file.delete();
                    }

                } else result = false;
                return result;
            } catch (Exception e) {
                return false;
            }
        } else return false;

    }
}
