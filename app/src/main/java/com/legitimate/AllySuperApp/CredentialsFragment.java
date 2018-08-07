package com.legitimate.AllySuperApp;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;

import com.github.ybq.android.spinkit.style.DoubleBounce;
import com.legitimate.AllySuperApp.R;
import com.legitimate.AllySuperApp.account.Utils;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.AuthScheme;
import co.tinode.tinodesdk.model.Credential;
import co.tinode.tinodesdk.model.ServerMessage;

/**
 * A placeholder fragment containing a simple view.
 */
public class CredentialsFragment extends Fragment implements View.OnClickListener{
    private static final String TAG = "CredentialsFragment";

    String msisdn = null;
    String password = null;
    Activity context = null;
    LoginActivity parent = null;
    Button signIn;
    String oText;
    Boolean isLoading = false;
    ProgressBar progressBar;

    public CredentialsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        if (getArguments() != null) {
            msisdn = getArguments().getString("msisdn");
            password = getArguments().getString("secret");
        }

        this.context = getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(false);

        ActionBar bar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }

        View fragment = inflater.inflate(R.layout.fragment_validate, container, false);
        fragment.findViewById(R.id.signUp).setOnClickListener(this);

        signIn = (Button) fragment.findViewById(R.id.signUp);
        progressBar = (ProgressBar) fragment.findViewById(R.id.loadingProgressBar);

        DoubleBounce doubleBounce = new DoubleBounce();
        doubleBounce.setBounds(0, 0, 100, 100);
        doubleBounce.setColor(getResources().getColor(R.color.colorAccent));
        progressBar.setIndeterminateDrawable(doubleBounce);
        oText = signIn.getText().toString();

        parent = (LoginActivity) getActivity();

        return fragment;
    }

    @Override
    public void onActivityCreated(Bundle unused) {
        super.onActivityCreated(unused);
    }

    public void loading(){

        signIn.setText(getText(R.string.loading_text));
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.VISIBLE);
        isLoading = true;
    }

    public void stopLoading(){

        signIn.setText(oText);
        progressBar.setIndeterminate(false);
        progressBar.setVisibility(View.GONE);
        isLoading = false;
    }


    @Override
    public void onClick(View view) {

        if(isLoading){
            return;
        }

        final Tinode tinode = Cache.getTinode();
        final String token = tinode.getAuthToken();

        Log.d("DEBUGTEST","at 1 ");

        Log.d("DEBUGTEST","at 3 token "+token);

        final String code = ((EditText) parent.findViewById(R.id.response)).getText().toString().trim();
        if (code.isEmpty()) {
            ((EditText) parent.findViewById(R.id.response)).setError(getText(R.string.enter_confirmation_code));
            return;
        }

        if (TextUtils.isEmpty(token)) {

            reLogin(code);

            Log.d("DEBUGTEST","at 2 token is empty ");
            FragmentTransaction trx = parent.getSupportFragmentManager().beginTransaction();
            trx.replace(R.id.contentFragment, new LoginFragment());
            trx.commit();
            return;
        }

        loading();

        try {

            //Bundle args = this.getArguments();
            String method = "tel";//args.getString("credential");

            Credential[] cred = new Credential[1];
            cred[0] = new Credential(method, null, code, null);

            tinode.loginToken(token, cred).thenApply(
                new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage msg) {

                        stopLoading();

                        Log.d("DEBUGTEST","at 4 success validated ");
                        login();
                        return null;
                    }
                },
                new PromisedReply.FailureListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onFailure(Exception err) {

                        Log.d("DEBUGTEST","at 5 incorrect code ");

                        stopLoading();
                        parent.reportError(err, R.string.failed_credential_confirmation);
                        return null;
                    }
                });

        } catch (Exception e) {
            stopLoading();
            Log.e(TAG, "Something went wrong", e);
        }
    }

    private Account addAndroidAccount(final String uid, final String secret, final String token) {

        final AccountManager am = AccountManager.get(this.context.getBaseContext());
        final Account acc = Utils.createAccount(uid);
        am.addAccountExplicitly(acc, secret, null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.notifyAccountAuthenticated(acc);
        }
        if (!TextUtils.isEmpty(token)) {
            am.setAuthToken(acc, Utils.TOKEN_TYPE, token);
        }
        return acc;
    }

    private void login(){

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(parent);

        String hostName =  Cache.HOST_NAME;
        boolean tls = Cache.PREFS_USE_TLS;

        final Tinode tinode = Cache.getTinode();

        loading();

        try {
            Log.d(TAG, "CONNECTING to "+hostName);
            // This is called on the websocket thread.
            tinode.connect(hostName, tls)
                    .thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored) throws Exception {
                                    return tinode.loginBasic(
                                            msisdn,
                                            password);
                                }
                            },
                            null)
                    .thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage msg) {
                                    sharedPref.edit().putString(LoginActivity.PREFS_LAST_LOGIN, msisdn).apply();

                                    stopLoading();
                                    final Account acc = addAndroidAccount(
                                            tinode.getMyId(),
                                            AuthScheme.basicInstance(msisdn, password).toString(),
                                            tinode.getAuthToken());


                                    if (msg.ctrl.code >= 300 && msg.ctrl.text.contains("validate credentials")) {
                                        //
                                    } else {
                                        // Force immediate sync, otherwise Contacts tab may be unusable.
                                        Bundle bundle = new Bundle();
                                        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
                                        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
                                        ContentResolver.requestSync(acc, Utils.SYNC_AUTHORITY, bundle);
                                        UiUtils.onLoginSuccess(parent, null);
                                    }
                                    return null;
                                }
                            },
                            new PromisedReply.FailureListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onFailure(Exception err) {
                                    stopLoading();
                                    Log.d(TAG, "Login failed", err);
                                    parent.reportError(err, null, R.string.error_login_failed);
                                    return null;
                                }
                            });
        } catch (Exception err) {
            stopLoading();
            Log.e(TAG, "Something went wrong", err);
            parent.reportError(err, null, R.string.error_login_failed);

        }
    }

    private void reLogin(final String code){

        // try to auto login

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(parent);
        String hostName =  Cache.HOST_NAME;
        boolean tls = Cache.PREFS_USE_TLS;

        final Tinode tinode = Cache.getTinode();
        final String token = tinode.getAuthToken();

        Log.d("DEBUGTEST","at 3 token "+token);

        loading();

        try {
            Log.d(TAG, "CONNECTING to "+hostName);
            // This is called on the websocket thread.
            tinode.connect(hostName, tls)
                    .thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored) throws Exception {
                                    return tinode.loginBasic(
                                            msisdn,
                                            password);
                                }
                            },
                            null)
                    .thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage msg) {
                                    sharedPref.edit().putString(LoginActivity.PREFS_LAST_LOGIN, msisdn).apply();

                                    final Account acc = addAndroidAccount(
                                            tinode.getMyId(),
                                            AuthScheme.basicInstance(msisdn, password).toString(),
                                            tinode.getAuthToken());

                                    if (msg.ctrl.code >= 300 && msg.ctrl.text.contains("validate credentials")) {
                                        parent.runOnUiThread(new Runnable() {
                                            public void run() {

                                                try {
                                                    //Bundle args = this.getArguments();
                                                    String method = "tel";//args.getString("credential");

                                                    Credential[] cred = new Credential[1];
                                                    cred[0] = new Credential(method, null, code, null);

                                                    String new_token = tinode.getAuthToken();

                                                    tinode.loginToken(new_token, cred).thenApply(
                                                            new PromisedReply.SuccessListener<ServerMessage>() {
                                                                @Override
                                                                public PromisedReply<ServerMessage> onSuccess(ServerMessage msg) {

                                                                    Log.d("DEBUGTEST","at 4b success validated ");

                                                                    sharedPref.edit().putString(LoginActivity.PREFS_LAST_LOGIN, msisdn).apply();

                                                                    final Account acc = addAndroidAccount(
                                                                            tinode.getMyId(),
                                                                            AuthScheme.basicInstance(msisdn, password).toString(),
                                                                            tinode.getAuthToken());

                                                                    if (msg.ctrl.code >= 300 && msg.ctrl.text.contains("validate credentials")) {
                                                                        //
                                                                    } else {
                                                                        // Force immediate sync, otherwise Contacts tab may be unusable.
                                                                        Bundle bundle = new Bundle();
                                                                        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
                                                                        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
                                                                        ContentResolver.requestSync(acc, Utils.SYNC_AUTHORITY, bundle);
                                                                        UiUtils.onLoginSuccess(parent, null);
                                                                    }
                                                                    return null;

                                                                    /**
                                                                    // Flip back to login screen on success;
                                                                    parent.runOnUiThread(new Runnable() {
                                                                        public void run() {
                                                                            confirm.setEnabled(true);
                                                                            confirm.loadingSuccessful();
                                                                            FragmentTransaction trx = parent.getSupportFragmentManager().beginTransaction();
                                                                            trx.replace(R.id.contentFragment, new LoginFragment());
                                                                            trx.commit();
                                                                        }
                                                                    });
                                                                    */
                                                                    //return null;
                                                                }
                                                            },
                                                            new PromisedReply.FailureListener<ServerMessage>() {
                                                                @Override
                                                                public PromisedReply<ServerMessage> onFailure(Exception err) {

                                                                    Log.d("DEBUGTEST","at 5 incorrect code ");

                                                                    stopLoading();
                                                                    parent.reportError(err, R.string.failed_credential_confirmation);
                                                                    return null;
                                                                }
                                                            });
                                                } catch (Exception e) {
                                                    stopLoading();
                                                    Log.e(TAG, "Something went wrong", e);

                                                }
                                            }
                                        });
                                    } else {
                                        stopLoading();
                                        // Force immediate sync, otherwise Contacts tab may be unusable.
                                        Bundle bundle = new Bundle();
                                        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
                                        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
                                        ContentResolver.requestSync(acc, Utils.SYNC_AUTHORITY, bundle);
                                    }
                                    return null;
                                }
                            },
                            new PromisedReply.FailureListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onFailure(Exception err) {

                                    stopLoading();

                                    Log.d(TAG, "Login failed", err);
                                    parent.reportError(err, R.string.error_login_failed);

                                    return null;
                                }
                            });
        } catch (Exception err) {
            stopLoading();
            Log.e(TAG, "Something went wrong", err);
            parent.reportError(err, R.string.error_login_failed);
        }
    }
}
