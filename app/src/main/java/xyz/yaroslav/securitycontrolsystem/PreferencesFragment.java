package xyz.yaroslav.securitycontrolsystem;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import static android.content.Context.MODE_PRIVATE;


public class PreferencesFragment extends DialogFragment {
    //#region Variables

    public static final String APP_PREFERENCES = "ApplicationPreferences";
    public static final String SRV_PROTOCOL = "srv_protocol";
    public static final String SRV_ADDRESS = "srv_address";
    public static final String SRV_PORT = "srv_port";
    public static final String SRV_POSTFIX_ADD = "srv_postfix_add";
    public static final String SRV_POSTFIX_TAGS = "srv_postfix_tags";
    public static final String SRV_POSTFIX_EVENTS = "srv_postfix_events";

    SharedPreferences appPreferences;
    SharedPreferences.Editor editor;

    EditText prefProtocol;
    EditText prefAddress;
    EditText prefPort;
    EditText prefPostfixAdd;
    EditText prefPostfixTags;
    EditText prefPostfixEvents;
    Button prefCancel;
    Button prefSave;

    //#endregion

    //#region Fragment Methods

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_preferences, container, false);

        prefProtocol = view.findViewById(R.id.pref_protocol);
        prefAddress = view.findViewById(R.id.pref_address);
        prefPort = view.findViewById(R.id.pref_port);
        prefPostfixAdd = view.findViewById(R.id.pref_postfix_add);
        prefPostfixTags = view.findViewById(R.id.pref_postfix_tags);
        prefPostfixEvents = view.findViewById(R.id.pref_postfix_events);

        prefCancel = view.findViewById(R.id.button_cancel);
        prefSave = view.findViewById(R.id.button_save);

        appPreferences = getActivity().getSharedPreferences(APP_PREFERENCES, MODE_PRIVATE);
        prefProtocol.setText(appPreferences.getString(SRV_PROTOCOL, "http"));
        prefAddress.setText(appPreferences.getString(SRV_ADDRESS, "192.168.0.14"));
        prefPort.setText(appPreferences.getString(SRV_PORT, "5002"));
        prefPostfixAdd.setText(appPreferences.getString(SRV_POSTFIX_ADD, "add"));
        prefPostfixTags.setText(appPreferences.getString(SRV_POSTFIX_TAGS, "tags"));
        prefPostfixEvents.setText(appPreferences.getString(SRV_POSTFIX_EVENTS, "events?st=&et="));

        editor = appPreferences.edit();

        prefCancel.setOnClickListener(v -> getDialog().dismiss());
        prefSave.setOnClickListener(v -> {
            if (valuesValid()) {
                editor.apply();
                getDialog().dismiss();
            } else {
                Toast.makeText(getContext(), getString(R.string.toast_empty_fields), Toast.LENGTH_SHORT).show();
            }
        });

        return view;
    }

    //#endregion

    //#region Preferences Method

    private boolean valuesValid() {
        String protocol = prefProtocol.getText().toString().trim();
        String address = prefAddress.getText().toString().trim();
        String port = prefPort.getText().toString().trim();
        String postfix_new = prefPostfixAdd.getText().toString().trim();
        String postfix_whitelist = prefPostfixTags.getText().toString().trim();
        String postfix_history = prefPostfixEvents.getText().toString().trim();

        if (!protocol.equals("")) {
            editor.putString(SRV_PROTOCOL, protocol);
        } else {
            return false;
        }
        if (!address.equals("")) {
            editor.putString(SRV_ADDRESS, prefAddress.getText().toString());
        } else {
            return false;
        }
        if (!port.equals("")) {
            editor.putString(SRV_PORT, prefPort.getText().toString());
        } else {
            return false;
        }
        if (!postfix_new.equals("")) {
            editor.putString(SRV_POSTFIX_ADD, prefPostfixAdd.getText().toString());
        } else {
            return false;
        }
        if (!postfix_whitelist.equals("")) {
            editor.putString(SRV_POSTFIX_TAGS, prefPostfixTags.getText().toString());
        } else {
            return false;
        }
        if (!postfix_history.equals("")) {
            editor.putString(SRV_POSTFIX_EVENTS, prefPostfixEvents.getText().toString());
        } else {
            return false;
        }
        return true;
    }

    //#endregion

}
























