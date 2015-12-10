package it.dcsystem.strawberryapp.database.beans;


import android.provider.BaseColumns;

/**
 * Created by Scugnixxo on 17/10/15.
 */
public final class Scansioni {

    public Scansioni() {
    }

    /**
     * Inner class per definire la struttura della tabella sqlite
     */
    public static abstract class ScansioniEntry implements BaseColumns {
        public static final String TABLE_NAME = "scansioni";
        public static final String COLUMN_NAME_SCANSIONE_A = "scansione_a";
        public static final String COLUMN_NAME_SCANSIONE_B = "scansione_b";
        public static final String COLUMN_NAME_DATA_SCANSIONE = "data_scansione";
    }
}
