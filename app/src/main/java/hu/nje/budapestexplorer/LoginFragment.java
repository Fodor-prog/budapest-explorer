package hu.nje.budapestexplorer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import java.util.Arrays;

public class LoginFragment extends Fragment {

    private UserRepository userRepository;

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

        userRepository = new UserRepository(requireContext());

        EditText emailInput = view.findViewById(R.id.edit_login_email);
        EditText passwordInput = view.findViewById(R.id.edit_login_password);

        view.findViewById(R.id.button_login_to_register)
                .setOnClickListener(button ->
                        Navigation.findNavController(view)
                                .navigate(
                                        R.id.action_loginFragment_to_registerFragment
                                )
                );

        view.findViewById(R.id.button_login_to_profile)
                .setOnClickListener(button ->
                        Navigation.findNavController(view)
                                .navigate(
                                        R.id.action_loginFragment_to_profileFragment
                                )
                );

        view.findViewById(R.id.button_login_submit)
                .setOnClickListener(button -> {
                    String email = emailInput.getText()
                            .toString()
                            .trim();

                    char[] password = passwordInput.getText()
                            .toString()
                            .toCharArray();

                    if (email.isEmpty() || password.length == 0) {
                        clearPassword(password);
                        showMessage("Add meg az e-mail-címedet és a jelszavadat.");
                        return;
                    }

                    passwordInput.setText("");

                    userRepository.login(
                            email,
                            password,
                            result -> {
                                if (!isAdded()) {
                                    return;
                                }

                                switch (result) {
                                    case SUCCESS:
                                        showMessage("Sikeres bejelentkezés.");
                                        Navigation.findNavController(view)
                                                .navigate(
                                                        R.id.action_loginFragment_to_profileFragment
                                                );
                                        break;

                                    case INVALID_CREDENTIALS:
                                        showMessage(
                                                "Hibás e-mail-cím vagy jelszó."
                                        );
                                        break;

                                    case INVALID_INPUT:
                                        showMessage(
                                                "Ellenőrizd a megadott adatokat."
                                        );
                                        break;

                                    case EMAIL_ALREADY_REGISTERED:
                                        showMessage(
                                                "Ezzel az e-mail-címmel már van fiók."
                                        );
                                        break;

                                    case ERROR:
                                        showMessage(
                                                "Nem sikerült bejelentkezni."
                                        );
                                        break;
                                }
                            }
                    );
                });
    }

    private void showMessage(String message) {
        if (isAdded()) {
            Toast.makeText(
                    requireContext(),
                    message,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void clearPassword(char[] password) {
        Arrays.fill(password, '\0');
    }

    @Override
    public void onDestroyView() {
        userRepository = null;
        super.onDestroyView();
    }
}