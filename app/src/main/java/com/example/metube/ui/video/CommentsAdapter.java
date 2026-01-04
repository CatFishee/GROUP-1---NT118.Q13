package com.example.metube.ui.video;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.metube.R;
import com.example.metube.model.Comment;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.List;
import de.hdodenhof.circleimageview.CircleImageView;

public class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.ViewHolder> {

    private List<Comment> commentList;
    private FirebaseFirestore firestore;

    public CommentsAdapter(List<Comment> commentList) {
        this.commentList = commentList;
        this.firestore = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Comment comment = commentList.get(position);

        // 1. Set Content
        holder.tvContent.setText(comment.getText());

        // 2. Set Time
        String timeAgo;
        if (comment.getCreatedAt() != null) {
            timeAgo = DateUtils.getRelativeTimeSpanString(comment.getCreatedAt().toDate().getTime()).toString();
        } else {
            timeAgo = "Just now";
        }

        // 3. User Info Logic (Cache & Fetch)
        if (comment.getAuthorName() != null) {
            // Data already fetched, use it
            holder.tvAuthor.setText(comment.getAuthorName() + " • " + timeAgo);
            if (comment.getAuthorAvatarUrl() != null && !comment.getAuthorAvatarUrl().isEmpty()) {
                Glide.with(holder.itemView).load(comment.getAuthorAvatarUrl()).into(holder.ivAvatar);
            }
        } else {
            // Data missing, fetch from Firestore
            holder.tvAuthor.setText("Loading... • " + timeAgo);

            firestore.collection("users").document(comment.getCommenterID()).get()
                    .addOnSuccessListener(snapshot -> {
                        if (snapshot.exists()) {
                            String name = snapshot.getString("name");
                            String url = snapshot.getString("profileURL");

                            // Save to model so we don't fetch again on scroll
                            comment.setAuthorName(name);
                            comment.setAuthorAvatarUrl(url);

                            // Update UI
                            holder.tvAuthor.setText(name + " • " + timeAgo);
                            if (url != null && !url.isEmpty()) {
                                Glide.with(holder.itemView).load(url).into(holder.ivAvatar);
                            }
                        }
                    });
        }
    }

    @Override
    public int getItemCount() { return commentList.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAuthor, tvContent;
        CircleImageView ivAvatar;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAuthor = itemView.findViewById(R.id.tv_comment_author);
            tvContent = itemView.findViewById(R.id.tv_comment_content);
            ivAvatar = itemView.findViewById(R.id.iv_comment_avatar);
        }
    }
}