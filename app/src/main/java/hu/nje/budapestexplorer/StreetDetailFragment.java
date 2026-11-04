package hu.nje.budapestexplorer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

public class StreetDetailFragment extends Fragment {

    public StreetDetailFragment() {
        // Az Android keretrendszer által használt üres konstruktor.
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        return inflater.inflate(
                R.layout.fragment_street_detail,
                container,
                false
        );
    }
}


