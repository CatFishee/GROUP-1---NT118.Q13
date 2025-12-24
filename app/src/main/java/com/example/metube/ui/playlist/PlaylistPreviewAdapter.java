package com.example.metube.ui.playlist;

import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.metube.R;
import com.example.metube.model.Playlist;
import com.google.firebase.firestore.FirebaseFirestore;

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

        // Xử lý hiển thị Visibility
        String visibility = p.getVisibility() != null ? p.getVisibility() : "Private";
        String prettyVisibility = visibility.substring(0, 1).toUpperCase() + visibility.substring(1).toLowerCase();
        holder.tvVisibility.setText(prettyVisibility);

        // Xử lý số lượng video
        int count = p.getVideoIds() != null ? p.getVideoIds().size() : 0;
        holder.tvCount.setText(count + " videos");

        // Reset ảnh về mặc định trước (để tránh lỗi hiển thị khi tái sử dụng view)
        resetStackColors(holder);
        holder.ivThumb.setImageResource(android.R.color.black);

        // 2. Kiểm tra xem playlist có video không
        if (p.getVideoIds() != null && !p.getVideoIds().isEmpty()) {
            String firstVideoId = p.getVideoIds().get(0);

            FirebaseFirestore.getInstance()
                    .collection("videos")
                    .document(firstVideoId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String thumbUrl = documentSnapshot.getString("thumbnailURL");
                            if (thumbUrl != null && !thumbUrl.isEmpty()) {

                                // --- DÙNG GLIDE ĐỂ LẤY BITMAP ---
                                Glide.with(holder.itemView.getContext())
                                        .asBitmap() // Quan trọng: Load dạng Bitmap
                                        .load(thumbUrl)
                                        .centerCrop()
                                        .into(new CustomTarget<Bitmap>() {
                                            @Override
                                            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                                                // A. Hiển thị ảnh
                                                holder.ivThumb.setImageBitmap(resource);

                                                // B. Dùng Palette trích xuất màu
                                                Palette.from(resource).generate(palette -> {
                                                    if (palette != null) {
                                                        // Lấy màu chủ đạo (Dominant) hoặc màu rực rỡ (Vibrant)
                                                        // Fallback là màu xám nếu không tìm ra
                                                        int defaultColor = ContextCompat.getColor(holder.itemView.getContext(), android.R.color.darker_gray);

                                                        // Bạn có thể thử getVibrantColor hoặc getMutedColor tùy gu
                                                        int color = palette.getDominantColor(defaultColor);

                                                        // C. Tô màu cho 2 cái Stack phía sau
                                                        applyColorToStack(holder.viewStack1, color);
//                                                        applyColorToStack(holder.viewStack2, color);
                                                    }
                                                });
                                            }

                                            @Override
                                            public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
                                                // Reset khi view bị tái sử dụng
                                                holder.ivThumb.setImageDrawable(placeholder);
                                                resetStackColors(holder);
                                            }
                                        });
                            }
                        }
                    });
        }
    }
    // Hàm tô màu cho View (giữ nguyên bo góc)
    private void applyColorToStack(View view, int color) {
        // Lấy background hiện tại (đang là file shape xml)
        android.graphics.drawable.Drawable background = view.getBackground();

        // Cần mutate() để không ảnh hưởng đến các item khác trong RecyclerView
        if (background instanceof GradientDrawable) {
            GradientDrawable shape = (GradientDrawable) background.mutate();
            shape.setColor(color);
        }
    }

    // Hàm reset màu về mặc định (Xám)
    private void resetStackColors(ViewHolder holder) {
        int defaultColor = ContextCompat.getColor(holder.itemView.getContext(), android.R.color.darker_gray);
        applyColorToStack(holder.viewStack1, defaultColor);
//        applyColorToStack(holder.viewStack2, defaultColor);
    }

    @Override public int getItemCount() { return list.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvVisibility, tvCount;
        ImageView ivThumb;
        View viewStack1, viewStack2;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvVisibility = itemView.findViewById(R.id.tv_visibility);
            tvCount = itemView.findViewById(R.id.tv_count);
            ivThumb = itemView.findViewById(R.id.iv_thumb);
            viewStack1 = itemView.findViewById(R.id.view_stack_1);
//            viewStack2 = itemView.findViewById(R.id.view_stack_2);
        }
    }
}