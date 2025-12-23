package com.example.metube.ui.playlist;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.metube.R;
import com.example.metube.model.Playlist;
import java.util.List;

public class PlaylistPreviewAdapter extends RecyclerView.Adapter<PlaylistPreviewAdapter.ViewHolder> {
    private List<Playlist> list;

    public PlaylistPreviewAdapter(List<Playlist> list) { this.list = list; }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlist_preview_horizontal, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Playlist p = list.get(position);
        holder.tvTitle.setText(p.getTitle());
        holder.tvVisibility.setText(p.getVisibility());
        int count = p.getVideoIds() != null ? p.getVideoIds().size() : 0;
        holder.tvCount.setText(count + " videos");
    }

    @Override public int getItemCount() { return list.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvVisibility, tvCount;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvVisibility = itemView.findViewById(R.id.tv_visibility);
            tvCount = itemView.findViewById(R.id.tv_count);
        }
    }
}