package com.legitimate.AllySuperApp;


import android.Manifest;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.legitimate.AllySuperApp.R;
import co.tinode.tinodesdk.Tinode;

/**
 * LoginActivity is a FrameLayout which switches between three fragments:
 *  - LoginFragment
 *  - SignUpFragment
 *  - LoginSettingsFragment
 *
 *  1. If connection to the server is already established and authenticated, launch ContactsActivity
 *  2. If no connection to the server, get the last used account:
 *  3.1 Connect to server
 *  3.1.1 If connection is successful, authenticate with the token received from the account
 *  3.1.1.1 If authentication is successful go to 1.
 *  3.1.1.2 If not, go to 4.
 *  3.1.2 If connection is not successful
 *  3.1.2 Show offline indicator
 *  3.1.3 Access locally stored account.
 *  3.1.3.1 If locally stored account is found, launch ContactsActivity
 *  3.1.3.2 If not found, go to 4.
 *  4. If account not found, show login form
 *
 */

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final int PERMISSIONS_REQUEST_GET_ACCOUNTS = 100;

    public static final String EXTRA_CONFIRM_CREDENTIALS = "confirmCredentials";
    public static final String EXTRA_ADDING_ACCOUNT = "addNewAccount";

    private static final String FRAGMENT_LOGIN = "login";
    private static final String FRAGMENT_SIGNUP = "signup";
    private static final String FRAGMENT_SETTINGS = "settings";
    private static final String FRAGMENT_CREDENTIALS = "cred";

    static final String PREFS_LAST_LOGIN = "pref_lastLogin";

    private AccountAuthenticatorResponse mAccountAuthenticatorResponse = null;
    private Bundle mResultBundle = null;

    //private LoginFragment mLoginFragment = null;
    //private SignUpFragment mSignUpFragment = null;
    //private LoginSettingsFragment mSettingsFragment = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);

        PreferenceManager.setDefaultValues(this, R.xml.login_preferences, false);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Handle clicks on the '<-' arrow in the toolbar.
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFragment(FRAGMENT_LOGIN);
            }
        });

        Tinode tinode = Cache.getTinode();
        if (tinode.isAuthenticated()) {
            // We already have a live connection to the server. All good.
            // Launch the contacts activity and stop.
            Intent intent = new Intent(this, ContactsActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        // Check if the activity show ask for credentials instead of login/password.
        final Intent intent = getIntent();
        String cred = intent.getStringExtra("credential");

        if (TextUtils.isEmpty(cred)) {

            String signup = intent.getStringExtra("signup");
            if (TextUtils.isEmpty(signup)) {
                // Display the login form.
                showFragment(FRAGMENT_LOGIN);
            }
            else {

                // Display the signup form.
                showFragment(FRAGMENT_SIGNUP);
            }
        }
        else {
            // Ask for validation code
            Bundle args = new Bundle();
            args.putString("credential", cred);
            showFragment(FRAGMENT_CREDENTIALS, args);
        }
        // Request permission to access accounts. We need access to acccounts to store the login token.
        if (!UiUtils.checkPermission(this, Manifest.permission.GET_ACCOUNTS)) {
            requestAccountAccessPermission();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        UiUtils.setupToolbar(this, null, null, false);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    // Run-time check for permission to GET_ACCOUNTS
    private void requestAccountAccessPermission() {
            // Result will be returned in onRequestPermissionsResult
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.GET_ACCOUNTS},
                    PERMISSIONS_REQUEST_GET_ACCOUNTS);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions,
                                           @NonNull final int[] grantResults) {
        //
        if (requestCode == PERMISSIONS_REQUEST_GET_ACCOUNTS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission is granted
                Log.d(TAG, "Access granted");
            } else {
                // Permission denied, so we won't be able to save login token

                Log.d(TAG, "Access denied");
            }
        }
    }


    void reportError(final Exception err, final Button button, final int errId) {
        String message = err.getMessage();
        Log.i(TAG, getText(errId) + " " + message, err);

        Throwable cause = err;
        while ((cause = cause.getCause()) != null) {
            message = cause.getMessage();
        }
        final String finalMessage = message;

        runOnUiThread(new Runnable() {
            public void run() {
                if (button != null) {
                    button.setEnabled(true);
                }
                Toast.makeText(LoginActivity.this,
                        getText(errId) + " " + finalMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    void reportError(final Exception err, final int errId) {
        String message = err.getMessage();
        Log.i(TAG, getText(errId) + " " + message, err);

        Throwable cause = err;
        while ((cause = cause.getCause()) != null) {
            message = cause.getMessage();
        }
        final String finalMessage = message;

        runOnUiThread(new Runnable() {
            public void run() {

                Toast.makeText(LoginActivity.this,
                        getText(errId) + " " + finalMessage, Toast.LENGTH_LONG).show();
            }
        });
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {
            /**
            case R.id.action_settings: {
                showFragment(FRAGMENT_SETTINGS);
                return true;
            }
            */
            case R.id.action_signup: {
                showFragment(FRAGMENT_SIGNUP);
                return true;
            }
            case R.id.action_about:
                DialogFragment about = new AboutDialogFragment();
                about.show(getSupportFragmentManager(), "about");
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showFragment(String tag) {
        showFragment(tag, null);
    }

    private void showFragment(String tag, Bundle args) {
        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentByTag(tag);
        if (fragment == null) {
            switch (tag) {
                case FRAGMENT_LOGIN:
                    fragment = new LoginFragment();
                    break;
                case FRAGMENT_SETTINGS:
                    fragment = new LoginSettingsFragment();
                    break;
                case FRAGMENT_SIGNUP:
                    fragment = new SignUpFragment();
                    break;
                case FRAGMENT_CREDENTIALS:
                    fragment = new CredentialsFragment();
                    break;
                default:
                    throw new IllegalArgumentException();
            }
        }

        if (args != null) {
            fragment.setArguments(args);
        }

        fm.beginTransaction()
            .replace(R.id.contentFragment, fragment)
            .commit();
    }

    /**
     * Sends the result or a Constants.ERROR_CODE_CANCELED error if a result isn't present.
     */
    @Override
    public void finish() {
        if (mAccountAuthenticatorResponse != null) {
            // send the result bundle back if set, otherwise send an error.
            if (mResultBundle != null) {
                mAccountAuthenticatorResponse.onResult(mResultBundle);
            } else {
                mAccountAuthenticatorResponse.onError(AccountManager.ERROR_CODE_CANCELED, "canceled");
            }
            mAccountAuthenticatorResponse = null;
        }
        super.finish();
    }
}
