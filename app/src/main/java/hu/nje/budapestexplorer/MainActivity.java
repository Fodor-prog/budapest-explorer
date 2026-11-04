package hu.nje.budapestexplorer;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;

public class MainActivity extends AppCompatActivity {

    private NavController navController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment == null) {
            throw new IllegalStateException("A Navigation Host nem található.");
        }

        navController = navHostFragment.getNavController();

        Button mapButton = findViewById(R.id.button_map);
        Button profileButton = findViewById(R.id.button_profile);

        mapButton.setOnClickListener(view ->
                navigateTo(R.id.mapFragment));

        profileButton.setOnClickListener(view ->
                navigateTo(R.id.profileFragment));
    }

    private void navigateTo(int destinationId) {
        NavDestination currentDestination =
                navController.getCurrentDestination();

        if (currentDestination != null
                && currentDestination.getId() == destinationId) {
            return;
        }

        if (!navController.popBackStack(destinationId, false)) {
            navController.navigate(destinationId);
        }
    }
}


