package hu.nje.budapestexplorer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WalkAdapter extends RecyclerView.Adapter<WalkAdapter.WalkViewHolder> {

    public interface OnWalkClickListener {
        void onWalkClick(WalkEntity walk);
    }

    private final List<WalkEntity> walks = new ArrayList<>();
    private final OnWalkClickListener onWalkClickListener;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy. MM. dd. HH:mm", new Locale("hu", "HU"));

    public WalkAdapter(OnWalkClickListener onWalkClickListener) {
        this.onWalkClickListener = onWalkClickListener;
    }

    @NonNull
    @Override
    public WalkViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_walk, parent, false);

        return new WalkViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull WalkViewHolder holder,
            int position
    ) {
        WalkEntity walk = walks.get(position);

        holder.title.setText(
                "Séta – " + dateFormat.format(
                        new Date(walk.startedAtMillis)
                )
        );

        if (walk.endedAtMillis == null) {
            holder.details.setText("A séta még nincs lezárva.");
        } else {
            holder.details.setText(
                    "Befejezve: " + dateFormat.format(
                            new Date(walk.endedAtMillis)
                    )
            );
        }

        holder.itemView.setOnClickListener(view ->
                onWalkClickListener.onWalkClick(walk)
        );
    }

    @Override
    public int getItemCount() {
        return walks.size();
    }

    public void setWalks(List<WalkEntity> newWalks) {
        walks.clear();

        if (newWalks != null) {
            walks.addAll(newWalks);
        }

        notifyDataSetChanged();
    }

    static class WalkViewHolder extends RecyclerView.ViewHolder {

        private final TextView title;
        private final TextView details;

        WalkViewHolder(@NonNull View itemView) {
            super(itemView);

            title = itemView.findViewById(R.id.textWalkTitle);
            details = itemView.findViewById(R.id.textWalkDetails);
        }
    }
}