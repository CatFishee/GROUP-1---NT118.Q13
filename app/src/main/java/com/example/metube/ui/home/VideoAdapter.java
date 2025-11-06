package com.example.metube.ui.home; // Hoặc package của bạn

import android.content.Context;
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
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import de.hdodenhof.circleimageview.CircleImageView;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

    // "Hợp đồng" để thông báo cho Activity khi có click
    public interface OnVideoClickListener {
        void onVideoClick(Video video);
    }

    private List<Video> videoList;
    private Context context;
    private final OnVideoClickListener clickListener;

    // Constructor đúng, nhận vào cả listener
    public VideoAdapter(Context context, List<Video> videoList, OnVideoClickListener clickListener) {
        this.context = context;
        this.videoList = videoList;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video, parent, false);
        return new VideoViewHolder(view);
    }

    // Truyền cả listener vào cho ViewHolder
    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        Video currentVideo = videoList.get(position);
        holder.bind(currentVideo, context, clickListener);
    }

    @Override
    public int getItemCount() {
        return videoList != null ? videoList.size() : 0;
    }

    public void setVideos(List<Video> videos) {
        this.videoList = videos;
        notifyDataSetChanged();
    }

    // Lớp ViewHolder đã được sửa lại hoàn chỉnh
    public static class VideoViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        CircleImageView avatar;
        TextView title;
        TextView info;

        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.video_thumbnail);
            avatar = itemView.findViewById(R.id.channel_avatar);
            title = itemView.findViewById(R.id.video_title);
            info = itemView.findViewById(R.id.video_info);
        }

        // --- HÀM BIND ĐÃ ĐƯỢC SỬA LẠI VỚI ĐỦ 3 THAM SỐ ---
        public void bind(final Video video, Context context, final OnVideoClickListener listener) {
            title.setText(video.getTitle());
            info.setText("Loading..."); // Hiển thị tạm thời

            // Gọi hàm để lấy thông tin kênh và ngày đăng
            loadChannelAndVideoInfo(video, info);

            // Tải ảnh thumbnail bằng Glide
            Glide.with(context)
                    .load(video.getThumbnailURL())
                    .placeholder(R.color.light_green_background)
                    .error(R.drawable.logo_metube)
                    .into(thumbnail);

            // --- THIẾT LẬP SỰ KIỆN CLICK ---
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    // Nếu có, mới gửi thông báo đi.
                    listener.onVideoClick(video);
                }
            });
        }

        // --- HÀM LẤY THÔNG TIN ĐÃ BỊ THIẾU ---
        private void loadChannelAndVideoInfo(Video video, TextView infoTextView) {
            String uploaderId = video.getUploaderID();
            if (uploaderId == null || uploaderId.isEmpty()) {
                infoTextView.setText("Unknown Channel");
                return;
            }

            FirebaseFirestore.getInstance().collection("users").document(uploaderId).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        String channelName = "Unknown Channel";
                        if (documentSnapshot.exists()) {
                            String name = documentSnapshot.getString("channelName");
                            if (name != null && !name.isEmpty()) {
                                channelName = name;
                            }
                        }
                        String timeAgo = formatTimeAgo(video.getCreatedAt());
                        String finalInfoText = channelName + " • " + timeAgo;
                        infoTextView.setText(finalInfoText);
                    })
                    .addOnFailureListener(e -> infoTextView.setText("Unknown Channel"));
        }

        // --- HÀM FORMAT THỜI GIAN ĐÃ BỊ THIẾU ---
        private String formatTimeAgo(com.google.firebase.Timestamp timestamp) {
            if (timestamp == null) return "";
            return android.text.format.DateUtils.getRelativeTimeSpanString(
                    timestamp.toDate().getTime(),
                    System.currentTimeMillis(),
                    android.text.format.DateUtils.MINUTE_IN_MILLIS).toString();
        }
    }
}