package com.example.metube.ui.login;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.metube.R;
import com.example.metube.model.User;
import com.example.metube.ui.login.LoginActivity;
import com.example.metube.utils.AccountUtil;
import com.google.firebase.auth.FirebaseAuth;
import java.util.ArrayList;
import java.util.List;

public class SwitchAccountDialog extends DialogFragment {

    private User currentUser;

    public SwitchAccountDialog(User currentUser) {
        this.currentUser = currentUser;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); // Làm trong suốt nền để bo góc
            getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        }
        return inflater.inflate(R.layout.dialog_switch_account, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        // Chỉnh kích thước Dialog (rộng 90% màn hình)
        if (getDialog() != null && getDialog().getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
            getDialog().getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. Setup Current User
        if (currentUser != null) {
            TextView tvName = view.findViewById(R.id.tv_current_name);
            TextView tvEmail = view.findViewById(R.id.tv_current_email);
            ImageView ivAvatar = view.findViewById(R.id.iv_current_avatar);

            tvName.setText(currentUser.getName());
            tvEmail.setText(currentUser.getEmail());
            if (currentUser.getProfileURL() != null) {
                Glide.with(this).load(currentUser.getProfileURL()).into(ivAvatar);
            }
        }

        // 2. Setup List Other Accounts
        List<User> allSaved = AccountUtil.getSavedAccounts(requireContext());
        List<User> otherAccounts = new ArrayList<>();

        if (currentUser != null) {
            for (User u : allSaved) {
                if (!u.getUserID().equals(currentUser.getUserID())) {
                    otherAccounts.add(u);
                }
            }
        }

        View labelOther = view.findViewById(R.id.tv_label_other);
        RecyclerView rv = view.findViewById(R.id.rv_accounts);

        if (!otherAccounts.isEmpty()) {
            labelOther.setVisibility(View.VISIBLE);
            rv.setVisibility(View.VISIBLE);
            rv.setLayoutManager(new LinearLayoutManager(getContext()));
            rv.setAdapter(new AccountsAdapter(otherAccounts, user -> {
                performSwitch();
            }));
        } else {
            labelOther.setVisibility(View.GONE);
            rv.setVisibility(View.GONE);
        }

        // 3. Buttons
        view.findViewById(R.id.btn_close).setOnClickListener(v -> dismiss());

        view.findViewById(R.id.btn_add_account).setOnClickListener(v -> {
            performSwitch(); // Null email -> Login mới hoàn toàn
        });

        view.findViewById(R.id.btn_sign_out).setOnClickListener(v -> {
            performSwitch();
        });
    }

    private void performSwitch() {
        FirebaseAuth.getInstance().signOut();
        // Để lần sau bấm nút Google nó hiện lại bảng chọn tài khoản, chứ không tự login acc cũ
        com.google.android.gms.auth.api.signin.GoogleSignInOptions gso =
                new com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                        com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN).build();
        com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(requireContext(), gso).signOut();

        // 3. Đăng xuất Facebook (Nếu có dùng)
        com.facebook.login.LoginManager.getInstance().logOut();

        // 4. Chuyển về màn hình Login
        Intent intent = new Intent(requireActivity(), LoginActivity.class);
        // Xóa hết Activity cũ để không back lại được
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}