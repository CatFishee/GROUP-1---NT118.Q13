package com.example.metube.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.example.metube.R;
import com.example.metube.model.ContentCreatorStat;
import com.example.metube.model.Video;
import com.example.metube.service.SubscriptionManager;
import com.example.metube.ui.upload.UploadActivity;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class CreatorFragment extends Fragment {
    private static final String TAG = "CreatorFragment";

    // UI Components
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout emptyState, contentState;
    private Button btnGetStarted;
    private TextView tvTotalSubscribers, tvTotalViews, tvTotalVideos, tvAvgViews;
    private TextView tvLikeRatio, tvEngagementRate;
    private TextView tvTopVideoTitle, tvTopVideoStats;
    private ImageView ivTopVideoThumbnail;
    private LineChart lineChart;
    private com.google.android.material.button.MaterialButtonToggleGroup toggleTimePeriod;
    private RecyclerView rvRecentVideos;
    private CreatorVideoAdapter videoAdapter;

    // Data
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private DatabaseReference realtimeDb;
    private SubscriptionManager subscriptionManager;
    private String currentUserId;
    private List<Video> videoList = new ArrayList<>();
    private int selectedTimePeriod = 30; // Default 30 days

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_creator, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        initFirebase();
        setupListeners();
        loadData();
    }

    private void initViews(View view) {
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        emptyState = view.findViewById(R.id.empty_state);
        contentState = view.findViewById(R.id.content_state);
        btnGetStarted = view.findViewById(R.id.btn_get_started);

        // Overview cards
        tvTotalSubscribers = view.findViewById(R.id.tv_total_subscribers);
        tvTotalViews = view.findViewById(R.id.tv_total_views);
        tvTotalVideos = view.findViewById(R.id.tv_total_videos);
        tvAvgViews = view.findViewById(R.id.tv_avg_views);

        // Engagement
        tvLikeRatio = view.findViewById(R.id.tv_like_ratio);
        tvEngagementRate = view.findViewById(R.id.tv_engagement_rate);

        // Top video
        tvTopVideoTitle = view.findViewById(R.id.tv_top_video_title);
        tvTopVideoStats = view.findViewById(R.id.tv_top_video_stats);
        ivTopVideoThumbnail = view.findViewById(R.id.iv_top_video_thumbnail);

        // Graph
        lineChart = view.findViewById(R.id.line_chart);
        toggleTimePeriod = view.findViewById(R.id.toggle_time_period);
        toggleTimePeriod.check(R.id.btn_30_days);

        // RecyclerView
        rvRecentVideos = view.findViewById(R.id.rv_recent_videos);
        videoAdapter = new CreatorVideoAdapter(videoList);
        rvRecentVideos.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvRecentVideos.setAdapter(videoAdapter);

        setupChart();
    }

    private void initFirebase() {
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        realtimeDb = FirebaseDatabase.getInstance().getReference();
        subscriptionManager = SubscriptionManager.getInstance();
        currentUserId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;

        Log.d(TAG, "=== FIREBASE INITIALIZED ===");
        Log.d(TAG, "Current User ID: " + currentUserId);
    }

    private void setupListeners() {
        btnGetStarted.setOnClickListener(v -> {
            if (isAdded()) {
                startActivity(new Intent(requireContext(), UploadActivity.class));
            }
        });

        swipeRefresh.setOnRefreshListener(this::loadData);

        toggleTimePeriod.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn_7_days) {
                    selectedTimePeriod = 7;
                } else if (checkedId == R.id.btn_30_days) {
                    selectedTimePeriod = 30;
                } else if (checkedId == R.id.btn_90_days) {
                    selectedTimePeriod = 90;
                }
                Log.d(TAG, "Time period changed to: " + selectedTimePeriod + " days");
                loadSubscriberGrowthData();
            }
        });
    }

    private void loadData() {
        if (currentUserId == null) {
            Log.e(TAG, "❌ Current user is null!");
            swipeRefresh.setRefreshing(false);
            return;
        }

        Log.d(TAG, "=== LOADING DATA FOR USER: " + currentUserId + " ===");
        swipeRefresh.setRefreshing(true);

        // Check if user has videos
        firestore.collection("videos")
                .whereEqualTo("uploaderID", currentUserId)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        Log.d(TAG, "No videos found - showing empty state");
                        showEmptyState();
                    } else {
                        Log.d(TAG, "Videos found - showing content state");
                        showContentState();
                        loadPhase1Data();
                        loadPhase2Data();
                        loadPhase3Data();
                    }
                    swipeRefresh.setRefreshing(false);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error checking for videos", e);
                    swipeRefresh.setRefreshing(false);
                });
    }

    private void showEmptyState() {
        emptyState.setVisibility(View.VISIBLE);
        contentState.setVisibility(View.GONE);
    }

    private void showContentState() {
        emptyState.setVisibility(View.GONE);
        contentState.setVisibility(View.VISIBLE);
    }

    // PHASE 1: Overview Cards (Subscribers, Video Count)
    private void loadPhase1Data() {
        Log.d(TAG, "=== PHASE 1: Loading Overview Cards ===");

        // Load subscriber count
        Log.d(TAG, "Querying subscriptions where uploaderID = " + currentUserId);
        subscriptionManager.getSubscriberCount(currentUserId, count -> {
            Log.d(TAG, "✅ Subscriber count received: " + count);
            animateValue(tvTotalSubscribers, 0, count, 800);
        });

        // Load video count
        Log.d(TAG, "Querying videos where uploaderID = " + currentUserId);
        firestore.collection("videos")
                .whereEqualTo("uploaderID", currentUserId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int videoCount = querySnapshot.size();
                    Log.d(TAG, "✅ Video count: " + videoCount);
                    animateValue(tvTotalVideos, 0, videoCount, 800);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to load video count", e);
                });
    }

    // PHASE 2: Graph Data
    private void loadPhase2Data() {
        Log.d(TAG, "=== PHASE 2: Loading Graph Data ===");
        loadSubscriberGrowthData();
    }

    // PHASE 3: Detailed Stats & Recent Videos
    private void loadPhase3Data() {
        Log.d(TAG, "=== PHASE 3: Loading Detailed Stats ===");
        loadVideosAndCalculateStats();
    }

    private void loadSubscriberGrowthData() {
        Log.d(TAG, "Loading subscriber growth for period: " + selectedTimePeriod + " days");

        // First, get current total subscribers
        subscriptionManager.getSubscriberCount(currentUserId, currentTotal -> {
            Log.d(TAG, "Current total subscribers: " + currentTotal);

            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, -selectedTimePeriod);
            Date startDate = cal.getTime();

            Log.d(TAG, "Querying contentCreatorStats from: " + startDate);

            firestore.collection("contentCreatorStats")
                    .whereEqualTo("userID", currentUserId)
                    .whereGreaterThanOrEqualTo("createdAt", new Timestamp(startDate))
                    .orderBy("createdAt", Query.Direction.ASCENDING)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        Log.d(TAG, "✅ Found " + querySnapshot.size() + " stat documents");

                        List<Entry> entries = new ArrayList<>();

                        // Calculate initial count by subtracting all changes from current total
                        long totalChange = 0;
                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            ContentCreatorStat stat = doc.toObject(ContentCreatorStat.class);
                            totalChange += (stat.getSubGained() - stat.getSubLost());
                            Log.d(TAG, "Stat: +" + stat.getSubGained() + " -" + stat.getSubLost());
                        }

                        long initialCount = currentTotal - totalChange;
                        long cumulativeCount = initialCount;
                        int index = 0;

                        Log.d(TAG, "Initial count: " + initialCount + " (Current: " + currentTotal + " - Changes: " + totalChange + ")");

                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            ContentCreatorStat stat = doc.toObject(ContentCreatorStat.class);
                            cumulativeCount += (stat.getSubGained() - stat.getSubLost());
                            entries.add(new Entry(index++, cumulativeCount));
                            Log.d(TAG, "Entry " + index + ": " + cumulativeCount);
                        }

                        updateChart(entries);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Failed to load subscriber stats", e);
                    });
        });
    }

    private void loadVideosAndCalculateStats() {
        Log.d(TAG, "Loading videos for stats calculation");

        firestore.collection("videos")
                .whereEqualTo("uploaderID", currentUserId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    videoList.clear();
                    if (querySnapshot.isEmpty()) {
                        Log.d(TAG, "No videos found for stats");
                        swipeRefresh.setRefreshing(false);
                        return;
                    }

                    Log.d(TAG, "✅ Found " + querySnapshot.size() + " videos");

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Video video = doc.toObject(Video.class);
                        video.setVideoID(doc.getId());
                        videoList.add(video);
                        Log.d(TAG, "Video: " + video.getTitle() + " (ID: " + video.getVideoID() + ")");
                    }
                    videoAdapter.notifyDataSetChanged();
                    fetchDetailedVideoStats();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error loading videos", e);
                    swipeRefresh.setRefreshing(false);
                });
    }

    private void fetchDetailedVideoStats() {
        Log.d(TAG, "Fetching detailed stats from Realtime DB for " + videoList.size() + " videos");

        AtomicInteger pendingRequests = new AtomicInteger(videoList.size());
        long[] totalViews = {0};
        long[] totalLikes = {0};
        long[] totalDislikes = {0};
        final Video[] topVideo = {null};
        long[] maxViews = {-1};

        for (Video video : videoList) {
            Log.d(TAG, "Querying videostat/" + video.getVideoID());

            realtimeDb.child("videostat").child(video.getVideoID())
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            long views = snapshot.child("viewCount").exists() ? snapshot.child("viewCount").getValue(Long.class) : 0;
                            long likes = snapshot.child("likes").getChildrenCount();
                            long dislikes = snapshot.child("dislikes").getChildrenCount();

                            Log.d(TAG, "Stats for " + video.getTitle() + ": " + views + " views, " + likes + " likes");

                            totalViews[0] += views;
                            totalLikes[0] += likes;
                            totalDislikes[0] += dislikes;

                            if (views > maxViews[0]) {
                                maxViews[0] = views;
                                topVideo[0] = video;
                            }

                            if (pendingRequests.decrementAndGet() == 0) {
                                Log.d(TAG, "=== ALL STATS LOADED ===");
                                Log.d(TAG, "Total views: " + totalViews[0]);
                                Log.d(TAG, "Total likes: " + totalLikes[0]);
                                Log.d(TAG, "Total dislikes: " + totalDislikes[0]);
                                updateEngagementUI(totalViews[0], totalLikes[0], totalDislikes[0], topVideo[0], maxViews[0]);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e(TAG, "❌ Error fetching video stat for " + video.getVideoID(), error.toException());
                            if (pendingRequests.decrementAndGet() == 0) {
                                updateEngagementUI(totalViews[0], totalLikes[0], totalDislikes[0], topVideo[0], maxViews[0]);
                            }
                        }
                    });
        }
    }

    private void updateEngagementUI(long totalViews, long totalLikes, long totalDislikes, Video topVideo, long topVideoViews) {
        Log.d(TAG, "Updating engagement UI");

        animateValue(tvTotalViews, 0, (int) totalViews, 800);

        int avgViews = videoList.isEmpty() ? 0 : (int) (totalViews / videoList.size());
        animateValue(tvAvgViews, 0, avgViews, 800);

        long totalReactions = totalLikes + totalDislikes;
        int likeRatio = totalReactions == 0 ? 0 : (int) ((totalLikes * 100.0) / totalReactions);
        tvLikeRatio.setText(String.format(Locale.getDefault(), "%d%%", likeRatio));

        int engagementRate = totalViews == 0 ? 0 : (int) ((totalReactions * 100.0) / totalViews);
        tvEngagementRate.setText(String.format(Locale.getDefault(), "%d%%", engagementRate));

        if (topVideo != null && isAdded()) {
            Log.d(TAG, "Top video: " + topVideo.getTitle() + " with " + topVideoViews + " views");
            tvTopVideoTitle.setText(topVideo.getTitle());

            realtimeDb.child("videostat").child(topVideo.getVideoID()).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    long topVideoLikes = snapshot.child("likes").getChildrenCount();
                    tvTopVideoStats.setText(String.format(Locale.getDefault(), "%s views • %s likes", formatNumber(topVideoViews), formatNumber(topVideoLikes)));
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            });

            Glide.with(this)
                    .load(topVideo.getThumbnailURL())
                    .placeholder(R.color.light_green_background)
                    .into(ivTopVideoThumbnail);
        }
    }

    private void setupChart() {
        lineChart.getDescription().setEnabled(false);
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(false);
        lineChart.setPinchZoom(false);
        lineChart.setDrawGridBackground(false);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        if (isAdded()) {
            xAxis.setTextColor(ContextCompat.getColor(requireContext(), R.color.app_main_color));
        }

        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        if (isAdded()) {
            leftAxis.setTextColor(ContextCompat.getColor(requireContext(), R.color.app_main_color));
        }
        leftAxis.setAxisMinimum(0f);

        lineChart.getAxisRight().setEnabled(false);
        lineChart.getLegend().setEnabled(false);
    }

    private void updateChart(List<Entry> entries) {
        if (!isAdded() || entries.isEmpty()) {
            Log.d(TAG, "Cannot update chart - fragment not added or no entries");
            lineChart.clear();
            lineChart.invalidate();
            return;
        }

        Log.d(TAG, "Updating chart with " + entries.size() + " entries");

        LineDataSet dataSet = new LineDataSet(entries, "Subscribers");
        dataSet.setColor(ContextCompat.getColor(requireContext(), R.color.app_main_color));
        dataSet.setCircleColor(ContextCompat.getColor(requireContext(), R.color.app_main_color));
        dataSet.setFillColor(ContextCompat.getColor(requireContext(), R.color.light_green_background));
        dataSet.setLineWidth(2.5f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawCircleHole(false);
        dataSet.setValueTextSize(0f);
        dataSet.setDrawFilled(true);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineData lineData = new LineData(dataSet);
        lineChart.setData(lineData);
        lineChart.animateX(1000);
        lineChart.invalidate();
    }

    private void animateValue(TextView textView, int start, int end, int duration) {
        Handler handler = new Handler(Looper.getMainLooper());
        long startTime = System.currentTimeMillis();

        handler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - startTime;
                float progress = Math.min(elapsed / (float) duration, 1f);
                int current = (int) (start + (end - start) * progress);
                textView.setText(formatNumber(current));

                if (progress < 1f) {
                    handler.postDelayed(this, 16);
                }
            }
        });
    }

    private String formatNumber(long number) {
        if (number < 1000) return String.valueOf(number);
        if (number < 1000000) return String.format(Locale.US, "%.1fK", number / 1000.0).replace(".0K", "K");
        return String.format(Locale.US, "%.1fM", number / 1000000.0).replace(".0M", "M");
    }

    // Inner Adapter Class for Recent Videos
    private static class CreatorVideoAdapter extends RecyclerView.Adapter<CreatorVideoAdapter.VideoViewHolder> {
        private final List<Video> videos;

        CreatorVideoAdapter(List<Video> videos) {
            this.videos = videos;
        }

        @NonNull
        @Override
        public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_creator_video, parent, false);
            return new VideoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
            Video video = videos.get(position);
            holder.bind(video);
        }

        @Override
        public int getItemCount() {
            return Math.min(videos.size(), 5);
        }

        static class VideoViewHolder extends RecyclerView.ViewHolder {
            ImageView thumbnail;
            TextView title, info;

            VideoViewHolder(@NonNull View itemView) {
                super(itemView);
                thumbnail = itemView.findViewById(R.id.video_thumbnail);
                title = itemView.findViewById(R.id.video_title);
                info = itemView.findViewById(R.id.video_info);
            }

            void bind(Video video) {
                title.setText(video.getTitle());
                Glide.with(itemView.getContext())
                        .load(video.getThumbnailURL())
                        .placeholder(R.color.light_green_background)
                        .into(thumbnail);

                FirebaseDatabase.getInstance().getReference("videostat").child(video.getVideoID())
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                long views = snapshot.child("viewCount").exists() ? snapshot.child("viewCount").getValue(Long.class) : 0;
                                String viewText = formatNumber(views) + " views";

                                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                                String dateString = sdf.format(video.getCreatedAt().toDate());
                                info.setText(String.format("%s • %s", viewText, dateString));
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                info.setText("0 views");
                            }
                        });
            }

            private String formatNumber(long number) {
                if (number < 1000) return String.valueOf(number);
                if (number < 1000000) return String.format(Locale.US, "%.1fK", number / 1000.0).replace(".0K", "K");
                return String.format(Locale.US, "%.1fM", number / 1000000.0).replace(".0M", "M");
            }
        }
    }
}