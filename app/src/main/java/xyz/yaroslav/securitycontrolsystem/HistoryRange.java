package xyz.yaroslav.securitycontrolsystem;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;

import java.util.Calendar;
import java.util.Date;

import static android.content.Context.MODE_PRIVATE;

public class HistoryRange extends DialogFragment {

    //#region Variables

    SharedPreferences appPreferences;

    public static final String APP_PREFERENCES = "ApplicationPreferences";
    public static final String SRV_PROTOCOL = "srv_protocol"; //http
    public static final String SRV_ADDRESS = "srv_address"; //192.168.0.14
    public static final String SRV_PORT = "srv_port"; //5002
    public static final String SRV_POSTFIX_EVENTS = "srv_postfix_events"; //events?st=&et=

    EditText startDate;
    EditText finishDate;
    Button cancelButton;
    Button okButton;
    Calendar calendar;

    private static final long dateCorrection = 1000 * 60 * 60 * 24;
    private static long todayDate = new Date().getTime();
    private long startTimeMsec = 0;
    private long endTimeMsec = 0;

    public interface HistoryRangeListener {
        void onDialogPositiveClick(String url);
    }

    HistoryRangeListener mListener;

    //#endregion

    //#region Fragment Methods

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mListener = (HistoryRangeListener) context;
        } catch (ClassCastException e) {
            e.printStackTrace();
        }
    }

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
        View view = inflater.inflate(R.layout.fragment_range, container, false);

        appPreferences = getActivity().getSharedPreferences(APP_PREFERENCES, MODE_PRIVATE);

        calendar = Calendar.getInstance();

        cancelButton = view.findViewById(R.id.button_cancel);
        okButton = view.findViewById(R.id.button_ok);
        startDate = view.findViewById(R.id.date_start);
        finishDate = view.findViewById(R.id.date_finish);

        startDate.setOnClickListener(view12 -> setDate(startDate, 1));

        finishDate.setOnClickListener(view1 -> setDate(finishDate, 2));

        cancelButton.setOnClickListener(v -> getDialog().dismiss());

        okButton.setOnClickListener(v -> {
            mListener.onDialogPositiveClick(requestTags());
            getDialog().dismiss();
        });

        return view;
    }

    //#endregion

    //#region Date and Time

    public static Calendar setDefaultTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        return calendar;
    }

    private Calendar todayDate() {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.MILLISECOND, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.HOUR_OF_DAY, 0);
        return today;
    }

    private void getDateInMilliseconds(EditText edit_field, int typeOfDate) {
        long currentDateInMillisec = calendar.getTimeInMillis();
        String str = (DateUtils.formatDateTime(getContext(), currentDateInMillisec,DateUtils.FORMAT_NUMERIC_DATE | DateUtils.FORMAT_SHOW_YEAR ));
        edit_field.setText(str);
        if (typeOfDate == 1) {
            startTimeMsec = currentDateInMillisec;
        } else if (typeOfDate == 2) {
            endTimeMsec = currentDateInMillisec;
        }
    }

    private void setDate(final EditText editText, final int flag) {
        DatePickerDialog dialog = new DatePickerDialog(getContext(), (view, year, monthOfYear, dayOfMonth) -> {
            calendar = setDefaultTime(calendar);
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, monthOfYear);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            getDateInMilliseconds(editText, flag);
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMaxDate(todayDate);
        dialog.show();
    }

    private void makeSureThatTimeIsNormal() {
        if (endTimeMsec == 0) {
            endTimeMsec = todayDate().getTimeInMillis() + dateCorrection;
        }
        if (startTimeMsec == 0) {
            startTimeMsec = todayDate().getTimeInMillis();
        }
        if (startTimeMsec > endTimeMsec) {
            long temp = startTimeMsec;
            startTimeMsec = endTimeMsec;
            endTimeMsec = temp;
        } else if (startTimeMsec == endTimeMsec) {
            endTimeMsec += dateCorrection;
        }

    }

    //#endregion

    //#region Request

    private String requestTags() {
        makeSureThatTimeIsNormal();
        return makeUrlWithDateParameters(startTimeMsec, endTimeMsec);
    }

    private String makeUrlWithDateParameters(long start, long end) {
        String protocol = appPreferences.getString(SRV_PROTOCOL, "http");
        String address = appPreferences.getString(SRV_ADDRESS, "192.168.0.14");
        String port = appPreferences.getString(SRV_PORT, "5002");
        String postfix_history = appPreferences.getString(SRV_POSTFIX_EVENTS, "events?st=&et=");

        assert postfix_history != null;
        String[] first = postfix_history.split("\\?");
        String[] second = first[1].split("&");

        String url = protocol + "://" + address + ":" + port + "/" + first[0] + getString(R.string.char_question) + second[0] + start + getString(R.string.char_ampersand) + second[1] + end;

        return url;
    }

    //#endregion

}





































