package xyz.yaroslav.securitycontrolsystem;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Parcelable;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
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

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity implements HistoryRange.HistoryRangeListener {

    //#region Variables

    private static final String default_url = "http://192.168.0.14:5002/add";
    private static final String tags_url = "http://192.168.0.14:5002/tags";
    private static final String fileName = "temp.txt";
    private static final String whiteList = "tags.txt";
    private static final String shortList = "recent.txt";

    private List<Map> white_list;

    NfcManager nfcManager;
    NfcAdapter nfcAdapter;

    ImageView nfcIcon;
    ImageView netIcon;
    TextView nfcLabel;
    TextView netLabel;
    ProgressBar progressBar;

    private boolean isWhiteListExists = false;

    private Handler pHandler;

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

    //#region Activity Actions

    @Override
    public void onDialogPositiveClick(String url) {
        HistoryFragment fragment = new HistoryFragment();
        fragment.setUrl(url);
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.fragment_container, fragment);
        ft.addToBackStack(null);
        ft.commit();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setOrientationToPortrait();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        nfcIcon = findViewById(R.id.nfc_icon);
        netIcon = findViewById(R.id.net_icon);
        nfcLabel = findViewById(R.id.nfc_text);
        netLabel = findViewById(R.id.net_text);
        progressBar = findViewById(R.id.main_progress);

        white_list = new ArrayList<>();

        displayMainFragment();

        new RenewLocalHistoryAsynkTask().execute();

        testInternetState();
        testNfcState();
    }

    @Override
    protected void onResume() {
        super.onResume();

        listenForNfc();
    }

    @Override
    protected void onStart() {
        super.onStart();

        delayWhiteList();
        delayCache();

        parseJsonFromFile();
    }

    @Override
    public void onBackPressed() {
        displayMainFragment();
    }

    private void displayMainFragment() {
        MainFragment fragment = new MainFragment();
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.fragment_container, fragment);
        ft.addToBackStack(null);
        ft.commit();
    }

    private void delayCache() {
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                postHandler();
            }
        }, 10000);
    }

    private void delayWhiteList() {
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    new LoadWhiteListTagsAsyncTask().execute(tags_url).get();
                } catch (ExecutionException | InterruptedException e) {
                    Log.e("WHITE_LIST", "AsyncTask while loading exception: " + e.getMessage());
                }
            }
        }, 2000);
    }

    //#endregion

    //#region Initialization

    private void setOrientationToPortrait() {
        switch (getResources().getConfiguration().orientation){
            case Configuration.ORIENTATION_PORTRAIT:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;

            case Configuration.ORIENTATION_LANDSCAPE:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;
        }
    }

    private boolean initNFC() {
        nfcManager = (NfcManager) Objects.requireNonNull(getApplicationContext()).getSystemService(Context.NFC_SERVICE);
        boolean service_enabled = false;
        try {
            nfcAdapter = nfcManager.getDefaultAdapter();
            if (nfcAdapter != null && nfcAdapter.isEnabled()) {
                service_enabled = true;
            }
        } catch (NullPointerException e) {
            Log.e("initNFC", e.getLocalizedMessage());
        }
        return service_enabled;
    }

    private void listenForNfc() {
        if (initNFC()) {
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

    public void testNfcState() {
        Timer nfcTimer = new Timer();
        final Handler nfcHandler = new Handler();
        nfcTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                final int statusColor;
                if (initNFC()) {
                    statusColor = getResources().getColor(R.color.colorGreen);
                } else {
                    statusColor = getResources().getColor(R.color.colorRed);
                }
                nfcHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        nfcLabel.setTextColor(statusColor);
                        nfcIcon.setColorFilter(statusColor);
                    }
                });
            }
        }, 0L, 5L * 1000);
    }

    public void testInternetState() {
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
                    netHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            netLabel.setTextColor(textColor);
                            netIcon.setColorFilter(textColor);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0L, 2L * 1000);
    }

    private boolean isWiFiEnabled() {
        WifiManager wifi = (WifiManager) Objects.requireNonNull(getApplicationContext()).getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return wifi.isWifiEnabled();
    }

    //#endregion

    //#region Scan tag

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        StringBuilder tag_data = new StringBuilder();
        String tag_id = "";

        Parcelable[] data = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
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
        if (Objects.equals(intent.getAction(), NfcAdapter.ACTION_TAG_DISCOVERED)) {
            tag_id = ByteArrayToHexString(intent.getByteArrayExtra(NfcAdapter.EXTRA_ID));
        }

        if (tag_data.length() > 0 && !tag_id.equals("")) {
            if (isWhiteListExists) {
                if (compareTag(tag_id, tag_data.toString())) {
                    saveData(tag_id, tag_data.toString(), getCurTime());
                } else {
                    autoCloseDialog(getString(R.string.label_unknown_tag), getString(R.string.message_unknown_tag), 3);
                }
            } else {
                autoCloseDialog(getString(R.string.label_compare_error), getString(R.string.message_white_list), 2);
            }
        } else {
            autoCloseDialog(getString(R.string.label_error), getString(R.string.message_fail), 2);
        }
    }

    private String ByteArrayToHexString(byte [] bytesArray) {
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

    //#region Save tag

    private void saveData(String uid, String payload, String time) {
        try {
            String value = uid + "," + payload + "," + time;
            String result = new HTTPAsyncTask().execute(value).get();
            String str = buidJsonObject(uid, payload, time) + ";";
            saveToShortHistoryFile(str);
            if (!result.equals("OK")) {
                if (!str.equals("")) {
                    writeToFile(str, payload, true);
                } else {
                    autoCloseDialog(getString(R.string.label_error), getString(R.string.message_fail), 2);
                }
            } else {
                autoCloseDialog(payload, getString(R.string.message_success), 1);
            }
        } catch (ExecutionException | InterruptedException e) {
            Log.e("SAVE_TAG", "AsyncTask exception: " + e.getMessage());
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

    private String getCurTime() {
        long value = System.currentTimeMillis();
        return String.valueOf(value);
    }

    private void writeToFile(String data, String tagName, boolean flag) {
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(openFileOutput(fileName, Context.MODE_APPEND));
            outputStreamWriter.write(data);
            outputStreamWriter.close();
            if (flag) {
                autoCloseDialog(tagName, getString(R.string.message_success), 1);
            }
        }
        catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }

    //#endregion

    //#region Send to server

    private class HTTPAsyncTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            try {
                try {
                    return HttpPost(urls[0]);
                } catch (JSONException e) {
                    Log.e("HTTP_POST", "JSON Exception: " + e.getMessage());
                    return "Error!";
                }
            } catch (IOException e) {
                Log.e("HTTP_POST", "IO Exception: " + e.getMessage());
                return "Unable to retrieve web page. URL may be invalid.";
            }
        }
    }

    private String HttpPost(String params) throws IOException, JSONException {
        String result;

        URL url = new URL(default_url);

        // 1. create HttpURLConnection
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        // 2. build JSON object
        JSONObject jsonObject = buidJsonObjectWithParams(params);

        // 3. add JSON content to POST request body
        setPostRequestContent(conn, jsonObject);

        // 4. make POST request to the given URL
        conn.connect();

        result = conn.getResponseMessage();

        // 5. return response message
        return result;

    }

    private JSONObject buidJsonObjectWithParams(String params) throws JSONException {
        String[] arr = params.split(",");
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("tag_id", arr[0]);
        jsonObject.put("tag_data", arr[1]);
        jsonObject.put("tag_time", arr[2]);

        return jsonObject;
    }

    private String buidJsonObject(String uid, String payload, String time) {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("tag_id", uid);
            jsonObject.put("tag_data", payload);
            jsonObject.put("tag_time", time);
            return jsonObject.toString();
        } catch (JSONException e) {
            Log.e("BUILD_JSON", "JSON Exception: " + e.getMessage());
            return "";
        }
    }

    private void setPostRequestContent(HttpURLConnection conn, JSONObject jsonObject) throws IOException {
        OutputStream os = conn.getOutputStream();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
        writer.write(jsonObject.toString());
        Log.i(MainActivity.class.toString(), jsonObject.toString());
        writer.flush();
        writer.close();
        os.close();
    }

    //#endregion

    //#region Ping server

    private class PingAsyncTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            try {
                return HttpPostPing(urls[0]);
            } catch (IOException e) {
                Log.e("PING_SERVER", "IO Exception: " + e.getMessage());
                return "Unable to retrieve web page. URL may be invalid.";
            }
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            Log.i("PING", result);
        }
    }

    private String HttpPostPing(String myUrl) throws IOException {
        URL url = new URL(myUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.connect();
        return conn.getResponseMessage();
    }

    //#endregion

    //#region Operate with saved tags

    private void postHandler() {
        HandlerThread mHandlerThread = new HandlerThread("postThread");
        mHandlerThread.start();
        pHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                try {
                    String result = new PingAsyncTask().execute(default_url).get();
                    if (result.equals("OK")) {
                        getTagsFromFile();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    Log.e("PING_SERVER", "AsyncTask exception: " + e.getMessage());
                }

                pHandler.sendEmptyMessageDelayed(0, 60 * 1000);
            }
        };

        pHandler.sendEmptyMessage(0);
    }

    private void getTagsFromFile() {
        String tmp = readDataFromFile();
        String[] arr = tmp.split(";");
        if (arr.length > 0) {
            ArrayList<String> list = new ArrayList<>(Arrays.asList(arr));
            ArrayList<String> temp = new ArrayList<>(Arrays.asList(arr));
            for (String s : list) {
                try {
                    String result = new SendSavedTagAsynkTask().execute(s).get();
                    if (result.equals("OK")) {
                        temp.remove(s);
                    }
                } catch (ExecutionException | InterruptedException e) {
                    Log.e("SAVED_TAGS", "AsyncTask exception: " + e.getMessage());
                }
            }
            try {
                deleteFile();
                if (temp.size() > 0) {
                    for (String str : temp) {
                        str += ";";
                        writeToFile(str, "",false);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String readDataFromFile() {
        String ret = "";
        try {
            InputStream inputStream = Objects.requireNonNull(getApplicationContext()).openFileInput(fileName);

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
            Log.e("SAVED_TAGS", "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.e("SAVED_TAGS", "IO Exception: " + e.getMessage());
        }

        return ret;
    }

    private class SendSavedTagAsynkTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            try {
                return SavedTagPost(params[0]);
            } catch (IOException e) {
                Log.e("SAVED_TAGS", "IO Exception: " + e.getMessage());
                return "Unable to retrieve web page. URL may be invalid.";
            }
        }
    }

    private String SavedTagPost(String dataObj) throws IOException {
        String result;

        URL url = new URL(default_url);

        // 1. create HttpURLConnection
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        // 3. add JSON content to POST request body
        setPostRequestSavedContent(conn, dataObj);

        // 4. make POST request to the given URL
        conn.connect();

        result = conn.getResponseMessage();

        // 5. return response message
        return result;

    }

    private void setPostRequestSavedContent(HttpURLConnection conn, String object) throws IOException {
        OutputStream os = conn.getOutputStream();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
        writer.write(object);
        writer.flush();
        writer.close();
        os.close();
    }

    private void deleteFile() {
        try {
            getApplicationContext().deleteFile(fileName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //#endregion

    //#region White list of Tags

    private class LoadWhiteListTagsAsyncTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... strings) {
            getWhiteListJson(strings[0]);
            return "";
        }
    }

    private void getWhiteListJson(String srv_url) {
        try {
            URL url = new URL(srv_url);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

            InputStream stream = new BufferedInputStream(urlConnection.getInputStream());
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream));
            StringBuilder builder = new StringBuilder();

            String inputString ;

            while ((inputString = bufferedReader.readLine()) != null) {
                builder.append(inputString);
            }

            urlConnection.disconnect();

            if (!builder.toString().equals(readWhiteListFromFile())) {
                saveWhiteList(builder.toString());
            }

        } catch (IOException e) {
            Log.e("WHITE_LIST", "IO Exception: " + e.getMessage());
        }
    }

    private void saveWhiteList(String data) {
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(openFileOutput(whiteList, Context.MODE_PRIVATE));
            outputStreamWriter.write(data);
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.e("WHITE_LIST", "Error occurred: " + e.toString());
        }
    }

    private String readWhiteListFromFile() {
        String ret = "";

        try {
            InputStream inputStream = Objects.requireNonNull(getApplicationContext()).openFileInput(whiteList);

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

    private void parseJsonFromFile() {
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
    }

    private boolean compareTag(String uid, String tagName) {
        Map<String, Object> tagItem = new TagItem(uid, tagName).toWhiteListMap();
        return white_list.contains(tagItem);
    }

    //#endregion

    //#region Local history

    private class RenewLocalHistoryAsynkTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... strings) {
            removeExpiredTags();
            return "";
        }
    }

    private void saveToShortHistoryFile(String data) {
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(openFileOutput(shortList, Context.MODE_APPEND));
            outputStreamWriter.write(data);
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.e("SHORT_LIST", "Write to file failed: " + e.toString());
        }
    }

    private boolean checkTagRecency(String value) {
        long currentTime = System.currentTimeMillis();
        long comparableTime = Long.parseLong(value);
        return ((currentTime - comparableTime) <= 86400000);
    }

    private String readHistoryFromFile() {
        String ret = "";
        try {
            InputStream inputStream = Objects.requireNonNull(getApplicationContext()).openFileInput(shortList);

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
            Log.e("HISTORY_FILE", "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.e("HISTORY_FILE", "IO Exception: " + e.getMessage());
        }

        return ret;
    }

    private void removeExpiredTags() {
        String tmp = readHistoryFromFile();
        String[] arr = tmp.split(";");
        if (arr.length > 0) {
            List<String> temp = new ArrayList<>();
            ArrayList<String> list = new ArrayList<>(Arrays.asList(arr));
            for (String str : list) {
                try {
                    JSONObject jsonObject = new JSONObject(str);
                    String tagTime = String.valueOf(jsonObject.getString("tag_time"));
                    Log.i("TEST_SHOW_TIME", tagTime);
                    if (checkTagRecency(tagTime)) {
                        temp.add(str + ";");
                    }
                } catch (JSONException e) {
                    Log.e("JSON_PARSE", e.getMessage());
                }
            }
            rewriteHistoryFile(temp);
        }
    }

    private void rewriteHistoryFile(List<String> values) {
        getApplicationContext().deleteFile(shortList);
        for (String value : values) {
            saveToShortHistoryFile(value);
        }
    }

    //#endregion

}




















