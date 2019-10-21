package xyz.yaroslav.securitycontrolsystem;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.Objects;

public class MainFragment extends Fragment {
    ImageView historyIcon;
    ImageView settingsIcon;

    public MainFragment() {}

    public static MainFragment newInstance() {
        return new MainFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_default, container, false);

        historyIcon = rootView.findViewById(R.id.menu_history);
        settingsIcon = rootView.findViewById(R.id.menu_settings);

        historyIcon.setOnClickListener(v -> showHistoryFragment());
        settingsIcon.setOnClickListener(v -> {
            DialogFragment dialogFragment = new PreferencesFragment();
            dialogFragment.show(Objects.requireNonNull(getActivity()).getSupportFragmentManager(), "PREFERENCES");
        });

        return rootView;
    }

    private void showHistoryFragment() {
        HistoryFragment fragment = new HistoryFragment();
        FragmentTransaction ft = getActivity().getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.fragment_container, fragment);
        ft.addToBackStack(null);
        ft.commit();
    }
}





































