package com.example.metube.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.metube.R;
import java.util.ArrayList;
import java.util.List;

public class ParticipantAdapter extends RecyclerView.Adapter<ParticipantAdapter.ViewHolder> {

    public static class Participant {
        public String uid;
        public String name;
        public boolean isHost;

        public Participant(String uid, String name, boolean isHost) {
            this.uid = uid;
            this.name = name;
            this.isHost = isHost;
        }
    }

    private List<Participant> userList = new ArrayList<>();

    public void setParticipants(List<Participant> list) {
        this.userList = list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Using the new item_participant.xml
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_participant, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Participant p = userList.get(position);
        holder.tvUserName.setText(p.name);

        if (p.isHost) {
            holder.tvHostBadge.setVisibility(View.VISIBLE);
        } else {
            holder.tvHostBadge.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvUserName, tvHostBadge;
        ViewHolder(View itemView) {
            super(itemView);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvHostBadge = itemView.findViewById(R.id.tvHostBadge);
        }
    }
}