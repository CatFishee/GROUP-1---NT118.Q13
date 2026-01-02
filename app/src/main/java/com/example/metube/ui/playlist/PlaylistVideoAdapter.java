package com.example.metube.ui.playlist;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.metube.R;
import com.example.metube.model.Video;
import com.example.metube.ui.history.HistoryMenuBottomSheet;
import com.example.metube.utils.TimeUtil;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.Locale;

public class PlaylistVideoAdapter extends RecyclerView.Adapter<PlaylistVideoAdapter.ViewHolder> {

    private List<Video> videos;
    private OnPlaylistVideoActionListener listener;
    private String playlistTitle;

    public PlaylistVideoAdapter(List<Video> videos, OnPlaylistVideoActionListener listener) {
        this.videos = videos;
        this.listener = listener;
    }
    public interface OnPlaylistVideoActionListener {
        void onRemoveVideo(Video video, int position);
        void onShareVideo(Video video);
    }
    public void updatePlaylistTitle(String title) {
        this.playlistTitle = title;
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
        Context context = holder.itemView.getContext();

        holder.tvTitle.setText(video.getTitle());
        holder.tvDuration.setText(TimeUtil.formatDuration(video.getDuration()));

        String uploader = video.getUploaderName() != null ? video.getUploaderName() : "Unknown";
        holder.tvUploader.setText(uploader + " • " + video.getViewCount() + " views");

        Glide.with(holder.itemView.getContext())
                .load(video.getThumbnailURL())
                .placeholder(R.color.light_green_background)
                .centerCrop()
                .into(holder.ivThumbnail);
        // A. Lấy ngày đăng (Relative Time: "2 days ago")
        String timeAgo = "Just now";
        if (video.getCreatedAt() != null) {
            timeAgo = DateUtils.getRelativeTimeSpanString(
                    video.getCreatedAt().toDate().getTime(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS).toString();
        }
        // B. Lấy tên người đăng (Từ Firestore)
        String finalTimeAgo = timeAgo; // Biến final để dùng trong inner class

        FirebaseFirestore.getInstance().collection("users")
                .document(video.getUploaderID())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String uploaderName = "Unknown";
                    if (documentSnapshot.exists()) {
                        uploaderName = documentSnapshot.getString("name");
                    }

                    String finalUploaderName = uploaderName;

                    // C. Lấy số View thực tế (Từ Realtime Database)
                    FirebaseDatabase.getInstance().getReference("videostat")
                            .child(video.getVideoID())
                            .child("viewCount")
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    long views = 0;
                                    if (snapshot.exists()) {
                                        // Kiểm tra kiểu dữ liệu để tránh lỗi cast
                                        Object val = snapshot.getValue();
                                        if (val instanceof Long) views = (Long) val;
                                        else if (val instanceof Integer) views = ((Integer) val).longValue();
                                    }

                                    // D. GHÉP CHUỖI HOÀN CHỈNH
                                    // Format: "Giang Phương • 1.2K views • 2 days ago"
                                    String info = finalUploaderName + "\n" + formatViewCount(views) + " • " + finalTimeAgo;
                                    holder.tvUploader.setText(info);
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    // Fallback nếu lỗi mạng
                                    String info = finalUploaderName + " • 0 views • " + finalTimeAgo;
                                    holder.tvUploader.setText(info);
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    holder.tvUploader.setText("Unknown • 0 views • " + finalTimeAgo);
                });

        // Click mở video
        holder.itemView.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(holder.itemView.getContext(), com.example.metube.ui.video.VideoActivity.class);
            intent.putExtra("video_id", video.getVideoID());
            holder.itemView.getContext().startActivity(intent);
        });
        holder.btnMore.setOnClickListener(v -> {
            // 1. Ép kiểu Context sang AppCompatActivity để lấy FragmentManager
            AppCompatActivity activity = (AppCompatActivity) context;
            int currentPosition = holder.getAdapterPosition();

            // 2. Tạo BottomSheet
            // ID để xóa: Trong playlist ta dùng videoID làm key để xóa khỏi mảng
            String idToRemove = video.getVideoID();

            if (currentPosition == RecyclerView.NO_POSITION) return;

            HistoryMenuBottomSheet bottomSheet = new HistoryMenuBottomSheet(
                    video,
                    idToRemove,
                    currentPosition,  // ✅ Dùng vị trí thực tế
                    HistoryMenuBottomSheet.TYPE_PLAYLIST,
                    playlistTitle,
                    (HistoryMenuBottomSheet.HistoryMenuListener) activity
            );

            // 3. Hiển thị (Đây là đoạn thay thế cho dấu chấm chấm)
            bottomSheet.show(activity.getSupportFragmentManager(), "PlaylistMenu");
        });
    }
    // Hàm format số view (1000 -> 1K)
    private String formatViewCount(long count) {
        if (count < 1000) return count + " views";
        if (count < 1000000) return String.format(Locale.US, "%.1fK views", count / 1000.0);
        return String.format(Locale.US, "%.1fM views", count / 1000000.0);
    }


    @Override public int getItemCount() { return videos != null ? videos.size() : 0; }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumbnail, ivDragHandle, btnMore; // Drag Handle ở đây
        TextView tvTitle, tvUploader, tvDuration;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.iv_thumbnail);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvUploader = itemView.findViewById(R.id.tv_uploader);
            tvDuration = itemView.findViewById(R.id.tv_duration);

            // Nếu bạn muốn xử lý sự kiện kéo thả sau này thì ánh xạ nó, giờ để hiển thị thì không cần cũng được
            ivDragHandle = itemView.findViewById(R.id.iv_drag_handle);
            btnMore = itemView.findViewById(R.id.btn_more);
        }
    }
}