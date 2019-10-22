package xyz.yaroslav.securitycontrolsystem;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.wifi.WifiManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcV;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Parcelable;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity implements HistoryRange.HistoryRangeListener {

    //#region Variables

    public static final String APP_PREFERENCES = "ApplicationPreferences";
    public static final String SRV_PROTOCOL = "srv_protocol"; //http
    public static final String SRV_ADDRESS = "srv_address"; //192.168.0.14
    public static final String SRV_PORT = "srv_port"; //5002
    public static final String SRV_POSTFIX_ADD = "srv_postfix_add"; //add
    public static final String SRV_POSTFIX_TAGS = "srv_postfix_tags"; //tags
    public static final String SRV_POSTFIX_EVENTS = "srv_postfix_events"; //events?st=&et=

    SharedPreferences appPreferences;

    private String protocol;
    private String address;
    private String port;
    private String postfix_new;
    private String postfix_whitelist;
    private String postfix_history;

    private static final String cache_file = "temp.txt";
    private static final String whitelist_file = "tags.txt";
    private static final String history_file = "recent.txt";

    private boolean isWhiteListExists = false;

    private List<Map> white_list;

    NfcManager nfcManager;
    NfcAdapter nfcAdapter;

    ImageView nfcIcon;
    ImageView netIcon;
    TextView nfcLabel;
    TextView netLabel;
    ProgressBar progressBar;

    private HandlerThread whitelistThread;
    private Handler whilelistHandler;
    private HandlerThread jsonParseThread;
    private Handler jsonParseHandler;
    private HandlerThread saveTagInLocalFile;
    private Handler saveLocalHandler;
    private HandlerThread updateTagsInHistory;
    private Handler updateTagsHandler;
    private HandlerThread sendTagsWhenOnline;
    private Handler sendTagsOnlineHandler;

    private final String[][] techList = new String[][] {
            new String[] {
                    NfcA.class.getName(),
                    NfcB.class.getName(),
                    NfcV.class.getName(),
                    IsoDep.class.getName(),
                    MifareClassic.class.getName(),
                    MifareUltralight.class.getName(),
                    Ndef.class.getName()
            }
    };

    //#endregion

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setScreenOrientationToPortrait();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        appPreferences = getSharedPreferences(APP_PREFERENCES, MODE_PRIVATE);
        protocol = appPreferences.getString(SRV_PROTOCOL, "http");
        address = appPreferences.getString(SRV_ADDRESS, "192.168.0.14");
        port = appPreferences.getString(SRV_PORT, "5002");
        postfix_new = appPreferences.getString(SRV_POSTFIX_ADD, "add");
        postfix_whitelist = appPreferences.getString(SRV_POSTFIX_TAGS, "tags");
        postfix_history = appPreferences.getString(SRV_POSTFIX_EVENTS, "events?st=&et=");

        nfcIcon = findViewById(R.id.nfc_icon);
        netIcon = findViewById(R.id.net_icon);
        nfcLabel = findViewById(R.id.nfc_text);
        netLabel = findViewById(R.id.net_text);
        progressBar = findViewById(R.id.main_progress);

        white_list = new ArrayList<>();

        displayMainFragment();
        checkInternetState();
        checkNfcState();

        whitelistThread = new HandlerThread("WhiteListThread");
        whitelistThread.start();
        whilelistHandler = new Handler(whitelistThread.getLooper());

        jsonParseThread = new HandlerThread("JsonParseThread");
        jsonParseThread.start();
        jsonParseHandler = new Handler(jsonParseThread.getLooper());

        updateTagsInHistory = new HandlerThread("UpdateTagsHistory");
        updateTagsInHistory.start();
        updateTagsHandler = new Handler(updateTagsInHistory.getLooper());
    }

    @Override
    protected void onResume() {
        super.onResume();

        listenForNfc();
    }

    @Override
    protected void onStart() {
        super.onStart();

        loadWhiteListFromServer();
        parseWhiteListJsonFromFile();
        updateLocalHistory();
        tryToSendSavedTags();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        whitelistThread.quitSafely();
        jsonParseThread.quitSafely();
        updateTagsInHistory.quitSafely();
        sendTagsWhenOnline.quitSafely();
    }

    @Override
    public void onBackPressed() {
        displayMainFragment();
    }

    //#region Initialization

    private void setScreenOrientationToPortrait() {
        switch (getResources().getConfiguration().orientation){
            case Configuration.ORIENTATION_PORTRAIT:

            case Configuration.ORIENTATION_LANDSCAPE:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;
        }
    }

    public void checkNfcState() {
        Timer nfcTimer = new Timer();
        final Handler nfcHandler = new Handler();
        nfcTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                final int statusColor;
                if (isNfcResponsible()) {
                    statusColor = getResources().getColor(R.color.colorGreen);
                } else {
                    statusColor = getResources().getColor(R.color.colorRed);
                }
                nfcHandler.post(() -> {
                    nfcLabel.setTextColor(statusColor);
                    nfcIcon.setColorFilter(statusColor);
                });
            }
        }, 0L, 3L * 1000);
    }

    public void checkInternetState() {
        Timer netTimer = new Timer();
        final Handler netHandler = new Handler();
        netTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                final int textColor;
                try {
                    if (isWiFiEnabled()) {
                        textColor = getResources().getColor(R.color.colorGreen);
                    } else {
                        textColor = getResources().getColor(R.color.colorRed);
                    }
                    netHandler.post(() -> {
                        netLabel.setTextColor(textColor);
                        netIcon.setColorFilter(textColor);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0L, 2L * 1000);
    }

    //#endregion

    //#region Utilities

    private boolean isNfcResponsible() {
        nfcManager = (NfcManager) Objects.requireNonNull(getApplicationContext()).getSystemService(Context.NFC_SERVICE);
        boolean service_enabled = false;
        try {
            nfcAdapter = nfcManager.getDefaultAdapter();
            if (nfcAdapter != null && nfcAdapter.isEnabled()) {
                service_enabled = true;
            }
        } catch (NullPointerException e) {
            Log.e("NFC_INIT", e.getLocalizedMessage());
        }
        return service_enabled;
    }

    private boolean isWiFiEnabled() {
        WifiManager wifi = (WifiManager) Objects.requireNonNull(getApplicationContext()).getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return wifi.isWifiEnabled();
    }

    private void listenForNfc() {
        if (isNfcResponsible()) {
            try {
                PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, new Intent(getApplicationContext(), getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
                IntentFilter filter = new IntentFilter();
                filter.addAction(NfcAdapter.ACTION_TAG_DISCOVERED);
                filter.addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
                filter.addAction(NfcAdapter.ACTION_TECH_DISCOVERED);
                nfcAdapter = NfcAdapter.getDefaultAdapter(getApplicationContext());
                nfcAdapter.enableForegroundDispatch(this, pendingIntent, new IntentFilter[]{filter}, this.techList);
            } catch (Exception e) {
                Log.e("LISTEN_NFC", e.getLocalizedMessage());
            }
        }
    }

    private void autoCloseDialog(String title, String message, int iconType) {
        AlertDialog.Builder builder = new AlertDialog.Builder(
                MainActivity.this);
        builder.setTitle(title);
        builder.setMessage(message);
        switch (iconType) {
            case 1:
                builder.setIcon(R.drawable.ic_success);
                break;
            case 2:
                builder.setIcon(R.drawable.ic_error);
                break;
            case 3:
                builder.setIcon(R.drawable.ic_warning);
                break;
            case 4:
                builder.setIcon(R.drawable.ic_info);
                break;
        }
        builder.setCancelable(true);

        final AlertDialog closedialog = builder.create();

        closedialog.show();

        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                closedialog.dismiss();
                timer.cancel();
            }
        }, 2000);

    }

    private String buildUrl(String postfix) {
        return protocol + "://" + address + ":" + port + "/" + postfix;
    }

    private boolean compareTag(String uid, String tagName) {
        Map<String, Object> tagItem = new TagItem(uid, tagName).toWhiteListMap();
        return white_list.contains(tagItem);
    }

    private JSONObject buidJsonObject(String uid, String payload, String time) {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("tag_id", uid);
            jsonObject.put("tag_data", payload);
            jsonObject.put("tag_time", time);
            return jsonObject;
        } catch (JSONException e) {
            Log.e("BUILD_JSON", "JSON Exception: " + e.getMessage());
            return null;
        }
    }

    //#endregion

    //#region Fragments

    private void displayMainFragment() {
        MainFragment fragment = new MainFragment();
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.fragment_container, fragment);
        ft.addToBackStack(null);
        ft.commit();
    }

    @Override
    public void onDialogPositiveClick(String url) {
        HistoryFragment fragment = new HistoryFragment();
        fragment.setUrl(url);
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.fragment_container, fragment);
        ft.addToBackStack(null);
        ft.commit();
    }

    //#endregion

    //#region Read NFC

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        new IntentProcessingAsync().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, intent);
    }

    private class IntentProcessingAsync extends AsyncTask<Intent, Void, Map> {
        @Override
        protected Map doInBackground(Intent... intents) {
            String tag_data = "";
            String tag_id = "";
            long cur_time = System.currentTimeMillis();

            Parcelable[] data = intents[0].getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            tag_data = parseTagData(data);

            if (Objects.equals(intents[0].getAction(), NfcAdapter.ACTION_TAG_DISCOVERED)) {
                tag_id = parseTagId(intents[0].getByteArrayExtra(NfcAdapter.EXTRA_ID));
            }


            @SuppressLint("UseSparseArrays") Map<Integer, String> flag_name = new HashMap<>();

            if (!tag_id.equals("") && !tag_data.equals("")) {
                if (isWhiteListExists) {
                    if (compareTag(tag_id, tag_data)) {
                        flag_name.put(1, tag_data);
                        saveInLocalFile(tag_id, tag_data, String.valueOf(cur_time));
                        new SendTagToServerAsync().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, buildUrl(postfix_new), tag_id, tag_data, String.valueOf(cur_time));
                    } else {
                        flag_name.put(2, tag_id);
                    }
                } else {
                    flag_name.put(3, tag_id);
                }
            } else {
                flag_name.put(4, "");
            }

            return flag_name;
        }

        @Override
        protected void onPostExecute(Map pair) {
            if (pair.containsKey(1)) {
                autoCloseDialog(pair.get(1).toString(), getString(R.string.message_success), 1);
            } else if (pair.containsKey(2)) {
                autoCloseDialog(getString(R.string.label_unknown_tag), getString(R.string.message_unknown_tag), 3);
            } else if (pair.containsKey(3)) {
                autoCloseDialog(getString(R.string.label_compare_error), getString(R.string.message_white_list), 2);
            } else {
                autoCloseDialog(getString(R.string.label_error), getString(R.string.message_fail), 2);
            }
        }
    }

    private String parseTagData(Parcelable[] data) {
        StringBuilder tag_data = new StringBuilder();
        if (data != null) {
            try {
                for (Parcelable aData : data) {
                    NdefRecord[] recs = ((NdefMessage) aData).getRecords();
                    for (NdefRecord rec : recs) {
                        if (rec.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(rec.getType(), NdefRecord.RTD_TEXT)) {
                            byte[] payload = rec.getPayload();
                            String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";
                            int langCodeLen = payload[0] & 63;
                            tag_data.append(new String(payload, langCodeLen + 1, payload.length - langCodeLen - 1, textEncoding));
                        }
                    }
                }
            } catch (Exception e) {
                if (e.getMessage() != null) {
                    Log.e("TAG_DISPATCH", e.getLocalizedMessage());
                } else {
                    e.printStackTrace();
                }
            }
        }
        if (tag_data.length() == 0) {
            return "";
        }
        return tag_data.toString();
    }

    private String parseTagId(byte [] bytesArray) {
        int i, j, in;
        String [] hex = {"0","1","2","3","4","5","6","7","8","9","A","B","C","D","E","F"};
        StringBuilder out= new StringBuilder();

        for (j = 0; j < bytesArray.length; ++j) {
            in = (int) bytesArray[j] & 0xff;
            i = (in >> 4) & 0x0f;
            out.append(hex[i]);
            i = in & 0x0f;
            out.append(hex[i]);
        }

        return out.toString();
    }

    //#endregion

    //#region Get WhiteList of Tags

    private void loadWhiteListFromServer() {
        whilelistHandler.postDelayed(() -> {
            try {
                URL url = new URL(buildUrl(postfix_whitelist));
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setConnectTimeout(2000);
                urlConnection.setReadTimeout(2000);
                InputStream stream = new BufferedInputStream(urlConnection.getInputStream());
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream));
                StringBuilder builder = new StringBuilder();

                String inputString ;

                while ((inputString = bufferedReader.readLine()) != null) {
                    builder.append(inputString);
                }

                urlConnection.disconnect();

                if (!builder.toString().equals(readWhiteListFromFile())) {
                    try {
                        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(openFileOutput(whitelist_file, Context.MODE_PRIVATE));
                        outputStreamWriter.write(builder.toString());
                        outputStreamWriter.close();
                    }
                    catch (IOException e) {
                        Log.e("WHITE_LIST", "Error occurred: " + e.toString());
                    }
                }

            } catch (IOException e) {
                Log.e("WHITE_LIST", "IO Exception: " + e.getMessage());
            }
        }, 1000);
    }

    private void parseWhiteListJsonFromFile() {
        jsonParseHandler.postDelayed(() -> {
            try {
                String jsonString = readWhiteListFromFile();
                JSONObject topLevel = new JSONObject(jsonString);
                JSONArray jArray = topLevel.getJSONArray("tags");

                white_list.clear();

                for (int i = 0; i < jArray.length(); i++) {
                    String tagName;
                    String tagUid ;
                    try {
                        JSONObject nestedObject = jArray.getJSONObject(i);
                        tagName = String.valueOf(nestedObject.getString("tag_data"));
                        tagUid = String.valueOf(nestedObject.getString("tag_id"));

                        Map<String,Object> tagItem = new TagItem(tagUid, tagName).toWhiteListMap();
                        white_list.add(tagItem);
                    } catch (JSONException e) {
                        Log.e("WHITE_LIST", "JSON Exception: " + e.getMessage());
                    }
                }

                if (white_list.size() > 0) {
                    isWhiteListExists = true;
                }
            } catch (JSONException e) {
                Log.e("WHITE_LIST", "JSON Parse Exception: " + e.getMessage());
            }
        }, 2000);
    }

    private String readWhiteListFromFile() {
        String ret = "";

        try {
            InputStream inputStream = Objects.requireNonNull(getApplicationContext()).openFileInput(whitelist_file);

            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString;
                StringBuilder stringBuilder = new StringBuilder();

                while ((receiveString = bufferedReader.readLine()) != null ) {
                    stringBuilder.append(receiveString);
                }

                inputStream.close();
                ret = stringBuilder.toString();
            }
        }
        catch (FileNotFoundException e) {
            Log.e("WHITE_LIST", "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.e("WHITE_LIST", "IO Exception: " + e.getMessage());
        }

        return ret;
    }

    //#endregion

    //#region Save Tags Local

    private void saveInLocalFile(String uid, String payload, String time) {
        saveTagInLocalFile = new HandlerThread("SaveTagLocal");
        saveTagInLocalFile.start();
        saveLocalHandler = new Handler(saveTagInLocalFile.getLooper());

        saveLocalHandler.post(() -> {
            JSONObject jsonObject = buidJsonObject(uid, payload, time);
            if (jsonObject != null) {
                String str = jsonObject + ";";
                try {
                    OutputStreamWriter outputStreamWriter = new OutputStreamWriter(openFileOutput(history_file, Context.MODE_APPEND));
                    outputStreamWriter.write(str);
                    outputStreamWriter.close();
                } catch (IOException e) {
                    Log.e("Exception", "File write failed: " + e.toString());
                } finally {
                    saveTagInLocalFile.quitSafely();
                }
            }
        });
    }

    //#endregion

    //#region Send Tag to Server

    private class SendTagToServerAsync extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... strings) {
            try {
                URL url = new URL(strings[0]);

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

                JSONObject jsonObject = buidJsonObject(strings[1], strings[2], strings[3]);
                if (jsonObject != null) {
                    OutputStream os = conn.getOutputStream();
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
                    writer.write(jsonObject.toString());
                    writer.flush();
                    writer.close();
                    os.close();

                    conn.connect();
                    if (conn.getResponseCode() != 200) {
                        writeTagToTempFile(strings[1], strings[2], strings[3]);
                    }
                }
            } catch (IOException e) {
                Log.e("HTTP_POST", "IO Exception: " + e.getMessage());
                writeTagToTempFile(strings[1], strings[2], strings[3]);
            }
            return null;
        }
    }

    private void writeTagToTempFile(String tag_id, String tag_data, String timestamp) {
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(openFileOutput(cache_file, Context.MODE_APPEND));
            JSONObject jsonObject = buidJsonObject(tag_id, tag_data, timestamp);
            assert jsonObject != null;
            outputStreamWriter.write(jsonObject.toString() + ";");
            outputStreamWriter.close();
            Log.i("WRITE_TEMP", jsonObject.toString());
        } catch (IOException ex) {
            Log.e("Exception", "File write failed: " + ex.toString());
        }
    }

    //#endregion

    //#region Update History

    private void updateLocalHistory() {
        updateTagsHandler.postDelayed(() -> {
            String tmp = readHistoryFile();
            if (!tmp.equals("")) {
                String[] arr = tmp.split(";");
                if (arr.length > 0) {
                    List<String> temp = new ArrayList<>();
                    ArrayList<String> list = new ArrayList<>(Arrays.asList(arr));
                    for (String str : list) {
                        try {
                            JSONObject jsonObject = new JSONObject(str);
                            String tagTime = String.valueOf(jsonObject.getString("tag_time"));
                            if (isTagRecent(tagTime)) {
                                temp.add(str + ";");
                            }
                        } catch (JSONException e) {
                            Log.e("JSON_PARSE", e.getMessage());
                        }
                    }
                    rewriteHistoryFile(temp);
                }
            } else {
                Log.e("HISTORY_FILE", "Empty response");
            }
        }, 8000);
    }

    private boolean isTagRecent(String value) {
        long currentTime = System.currentTimeMillis();
        long comparableTime = Long.parseLong(value);
        return ((currentTime - comparableTime) <= 86400000);
    }

    private String readHistoryFile() {
        String ret = "";
        try {
            InputStream inputStream = Objects.requireNonNull(getApplicationContext()).openFileInput(history_file);

            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString;
                StringBuilder stringBuilder = new StringBuilder();

                while ((receiveString = bufferedReader.readLine()) != null ) {
                    stringBuilder.append(receiveString);
                }

                inputStream.close();
                ret = stringBuilder.toString();
            }
        } catch (FileNotFoundException e) {
            Log.e("HISTORY_FILE", "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.e("HISTORY_FILE", "IO Exception: " + e.getMessage());
        }

        return ret;
    }

    private void rewriteHistoryFile(List<String> values)  {
        try {
            OutputStreamWriter streamWriter = new OutputStreamWriter(openFileOutput(history_file, Context.MODE_PRIVATE));
            streamWriter.write("");
            streamWriter.close();
            Log.i("HISTORY_FILE", "Deleted");
            for (String value : values) {
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(openFileOutput(history_file, Context.MODE_APPEND));
                outputStreamWriter.write(value);
                outputStreamWriter.close();
            }
            Log.i("HISTORY_FILE", "Updated");
        }
        catch (IOException e) {
            Log.e("Exception", "History file write failed: " + e.toString());
        }
    }

    //#endregion

    //#region Send Saved offline Tags

    private void tryToSendSavedTags() {
        final Handler handler = new Handler();
        handler.postDelayed(() -> {
            sendTagsWhenOnline = new HandlerThread("SendTagsWhenOnline");
            sendTagsWhenOnline.start();
            sendTagsOnlineHandler = new Handler(sendTagsWhenOnline.getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    try {
                        Integer result = new PingAsyncTask().execute(buildUrl(postfix_whitelist)).get();
                        if (result.equals(200)) {
                            sendSavedOfllineTags();
                        }
                    } catch (ExecutionException | InterruptedException e) {
                        Log.e("PING_SERVER", "AsyncTask exception: " + e.getMessage());
                    }
                    sendTagsOnlineHandler.sendEmptyMessageDelayed(0, 60 * 1000);
                }
            };
            sendTagsOnlineHandler.sendEmptyMessage(0);
        }, 10000);
    }

    private class PingAsyncTask extends AsyncTask<String, Void, Integer> {
        @Override
        protected Integer doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                return conn.getResponseCode();
            } catch (IOException e) {
                Log.e("PING_SERVER", "IO Exception: " + e.getMessage());
            }
            return 0;
        }

        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);
            Log.i("PING", result.toString());
        }
    }

    private void sendSavedOfllineTags() {
        String tmp = readCacheFile();
        String[] arr = tmp.split(";");
        if (arr.length > 0) {
            ArrayList<String> list = new ArrayList<>(Arrays.asList(arr));
            ArrayList<String> temp = new ArrayList<>(Arrays.asList(arr));
            for (String s : list) {
                try {
                    Integer result = new SendSavedTagToServerAsync().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, buildUrl(postfix_new), s).get();
                    if (result == 200) {
                        temp.remove(s);
                    }
                } catch (ExecutionException | InterruptedException e) {
                    Log.e("SAVED_TAGS", "AsyncTask exception: " + e.getMessage());
                }
            }
            rewriteCacheFile(temp);
        }
    }

    private class SendSavedTagToServerAsync extends AsyncTask<String, Void, Integer> {
        @Override
        protected Integer doInBackground(String... strings) {
            try {
                URL url = new URL(strings[0]);

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

                if (strings[1] != null) {
                    OutputStream os = conn.getOutputStream();
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
                    writer.write(strings[1]);
                    writer.flush();
                    writer.close();
                    os.close();

                    conn.connect();
                    return conn.getResponseCode();
                }
            } catch (IOException e) {
                Log.e("HTTP_POST", "IO Exception: " + e.getMessage());
            }
            return 0;
        }
    }

    private String readCacheFile() {
        String ret = "";
        try {
            InputStream inputStream = Objects.requireNonNull(getApplicationContext()).openFileInput(cache_file);

            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString;
                StringBuilder stringBuilder = new StringBuilder();

                while ((receiveString = bufferedReader.readLine()) != null ) {
                    stringBuilder.append(receiveString);
                }

                inputStream.close();
                ret = stringBuilder.toString();
            }
        } catch (FileNotFoundException e) {
            Log.e("HISTORY_FILE", "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.e("HISTORY_FILE", "IO Exception: " + e.getMessage());
        }

        return ret;
    }

    private void rewriteCacheFile(List<String> values)  {
        try {
            OutputStreamWriter streamWriter = new OutputStreamWriter(openFileOutput(cache_file, Context.MODE_PRIVATE));
            streamWriter.write("");
            streamWriter.close();
            Log.i("CACHE_FILE", "Deleted");
            if (values.size() > 0) {
                for (String value : values) {
                    OutputStreamWriter outputStreamWriter = new OutputStreamWriter(openFileOutput(cache_file, Context.MODE_APPEND));
                    outputStreamWriter.write(value);
                    outputStreamWriter.close();
                }
                Log.i("CACHE_FILE", "Updated");
            }
        }
        catch (IOException e) {
            Log.e("Exception", "Cache file write failed: " + e.toString());
        }
    }

    //#endregion

}





















