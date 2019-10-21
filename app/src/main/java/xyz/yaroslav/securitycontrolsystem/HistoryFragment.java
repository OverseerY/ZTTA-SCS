package xyz.yaroslav.securitycontrolsystem;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static android.content.Context.MODE_PRIVATE;

public class HistoryFragment extends Fragment{

    //#region Variables

    SharedPreferences appPreferences;

    public static final String APP_PREFERENCES = "ApplicationPreferences";
    public static final String SRV_PROTOCOL = "srv_protocol"; //http
    public static final String SRV_ADDRESS = "srv_address"; //192.168.0.14
    public static final String SRV_PORT = "srv_port"; //5002
    public static final String SRV_POSTFIX_EVENTS = "srv_postfix_events"; //events?st=&et=

    ImageView rangeIcon;
    ImageView backIcon;
    ImageView localIcon;
    ImageView actionsMoreIcon;
    ProgressBar progressBar;

    private String url_with_range;

    private static final String history_file = "recent.txt";
    private static final String temp_file = "temp.txt";

    private List<TagItem> tagItemList;
    private List<TagItem> tagFromFileList;
    private List<TagItem> tagFromTempList;
    private TagAdapter tagAdapter;
    private RecyclerView historyRecyclerView;

    public HistoryFragment() {}

    public static HistoryFragment newInstance() {
        return new HistoryFragment();
    }

    //#endregion

    //#region Fragment Methods

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_history, container, false);

        appPreferences = getActivity().getSharedPreferences(APP_PREFERENCES, MODE_PRIVATE);

        rangeIcon = rootView.findViewById(R.id.menu_daterange);
        backIcon = rootView.findViewById(R.id.menu_back);
        localIcon = rootView.findViewById(R.id.menu_local_history);
        actionsMoreIcon = rootView.findViewById(R.id.menu_additional);
        progressBar = rootView.findViewById(R.id.history_progressbar);
        historyRecyclerView = rootView.findViewById(R.id.history_tags);

        tagItemList = new ArrayList<>();
        tagFromFileList = new ArrayList<>();
        tagFromTempList = new ArrayList<>();
        tagAdapter = new TagAdapter(tagItemList);

        /** Open custom DialogFragment to select dates range */
        rangeIcon.setOnClickListener(v -> {
            DialogFragment dialogFragment = new HistoryRange();
            dialogFragment.show(Objects.requireNonNull(getActivity()).getSupportFragmentManager(), "RANGE");
        });

        /** Open MainFragment */
        backIcon.setOnClickListener(v -> {
            MainFragment fragment = new MainFragment();
            FragmentTransaction ft = Objects.requireNonNull(getActivity()).getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.fragment_container, fragment);
            ft.addToBackStack(null);
            ft.commit();
        });

        localIcon.setOnClickListener(v -> new ShowTempAsync().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR));

        actionsMoreIcon.setOnClickListener(this::openFragmentPopUpMenu);

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        new ShowHistoryAsync().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

    }

    public void setUrl(String url_with_range) {
        this.url_with_range = url_with_range;
    }

    //#endregion

    //#region Retrieve Tags

    private class ShowHistoryAsync extends AsyncTask<Void, Void, Integer> {
        @Override
        protected Integer doInBackground(Void... voids) {
            if (url_with_range == null || url_with_range.equals("")) {
                String tmp = readTagsFromFile(history_file);
                if (!tmp.equals("")) {
                    String[] arr = tmp.split(";");
                    if (arr.length > 0) {
                        tagFromFileList.clear();
                        for (String value : arr) {
                            try {
                                String tagName;
                                String tagTime;
                                String tagUid;

                                JSONObject jsonObject = new JSONObject(value);
                                tagName = String.valueOf(jsonObject.getString("tag_data"));
                                tagTime = String.valueOf(jsonObject.getString("tag_time"));
                                tagUid = String.valueOf(jsonObject.getString("tag_id"));
                                TagItem tagItem = new TagItem(tagUid, tagName, tagTime);

                                tagFromFileList.add(tagItem);
                            } catch (JSONException e) {
                                Log.e("HISTORY_FILE", "JSON Exception in " + "(" + e.getClass() + "): " + e.getMessage());
                            }
                        }
                        if (!tagFromFileList.isEmpty()) {
                            return 0;
                        }
                    }
                }
            }
            return 1;
        }

        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected void onPostExecute(Integer flag) {
            if (flag == 0) {
                showLocalTags();
            } else {
                //Toast.makeText(getContext(), getString(R.string.toast_msg_empty_history), Toast.LENGTH_SHORT).show();
                showTagsFromServer();
            }
        }
    }

    private void showLocalTags() {
        final Handler handler = new Handler();
        handler.postDelayed(() -> {
            Collections.sort(tagFromFileList, TagItem.TagComparator);
            tagAdapter = new TagAdapter(tagFromFileList);
            RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getContext());
            historyRecyclerView.setLayoutManager(layoutManager);
            historyRecyclerView.setItemAnimator(new DefaultItemAnimator());
            historyRecyclerView.setAdapter(tagAdapter);
            progressBar.setVisibility(View.INVISIBLE);
        }, 500);
    }

    private String buildUrl() {
        String protocol = appPreferences.getString(SRV_PROTOCOL, "http");
        String address = appPreferences.getString(SRV_ADDRESS, "192.168.0.14");
        String port = appPreferences.getString(SRV_PORT, "5002");
        String postfix_history = appPreferences.getString(SRV_POSTFIX_EVENTS, "events?st=&et=");

        return protocol + "://" + address + ":" + port + "/" + postfix_history;
    }

    private void showTagsFromServer() {
        final Handler handler = new Handler();
        handler.postDelayed(() -> {
            String url;
            if (url_with_range == null || url_with_range.equals("")) {
                url = buildUrl();
            } else {
                url = url_with_range;
            }

            try {
                Integer result = new ParseJsonAsync().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, url).get();
                if (result == 0) {
                    Collections.sort(tagItemList, TagItem.TagComparator);
                    RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getContext());
                    historyRecyclerView.setLayoutManager(layoutManager);
                    historyRecyclerView.setItemAnimator(new DefaultItemAnimator());
                    historyRecyclerView.setAdapter(tagAdapter);
                } else {
                    Toast.makeText(getContext(), getString(R.string.toast_msg_empty_response), Toast.LENGTH_SHORT).show();
                }
            } catch (ExecutionException | InterruptedException e) {
                Log.e("JSON_PARSE", "Exception - Parsing JSON from server");
            }
            progressBar.setVisibility(View.INVISIBLE);
        }, 500);
    }

    private String readTagsFromFile(String filename) {
        String ret = "";
        try {
            InputStream inputStream = Objects.requireNonNull(getContext()).openFileInput(filename);

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
            Log.e("LOCAL_FILE", "File <" + filename + "> not found: " + e.getMessage());
        } catch (IOException e) {
            Log.e("HISTORY_FILE", "IO Exception: " + e.getMessage());
        }

        return ret;
    }

    private class ParseJsonAsync extends AsyncTask<String, Void, Integer> {
        @Override
        protected Integer doInBackground(String... strings) {
            try {
                URL url = new URL(strings[0]);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

                InputStream stream = new BufferedInputStream(urlConnection.getInputStream());
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream));
                StringBuilder builder = new StringBuilder();

                String inputString;

                while ((inputString = bufferedReader.readLine()) != null) {
                    builder.append(inputString);
                }

                urlConnection.disconnect();

                JSONObject topLevel = new JSONObject(builder.toString());
                JSONArray jArray = topLevel.getJSONArray("events");

                tagItemList.clear();

                for (int i = 0; i < jArray.length(); i++) {
                    String tagName;
                    String tagTime;
                    String tagUid ;
                    try {
                        JSONObject nestedObject = jArray.getJSONObject(i);
                        tagName = String.valueOf(nestedObject.getString("tag_data"));
                        tagTime = String.valueOf(nestedObject.getString("tag_time"));
                        tagUid = String.valueOf(nestedObject.getString("tag_id"));

                        TagItem tagItem = new TagItem(tagUid, tagName, tagTime);
                        tagItemList.add(tagItem);
                    } catch (JSONException e) {
                        Log.e("GET_HISTORY", "JSON Exception: " + "(" + e.getClass() + "): " + e.getMessage());
                    }
                }
                if (!tagItemList.isEmpty()) {
                    return 0;
                }
            } catch (IOException e) {
                Log.e("GET_HISTORY", "IO Exception: " + "(" + e.getClass() + "): " + e.getMessage());
            } catch (JSONException e) {
                Log.e("GET_HISTORY", "JSON Exception: " + "(" + e.getClass() + "): " + e.getMessage());
            }
            return 1;
        }
    }

    //#endregion

    //#region Temp File Tags

    private class ShowTempAsync extends AsyncTask<Void, Void, Integer> {
        @Override
        protected Integer doInBackground(Void... voids) {
            String tmp = readTagsFromFile(temp_file);
            if (!tmp.equals("")) {
                String[] arr = tmp.split(";");
                if (arr.length > 0) {
                    tagFromTempList.clear();
                    for (String value : arr) {
                        try {
                            String tagName;
                            String tagTime;
                            String tagUid;

                            JSONObject jsonObject = new JSONObject(value);
                            tagName = String.valueOf(jsonObject.getString("tag_data"));
                            tagTime = String.valueOf(jsonObject.getString("tag_time"));
                            tagUid = String.valueOf(jsonObject.getString("tag_id"));
                            TagItem tagItem = new TagItem(tagUid, tagName, tagTime);

                            tagFromTempList.add(tagItem);
                        } catch (JSONException e) {
                            Log.e("TEMP_FILE", "JSON Exception in " + "(" + e.getClass() + "): " + e.getMessage());
                        }
                    }
                    if (!tagFromTempList.isEmpty()) {
                        return 0;
                    }
                }
            }
            return 1;
        }

        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected void onPostExecute(Integer flag) {
            if (flag == 0) {
                showTempTags();
            } else {
                Toast.makeText(getContext(), getString(R.string.toast_msg_empty_temp), Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void showTempTags() {
        final Handler handler = new Handler();
        handler.postDelayed(() -> {
            Collections.sort(tagFromTempList, TagItem.TagComparator);
            tagAdapter = new TagAdapter(tagFromTempList);
            RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getContext());
            historyRecyclerView.setLayoutManager(layoutManager);
            historyRecyclerView.setItemAnimator(new DefaultItemAnimator());
            historyRecyclerView.setAdapter(tagAdapter);
            progressBar.setVisibility(View.INVISIBLE);
        }, 500);
    }

    //#endregion

    //#region Menu

    private void openFragmentPopUpMenu(View menu_item) {
        try {
            Context context = getContext();
            assert context != null;
            PopupMenu popup = new PopupMenu(context, menu_item);
            popup.inflate(R.menu.settings_menu);
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.clear_file) {
                    warningDialog(history_file, 1);
                } else if (item.getItemId() == R.id.clear_temp_file) {
                    warningDialog(temp_file, 2);
                }
                return false;
            });
            popup.show();
        } catch (NullPointerException e) {
            Log.e("GET_CTX", e.getMessage());
        }
    }

    private void warningDialog(String file_name, Integer type) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(getString(R.string.label_warning));
        if (type == 1) {
            builder.setMessage(getString(R.string.message_warning_delete_history));
        } else if (type == 2) {
            builder.setMessage(getString(R.string.message_warning_delete_temp));
        } else {
            builder.setMessage(getString(R.string.message_warning_delete_file));
        }
        builder.setIcon(R.drawable.ic_warning);
        builder.setPositiveButton(getString(R.string.label_ok), (dialog, which) -> {
            getContext().deleteFile(file_name);
            dialog.dismiss();
        });

        builder.setNegativeButton(getString(R.string.label_cancel), (dialog, which) -> dialog.dismiss());

        final AlertDialog closedialog = builder.create();
        closedialog.show();
    }

    //#endregion
}


























