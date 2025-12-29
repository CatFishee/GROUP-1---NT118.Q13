package com.example.metube.ui.playlist;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.metube.R;
import com.example.metube.model.Video;
import com.example.metube.utils.TimeUtil;
import java.util.List;

public class PlaylistVideoAdapter extends RecyclerView.Adapter<PlaylistVideoAdapter.ViewHolder> {

    private List<Video> videos;

    public PlaylistVideoAdapter(List<Video> videos) {
        this.videos = videos;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // --- QUAN TRỌNG: Nạp đúng file layout có icon drag handle ---
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video_playlist, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Video video = videos.get(position);

        holder.tvTitle.setText(video.getTitle());
        holder.tvDuration.setText(TimeUtil.formatDuration(video.getDuration()));

        String uploader = video.getUploaderName() != null ? video.getUploaderName() : "Unknown";
        holder.tvUploader.setText(uploader + " • " + video.getViewCount() + " views");

        Glide.with(holder.itemView.getContext())
                .load(video.getThumbnailURL())
                .placeholder(R.color.light_green_background)
                .centerCrop()
                .into(holder.ivThumbnail);

        // Click mở video
        holder.itemView.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(holder.itemView.getContext(), com.example.metube.ui.video.VideoActivity.class);
            intent.putExtra("video_id", video.getVideoID());
            holder.itemView.getContext().startActivity(intent);
        });
    }

    @Override public int getItemCount() { return videos != null ? videos.size() : 0; }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumbnail, ivDragHandle; // Drag Handle ở đây
        TextView tvTitle, tvUploader, tvDuration;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.iv_thumbnail);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvUploader = itemView.findViewById(R.id.tv_uploader);
            tvDuration = itemView.findViewById(R.id.tv_duration);

            // Nếu bạn muốn xử lý sự kiện kéo thả sau này thì ánh xạ nó, giờ để hiển thị thì không cần cũng được
            ivDragHandle = itemView.findViewById(R.id.iv_drag_handle);
        }
    }
}