package hu.nje.budapestexplorer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

public class ProfileFragment extends Fragment {

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

        view.findViewById(R.id.button_open_login)
                .setOnClickListener(button ->
                        Navigation.findNavController(view)
                                .navigate(R.id.action_profileFragment_to_loginFragment)
                );

        view.findViewById(R.id.button_open_register)
                .setOnClickListener(button ->
                        Navigation.findNavController(view)
                                .navigate(R.id.action_profileFragment_to_registerFragment)
                );
    }
}

