package hu.nje.budapestexplorer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProfileFragment extends Fragment {

    private WalkRepository walkRepository;
    private WalkAdapter walkAdapter;
    private RecyclerView recyclerWalks;
    private TextView textNoSavedWalks;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy. MM. dd. HH:mm", new Locale("hu", "HU"));

    public ProfileFragment() {
        // Kötelező üres konstruktor
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        return inflater.inflate(
                R.layout.fragment_profile,
                container,
                false
        );
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        walkRepository = new WalkRepository(requireContext());

        recyclerWalks = view.findViewById(R.id.recyclerWalks);
        textNoSavedWalks = view.findViewById(R.id.textNoSavedWalks);

        recyclerWalks.setLayoutManager(
                new LinearLayoutManager(requireContext())
        );

        walkAdapter = new WalkAdapter(this::showWalkDetails);
        recyclerWalks.setAdapter(walkAdapter);

        view.findViewById(R.id.button_open_login)
                .setOnClickListener(button ->
                        Navigation.findNavController(view)
                                .navigate(
                                        R.id.action_profileFragment_to_loginFragment
                                )
                );

        view.findViewById(R.id.button_open_register)
                .setOnClickListener(button ->
                        Navigation.findNavController(view)
                                .navigate(
                                        R.id.action_profileFragment_to_registerFragment
                                )
                );
    }

    @Override
    public void onResume() {
        super.onResume();
        loadWalks();
    }

    private void loadWalks() {
        if (walkRepository == null || walkAdapter == null) {
            return;
        }

        walkRepository.getAllWalks(walks -> {
            if (!isAdded() || recyclerWalks == null || walkAdapter == null) {
                return;
            }

            walkAdapter.setWalks(walks);

            boolean noWalks = walks == null || walks.isEmpty();

            textNoSavedWalks.setVisibility(
                    noWalks ? View.VISIBLE : View.GONE
            );
        });
    }

    private void showWalkDetails(WalkEntity walk) {
        if (walkRepository == null) {
            return;
        }

        walkRepository.getPointsForWalk(walk.id, points -> {
            if (!isAdded()) {
                return;
            }

            String startTime = dateFormat.format(
                    new Date(walk.startedAtMillis)
            );

            String endTime;

            if (walk.endedAtMillis == null) {
                endTime = "A séta még nincs lezárva";
            } else {
                endTime = dateFormat.format(
                        new Date(walk.endedAtMillis)
                );
            }

            int pointCount = points == null ? 0 : points.size();

            String message =
                    "Indulás: " + startTime
                            + "\nBefejezés: " + endTime
                            + "\nRögzített GPS-pontok: " + pointCount;

            new AlertDialog.Builder(requireContext())
                    .setTitle("Séta részletei")
                    .setMessage(message)
                    .setPositiveButton("Bezárás", null)
                    .show();
        });
    }

    @Override
    public void onDestroyView() {
        recyclerWalks = null;
        textNoSavedWalks = null;
        walkAdapter = null;
        walkRepository = null;

        super.onDestroyView();
    }
}