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

public class RegisterFragment extends Fragment {

    public RegisterFragment() {
        // Kötelező üres konstruktor
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        return inflater.inflate(
                R.layout.fragment_register,
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

        view.findViewById(R.id.button_register_to_login)
                .setOnClickListener(button ->
                        Navigation.findNavController(view)
                                .navigate(R.id.action_registerFragment_to_loginFragment)
                );

        view.findViewById(R.id.button_register_to_profile)
                .setOnClickListener(button ->
                        Navigation.findNavController(view)
                                .navigate(R.id.action_registerFragment_to_profileFragment)
                );

        view.findViewById(R.id.button_register_submit)
                .setOnClickListener(button ->
                        Toast.makeText(
                                requireContext(),
                                "A regisztráció az adatbázis elkészítése után lesz elérhető.",
                                Toast.LENGTH_SHORT
                        ).show()
                );
    }
}


