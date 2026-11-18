package hu.nje.budapestexplorer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Patterns;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserRepository {

    private static final String PREFERENCES_NAME = "user_session";
    private static final String KEY_LOGGED_IN_USER_ID = "logged_in_user_id";

    private static final ExecutorService databaseExecutor =
            Executors.newSingleThreadExecutor();

    private final AppDatabase database;
    private final SharedPreferences preferences;
    private final Handler mainThreadHandler;

    public enum AuthResult {
        SUCCESS,
        EMAIL_ALREADY_REGISTERED,
        INVALID_CREDENTIALS,
        INVALID_INPUT,
        ERROR
    }

    public interface ResultCallback {
        void onResult(AuthResult result);
    }

    public UserRepository(Context context) {
        Context applicationContext = context.getApplicationContext();

        database = AppDatabase.getInstance(applicationContext);
        preferences = applicationContext.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
        );
        mainThreadHandler = new Handler(Looper.getMainLooper());
    }

    public void register(
            String email,
            char[] password,
            ResultCallback callback
    ) {
        String normalizedEmail = normalizeEmail(email);

        if (!isValidEmail(normalizedEmail)
                || password == null
                || password.length < 8) {
            clearPassword(password);
            postResult(callback, AuthResult.INVALID_INPUT);
            return;
        }

        char[] passwordCopy = Arrays.copyOf(password, password.length);
        clearPassword(password);

        databaseExecutor.execute(() -> {
            try {
                UserEntity existingUser =
                        database.userDao().getUserByEmail(normalizedEmail);

                if (existingUser != null) {
                    postResult(
                            callback,
                            AuthResult.EMAIL_ALREADY_REGISTERED
                    );
                    return;
                }

                PasswordHasher.HashedPassword hashedPassword =
                        PasswordHasher.hashPassword(passwordCopy);

                UserEntity user = new UserEntity(
                        normalizedEmail,
                        hashedPassword.passwordHash,
                        hashedPassword.passwordSalt,
                        System.currentTimeMillis()
                );

                long userId = database.userDao().insert(user);

                if (userId < 0) {
                    postResult(
                            callback,
                            AuthResult.EMAIL_ALREADY_REGISTERED
                    );
                    return;
                }

                saveLoggedInUser(userId);
                postResult(callback, AuthResult.SUCCESS);

            } catch (GeneralSecurityException | RuntimeException exception) {
                postResult(callback, AuthResult.ERROR);
            } finally {
                clearPassword(passwordCopy);
            }
        });
    }

    public void login(
            String email,
            char[] password,
            ResultCallback callback
    ) {
        String normalizedEmail = normalizeEmail(email);

        if (!isValidEmail(normalizedEmail)
                || password == null
                || password.length == 0) {
            clearPassword(password);
            postResult(callback, AuthResult.INVALID_INPUT);
            return;
        }

        char[] passwordCopy = Arrays.copyOf(password, password.length);
        clearPassword(password);

        databaseExecutor.execute(() -> {
            try {
                UserEntity user =
                        database.userDao().getUserByEmail(normalizedEmail);

                if (user == null) {
                    postResult(
                            callback,
                            AuthResult.INVALID_CREDENTIALS
                    );
                    return;
                }

                boolean passwordMatches =
                        PasswordHasher.verifyPassword(
                                passwordCopy,
                                user.passwordSalt,
                                user.passwordHash
                        );

                if (!passwordMatches) {
                    postResult(
                            callback,
                            AuthResult.INVALID_CREDENTIALS
                    );
                    return;
                }

                saveLoggedInUser(user.id);
                postResult(callback, AuthResult.SUCCESS);

            } catch (GeneralSecurityException | RuntimeException exception) {
                postResult(callback, AuthResult.ERROR);
            } finally {
                clearPassword(passwordCopy);
            }
        });
    }

    public long getLoggedInUserId() {
        return preferences.getLong(
                KEY_LOGGED_IN_USER_ID,
                -1L
        );
    }

    public boolean isLoggedIn() {
        return getLoggedInUserId() >= 0;
    }

    public void logout() {
        preferences.edit()
                .remove(KEY_LOGGED_IN_USER_ID)
                .apply();
    }

    private void saveLoggedInUser(long userId) {
        preferences.edit()
                .putLong(KEY_LOGGED_IN_USER_ID, userId)
                .apply();
    }

    private void postResult(
            ResultCallback callback,
            AuthResult result
    ) {
        if (callback != null) {
            mainThreadHandler.post(() -> callback.onResult(result));
        }
    }

    private String normalizeEmail(String email) {
        return email == null
                ? ""
                : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private boolean isValidEmail(String email) {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void clearPassword(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}