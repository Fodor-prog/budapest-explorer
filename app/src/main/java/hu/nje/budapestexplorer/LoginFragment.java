package hu.nje.budapestexplorer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

public class LoginFragment extends Fragment {

    public LoginFragment() {
        // Kötelező üres konstruktor
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        return inflater.inflate(
                R.layout.fragment_login,
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

        view.findViewById(R.id.button_login_to_register)
                .setOnClickListener(button ->
                        Navigation.findNavController(view)
                                .navigate(R.id.action_loginFragment_to_registerFragment)
                );

        view.findViewById(R.id.button_login_to_profile)
                .setOnClickListener(button ->
                        Navigation.findNavController(view)
                                .navigate(R.id.action_loginFragment_to_profileFragment)
                );

        view.findViewById(R.id.button_login_submit)
                .setOnClickListener(button ->
                        Toast.makeText(
                                requireContext(),
                                "A bejelentkezés az adatbázis elkészítése után lesz elérhető.",
                                Toast.LENGTH_SHORT
                        ).show()
                );
    }
}


