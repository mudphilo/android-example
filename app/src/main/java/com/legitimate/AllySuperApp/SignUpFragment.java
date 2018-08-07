package com.legitimate.AllySuperApp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.github.ybq.android.spinkit.style.DoubleBounce;
import com.hbb20.CountryCodePicker;

import com.legitimate.AllySuperApp.R;
import com.legitimate.AllySuperApp.media.VxCard;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.Credential;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.MetaSetDesc;

import static android.app.Activity.RESULT_OK;

/**
 * Fragment for managing registration of a new account.
 */
public class SignUpFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = "SignUpFragment";

    CountryCodePicker msisdnField;
    EditText editTextCarrierNumber;
    Button signIn;
    ProgressBar progressBar;
    String oText;
    Boolean isLoading = false;

    public SignUpFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(false);

        ActionBar bar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }

        View fragment = inflater.inflate(R.layout.activity_sign_up, container, false);

        fragment.findViewById(R.id.signUp).setOnClickListener(this);

        editTextCarrierNumber = (EditText) fragment.findViewById(R.id.editText_carrierNumber);
        msisdnField = (CountryCodePicker) fragment.findViewById(R.id.msisdn);
        msisdnField.registerCarrierNumberEditText(editTextCarrierNumber);

        signIn = (Button) fragment.findViewById(R.id.signIn);
        progressBar = (ProgressBar) fragment.findViewById(R.id.loadingProgressBar);

        DoubleBounce doubleBounce = new DoubleBounce();
        doubleBounce.setBounds(0, 0, 100, 100);
        doubleBounce.setColor(getResources().getColor(R.color.colorAccent));
        progressBar.setIndeterminateDrawable(doubleBounce);
        oText = signIn.getText().toString();

        return fragment;
    }

    @Override
    public void onActivityCreated(Bundle savedInstance) {
        super.onActivityCreated(savedInstance);

        // Get avatar from the gallery
        // TODO(gene): add support for taking a picture
        getActivity().findViewById(R.id.uploadAvatar).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiUtils.requestAvatar(SignUpFragment.this);
            }
        });
    }

    /**
     * Create new account with various methods
     *
     * @param v button pressed
     */
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.signUp:
                onSignUp();
                break;
            default:
        }
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

    public void onSignUp() {

        if(isLoading){
            return;
        }

        final LoginActivity parent = (LoginActivity) getActivity();

        if(!msisdnField.isValidFullNumber()){

            Toast.makeText(parent, "Invalid Mobile Number", Toast.LENGTH_SHORT).show();
            return;
        }

        String phoneNumber = editTextCarrierNumber.getText().toString();
        String countryCode = msisdnField.getSelectedCountryCode();
        String isoCode = msisdnField.getSelectedCountryNameCode();


        final String login = UiUtils.formatPhone(phoneNumber,isoCode);
        final String msisdn = String.valueOf(login).replace("+", "");

        final String password = ((EditText) parent.findViewById(R.id.newPassword)).getText().toString().trim();
        final String repeatPassword = ((EditText) parent.findViewById(R.id.repeatPassword)).getText().toString().trim();

        if (password.isEmpty()) {
            ((EditText) parent.findViewById(R.id.newPassword)).setError(getText(R.string.password_required));
            return;
        }

        if(TextUtils.isEmpty(msisdn)){

            Toast.makeText(parent, "Invalid Mobile Number", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(repeatPassword)) {
            ((EditText) parent.findViewById(R.id.newPassword)).setError(getText(R.string.passwords_dont_match));
            ((EditText) parent.findViewById(R.id.repeatPassword)).setError(getText(R.string.passwords_dont_match));
            return;
        }

        loading();

        SharedPreferences sharedPref = getContext().getSharedPreferences("ASA", 0);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("CC",countryCode);
        editor.commit();

        String hostName =  Cache.HOST_NAME;
        boolean tls = Cache.PREFS_USE_TLS;

        final String fullName = ((EditText) parent.findViewById(R.id.name)).getText().toString().trim();
        final ImageView avatar = (ImageView) parent.findViewById(R.id.imageAvatar);

        final Tinode tinode = Cache.getTinode();
        try {

            // This is called on the websocket thread.
            tinode.connect(hostName, tls)
                    .thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored_msg) throws Exception {

                                    stopLoading();
                                    // Try to create a new account.
                                    Bitmap bmp = null;
                                    try {
                                        bmp = ((BitmapDrawable) avatar.getDrawable()).getBitmap();
                                    } catch (ClassCastException ignored) {
                                        // If image is not loaded, the drawable is a vector.
                                        // Ignore it.
                                    }
                                    VxCard vcard = new VxCard(fullName, bmp);

                                    return tinode.createAccountBasic(
                                            msisdn, password, true, null,
                                            new MetaSetDesc<VxCard,String>(vcard, null),
                                            Credential.append(null, new Credential("tel", msisdn)));

                                }
                            }, null)
                    .thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(final ServerMessage msg) throws Exception {
                                    // Flip back to login screen on success;
                                    parent.runOnUiThread(new Runnable() {
                                        public void run() {
                                            stopLoading();

                                            Bundle authBundle = new Bundle();
                                            authBundle.putString("msisdn",msisdn);
                                            authBundle.putString("secret",password);

                                            FragmentTransaction trx = parent.getSupportFragmentManager().beginTransaction();

                                            if (msg.ctrl.code >= 300 && msg.ctrl.text.contains("validate credentials")) {

                                                CredentialsFragment credentialsFragment = new CredentialsFragment();
                                                credentialsFragment.setArguments(authBundle);
                                                trx.replace(R.id.contentFragment, credentialsFragment);

                                            } else {

                                                trx.replace(R.id.contentFragment, new LoginFragment());

                                            }
                                            trx.commit();
                                        }
                                    });
                                    return null;
                                }
                            },
                            new PromisedReply.FailureListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onFailure(Exception err) throws Exception {
                                    stopLoading();
                                    parent.reportError(err, R.string.error_new_account_failed);
                                    return null;
                                }
                            });

        } catch (Exception e) {
            stopLoading();
            Log.e(TAG, "Something went wrong", e);
        }
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == UiUtils.SELECT_PICTURE && resultCode == RESULT_OK) {
            UiUtils.acceptAvatar(getActivity(), (ImageView) getActivity().findViewById(R.id.imageAvatar), data);
        }
    }
}
