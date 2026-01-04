package com.example.metube.ui.watchtogether;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.metube.R;
import com.example.metube.model.QueueItem;
import java.util.ArrayList;
import java.util.List;

public class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.ViewHolder> {
    private List<QueueItem> queueList = new ArrayList<>();
    private boolean isHost = false;
    private OnQueueActionListener actionListener;

    // Interface for handling both Play (click item) and Delete events
    public interface OnQueueActionListener {
        void onDeleteClick(int position);
        void onItemClick(int position);
    }

    public void setQueueList(List<QueueItem> list) {
        this.queueList = list;
        notifyDataSetChanged();
    }

    public void setHost(boolean host) {
        this.isHost = host;
        notifyDataSetChanged();
    }

    public void setOnQueueActionListener(OnQueueActionListener listener) {
        this.actionListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video_queue, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        QueueItem item = queueList.get(position);
        holder.tvTitle.setText(item.getTitle() != null ? item.getTitle() : "Unknown");
        holder.tvUrl.setText(item.getUrl());

        // VISIBILITY: Only Host controls the queue
        holder.btnDelete.setVisibility(isHost ? View.VISIBLE : View.GONE);
        holder.itemView.setClickable(isHost);
        holder.itemView.setFocusable(isHost);

        // EVENT: Delete Video
        holder.btnDelete.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onDeleteClick(position);
        });

        // EVENT: Play Video (Clicking the list item)
        holder.itemView.setOnClickListener(v -> {
            if (isHost && actionListener != null) {
                actionListener.onItemClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return queueList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvUrl;
        ImageButton btnDelete;

        ViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvUrl = itemView.findViewById(R.id.tvUrl);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}