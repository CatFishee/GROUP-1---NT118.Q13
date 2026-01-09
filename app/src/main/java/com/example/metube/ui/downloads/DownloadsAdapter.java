package com.example.metube.ui.downloads;

import android.content.Context;
import android.content.Intent;
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
import com.example.metube.ui.video.VideoActivity;

import java.util.ArrayList;
import java.util.List;

public class DownloadsAdapter extends RecyclerView.Adapter<DownloadsAdapter.ViewHolder> {

    private List<Video> videos = new ArrayList<>();

    public void setData(List<Video> videos) {
        this.videos = videos;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Tái sử dụng layout item playlist hoặc history (hoặc tạo layout mới nếu muốn)
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video_playlist, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Video video = videos.get(position);
        Context context = holder.itemView.getContext();

        holder.tvTitle.setText(video.getTitle());
        holder.tvUploader.setText("Offline Video"); // Video offline không có tên kênh
        holder.tvDuration.setVisibility(View.GONE); // Ẩn thời gian nếu không lấy được metadata

        // Glide cực mạnh: Nó tự lấy thumbnail từ FILE VIDEO trong máy
        Glide.with(context)
                .load(video.getVideoURL()) // Load từ đường dẫn file
                .centerCrop()
                .placeholder(R.color.black)
                .into(holder.ivThumbnail);

        // Click mở video offline
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, VideoActivity.class);
            intent.putExtra("video_id", video.getVideoID()); // ID giả: "local_..."
            intent.putExtra("local_video_path", video.getVideoURL()); // Truyền thêm đường dẫn file
            context.startActivity(intent);
        });
        holder.btnMore.setVisibility(View.VISIBLE); // Hiện nút 3 chấm
        holder.btnMore.setOnClickListener(v -> {
            showPopupMenu(context, v, video, position);
        });
    }

    @Override
    public int getItemCount() { return videos.size(); }
    // Hàm hiển thị Menu xóa
    private void showPopupMenu(Context context, View view, Video video, int position) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(context, view);
        // Bạn có thể tạo file menu xml hoặc add trực tiếp bằng code
        popup.getMenu().add("Delete");

        popup.setOnMenuItemClickListener(item -> {
            if (item.getTitle().equals("Delete")) {
                deleteVideoFile(context, video, position);
                return true;
            }
            return false;
        });
        popup.show();
    }

    // Hàm thực hiện xóa file và cập nhật list
    private void deleteVideoFile(Context context, Video video, int position) {
        try {
            java.io.File file = new java.io.File(video.getVideoURL());
            if (file.exists()) {
                boolean deleted = file.delete();
                if (deleted) {
                    // Xóa khỏi danh sách hiển thị
                    videos.remove(position);
                    notifyItemRemoved(position);
                    notifyItemRangeChanged(position, videos.size());
                    android.widget.Toast.makeText(context, "Deleted", android.widget.Toast.LENGTH_SHORT).show();
                } else {
                    android.widget.Toast.makeText(context, "Failed to delete file", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumbnail, btnMore;
        TextView tvTitle, tvUploader, tvDuration;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            // Ánh xạ theo layout item_video_playlist.xml
            ivThumbnail = itemView.findViewById(R.id.iv_thumbnail);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvUploader = itemView.findViewById(R.id.tv_uploader);
            tvDuration = itemView.findViewById(R.id.tv_duration);
            btnMore = itemView.findViewById(R.id.btn_more);
        }
    }

}