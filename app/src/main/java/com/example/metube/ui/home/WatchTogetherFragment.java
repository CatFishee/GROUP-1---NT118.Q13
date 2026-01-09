package com.example.metube.ui.home;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.metube.R;
import com.example.metube.model.QueueItem;
import com.example.metube.model.Video;
import com.example.metube.model.WatchTogetherSession;
import com.example.metube.ui.watchtogether.ParticipantAdapter;
import com.example.metube.ui.watchtogether.QueueAdapter;
import com.example.metube.ui.watchtogether.VideoPickerAdapter;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class WatchTogetherFragment extends Fragment {

    // --- UI Components ---
    private View layoutLobby, layoutRoom;
    private EditText etRoomCode;
    private TextView tvRoomCodeDisplay;
    private TextView tvHostStatus;
    private Button btnAddVideo, btnShowUsers;

    // Player & Guest Controls
    private PlayerView playerView;
    private LinearLayout layoutGuestControls;
    private TextView tvGuestTime, tvGuestSpeed;
    private SeekBar sbGuestProgress;

    // Queue
    private RecyclerView rvVideoQueue;
    private QueueAdapter queueAdapter;
    private List<QueueItem> currentQueue = new ArrayList<>();

    // --- Firebase ---
    private DatabaseReference roomsRef;
    private DatabaseReference currentSessionRef;
    private FirebaseFirestore firestore; // For querying videos and users
    private String currentSessionId;
    private String myUid;
    private String myName = "Unknown"; // Store current user's name

    // --- Logic State ---
    private boolean isHost = false;
    private final Handler heartbeatHandler = new Handler(Looper.getMainLooper());
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    // --- Media ---
    private ExoPlayer player;
    private String currentVideoUrl = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_watch_together, container, false);

        // 1. Initialize Firebase
        firestore = FirebaseFirestore.getInstance();
        roomsRef = FirebaseDatabase.getInstance().getReference("sessions");

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            fetchMyProfile(); // Get my own name for the list
        } else {
            myUid = "anon_" + new Random().nextInt(1000);
            myName = "Anonymous";
        }

        // 2. Bind Views
        layoutLobby = view.findViewById(R.id.layoutLobby);
        layoutRoom = view.findViewById(R.id.layoutRoom);
        etRoomCode = view.findViewById(R.id.etRoomCode);
        tvRoomCodeDisplay = view.findViewById(R.id.tvRoomCodeDisplay);
        tvHostStatus = view.findViewById(R.id.tvHostStatus);

        playerView = view.findViewById(R.id.playerView);
        layoutGuestControls = view.findViewById(R.id.layoutGuestControls);
        tvGuestTime = view.findViewById(R.id.tvGuestTime);
        tvGuestSpeed = view.findViewById(R.id.tvGuestSpeed);
        sbGuestProgress = view.findViewById(R.id.sbGuestProgress);

        rvVideoQueue = view.findViewById(R.id.rvVideoQueue);
        btnAddVideo = view.findViewById(R.id.btnAddVideo);
        btnShowUsers = view.findViewById(R.id.btnShowUsers);

        // 3. Setup Queue Adapter
        rvVideoQueue.setLayoutManager(new LinearLayoutManager(getContext()));
        queueAdapter = new QueueAdapter();
        rvVideoQueue.setAdapter(queueAdapter);

        queueAdapter.setOnQueueActionListener(new QueueAdapter.OnQueueActionListener() {
            @Override
            public void onDeleteClick(int position) {
                if (isHost && position < currentQueue.size()) {
                    String key = currentQueue.get(position).getKey();
                    if (key != null && currentSessionRef != null) {
                        currentSessionRef.child("queue").child(key).removeValue();
                    }
                }
            }
            @Override
            public void onItemClick(int position) {
                if (isHost && position < currentQueue.size()) {
                    playQueueItem(currentQueue.get(position));
                }
            }
        });

        // 4. Initialize Player
        initializePlayer();

        // 5. Button Listeners
        view.findViewById(R.id.btnCreate).setOnClickListener(v -> createSession());
        view.findViewById(R.id.btnJoin).setOnClickListener(v -> joinSession(etRoomCode.getText().toString().trim()));
        view.findViewById(R.id.btnLeave).setOnClickListener(v -> leaveSession());

        if (btnAddVideo != null) {
            btnAddVideo.setOnClickListener(v -> showVideoPickerDialog()); // Changed to Video Picker
        }
        if (btnShowUsers != null) {
            btnShowUsers.setOnClickListener(v -> showUserListDialog());
        }

        return view;
    }

    // ==========================================
    // EXOPLAYER & EVENTS
    // ==========================================
    private void initializePlayer() {
        if (player == null) {
            player = new ExoPlayer.Builder(requireContext()).build();
            playerView.setPlayer(player);
            player.addListener(new Player.Listener() {
                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    if (isHost) {
                        pushSessionState();
                        if (isPlaying) startHeartbeat(); else stopHeartbeat();
                    } else {
                        if (isPlaying) startGuestUiUpdater(); else stopGuestUiUpdater();
                    }
                }
                @Override
                public void onPlaybackParametersChanged(@NonNull PlaybackParameters playbackParameters) {
                    if (isHost) pushSessionState();
                }
                @Override
                public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
                    if (isHost && reason == Player.DISCONTINUITY_REASON_SEEK) pushSessionState();
                }
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (isHost && playbackState == Player.STATE_ENDED) playNextVideoInQueue();
                }
            });
        }
    }

    private void playVideo(String url) {
        if (url == null || url.isEmpty()) return;
        if (!url.equals(currentVideoUrl)) {
            currentVideoUrl = url;
            MediaItem mediaItem = MediaItem.fromUri(url);
            player.setMediaItem(mediaItem);
            player.prepare();
            player.play();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Prevent disconnecting when simply rotating the screen
        if (getActivity() != null && getActivity().isChangingConfigurations()) {
            return;
        }

        // Check if we are actually in a session before trying to leave
        if (currentSessionRef != null) {
            leaveSession();
        }

        // Clean up player resources
        if (player != null) {
            player.release();
            player = null;
        }
    }

    // ==========================================
    // USER DATA FETCHING
    // ==========================================
    private void fetchMyProfile() {
        firestore.collection("users").document(myUid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Assuming field is "name" or "username"
                        String name = documentSnapshot.getString("name");
                        if (name == null) name = documentSnapshot.getString("username");
                        if (name != null) myName = name;
                    }
                });
    }

    // ==========================================
    // SESSION MANAGEMENT
    // ==========================================
    private void createSession() {
        String code = String.format("%08d", new Random().nextInt(100000000));
        roomsRef.child(code).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) createSession();
                else setupSession(code, true);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void joinSession(String code) {
        if (TextUtils.isEmpty(code) || code.length() != 8) return;
        roomsRef.child(code).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) setupSession(code, false);
                else Toast.makeText(getContext(), "Session not found", Toast.LENGTH_SHORT).show();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void setupSession(String code, boolean amIHost) {
        currentSessionId = code;
        isHost = amIHost;
        currentSessionRef = roomsRef.child(code);

        layoutLobby.setVisibility(View.GONE);
        layoutRoom.setVisibility(View.VISIBLE);
        tvRoomCodeDisplay.setText("Code: " + code);
        updateUserInterface(isHost);

        Map<String, Object> updates = new HashMap<>();
        updates.put("participants/" + myUid, ServerValue.TIMESTAMP);

        if (isHost) {
            updates.put("hostID", myUid);
            updates.put("sessionID", code);
            updates.put("playbackState", WatchTogetherSession.PlaybackState.PAUSED);
            updates.put("playbackSpeed", 1.0f);
            startHeartbeat();
        }
        currentSessionRef.updateChildren(updates);
        currentSessionRef.child("participants").child(myUid).onDisconnect().removeValue();
        if (isHost) currentSessionRef.child("hostID").onDisconnect().removeValue();

        listenToSessionChanges();
        listenToQueue();
    }

    private void leaveSession() {
        // 1. Stop local background tasks
        stopHeartbeat();
        stopGuestUiUpdater();

        // 2. Pause player
        if (player != null) {
            player.pause();
        }

        // 3. Database Cleanup
        if (currentSessionRef != null && myUid != null) {
            // We need a temporary reference because currentSessionRef gets nulled below
            DatabaseReference refToClean = currentSessionRef;
            String uidToRemove = myUid;
            boolean wasHost = isHost;

            refToClean.child("participants").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
//                    if (snapshot.exists() && snapshot.getChildrenCount() <= 1) {
//                        // CASE A: I am the last person (or list is empty).
//                        // Delete the ENTIRE session node.
//                        refToClean.removeValue();
//                    } else {
//                        // CASE B: Others are still here.
//                        // 1. Remove myself
//                        refToClean.child("participants").child(uidToRemove).removeValue();
//
//                        // 2. If I was host, remove hostId to trigger auto-promotion for others
//                        if (wasHost) {
//                            refToClean.child("hostId").removeValue();
//                        }
//                    }
                    if (snapshot.exists() && snapshot.getChildrenCount() <= 1) {
                        // Nếu là người cuối cùng -> Xóa luôn phòng
                        refToClean.removeValue();
                    } else {
                        // Nếu còn người khác -> Xóa mình ra
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("participants/" + uidToRemove, null); // Xóa khỏi danh sách

                        if (wasHost) {
                            // ✅ SỬA LỖI: Dùng đúng key "hostID" (chữ D hoa)
                            // Xóa hostID để các client khác phát hiện ra host bị mất
                            updates.put("hostID", null);
                        }

                        // Cập nhật 1 lần (Atomic update) để giảm độ trễ
                        refToClean.updateChildren(updates);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    // Fallback: just remove self if read fails
                    refToClean.child("participants").child(uidToRemove).removeValue();
                }
            });
        }

        // 4. Reset Local UI State
        resetLocalState();
    }

    private void resetLocalState() {
        layoutLobby.setVisibility(View.VISIBLE);
        layoutRoom.setVisibility(View.GONE);

        currentSessionRef = null;
        currentSessionId = null;
        isHost = false;
        currentVideoUrl = "";

        currentQueue.clear();
        queueAdapter.setQueueList(currentQueue);
    }

    private void updateUserInterface(boolean amIHost) {
        if (amIHost) {
            tvHostStatus.setText("Status: HOST");
            playerView.setUseController(true);
            layoutGuestControls.setVisibility(View.GONE);
            stopGuestUiUpdater();
        } else {
            tvHostStatus.setText("Status: GUEST");
            playerView.setUseController(false);
            layoutGuestControls.setVisibility(View.VISIBLE);
            startGuestUiUpdater();
        }
        queueAdapter.setHost(amIHost);
    }

    // ==========================================
    // SYNC LOGIC
    // ==========================================
    private void listenToSessionChanges() {
        currentSessionRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                WatchTogetherSession session = snapshot.getValue(WatchTogetherSession.class);
                if (session == null) return;

                if (session.getHostID() == null) {
                    handleHostMissing(session);
                    return;
                }

                boolean wasHost = isHost;
                isHost = myUid.equals(session.getHostID());
                if (wasHost != isHost) {
                    updateUserInterface(isHost);
                    if (isHost) {
                        Toast.makeText(getContext(), "You are now the Host", Toast.LENGTH_SHORT).show();
                        pushSessionState();
                    }
                }

                if (!isHost) syncPlayerWithSession(session);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void syncPlayerWithSession(WatchTogetherSession session) {
        playVideo(session.getVideoID());
        if (player.getPlaybackParameters().speed != session.getPlaybackSpeed()) {
            player.setPlaybackParameters(new PlaybackParameters(session.getPlaybackSpeed()));
        }
        tvGuestSpeed.setText(String.format(Locale.getDefault(), "%.1fx", session.getPlaybackSpeed()));

        boolean shouldPlay = (session.getPlaybackState() == WatchTogetherSession.PlaybackState.PLAYING);
        if (shouldPlay && !player.isPlaying()) player.play();
        else if (!shouldPlay && player.isPlaying()) player.pause();

        if (Math.abs(session.getCurrentTimestamp() - player.getCurrentPosition()) > 2000) {
            player.seekTo(session.getCurrentTimestamp());
        }
    }

    private void pushSessionState() {
        if (!isHost || currentSessionRef == null) return;
        Map<String, Object> updates = new HashMap<>();
        updates.put("videoID", currentVideoUrl);
        updates.put("currentTimestamp", player.getCurrentPosition());
        updates.put("playbackSpeed", player.getPlaybackParameters().speed);
        updates.put("playbackState", player.isPlaying() ? WatchTogetherSession.PlaybackState.PLAYING : WatchTogetherSession.PlaybackState.PAUSED);
        currentSessionRef.updateChildren(updates);
    }

    // ==========================================
    // DIALOG: VIDEO PICKER (Search & Select)
    // ==========================================
    private void showVideoPickerDialog() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());

        // Inflate the XML layout
        View sheetView = LayoutInflater.from(getContext()).inflate(R.layout.layout_video_picker, null);
        bottomSheetDialog.setContentView(sheetView);

        // Bind views from the XML
        EditText etSearch = sheetView.findViewById(R.id.etSearch);
        RecyclerView rvPicker = sheetView.findViewById(R.id.rvPicker);

        // Ensure RecyclerView has a LayoutManager
        rvPicker.setLayoutManager(new LinearLayoutManager(getContext()));

        // Initialize the logic
        setupPickerLogic(etSearch, rvPicker, bottomSheetDialog);

        bottomSheetDialog.show();
    }

    // Logic for the picker
    private void setupPickerLogic(EditText etSearch, RecyclerView rvPicker, Dialog dialog) {
        VideoPickerAdapter adapter = new VideoPickerAdapter(video -> {
            addToQueue(video);
            dialog.dismiss();
        });
        rvPicker.setAdapter(adapter);

        // Load initial videos (Recent)
        loadVideosFromFirestore(null, adapter);

        // Search Listener
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                loadVideosFromFirestore(s.toString(), adapter);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void loadVideosFromFirestore(String queryText, VideoPickerAdapter adapter) {
        Query query;
        if (TextUtils.isEmpty(queryText)) {
            // Load recent 20 videos
            query = firestore.collection("videos")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(20);
        } else {
            // Simple title search (case sensitive usually in firestore, keeping it simple)
            // Or use keywords if you populated them in lowercase
            query = firestore.collection("videos")
                    .whereArrayContains("searchKeywords", queryText.toLowerCase())
                    .limit(20);
        }

        query.get().addOnSuccessListener(snapshots -> {
            List<Video> videos = snapshots.toObjects(Video.class);
            adapter.setVideos(videos);
        });
    }

    // ==========================================
    // DIALOG: USER LIST
    // ==========================================
    private void showUserListDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16,16,16,16);

        TextView title = new TextView(getContext());
        title.setText("Participants");
        title.setTextSize(20);
        title.setPadding(0,0,0,20);
        layout.addView(title);

        RecyclerView rvUsers = new RecyclerView(getContext());
        rvUsers.setLayoutManager(new LinearLayoutManager(getContext()));
        ParticipantAdapter adapter = new ParticipantAdapter();
        rvUsers.setAdapter(adapter);
        layout.addView(rvUsers);

        dialog.setContentView(layout);
        dialog.show();

        // Load Users
        currentSessionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                WatchTogetherSession session = snapshot.getValue(WatchTogetherSession.class);
                if (session != null && session.getParticipants() != null) {
                    loadParticipantDetails(session, adapter);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadParticipantDetails(WatchTogetherSession session, ParticipantAdapter adapter) {
        List<ParticipantAdapter.Participant> list = new ArrayList<>();
        Map<String, Long> participants = session.getParticipants();
        String currentHostId = session.getHostID();

        if (participants == null) return;

        for (String uid : participants.keySet()) {
            // Create placeholder with default data
            ParticipantAdapter.Participant p = new ParticipantAdapter.Participant(
                    uid,
                    "Loading...",
                    null, // No URL yet
                    uid.equals(currentHostId)
            );
            list.add(p);

            // Fetch User Details from Firestore
            firestore.collection("users").document(uid).get().addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    // 1. Get Name
                    String name = doc.getString("name");
                    if (name == null) name = doc.getString("username");

                    // 2. Get Profile URL
                    String profileUrl = doc.getString("profileURL");

                    // 3. Update the Participant object
                    if (name != null) p.name = name;
                    p.profileUrl = profileUrl;

                    // 4. Refresh List
                    adapter.notifyDataSetChanged();
                }
            });
        }
        adapter.setParticipants(list);
    }

    // ==========================================
    // QUEUE LOGIC
    // ==========================================
    private void addToQueue(Video video) {
        if (currentSessionRef == null) return;

        // Auto-play if idle
        boolean shouldAutoPlay = isHost && currentQueue.isEmpty() && (!player.isPlaying() && currentVideoUrl.isEmpty());

        // Create Queue Item from Video Model
        QueueItem item = new QueueItem(video.getTitle(), video.getVideoURL(), myName);

        if (shouldAutoPlay) {
            playQueueItem(item);
        } else {
            String key = currentSessionRef.child("queue").push().getKey();
            currentSessionRef.child("queue").child(key).setValue(item);
        }
    }

    private void playQueueItem(QueueItem item) {
        if (item == null) return;
        if (item.getKey() != null) currentSessionRef.child("queue").child(item.getKey()).removeValue();
        playVideo(item.getUrl());

        Map<String, Object> updates = new HashMap<>();
        updates.put("videoID", item.getUrl());
        updates.put("videoTitle", item.getTitle());
        updates.put("currentTimestamp", 0);
        updates.put("playbackState", WatchTogetherSession.PlaybackState.PLAYING);
        updates.put("playbackSpeed", player.getPlaybackParameters().speed);
        currentSessionRef.updateChildren(updates);
    }

    // ... (Helpers: playNextVideoInQueue, listenToQueue, formatTime, Handlers etc. same as before)
    // Included purely to ensure no compile errors if you copy-paste, simplified here:

    private void playNextVideoInQueue() {
        if (currentQueue.isEmpty()) return;
        playQueueItem(currentQueue.get(0));
    }

    private void listenToQueue() {
        if (currentSessionRef == null) return;
        currentSessionRef.child("queue").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                currentQueue.clear();
                for (DataSnapshot s : snapshot.getChildren()) {
                    QueueItem item = s.getValue(QueueItem.class);
                    if (item != null) {
                        item.setKey(s.getKey());
                        currentQueue.add(item);
                    }
                }
                queueAdapter.setQueueList(currentQueue);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void handleHostMissing(WatchTogetherSession session) {
        if (session.getParticipants() == null) return;
        String oldestUid = null;
        long minTimestamp = Long.MAX_VALUE;
        for (Map.Entry<String, Long> entry : session.getParticipants().entrySet()) {
            if (entry.getValue() < minTimestamp) {
                minTimestamp = entry.getValue();
                oldestUid = entry.getKey();
            }
        }
//        if (oldestUid != null && oldestUid.equals(myUid)) {
//            currentSessionRef.child("hostID").setValue(myUid);
//            currentSessionRef.child("hostID").onDisconnect().removeValue();
//        }
        if (oldestUid != null && oldestUid.equals(myUid)) {
            // Set hostID mới
            currentSessionRef.child("hostID").setValue(myUid);

            // ✅ QUAN TRỌNG: Đăng ký lại onDisconnect cho Host mới
            // Để nếu Host mới này crash app, quyền host lại được chuyển tiếp
            currentSessionRef.child("hostID").onDisconnect().removeValue();

            Toast.makeText(getContext(), "You are now the Host", Toast.LENGTH_SHORT).show();
        }
    }

    // Helpers
    private void startHeartbeat() { heartbeatHandler.removeCallbacks(heartbeatRunnable); heartbeatRunnable.run(); }
    private void stopHeartbeat() { heartbeatHandler.removeCallbacks(heartbeatRunnable); }
    private Runnable heartbeatRunnable = new Runnable() {
        @Override public void run() { if (isHost && player.isPlaying()) { pushSessionState(); heartbeatHandler.postDelayed(this, 3000); } }
    };
    private void startGuestUiUpdater() { uiHandler.removeCallbacks(guestUiRunnable); guestUiRunnable.run(); }
    private void stopGuestUiUpdater() { uiHandler.removeCallbacks(guestUiRunnable); }
    private Runnable guestUiRunnable = new Runnable() {
        @Override public void run() {
            if (!isHost && player != null) {
                long current = player.getCurrentPosition();
                long duration = player.getDuration();
                if (duration > 0) {
                    int progress = (int) ((current * 100) / duration);
                    sbGuestProgress.setProgress(progress);
                    tvGuestTime.setText(formatTime(current) + " / " + formatTime(duration));
                }
                uiHandler.postDelayed(this, 1000);
            }
        }
    };
    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }
}