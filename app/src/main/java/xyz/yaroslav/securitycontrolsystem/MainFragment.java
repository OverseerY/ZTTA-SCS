package xyz.yaroslav.securitycontrolsystem;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.PopupMenu;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.List;

public class MainFragment extends Fragment {
    private static final String temp_file = "recent.txt";
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
        settingsIcon.setOnClickListener(v -> openFragmentPopUpMenu(v));

        return rootView;
    }

    private void showHistoryFragment() {
        HistoryFragment fragment = new HistoryFragment();
        FragmentTransaction ft = getActivity().getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.fragment_container, fragment);
        ft.addToBackStack(null);
        ft.commit();
    }


    private void openFragmentPopUpMenu(View menu_item) {
        try {
            Context context = getContext();
            assert context != null;
            PopupMenu popup = new PopupMenu(context, menu_item);
            popup.inflate(R.menu.settings_menu);
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.clear_file) {
                    warningDialog();
                }
                return false;
            });
            popup.show();
        } catch (NullPointerException e) {
            Log.e("GET_CTX", e.getMessage());
        }
    }

    private void warningDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(getString(R.string.label_warning));
        builder.setMessage(getString(R.string.message_warning_delete));
        builder.setIcon(R.drawable.ic_warning);
        builder.setPositiveButton(getString(R.string.label_ok), (dialog, which) -> {
            getContext().deleteFile(temp_file);
            dialog.dismiss();
        });

        builder.setNegativeButton(getString(R.string.label_cancel), (dialog, which) -> dialog.dismiss());

        final AlertDialog closedialog = builder.create();
        closedialog.show();
    }
}





































