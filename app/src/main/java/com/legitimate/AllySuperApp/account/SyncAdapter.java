package com.legitimate.AllySuperApp.account;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v7.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import com.google.i18n.phonenumbers.PhoneNumberUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import com.legitimate.AllySuperApp.Cache;
import com.legitimate.AllySuperApp.UiUtils;
import com.legitimate.AllySuperApp.db.BaseDb;
import com.legitimate.AllySuperApp.media.VxCard;
import co.tinode.tinodesdk.AlreadySubscribedException;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.MetaSetDesc;
import co.tinode.tinodesdk.model.MetaSetSub;
import co.tinode.tinodesdk.model.MsgGetMeta;
import co.tinode.tinodesdk.model.MsgSetMeta;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Define a sync adapter for the app.
 * <p>
 * <p>This class is instantiated in {@link SyncService}, which also binds SyncAdapter to the system.
 * SyncAdapter should only be initialized in SyncService, never anywhere else.
 * <p>
 * <p>The system calls onPerformSync() via an RPC call through the IBinder object supplied by
 * SyncService.
 */
class SyncAdapter extends AbstractThreadedSyncAdapter {

    public static final String TAG = "SyncAdapter";

    private static final String ACCKEY_SYNC_MARKER = "com.legitimate.AllySuperApp.sync_marker_contacts";
    private static final String ACCKEY_INVISIBLE_GROUP = "com.legitimate.AllySuperApp.invisible_group_id";

    // Context for loading preferences
    private final Context mContext;
    private final AccountManager mAccountManager;
    private int BATCH_SIZE = 20;
    String [] str = new String[BATCH_SIZE];

    /**
     * Constructor. Obtains handle to content resolver for later use.
     */
    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContext = context;
        mAccountManager = AccountManager.get(context);
    }

    /**
     * Called by the Android system in response to a request to run the sync adapter. The work
     * required to read data from the network, parse it, and store it in the content provider is
     * done here. Extending AbstractThreadedSyncAdapter ensures that all methods within SyncAdapter
     * run on a background thread. For this reason, blocking I/O and other long-running tasks can be
     * run <em>in situ</em>, and you don't have to set up a separate thread for them.
     * .
     * <p>
     * <p>This is where we actually perform any work required to perform a sync.
     * {@link AbstractThreadedSyncAdapter} guarantees that this will be called on a non-UI thread,
     * so it is safe to peform blocking I/O here.
     * <p>
     * <p>The syncResult argument allows you to pass information back to the method that triggered
     * the sync.
     */
    //@Override
    public void onPerformSync1(final Account account, final Bundle extras, String authority,
                              ContentProviderClient provider, final SyncResult syncResult) {
        //Log.i(TAG, "Beginning network synchronization");
        final Tinode tinode = Cache.getTinode();
        try {
            Log.i(TAG, "Starting sync for account " + account.name);

            // See if we already have a sync-state attached to this account.
            Date lastSyncMarker = getServerSyncMarker(account);

            // By default, contacts from a 3rd party provider are hidden in the contacts
            // list. So let's set the flag that causes them to be visible, so that users
            // can actually see these contacts.
            if (lastSyncMarker == null) {
                ContactsManager.makeAccountContactsVisibile(mContext, account);
            }

            final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
            String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, Cache.HOST_NAME);
            boolean tls = sharedPref.getBoolean(Utils.PREFS_USE_TLS, false);
            String token = AccountManager.get(mContext)
                    .blockingGetAuthToken(account, Utils.TOKEN_TYPE, false);
            tinode.connect(hostName, tls).getResult();
            tinode.loginToken(token).getResult();

            // Don't care if it's resolved or rejected
            tinode.subscribe(Tinode.TOPIC_FND, null, null).waitResult();

            final MsgGetMeta meta = MsgGetMeta.sub();
            // FIXME(gene): The following is commented out for debugging
            // MsgGetMeta meta = new MsgGetMeta(null, new MetaGetSub(getServerSyncMarker(account), null), null);
            PromisedReply<ServerMessage> future = tinode.getMeta(Tinode.TOPIC_FND, meta);
            if (future.waitResult()) {
                ServerMessage<?,?,VxCard,PrivateType> pkt = future.getResult();
                if (pkt.meta == null || pkt.meta.sub == null) {
                    // Server did not return any contacts.
                    return;
                }
                // Fetch the list of updated contacts. Group subscriptions will be stored in
                // the address book but as invisible contacts (members of invisible group)
                Collection<Subscription<VxCard,?>> updated = new ArrayList<>();
                for (Subscription<VxCard,?> sub : pkt.meta.sub) {
                    Log.d(TAG, "updating contact, user=" + sub.user);
                    if (Topic.getTopicTypeByName(sub.user) == Topic.TopicType.P2P) {
                        //Log.d(TAG, "contact " + sub.topic + "/" + sub.with + " added to list");
                        updated.add(sub);
                    }
                }
                Date upd = ContactsManager.updateContacts(mContext, account, updated,
                        meta.sub == null ? null : meta.sub.ims);
                setServerSyncMarker(account, upd);
            }
        } catch (IOException e) {
            e.printStackTrace();
            syncResult.stats.numIoExceptions++;
        } catch (Exception e) {
            e.printStackTrace();
            syncResult.stats.numAuthExceptions++;
        }
        Log.i(TAG, "Network synchronization complete");
    }
    @Override
    public void onPerformSync(final Account account, final Bundle extras, String authority,
                              ContentProviderClient provider, final SyncResult syncResult) {

        Log.i(TAG, "Beginning network synchronization");

        final Tinode tinode = Cache.getTinode();

        try {
            Log.i(TAG, "Starting sync for account " + account.name);

            // See if we already have a sync-state attached to this account.
            Date lastSyncMarker = getServerSyncMarker(account);
            long invisibleGroupId = getInvisibleGroupId(account);

            // By default, contacts from a 3rd party provider are hidden in the contacts
            // list. So let's set the flag that causes them to be visible, so that users
            // can actually see these contacts.
            if (lastSyncMarker == null) {
                ContactsManager.makeAccountContactsVisibile(mContext, account);
                invisibleGroupId = ContactsManager.createInvisibleTinodeGroup(mContext, account);
                setInvisibleGroupId(account, invisibleGroupId);
            }

            // See if we already have a sync-state attached to this account.
            //Date lastSyncMarker = getServerSyncMarker(account);

            // By default, contacts from a 3rd party provider are hidden in the contacts
            // list. So let's set the flag that causes them to be visible, so that users
            // can actually see these contacts.
            if (lastSyncMarker == null) {
                ContactsManager.makeAccountContactsVisibile(mContext, account);
            }

            final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
            String hostName = Cache.HOST_NAME;
            boolean tls = Cache.PREFS_USE_TLS;

            String token = AccountManager.get(mContext)
                    .blockingGetAuthToken(account, Utils.TOKEN_TYPE, false);
            tinode.connect(hostName, tls).getResult();
            tinode.loginToken(token).getResult();
            final String country = "KE";//BaseDb.getInstance().getCC();

            final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
            //final String country = Locale.getDefault().getCountry();

            Topic fndTopic = tinode.newTopic(Tinode.TOPIC_FND, null);

            try {

                fndTopic.subscribe().waitResult();

            } catch (AlreadySubscribedException ex){
                ex.printStackTrace();
            } catch (NotConnectedException ignored) {
                //Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                //return false;
                ignored.printStackTrace();
            } catch (Exception ignored) {
                //Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
                //return false;
                ignored.printStackTrace();
            }

            SparseArray<Utils.ContactHolder> obj = Utils.fetchEmailsAndPhones(getContext().getContentResolver(),ContactsContract.Data.CONTENT_URI,country);
            Date upd = null;
            ArrayList<String> tags = new ArrayList<String>();

            int x = obj.size();

            int y = 0;
            List<String> searchContacts = new ArrayList<String>();
            String search = "";

            MetaSetSub sub = new MetaSetSub(tinode.getMyId(),"JRWP");
            int batch = 0;

            while (y < x){

                Utils.ContactHolder contact = obj.valueAt(y);
                y++;
                try {

                    String login = contact.getPhone();
                    final String msisdn = String.valueOf(login).replace("+", "");
                    Log.d(TAG, login + " --> " + msisdn);

                    if (!TextUtils.isEmpty(msisdn) && msisdn.length() > 10){

                        if(search.length() > 5) {
                            search += ",tel:" + msisdn;
                        }
                        else {
                            search = "tel:" + msisdn;
                        }

                        searchContacts.add("tel:" + msisdn);
                        tags.add("tel:" + msisdn);
                    }
                }
                catch (Exception e){
                    e.printStackTrace();
                }

                if(searchContacts.size() % BATCH_SIZE == 0){

                    batch++;

                    Log.d(TAG,"#"+batch+" got "+searchContacts.size()+" contacts");

                    MetaSetDesc desc = new MetaSetDesc<>();

                    Log.d("TAGS HERE ",search);

                    desc.pub = search;
                    MsgSetMeta meta =  new MsgSetMeta(desc,null,null);
                    search = "";

                    try {

                        fndTopic.searchContact(meta).thenApply(null, null);

                    } catch (NotConnectedException ignored) {
                        ignored.printStackTrace();
                    } catch (Exception ignored) {
                        ignored.printStackTrace();
                    }

                    try {

                        final MsgGetMeta meta2 = MsgGetMeta.sub();
                        // FIXME(gene): The following is commented out for debugging
                        //MsgGetMeta meta = new MsgGetMeta(null, new MetaGetSub(getServerSyncMarker(account), null), null);
                        PromisedReply<ServerMessage> future = fndTopic.getMeta(meta2);// tinode.getMeta(Tinode.TOPIC_FND, meta2);
                        if (future.waitResult()) {

                            ServerMessage<?,?,VxCard,PrivateType> pkt = future.getResult();
                            if (pkt.meta == null || pkt.meta.sub == null) {
                                // Server did not return any contacts.
                                //return;
                            }
                            else {
                                // Fetch the list of updated contacts. Group subscriptions will be stored in
                                // the address book but as invisible contacts (members of invisible group)
                                Collection<Subscription<VxCard, ?>> updated = new ArrayList<>();
                                for (Subscription<VxCard, ?> sub0 : pkt.meta.sub) {

                                    if (Topic.getTopicTypeByName(sub0.user) == Topic.TopicType.P2P) {
                                        Log.d(TAG, "contact " + sub0.topic + "/" + sub0.pub.fn + " added to list");
                                        updated.add(sub0);
                                    }
                                }
                                upd = ContactsManager.updateContacts(mContext, account, updated,
                                        meta2.sub == null ? null : meta2.sub.ims);
                                setServerSyncMarker(account, upd);

                                Log.i(TAG, "#" + batch + " Beginning network synchronization f");
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.i(TAG, "#"+batch+" Beginning network synchronization g");
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.i(TAG, "#"+batch+" Beginning network synchronization h");
                    }

                    searchContacts.clear();
                    tags.clear();

                    Log.i(TAG, "#"+batch+" Beginning network synchronization i");

                }
            }

            if(searchContacts.size() % BATCH_SIZE != 0){

                batch++;

                Log.i(TAG, "#"+batch+" Beginning network synchronization a");

                Log.d(TAG,"#"+batch+" got "+y+" contacts");

                MetaSetDesc desc = new MetaSetDesc<>();

                //desc.priv = searchContacts;
                desc.pub = search;
                Log.d("TAGS HERE",search);

                MsgSetMeta meta =  new MsgSetMeta(desc,null,null);

                try {

                    fndTopic.searchContact(meta).thenApply(null, null);

                } catch (NotConnectedException ignored) {
                    ignored.printStackTrace();
                } catch (Exception ignored) {
                    ignored.printStackTrace();
                }

                Log.i(TAG, "#"+batch+" Beginning network synchronization b");

                Log.d(TAG, "#"+batch+" SYNC CONTACTS "+searchContacts.toString());

                try {

                    final MsgGetMeta meta2 = MsgGetMeta.sub();
                    // FIXME(gene): The following is commented out for debugging
                    //MsgGetMeta meta = new MsgGetMeta(null, new MetaGetSub(getServerSyncMarker(account), null), null);
                    PromisedReply<ServerMessage> future = fndTopic.getMeta(meta2);// tinode.getMeta(Tinode.TOPIC_FND, meta2);
                    if (future.waitResult()) {

                        Log.i(TAG, "#" + batch + " Beginning network synchronization c");

                        ServerMessage<?, ?, VxCard, PrivateType> pkt = future.getResult();
                        if (pkt.meta == null || pkt.meta.sub == null) {
                            // Server did not return any contacts.
                            //return;
                        }
                        else
                            {

                                Log.i(TAG, "#" + batch + " Beginning network synchronization d");
                                // Fetch the list of updated contacts. Group subscriptions will be stored in
                                // the address book but as invisible contacts (members of invisible group)
                                Collection<Subscription<VxCard, ?>> updated = new ArrayList<>();
                                for (Subscription<VxCard, ?> sub1 : pkt.meta.sub) {
                                    Log.d(TAG, "#" + batch + " updating contact, user=" + sub1.user);
                                    if (Topic.getTopicTypeByName(sub1.user) == Topic.TopicType.P2P) {
                                        Log.d(TAG, "contact " + sub1.topic + "/" + sub1.pub.fn + " added to list");
                                        updated.add(sub1);
                                    }
                                }
                                upd = ContactsManager.updateContacts(mContext, account, updated,
                                        meta2.sub == null ? null : meta2.sub.ims);
                                setServerSyncMarker(account, upd);
                            }

                        Log.i(TAG, "#"+batch+" Beginning network synchronization f");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.i(TAG, "#"+batch+" Beginning network synchronization g");
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.i(TAG, "#"+batch+" Beginning network synchronization h");
                }

                searchContacts.clear();
                tags.clear();
                Log.i(TAG, "#"+batch+" Beginning network synchronization i");

            }

            /**
            /// custom

            Cursor cursor = getContext().getContentResolver().query(ContactsContract.Data.CONTENT_URI, projection, selection, selectionArgs, "mimetype ASC");
            //Cursor cursor = getContext().getContentResolver().query(ContactsContract.Data.CONTENT_URI, projection, selection, selectionArgs, "mimetype ASC");

            if(cursor.getColumnCount() > BATCH_SIZE) {

                //BATCH_SIZE = cursor.getColumnCount();

            }

            if (cursor != null) {

                final int contactIdIdx = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID);
                final int mimeTypeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE);
                final int dataIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA);
                final int typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE);
                final int imProtocolIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Im.PROTOCOL);
                final int imProtocolNameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL);

                final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
                //final String country = Locale.getDefault().getCountry();

                Topic fndTopic = tinode.newTopic(Tinode.TOPIC_FND, null);

                try {

                    fndTopic.subscribe().waitResult();

                } catch (AlreadySubscribedException ex){
                    ex.printStackTrace();
                } catch (NotConnectedException ignored) {
                    //Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                    //return false;
                    ignored.printStackTrace();
                } catch (Exception ignored) {
                    //Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
                    //return false;
                    ignored.printStackTrace();
                }

                List<String> searchContacts = new ArrayList<String>();
                int batch = 0;

                while (cursor.moveToNext()) {

                    int type = cursor.getInt(typeIdx);
                    int contact_id = cursor.getInt(contactIdIdx);
                    String data = cursor.getString(dataIdx);
                    String mimeType = cursor.getString(mimeTypeIdx);

                    Log.d(TAG, "Got id=" + contact_id + ", type='" + type +"', val='" + data + "' country "+country);

                    switch (mimeType) {

                        case ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE:
                            searchContacts.add("email:"+data);
                            break;
                        case ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE:
                            break;
                        default:
                            // This is a phone number. Use mobile phones only.

                            break;
                    }


                    if (type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) {
                        // Log.d(TAG, "Adding mobile phone '" + data + "' to contact=" + contact_id);
                        try {
                            // Normalize phone number format
                            //data = data.replace(" ","").trim();
                            //data = phoneUtil.format(phoneUtil.parse(data, country), PhoneNumberUtil.PhoneNumberFormat.E164);
                            final String login = UiUtils.formatPhone(data,"254");
                            final String msisdn = String.valueOf(login).replace("+", "");
                            Log.d(TAG,data+" --> "+msisdn);
                            searchContacts.add("tel:"+msisdn);

                        } catch (Exception e) { //NumberParseException
                            e.printStackTrace();
                        }
                    }

                    if(searchContacts.size() % BATCH_SIZE == 0) {

                        batch++;

                        MetaSetDesc desc = new MetaSetDesc<>();
                        desc.priv = searchContacts;

                        MsgSetMeta meta =  new MsgSetMeta(desc,null,null);

                        try {

                            fndTopic.searchContact(meta).thenApply(null, null);

                        } catch (NotConnectedException ignored) {
                            ignored.printStackTrace();
                        } catch (Exception ignored) {
                            ignored.printStackTrace();
                        }

                        Log.d(TAG, "SYNC CONTACTS "+searchContacts.toString());

                        //return true;
                        searchContacts.clear();

                        try {

                            final MsgGetMeta meta2 = MsgGetMeta.sub();
                            // FIXME(gene): The following is commented out for debugging
                            //MsgGetMeta meta = new MsgGetMeta(null, new MetaGetSub(getServerSyncMarker(account), null), null);
                            PromisedReply<ServerMessage> future = fndTopic.getMeta(meta2);// tinode.getMeta(Tinode.TOPIC_FND, meta2);
                            if (future.waitResult()) {

                                ServerMessage<?,?,VxCard,PrivateType> pkt = future.getResult();
                                if (pkt.meta == null || pkt.meta.sub == null) {
                                    // Server did not return any contacts.
                                    return;
                                }
                                // Fetch the list of updated contacts. Group subscriptions will be stored in
                                // the address book but as invisible contacts (members of invisible group)
                                Collection<Subscription<VxCard,?>> updated = new ArrayList<>();
                                for (Subscription<VxCard,?> sub : pkt.meta.sub) {
                                    Log.d(TAG, "updating contact, user=" + sub.user);
                                    if (Topic.getTopicTypeByName(sub.user) == Topic.TopicType.P2P) {
                                        Log.d(TAG, "contact " + sub.topic + "/" + sub.pub.fn + " added to list");
                                        updated.add(sub);
                                    }
                                }
                                Date upd = ContactsManager.updateContacts(mContext, account, updated,
                                        meta2.sub == null ? null : meta2.sub.ims);
                                setServerSyncMarker(account, upd);

                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                cursor.close();

                if(searchContacts.size() % BATCH_SIZE != 0) {

                    batch++;
                    MetaSetDesc desc = new MetaSetDesc<>();
                    desc.priv = searchContacts;

                    MsgSetMeta meta =  new MsgSetMeta(desc,null,null);

                    try {

                        fndTopic.searchContact(meta).thenApply(null, null);

                    } catch (NotConnectedException ignored) {
                        ignored.printStackTrace();
                    } catch (Exception ignored) {
                        ignored.printStackTrace();
                    }

                    Log.d(TAG, "SYNC CONTACTS "+searchContacts.toString());
                    //return true;
                    searchContacts.clear();

                    try {

                        final MsgGetMeta meta2 = MsgGetMeta.sub();
                        // FIXME(gene): The following is commented out for debugging
                        //MsgGetMeta meta = new MsgGetMeta(null, new MetaGetSub(getServerSyncMarker(account), null), null);
                        PromisedReply<ServerMessage> future = fndTopic.getMeta(meta2);// tinode.getMeta(Tinode.TOPIC_FND, meta2);
                        if (future.waitResult()) {

                            ServerMessage<?,?,VxCard,PrivateType> pkt = future.getResult();
                            if (pkt.meta == null || pkt.meta.sub == null) {
                                // Server did not return any contacts.
                                return;
                            }
                            // Fetch the list of updated contacts. Group subscriptions will be stored in
                            // the address book but as invisible contacts (members of invisible group)
                            Collection<Subscription<VxCard,?>> updated = new ArrayList<>();
                            for (Subscription<VxCard,?> sub : pkt.meta.sub) {
                                Log.d(TAG, "updating contact, user=" + sub.user);
                                if (Topic.getTopicTypeByName(sub.user) == Topic.TopicType.P2P) {
                                    //Log.d(TAG, "contact " + sub.topic + "/" + sub.with + " added to list");
                                    updated.add(sub);
                                }
                            }
                            Date upd = ContactsManager.updateContacts(mContext, account, updated,
                                    meta2.sub == null ? null : meta2.sub.ims);
                            setServerSyncMarker(account, upd);

                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }

            }

            // Don't care if it's resolved or rejected
            tinode.subscribe(Tinode.TOPIC_FND, null, null).waitResult();

            final MsgGetMeta meta = MsgGetMeta.sub();

            // FIXME(gene): The following is commented out for debugging
            // MsgGetMeta meta = new MsgGetMeta(null, new MetaGetSub(getServerSyncMarker(account), null), null);
            PromisedReply<ServerMessage> future = tinode.getMeta(Tinode.TOPIC_FND, meta);
            if (future.waitResult()) {
                ServerMessage<?,?,VxCard,PrivateType> pkt = future.getResult();
                if (pkt.meta == null || pkt.meta.sub == null) {
                    // Server did not return any contacts.
                    return;
                }
                // Fetch the list of updated contacts. Group subscriptions will be stored in
                // the address book but as invisible contacts (members of invisible group)
                Collection<Subscription<VxCard,?>> updated = new ArrayList<>();
                for (Subscription<VxCard,?> sub : pkt.meta.sub) {
                    Log.d(TAG, "updating contact, user=" + sub.user);
                    if (Topic.getTopicTypeByName(sub.user) == Topic.TopicType.P2P) {
                        //Log.d(TAG, "contact " + sub.topic + "/" + sub.with + " added to list");
                        updated.add(sub);
                    }
                }
                Date upd = ContactsManager.updateContacts(mContext, account, updated,
                        meta.sub == null ? null : meta.sub.ims);
                setServerSyncMarker(account, upd);
            }
            */
        } catch (IOException e) {
            Log.i(TAG, "Beginning network synchronization k");
            e.printStackTrace();
            syncResult.stats.numIoExceptions++;
        } catch (Exception e) {
            Log.i(TAG, "Beginning network synchronization l");
            e.printStackTrace();
            syncResult.stats.numAuthExceptions++;
        }
        Log.i(TAG, "Network synchronization complete");
    }

    private Date getServerSyncMarker(Account account) {

        String markerString = mAccountManager.getUserData(account, ACCKEY_SYNC_MARKER);
        if (!TextUtils.isEmpty(markerString)) {
            return new Date(Long.parseLong(markerString));
        }
        return null;
    }

    private void setServerSyncMarker(Account account, Date marker) {
        // The marker could be null if user has no contacts
        if (marker != null) {
            mAccountManager.setUserData(account, ACCKEY_SYNC_MARKER, Long.toString(marker.getTime()));
        }
    }

    private long getInvisibleGroupId(Account account) {
        String idString = mAccountManager.getUserData(account, ACCKEY_INVISIBLE_GROUP);
        if (!TextUtils.isEmpty(idString)) {
            return Long.parseLong(idString);
        }
        return -1;
    }

    private void setInvisibleGroupId(Account account, long id) {
        mAccountManager.setUserData(account, ACCKEY_INVISIBLE_GROUP, Long.toString(id));
    }
}

