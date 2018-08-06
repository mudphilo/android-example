package com.legitimate.AllySuperApp.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

/**
 * List of Tinode accounts.
 * Schema:
 *  _ID -- account ID
 *  name - UID of the account
 *  last_active -- 1 if the account was used for last login, 0 otherwise
 */
public class AccountDb implements BaseColumns {
    public static final String TABLE_NAME = "accounts";
    public static final String COLUMN_NAME_UID = "uid";
    public static final String COLUMN_NAME_ACTIVE = "last_active";
    public static final String COLUMN_NAME_COUNTRY_CODE = "country_code";

    public static final String INDEX_UID = "accounts_uid";
    public static final String INDEX_ACTIVE = "accounts_active";

    /**
     * Statement to create account table - mapping of account UID to long id
     */
    static final String CREATE_TABLE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    _ID + " INTEGER PRIMARY KEY," +
                    COLUMN_NAME_UID + " TEXT," +
                    COLUMN_NAME_COUNTRY_CODE + " TEXT," +
                    COLUMN_NAME_ACTIVE + " INTEGER)";
    /**
     * Add index on account name
     */
    static final String CREATE_INDEX_1 =
            "CREATE UNIQUE INDEX " + INDEX_UID +
                    " ON " + TABLE_NAME + " (" +
                    COLUMN_NAME_UID + ")";
    /**
     * Add index on last active
     */
    static final String CREATE_INDEX_2 =
            "CREATE INDEX " + INDEX_ACTIVE +
                    " ON " + TABLE_NAME + " (" +
                    COLUMN_NAME_ACTIVE + ")";

    /**
     * Statements to drop accounts table and index
     */
    static final String DROP_TABLE =
            "DROP TABLE IF EXISTS " + TABLE_NAME;

    static final String DROP_INDEX_1 =
            "DROP INDEX IF EXISTS " + INDEX_UID;

    static final String DROP_INDEX_2 =
            "DROP INDEX IF EXISTS " + INDEX_ACTIVE;

    static StoredAccount addOrActivateAccount(SQLiteDatabase db, String uid,String countryCode) {
        StoredAccount acc = null;
        db.beginTransaction();
        try {
            // Clear Last Active
            deactivateAll(db);
            acc = new StoredAccount();
            acc.uid = uid;
            acc.id = getByUid(db, uid);
            if (acc.id >= 0) {
                db.execSQL("UPDATE " + TABLE_NAME +
                        " SET " + COLUMN_NAME_ACTIVE + "=1, " +
                        COLUMN_NAME_COUNTRY_CODE + "= " + countryCode +
                        " WHERE " + _ID + "=" + acc.id);
            } else {
                // Insert new account as active
                ContentValues values = new ContentValues();
                values.put(COLUMN_NAME_UID, uid);
                values.put(COLUMN_NAME_ACTIVE, 1);
                values.put(COLUMN_NAME_COUNTRY_CODE, countryCode);
                acc.id = db.insert(TABLE_NAME, null, values);
            }
            if (acc.id < 0) {
                acc = null;
            }
            db.setTransactionSuccessful();
        } catch (SQLException ignored) {
            acc = null;
        } finally {
            db.endTransaction();
        }

        return acc;
    }

    static StoredAccount getActiveAccount(SQLiteDatabase db) {
        StoredAccount acc = null;
        Cursor c = db.query(
                TABLE_NAME,
                new String[]{_ID, COLUMN_NAME_UID,COLUMN_NAME_COUNTRY_CODE},
                COLUMN_NAME_ACTIVE + "=1",
                null, null, null, null);
        if (c.moveToFirst()) {
            acc = new StoredAccount();
            acc.id = c.getLong(0);
            acc.uid = c.getString(1);
            acc.cc = c.getString(c.getColumnIndex(COLUMN_NAME_COUNTRY_CODE));

        }
        c.close();
        return acc;
    }

    private static long getByUid(SQLiteDatabase db, String uid) {
        long id = -1;
        Cursor c = db.query(
                TABLE_NAME,
                new String[]{ _ID },
                COLUMN_NAME_UID + "=?",
                new String[] { uid },
                null, null, null);
        if (c.moveToFirst()) {
            id = c.getLong(0);
        }
        c.close();
        return id;
    }

    static void deactivateAll(SQLiteDatabase db) {
        db.execSQL("UPDATE " + TABLE_NAME + " SET " + COLUMN_NAME_ACTIVE + "=0");
    }
}
