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
import com.google.firebase.firestore.DocumentSnapshot;
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
    private LineChart lineChartViews, lineChartVideos;
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

        // Graphs
        lineChartViews = view.findViewById(R.id.line_chart_views);
        lineChartVideos = view.findViewById(R.id.line_chart_videos);
        toggleTimePeriod = view.findViewById(R.id.toggle_time_period);
        toggleTimePeriod.check(R.id.btn_30_days);

        // RecyclerView
        rvRecentVideos = view.findViewById(R.id.rv_recent_videos);
        videoAdapter = new CreatorVideoAdapter(videoList);
        rvRecentVideos.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvRecentVideos.setAdapter(videoAdapter);

        setupCharts();
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
                loadGrowthData();
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
                        swipeRefresh.setRefreshing(false);
                    } else {
                        Log.d(TAG, "Videos found - showing content state");
                        showContentState();
                        loadPhase1Data();
                        loadPhase2Data();
                        loadPhase3Data();
                    }
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

    // PHASE 1: Create/Update Today's Stat & Load Overview Cards
    private void loadPhase1Data() {
        Log.d(TAG, "=== PHASE 1: Loading Overview & Creating Daily Stat ===");

        String todayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        Log.d(TAG, "Today's date key: " + todayKey);

        // Check if today's stat already exists
        firestore.collection("contentCreatorStats")
                .whereEqualTo("userID", currentUserId)
                .whereEqualTo("dateKey", todayKey)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        Log.d(TAG, "No stat for today - creating new one");
                        createTodaysStat(todayKey);
                    } else {
                        Log.d(TAG, "Today's stat exists - updating");
                        DocumentSnapshot doc = snapshot.getDocuments().get(0);
                        updateTodaysStat(doc.getId(), todayKey);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error checking today's stat", e);
                });

        // Load subscriber count separately
        subscriptionManager.getSubscriberCount(currentUserId, count -> {
            Log.d(TAG, "✅ Subscriber count received: " + count);
            animateValue(tvTotalSubscribers, 0, count, 800);
        });
    }

    private void createTodaysStat(String dateKey) {
        Log.d(TAG, "Creating new stat for " + dateKey);

        // Get all videos
        firestore.collection("videos")
                .whereEqualTo("uploaderID", currentUserId)
                .get()
                .addOnSuccessListener(videos -> {
                    int videoCount = videos.size();
                    Log.d(TAG, "Video count: " + videoCount);

                    if (videoCount == 0) {
                        saveStat(dateKey, 0, 0);
                        return;
                    }

                    // Fetch total views from Realtime DB
                    fetchTotalViewsAndSave(dateKey, videoCount, videos, true, null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error loading videos for stat", e);
                });
    }

    private void updateTodaysStat(String docId, String dateKey) {
        Log.d(TAG, "Updating existing stat: " + docId);

        // Get all videos
        firestore.collection("videos")
                .whereEqualTo("uploaderID", currentUserId)
                .get()
                .addOnSuccessListener(videos -> {
                    int videoCount = videos.size();
                    Log.d(TAG, "Video count: " + videoCount);

                    if (videoCount == 0) {
                        updateStat(docId, dateKey, 0, 0);
                        return;
                    }

                    // Fetch total views from Realtime DB
                    fetchTotalViewsAndSave(dateKey, videoCount, videos, false, docId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error loading videos for update", e);
                });
    }

    private void fetchTotalViewsAndSave(String dateKey, int videoCount,
                                        com.google.firebase.firestore.QuerySnapshot videos,
                                        boolean isCreate, String docId) {
        AtomicInteger pending = new AtomicInteger(videoCount);
        long[] totalViews = {0};

        for (QueryDocumentSnapshot doc : videos) {
            String videoId = doc.getId();

            realtimeDb.child("videostat").child(videoId).child("viewCount")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            long views = snapshot.exists() ? snapshot.getValue(Long.class) : 0;
                            totalViews[0] += views;

                            if (pending.decrementAndGet() == 0) {
                                Log.d(TAG, "Total views calculated: " + totalViews[0]);
                                if (isCreate) {
                                    saveStat(dateKey, videoCount, totalViews[0]);
                                } else {
                                    updateStat(docId, dateKey, videoCount, totalViews[0]);
                                }
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e(TAG, "❌ Error fetching view count", error.toException());
                            if (pending.decrementAndGet() == 0) {
                                if (isCreate) {
                                    saveStat(dateKey, videoCount, totalViews[0]);
                                } else {
                                    updateStat(docId, dateKey, videoCount, totalViews[0]);
                                }
                            }
                        }
                    });
        }
    }

    private void saveStat(String dateKey, long videos, long views) {
        ContentCreatorStat stat = new ContentCreatorStat(
                null,
                currentUserId,
                dateKey,
                videos,
                views,
                Timestamp.now()
        );

        firestore.collection("contentCreatorStats")
                .add(stat)
                .addOnSuccessListener(ref -> {
                    Log.d(TAG, "✅ Stat created: " + ref.getId());
                    displayOverviewStats(videos, views);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error creating stat", e);
                });
    }

    private void updateStat(String docId, String dateKey, long videos, long views) {
        ContentCreatorStat stat = new ContentCreatorStat(
                docId,
                currentUserId,
                dateKey,
                videos,
                views,
                Timestamp.now()
        );

        firestore.collection("contentCreatorStats")
                .document(docId)
                .set(stat)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Stat updated: " + docId);
                    displayOverviewStats(videos, views);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error updating stat", e);
                });
    }

    private void displayOverviewStats(long videos, long views) {
        animateValue(tvTotalVideos, 0, (int) videos, 800);
        animateValue(tvTotalViews, 0, (int) views, 800);

        int avgViews = videos == 0 ? 0 : (int) (views / videos);
        animateValue(tvAvgViews, 0, avgViews, 800);
    }

    // PHASE 2: Graph Data
    private void loadPhase2Data() {
        Log.d(TAG, "=== PHASE 2: Loading Graph Data ===");
        loadGrowthData();
    }

    // PHASE 3: Detailed Stats & Recent Videos
    private void loadPhase3Data() {
        Log.d(TAG, "=== PHASE 3: Loading Detailed Stats ===");
        loadVideosAndCalculateStats();
    }

    private void loadGrowthData() {
        Log.d(TAG, "Loading growth data for period: " + selectedTimePeriod + " days");

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -selectedTimePeriod);
        Date startDate = cal.getTime();

        firestore.collection("contentCreatorStats")
                .whereEqualTo("userID", currentUserId)
                .whereGreaterThanOrEqualTo("createdAt", new Timestamp(startDate))
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d(TAG, "✅ Found " + querySnapshot.size() + " stat documents");

                    List<Entry> viewEntries = new ArrayList<>();
                    List<Entry> videoEntries = new ArrayList<>();
                    int index = 0;

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        ContentCreatorStat stat = doc.toObject(ContentCreatorStat.class);
                        viewEntries.add(new Entry(index, stat.getTotalViews()));
                        videoEntries.add(new Entry(index, stat.getTotalVideos()));
                        index++;
                        Log.d(TAG, "Entry " + index + ": " + stat.getTotalViews() + " views, " + stat.getTotalVideos() + " videos");
                    }

                    updateChart(lineChartViews, viewEntries, "Views");
                    updateChart(lineChartVideos, videoEntries, "Videos");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to load growth data", e);
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
        long[] totalLikes = {0};
        long[] totalDislikes = {0};
        final Video[] topVideo = {null};
        long[] maxViews = {-1};

        for (Video video : videoList) {
            realtimeDb.child("videostat").child(video.getVideoID())
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            long views = snapshot.child("viewCount").exists() ? snapshot.child("viewCount").getValue(Long.class) : 0;
                            long likes = snapshot.child("likes").getChildrenCount();
                            long dislikes = snapshot.child("dislikes").getChildrenCount();

                            totalLikes[0] += likes;
                            totalDislikes[0] += dislikes;

                            if (views > maxViews[0]) {
                                maxViews[0] = views;
                                topVideo[0] = video;
                            }

                            if (pendingRequests.decrementAndGet() == 0) {
                                Log.d(TAG, "=== ALL STATS LOADED ===");
                                updateEngagementUI(totalLikes[0], totalDislikes[0], topVideo[0], maxViews[0]);
                                swipeRefresh.setRefreshing(false);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e(TAG, "❌ Error fetching video stat", error.toException());
                            if (pendingRequests.decrementAndGet() == 0) {
                                updateEngagementUI(totalLikes[0], totalDislikes[0], topVideo[0], maxViews[0]);
                                swipeRefresh.setRefreshing(false);
                            }
                        }
                    });
        }
    }

    private void updateEngagementUI(long totalLikes, long totalDislikes, Video topVideo, long topVideoViews) {
        Log.d(TAG, "Updating engagement UI");

        long totalReactions = totalLikes + totalDislikes;
        int likeRatio = totalReactions == 0 ? 0 : (int) ((totalLikes * 100.0) / totalReactions);
        tvLikeRatio.setText(String.format(Locale.getDefault(), "%d%%", likeRatio));

        // Get current total views from UI
        String viewsText = tvTotalViews.getText().toString().replace("K", "000").replace("M", "000000").replace(",", "");
        long totalViews = 0;
        try {
            totalViews = Long.parseLong(viewsText);
        } catch (NumberFormatException e) {
            totalViews = 0;
        }

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

    private void setupCharts() {
        setupChart(lineChartViews);
        setupChart(lineChartVideos);
    }

    private void setupChart(LineChart chart) {
        // Lấy màu từ Resource
        int textColor = ContextCompat.getColor(requireContext(), R.color.chart_value_text);
        int gridColor = ContextCompat.getColor(requireContext(), R.color.divider_color);

        chart.setBackgroundColor(android.graphics.Color.TRANSPARENT); // ← Nền trong suốt
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setDrawGridBackground(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(textColor);
        xAxis.setGranularity(1f);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(gridColor);
        leftAxis.setTextColor(textColor);
        leftAxis.setAxisMinimum(0f);    // Bắt đầu tại 0


        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setEnabled(false);

        Log.d(TAG, "Setting up chart - textColor: #" + Integer.toHexString(textColor) +
                ", gridColor: #" + Integer.toHexString(gridColor));
    }

    private void updateChart(LineChart chart, List<Entry> entries, String label) {
        if (!isAdded() || entries.isEmpty()) {
            Log.d(TAG, "Cannot update chart - fragment not added or no entries");
            chart.clear();
            chart.invalidate();
            return;
        }

        // Lấy màu
        int textColor = ContextCompat.getColor(requireContext(), R.color.creator_text);
        int lineColor = ContextCompat.getColor(requireContext(), R.color.chart_line_color);
        int valueTextColor = ContextCompat.getColor(requireContext(), R.color.chart_value_text); // ← MÀU MỚI

        LineDataSet dataSet = new LineDataSet(entries, label);
        dataSet.setColor(lineColor);
        dataSet.setCircleColor(lineColor);
        dataSet.setLineWidth(3f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawCircleHole(false);

        // Thiết lập hiển thị giá trị với màu CỐ ĐỊNH
        dataSet.setDrawValues(true);
        dataSet.setValueTextColor(valueTextColor); // ← DÙNG MÀU #364335
        dataSet.setValueTextSize(10f);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);
        chart.animateX(1000);
        chart.invalidate();

        Log.d(TAG, "Chart updated - valueTextColor: #" + Integer.toHexString(valueTextColor));
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