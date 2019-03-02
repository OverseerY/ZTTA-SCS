package xyz.yaroslav.securitycontrolsystem;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
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

public class HistoryFragment extends Fragment{
    ImageView rangeIcon;
    ImageView backIcon;
    ProgressBar progressBar;

    private String url_with_range;

    private static final String default_url = "http://192.168.0.14:5002/events?st=&et=";

    private List<TagItem> tagItemList;
    private TagAdapter tagAdapter;
    private RecyclerView historyRecyclerView;

    public HistoryFragment() {}

    public static HistoryFragment newInstance() {
        return new HistoryFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_history, container, false);

        rangeIcon = rootView.findViewById(R.id.menu_daterange);
        backIcon = rootView.findViewById(R.id.menu_back);
        progressBar = rootView.findViewById(R.id.history_progressbar);
        historyRecyclerView = rootView.findViewById(R.id.history_tags);

        tagItemList = new ArrayList<>();
        tagAdapter = new TagAdapter(tagItemList);

        rangeIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRangeDialog();
            }
        });

        backIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMainFragment();
            }
        });

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();

        progressBar.setVisibility(View.VISIBLE);
        delayRequest();
    }

    private void delayRequest() {
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                showTags(url_with_range);
            }
        }, 500);
    }

    public void setUrl(String url_with_range) {
        this.url_with_range = url_with_range;
    }

    private void showMainFragment() {
        MainFragment fragment = new MainFragment();
        FragmentTransaction ft = Objects.requireNonNull(getActivity()).getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.fragment_container, fragment);
        ft.addToBackStack(null);
        ft.commit();
    }

    private void showRangeDialog() {
        DialogFragment dialogFragment = new HistoryRange();
        dialogFragment.show(Objects.requireNonNull(getActivity()).getSupportFragmentManager(), "RANGE");
    }

    private class GetTagsAsynkTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... strings) {
            parseJsonFromServer(strings[0]);
            return "";
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            progressBar.setVisibility(View.INVISIBLE);
        }
    }

    private void parseJsonFromServer(String srv_url) {
        try {
            URL url = new URL(srv_url);
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
                    e.printStackTrace();
                }
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    private void showTags(String m_url) {
        String url;
        if (m_url == null || m_url.equals("")) {
            url = default_url;
        } else {
            url = m_url;
        }
        try {
            new GetTagsAsynkTask().execute(url).get();
            Collections.sort(tagItemList, TagItem.TagComparator);
            RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getContext());
            historyRecyclerView.setLayoutManager(layoutManager);
            historyRecyclerView.setItemAnimator(new DefaultItemAnimator());
            historyRecyclerView.setAdapter(tagAdapter);
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
    }

}
