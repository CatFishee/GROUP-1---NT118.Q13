package com.example.metube.ui.notifications;

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
import com.example.metube.model.Notification;
import com.example.metube.ui.video.VideoActivity;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.List;
import de.hdodenhof.circleimageview.CircleImageView;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    private List<Notification> notificationList;
    private Context context;

    public NotificationAdapter(Context context, List<Notification> notificationList) {
        this.context = context;
        this.notificationList = notificationList;
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        Notification notification = notificationList.get(position);
        holder.bind(notification, context);
    }

    @Override
    public int getItemCount() {
        return notificationList != null ? notificationList.size() : 0;
    }

    public void setNotifications(List<Notification> notifications) {
        this.notificationList = notifications;
        notifyDataSetChanged();
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {
        CircleImageView channelAvatar;
        ImageView videoThumbnail;
        TextView title, message, timeAgo;
        View unreadIndicator;

        public NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            channelAvatar = itemView.findViewById(R.id.channel_avatar);
            videoThumbnail = itemView.findViewById(R.id.video_thumbnail);
            title = itemView.findViewById(R.id.notification_title);
            message = itemView.findViewById(R.id.notification_message);
            timeAgo = itemView.findViewById(R.id.notification_time);
            unreadIndicator = itemView.findViewById(R.id.unread_indicator);
        }

        public void bind(Notification notification, Context context) {
            // Hiển thị title và message
            title.setText(notification.getTitle());
            message.setText(notification.getMessage());

            // Hiển thị thời gian
            if (notification.getCreatedAt() != null) {
                String time = android.text.format.DateUtils.getRelativeTimeSpanString(
                        notification.getCreatedAt().toDate().getTime(),
                        System.currentTimeMillis(),
                        android.text.format.DateUtils.MINUTE_IN_MILLIS
                ).toString();
                timeAgo.setText(time);
            }

            // Hiển thị unread indicator
            unreadIndicator.setVisibility(notification.isRead() ? View.GONE : View.VISIBLE);

            // Load avatar của channel
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(notification.getSenderID())
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            String avatarUrl = doc.getString("profileURL");
                            if (avatarUrl != null) {
                                Glide.with(context)
                                        .load(avatarUrl)
                                        .placeholder(R.drawable.ic_person)
                                        .into(channelAvatar);
                            }
                        }
                    });

            // Load thumbnail của video
            if (notification.getThumbnailURL() != null) {
                Glide.with(context)
                        .load(notification.getThumbnailURL())
                        .placeholder(R.color.light_green_background)
                        .into(videoThumbnail);
            }

            // Click vào notification → Mở video
            itemView.setOnClickListener(v -> {
                // Đánh dấu đã đọc
                markAsRead(notification);

                // Mở VideoActivity
                Intent intent = new Intent(context, VideoActivity.class);
                intent.putExtra("video_id", notification.getVideoID());
                context.startActivity(intent);
            });
        }
        private void handleNotificationClick(Notification notification, Context context) {
            String type = notification.getType();

            if ("NEW_SUBSCRIBER".equals(type)) {
                // ✅ Mở profile của subscriber
                if (notification.getSenderID() != null && !notification.getSenderID().isEmpty()) {
                    Intent intent = new Intent(context, com.example.metube.ui.contentcreator.CreatorProfileActivity.class);
                    intent.putExtra("creator_id", notification.getSenderID());
                    context.startActivity(intent);
                }
            } else if (notification.getVideoID() != null && !notification.getVideoID().isEmpty()) {
                // ✅ Mở video cho các loại thông báo khác (NEW_VIDEO, NEW_COMMENT, VIEW_MILESTONE)
                Intent intent = new Intent(context, VideoActivity.class);
                intent.putExtra("video_id", notification.getVideoID());
                context.startActivity(intent);
            }
        }

        private void markAsRead(Notification notification) {
            if (!notification.isRead()) {
                FirebaseFirestore.getInstance()
                        .collection("notifications")
                        .document(notification.getNotificationID())
                        .update("read", true)
                        .addOnSuccessListener(aVoid -> {
                            notification.setRead(true);
                            unreadIndicator.setVisibility(View.GONE);
                        });
            }
        }
    }
}