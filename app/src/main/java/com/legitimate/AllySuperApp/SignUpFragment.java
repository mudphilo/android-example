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
import android.widget.Toast;

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
    Button btn;

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

        fragment.findViewById(R.id.join).setOnClickListener(this);

        editTextCarrierNumber = (EditText) fragment.findViewById(R.id.editText_carrierNumber);
        msisdnField = (CountryCodePicker) fragment.findViewById(R.id.msisdn);
        msisdnField.registerCarrierNumberEditText(editTextCarrierNumber);

        btn = (Button) fragment.findViewById(R.id.join);

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
            case R.id.join:
                onSignUp();
                break;
            default:
        }
    }

    public void onSignUp() {

        final LoginActivity parent = (LoginActivity) getActivity();
        final String oText = btn.getText().toString();

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

        //final Button signUp = (Button) parent.findViewById(R.id.join);
        //btn.setEnabled(false);
        //signUp.setText(getText(R.id.loading_text));

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

            btn.setText(getText(R.string.loading_text));

            // This is called on the websocket thread.
            tinode.connect(hostName, tls)
                    .thenApply(
                            new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored_msg) throws Exception {
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
                                            btn.setEnabled(true);
                                            //btn.setText(oText);;

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
                                    parent.reportError(err, R.string.error_new_account_failed);
                                    //btn.setText(oText);
                                    btn.setEnabled(true);
                                    return null;
                                }
                            });

        } catch (Exception e) {
            Log.e(TAG, "Something went wrong", e);
            btn.setEnabled(true);
            btn.setText(oText);;
        }
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == UiUtils.SELECT_PICTURE && resultCode == RESULT_OK) {
            UiUtils.acceptAvatar(getActivity(), (ImageView) getActivity().findViewById(R.id.imageAvatar), data);
        }
    }




}
