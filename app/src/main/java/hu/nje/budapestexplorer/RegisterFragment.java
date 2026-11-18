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

public class RegisterFragment extends Fragment {

    private UserRepository userRepository;

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

        userRepository = new UserRepository(requireContext());

        EditText emailInput = view.findViewById(R.id.edit_register_email);
        EditText passwordInput =
                view.findViewById(R.id.edit_register_password);
        EditText confirmPasswordInput =
                view.findViewById(R.id.edit_register_password_confirm);

        view.findViewById(R.id.button_register_to_login)
                .setOnClickListener(button ->
                        Navigation.findNavController(view)
                                .navigate(
                                        R.id.action_registerFragment_to_loginFragment
                                )
                );

        view.findViewById(R.id.button_register_to_profile)
                .setOnClickListener(button ->
                        Navigation.findNavController(view)
                                .navigate(
                                        R.id.action_registerFragment_to_profileFragment
                                )
                );

        view.findViewById(R.id.button_register_submit)
                .setOnClickListener(button -> {
                    String email = emailInput.getText()
                            .toString()
                            .trim();

                    char[] password = passwordInput.getText()
                            .toString()
                            .toCharArray();

                    char[] confirmedPassword =
                            confirmPasswordInput.getText()
                                    .toString()
                                    .toCharArray();

                    if (email.isEmpty()) {
                        clearPassword(password);
                        clearPassword(confirmedPassword);
                        showMessage("Add meg az e-mail-címedet.");
                        return;
                    }

                    if (password.length < 8) {
                        clearPassword(password);
                        clearPassword(confirmedPassword);
                        showMessage(
                                "A jelszó legalább 8 karakter hosszú legyen."
                        );
                        return;
                    }

                    if (!Arrays.equals(password, confirmedPassword)) {
                        clearPassword(password);
                        clearPassword(confirmedPassword);
                        showMessage("A két jelszó nem egyezik.");
                        return;
                    }

                    clearPassword(confirmedPassword);
                    passwordInput.setText("");
                    confirmPasswordInput.setText("");

                    userRepository.register(
                            email,
                            password,
                            result -> {
                                if (!isAdded()) {
                                    return;
                                }

                                switch (result) {
                                    case SUCCESS:
                                        showMessage(
                                                "A fiók létrejött."
                                        );
                                        Navigation.findNavController(view)
                                                .navigate(
                                                        R.id.action_registerFragment_to_profileFragment
                                                );
                                        break;

                                    case EMAIL_ALREADY_REGISTERED:
                                        showMessage(
                                                "Ezzel az e-mail-címmel már van fiók."
                                        );
                                        break;

                                    case INVALID_INPUT:
                                        showMessage(
                                                "Ellenőrizd az e-mail-címet és a jelszót."
                                        );
                                        break;

                                    case INVALID_CREDENTIALS:
                                        showMessage(
                                                "Ellenőrizd a megadott adatokat."
                                        );
                                        break;

                                    case ERROR:
                                        showMessage(
                                                "Nem sikerült létrehozni a fiókot."
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