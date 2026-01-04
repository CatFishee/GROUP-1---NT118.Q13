package com.example.metube.ui.watchtogether;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.metube.R;
import java.util.ArrayList;
import java.util.List;

public class ParticipantAdapter extends RecyclerView.Adapter<ParticipantAdapter.ViewHolder> {

    public static class Participant {
        public String uid;
        public String name;
        public String profileUrl; // Added field
        public boolean isHost;

        public Participant(String uid, String name, String profileUrl, boolean isHost) {
            this.uid = uid;
            this.name = name;
            this.profileUrl = profileUrl;
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
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_participant, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Participant p = userList.get(position);
        holder.tvUserName.setText(p.name);

        // Host Badge
        if (p.isHost) {
            holder.tvHostBadge.setVisibility(View.VISIBLE);
        } else {
            holder.tvHostBadge.setVisibility(View.GONE);
        }

        // Load Avatar
        if (p.profileUrl != null && !p.profileUrl.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(p.profileUrl)
                    .apply(RequestOptions.circleCropTransform()) // Makes image circular
                    .placeholder(R.drawable.circle_shape)
                    .error(android.R.drawable.sym_def_app_icon)
                    .into(holder.ivAvatar);
        } else {
            // Default placeholder
            holder.ivAvatar.setImageResource(android.R.drawable.sym_def_app_icon);
        }
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvUserName, tvHostBadge;
        ImageView ivAvatar; // Added ImageView

        ViewHolder(View itemView) {
            super(itemView);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvHostBadge = itemView.findViewById(R.id.tvHostBadge);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
        }
    }
}