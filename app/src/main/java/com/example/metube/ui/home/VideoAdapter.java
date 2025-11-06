package com.example.metube.ui.home;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide; // Import Glide
import com.example.metube.R;
import com.example.metube.model.Video;

import java.util.List;
import de.hdodenhof.circleimageview.CircleImageView;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

    private List<Video> videoList;
    private Context context; // Thêm context để sử dụng Glide

    public VideoAdapter(Context context, List<Video> videoList) {
        this.context = context;
        this.videoList = videoList;
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        Video currentVideo = videoList.get(position);
        holder.bind(currentVideo, context); // Truyền context vào
    }

    @Override
    public int getItemCount() {
        return videoList != null ? videoList.size() : 0;
    }

    // Thêm hàm này để cập nhật dữ liệu từ Activity
    public void setVideos(List<Video> videos) {
        this.videoList = videos;
        notifyDataSetChanged(); // Báo cho RecyclerView biết dữ liệu đã thay đổi
    }

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

        // SỬA LẠI HÀM BIND ĐỂ TẢI ẢNH TỪ URL
        public void bind(Video video, Context context) {
            title.setText(video.getTitle());
            // TODO: Bạn cần có thông tin tên kênh trong model Video hoặc lấy từ uploaderID
            info.setText(video.getUploaderID()); // Tạm thời hiển thị uploaderID

            // Dùng Glide để tải ảnh thumbnail từ URL
            Glide.with(context)
                    .load(video.getThumbnailURL())
                    .placeholder(R.color.light_green_background) // Ảnh hiển thị trong lúc tải
                    .error(R.drawable.logo_metube) // Ảnh hiển thị nếu tải lỗi
                    .into(thumbnail);

            // Tương tự, tải ảnh avatar
            // TODO: Bạn cần có URL avatar của user
            // Glide.with(context).load(video.getAvatarURL()).into(avatar);
        }
    }
}