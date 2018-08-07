package com.legitimate.AllySuperApp;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.ColorRes;
import android.support.annotation.DimenRes;
import android.support.annotation.IntegerRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.github.ybq.android.spinkit.style.DoubleBounce;
import com.github.ybq.android.spinkit.style.Wave;
import com.hbb20.CountryCodePicker;

import com.legitimate.AllySuperApp.R;
import com.legitimate.AllySuperApp.account.Utils;
import com.legitimate.AllySuperApp.db.BaseDb;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.AuthScheme;
import co.tinode.tinodesdk.model.ServerMessage;

/**
 * A placeholder fragment containing a simple view.
 */
public class LoginFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = "LoginFragment";
    BaseDb db;

    CountryCodePicker msisdnField;
    EditText editTextCarrierNumber;
    Button signIn;
    String oText;
    Boolean isLoading = false;
    //public Wave mWaveDrawable;
    ProgressBar progressBar;

    public LoginFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(true);

        final ActionBar bar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(false);
        }

         db = BaseDb.getInstance();

        View fragment = inflater.inflate(R.layout.fragment_login, container, false);

        final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String login = pref.getString(LoginActivity.PREFS_LAST_LOGIN, null);
        final LoginActivity parent = (LoginActivity) getActivity();

        signIn = (Button) fragment.findViewById(R.id.signIn);
        progressBar = (ProgressBar) fragment.findViewById(R.id.loadingProgressBar);

        DoubleBounce doubleBounce = new DoubleBounce();
        doubleBounce.setBounds(0, 0, 100, 100);
        doubleBounce.setColor(getResources().getColor(R.color.colorAccent));
        progressBar.setIndeterminateDrawable(doubleBounce);
        oText = signIn.getText().toString();

        fragment.findViewById(R.id.signIn).setOnClickListener(this);
        fragment.findViewById(R.id.join).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                FragmentTransaction trx = parent.getSupportFragmentManager().beginTransaction();
                trx.replace(R.id.contentFragment, new SignUpFragment());
                trx.commit();
            }
        });

        editTextCarrierNumber = (EditText) fragment.findViewById(R.id.editText_carrierNumber);
        msisdnField = (CountryCodePicker) fragment.findViewById(R.id.msisdn);
        msisdnField.registerCarrierNumberEditText(editTextCarrierNumber);

        if (!TextUtils.isEmpty(login)) {

            if (msisdnField != null) {
                msisdnField.setFullNumber(login);
            }
        }

        return fragment;
    }

    public void loading(){

        getActivity().runOnUiThread(new Runnable() {

            @Override
            public void run() {

                signIn.setText(getText(R.string.loading_text));
                progressBar.setIndeterminate(true);
                progressBar.setVisibility(View.VISIBLE);
                isLoading = true;

            }
        });

    }

    public void stopLoading(){

        getActivity().runOnUiThread(new Runnable() {

            @Override
            public void run() {

                signIn.setText(oText);
                progressBar.setIndeterminate(false);
                progressBar.setVisibility(View.GONE);
                isLoading = false;

            }
        });
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_login, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    /**
     * Login button pressed.
     * @param v ignored
     */
    public void onClick(View v) {

        if(isLoading){
            return;
        }

        final LoginActivity parent = (LoginActivity) getActivity();

        EditText passwordInput = parent.findViewById(R.id.editPassword);

        if(!msisdnField.isValidFullNumber()){

            Toast.makeText(parent, "Invalid Mobile Number", Toast.LENGTH_SHORT).show();
            return;
        }

        String phoneNumber = editTextCarrierNumber.getText().toString();
        String countryCode = msisdnField.getSelectedCountryCode();
        String isoCode = msisdnField.getSelectedCountryNameCode();

        final String msisdn = UiUtils.formatPhone(phoneNumber,isoCode);
        final String login = String.valueOf(msisdn).replace("+", "");

        final String password = passwordInput.getText().toString().trim();
        if (password.isEmpty()) {
            passwordInput.setError(getText(R.string.password_required));
            return;
        }

        loading();

        SharedPreferences sharedPref1 = getContext().getSharedPreferences("ASA", 0);
        SharedPreferences.Editor editor = sharedPref1.edit();
        editor.putString("CC",countryCode);
        editor.commit();

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(parent);

        String hostName =  Cache.HOST_NAME;
        boolean tls = Cache.PREFS_USE_TLS;

        final Tinode tinode = Cache.getTinode();

        try {
            Log.d(TAG, "CONNECTING to "+hostName);
            // This is called on the websocket thread.
            tinode.connect(hostName, tls)
                    .thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored) throws Exception {
                                    return tinode.loginBasic(
                                            login,
                                            password);
                                }
                            },
                            null)
                    .thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage msg) {

                                    stopLoading();
                                    sharedPref.edit().putString(LoginActivity.PREFS_LAST_LOGIN, login).apply();

                                    final Account acc = addAndroidAccount(
                                            tinode.getMyId(),
                                            AuthScheme.basicInstance(login, password).toString(),
                                            tinode.getAuthToken());

                                    if (msg.ctrl.code >= 300 && msg.ctrl.text.contains("validate credentials")) {
                                        parent.runOnUiThread(new Runnable() {
                                            public void run() {

                                                FragmentTransaction trx = parent.getSupportFragmentManager().beginTransaction();
                                                trx.replace(R.id.contentFragment, new CredentialsFragment());
                                                trx.commit();
                                            }
                                        });
                                    } else {
                                        // Force immediate sync, otherwise Contacts tab may be unusable.
                                        Bundle bundle = new Bundle();
                                        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
                                        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
                                        ContentResolver.requestSync(acc, Utils.SYNC_AUTHORITY, bundle);
                                        UiUtils.onLoginSuccess(parent, signIn);
                                    }
                                    return null;
                                }
                            },
                            new PromisedReply.FailureListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onFailure(Exception err) {
                                    stopLoading();
                                    db.logout();
                                    Log.d(TAG, "Login failed", err);
                                    parent.reportError(err, signIn, R.string.error_login_failed);
                                    return null;
                                }
                            });
        } catch (Exception err) {
            stopLoading();
            Log.e(TAG, "Something went wrong", err);
            parent.reportError(err, signIn, R.string.error_login_failed);
        }
    }


    private Account addAndroidAccount(final String uid, final String secret, final String token) {
        final AccountManager am = AccountManager.get(getActivity().getBaseContext());
        final Account acc = Utils.createAccount(uid);

        final Bundle extraData = new Bundle();

        am.addAccountExplicitly(acc, secret, extraData);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.notifyAccountAuthenticated(acc);
        }
        if (!TextUtils.isEmpty(token)) {
            am.setAuthToken(acc, Utils.TOKEN_TYPE, token);
        }
        return acc;
    }
}
