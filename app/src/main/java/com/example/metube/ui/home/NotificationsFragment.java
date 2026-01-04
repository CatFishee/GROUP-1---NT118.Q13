package com.example.metube.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.metube.R;
import com.example.metube.model.Notification;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class NotificationsFragment extends Fragment {
    private static final String TAG = "NotificationsFragment";

    private View currentlySelectedButton = null;
    private LinearLayout filterContainer;
    private RecyclerView rvNotifications;
    private LinearLayout emptyStateLayout;

    private com.example.metube.ui.home.NotificationAdapter notificationAdapter;
    private List<Notification> notificationList = new ArrayList<>();

    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private String currentFilter = "All";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notifications, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        filterContainer = view.findViewById(R.id.filter_container);
        rvNotifications = view.findViewById(R.id.rv_notifications);
        emptyStateLayout = view.findViewById(R.id.empty_state_layout);

        setupRecyclerView();
        setupFilters();

        if (currentUser != null) {
            loadNotifications();
        }
    }

    private void setupRecyclerView() {
        notificationAdapter = new com.example.metube.ui.home.NotificationAdapter(requireContext(), notificationList);
        rvNotifications.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvNotifications.setAdapter(notificationAdapter);
    }

    private void setupFilters() {
        filterContainer.removeAllViews();

        Button btnAll = createFilterButton("All");
        Button btnMentions = createFilterButton("Mentions");

        filterContainer.addView(btnAll);
        filterContainer.addView(btnMentions);

        // Mặc định chọn nút "All"
        btnAll.setSelected(true);
        currentlySelectedButton = btnAll;
    }

    private Button createFilterButton(String text) {
        Button button = new Button(requireContext(), null, 0, R.style.Widget_App_TopicButton);
        button.setText(text);

        button.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.selector_topic_button_text));
        button.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.selector_topic_button_background));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMarginEnd(20);
        button.setLayoutParams(params);

        button.setOnClickListener(v -> {
            if (currentlySelectedButton != null) {
                currentlySelectedButton.setSelected(false);
            }
            v.setSelected(true);
            currentlySelectedButton = v;

            currentFilter = text;
            loadNotifications();
        });

        return button;
    }

    private void loadNotifications() {
        if (currentUser == null) {
            showEmptyState();
            return;
        }

        Log.d(TAG, "Loading notifications for user: " + currentUser.getUid());

        Query query = db.collection("notifications")
                .whereEqualTo("recipientID", currentUser.getUid())
                .orderBy("createdAt", Query.Direction.DESCENDING);

        // Lọc theo type nếu cần
        if (currentFilter.equals("Mentions")) {
            query = query.whereEqualTo("type", "MENTION");
        }

        query.addSnapshotListener((value, error) -> {
            if (error != null) {
                Log.e(TAG, "Error loading notifications", error);
                showEmptyState();
                return;
            }

            notificationList.clear();

            if (value != null && !value.isEmpty()) {
                for (QueryDocumentSnapshot doc : value) {
                    Notification notification = doc.toObject(Notification.class);
                    notificationList.add(notification);
                }

                Log.d(TAG, "Loaded " + notificationList.size() + " notifications");
                showNotifications();
            } else {
                Log.d(TAG, "No notifications found");
                showEmptyState();
            }
        });
    }

    private void showNotifications() {
        rvNotifications.setVisibility(View.VISIBLE);
        emptyStateLayout.setVisibility(View.GONE);
        notificationAdapter.setNotifications(notificationList);
    }

    private void showEmptyState() {
        rvNotifications.setVisibility(View.GONE);
        emptyStateLayout.setVisibility(View.VISIBLE);
    }
}