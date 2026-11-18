package hu.nje.budapestexplorer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class ProfileFragment extends Fragment {

    private UserRepository userRepository;
    private WalkRepository walkRepository;
    private WalkAdapter walkAdapter;

    private RecyclerView recyclerWalks;
    private TextView textNoSavedWalks;
    private TextView textProfileDescription;
    private Button loginButton;
    private Button registerButton;
    private Button logoutButton;

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

        userRepository = new UserRepository(requireContext());
        walkRepository = new WalkRepository(requireContext());

        recyclerWalks = view.findViewById(R.id.recyclerWalks);
        textNoSavedWalks = view.findViewById(R.id.textNoSavedWalks);
        textProfileDescription =
                view.findViewById(R.id.textProfileDescription);

        loginButton = view.findViewById(R.id.button_open_login);
        registerButton = view.findViewById(R.id.button_open_register);
        logoutButton = view.findViewById(R.id.button_logout);

        recyclerWalks.setLayoutManager(
                new LinearLayoutManager(requireContext())
        );

        walkAdapter = new WalkAdapter(this::showWalkDetails);
        recyclerWalks.setAdapter(walkAdapter);

        loginButton.setOnClickListener(button ->
                Navigation.findNavController(view)
                        .navigate(
                                R.id.action_profileFragment_to_loginFragment
                        )
        );

        registerButton.setOnClickListener(button ->
                Navigation.findNavController(view)
                        .navigate(
                                R.id.action_profileFragment_to_registerFragment
                        )
        );

        logoutButton.setOnClickListener(button -> {
            userRepository.logout();
            updateAuthenticationState();
            loadWalks();
            showMessage("Kijelentkeztél.");
        });

        updateAuthenticationState();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateAuthenticationState();
        loadWalks();
    }

    private void updateAuthenticationState() {
        if (userRepository == null
                || textProfileDescription == null
                || loginButton == null
                || registerButton == null
                || logoutButton == null) {
            return;
        }

        boolean loggedIn = userRepository.isLoggedIn();

        textProfileDescription.setText(
                loggedIn
                        ? "Be vagy jelentkezve a helyi fiókodba."
                        : "Jelentkezz be, vagy hozz létre egy új fiókot."
        );

        loginButton.setVisibility(
                loggedIn ? View.GONE : View.VISIBLE
        );

        registerButton.setVisibility(
                loggedIn ? View.GONE : View.VISIBLE
        );

        logoutButton.setVisibility(
                loggedIn ? View.VISIBLE : View.GONE
        );
    }

    private void loadWalks() {
        if (walkRepository == null
                || walkAdapter == null
                || userRepository == null
                || recyclerWalks == null
                || textNoSavedWalks == null) {
            return;
        }

        if (!userRepository.isLoggedIn()) {
            walkAdapter.setWalks(new ArrayList<>());
            recyclerWalks.setVisibility(View.GONE);
            textNoSavedWalks.setVisibility(View.VISIBLE);
            return;
        }

        recyclerWalks.setVisibility(View.VISIBLE);

        long loggedInUserId = userRepository.getLoggedInUserId();

        if (loggedInUserId < 0L) {
            walkAdapter.setWalks(new ArrayList<>());
            recyclerWalks.setVisibility(View.GONE);
            textNoSavedWalks.setVisibility(View.VISIBLE);
            return;
        }

        Long userId = loggedInUserId;

        walkRepository.getWalksForUser(userId, walks -> {
            if (!isAdded()
                    || recyclerWalks == null
                    || walkAdapter == null
                    || textNoSavedWalks == null) {
                return;
            }

            walkAdapter.setWalks(walks);

            boolean noWalks = walks == null || walks.isEmpty();

            recyclerWalks.setVisibility(
                    noWalks ? View.GONE : View.VISIBLE
            );

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
                    .setPositiveButton(
                            "Útvonal megtekintése",
                            (dialog, which) -> openWalkOnMap(walk)
                    )
                    .setNegativeButton("Bezárás", null)
                    .show();
        });
    }

    private void openWalkOnMap(WalkEntity walk) {
        Bundle arguments = new Bundle();
        arguments.putLong(MapFragment.ARG_WALK_ID, walk.id);

        Navigation.findNavController(requireView()).navigate(
                R.id.action_profileFragment_to_mapFragment,
                arguments
        );
    }

    private void showMessage(String message) {
        if (isAdded()) {
            Toast.makeText(
                    requireContext(),
                    message,
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    @Override
    public void onDestroyView() {
        recyclerWalks = null;
        textNoSavedWalks = null;
        textProfileDescription = null;
        loginButton = null;
        registerButton = null;
        logoutButton = null;
        walkAdapter = null;
        userRepository = null;
        walkRepository = null;

        super.onDestroyView();
    }
}