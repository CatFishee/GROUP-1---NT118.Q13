package com.example.metube.ui.login;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.metube.R;
import com.example.metube.model.User;
import java.util.List;

public class AccountsAdapter extends RecyclerView.Adapter<AccountsAdapter.ViewHolder> {

    public interface OnAccountClickListener {
        void onAccountClick(User user);
    }

    private List<User> accounts;
    private OnAccountClickListener listener;

    public AccountsAdapter(List<User> accounts, OnAccountClickListener listener) {
        this.accounts = accounts;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_account_switch, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User user = accounts.get(position);
        holder.tvName.setText(user.getName()); // hoặc getFullName()

        // Bạn cần đảm bảo Model User có field Email
        holder.tvEmail.setText(user.getEmail());

        if (user.getProfileURL() != null) {
            Glide.with(holder.itemView.getContext()).load(user.getProfileURL()).into(holder.ivAvatar);
        }

        holder.itemView.setOnClickListener(v -> listener.onAccountClick(user));
    }

    @Override
    public int getItemCount() { return accounts.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName, tvEmail;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            tvName = itemView.findViewById(R.id.tv_name);
            tvEmail = itemView.findViewById(R.id.tv_email);
        }
    }
}