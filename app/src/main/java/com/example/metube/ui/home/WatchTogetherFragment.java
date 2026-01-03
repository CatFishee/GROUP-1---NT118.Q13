package com.example.metube.ui.home;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
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
import com.example.metube.model.WatchTogetherSession;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

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
    private Button btnAddVideo;

    // Player & Guest Controls
    private PlayerView playerView;
    private LinearLayout layoutGuestControls;
    private TextView tvGuestTime;
    private TextView tvGuestSpeed;
    private SeekBar sbGuestProgress;

    // Queue
    private RecyclerView rvVideoQueue;
    private QueueAdapter queueAdapter;
    private List<QueueItem> currentQueue = new ArrayList<>();

    // --- Firebase ---
    private DatabaseReference roomsRef;
    private DatabaseReference currentSessionRef;
    private String currentSessionId;
    private String myUid;

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
        roomsRef = FirebaseDatabase.getInstance().getReference("sessions");
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        } else {
            myUid = "user_" + new Random().nextInt(10000);
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

        // 3. Setup Queue Adapter with Click Listeners
        rvVideoQueue.setLayoutManager(new LinearLayoutManager(getContext()));
        queueAdapter = new QueueAdapter();
        rvVideoQueue.setAdapter(queueAdapter);

        // IMPLEMENT NEW LISTENER
        queueAdapter.setOnQueueActionListener(new QueueAdapter.OnQueueActionListener() {
            @Override
            public void onDeleteClick(int position) {
                // Host deletes a video
                if (isHost && position < currentQueue.size()) {
                    String key = currentQueue.get(position).getKey();
                    if (key != null && currentSessionRef != null) {
                        currentSessionRef.child("queue").child(key).removeValue();
                    }
                }
            }

            @Override
            public void onItemClick(int position) {
                // Host plays a video
                if (isHost && position < currentQueue.size()) {
                    QueueItem item = currentQueue.get(position);
                    playQueueItem(item);
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
            btnAddVideo.setOnClickListener(v -> showAddVideoDialog());
        }

        return view;
    }

    // ==========================================
    // EXOPLAYER CONFIGURATION
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
                        if (isPlaying) startHeartbeat();
                        else stopHeartbeat();
                    } else {
                        if (isPlaying) startGuestUiUpdater();
                        else stopGuestUiUpdater();
                    }
                }

                @Override
                public void onPlaybackParametersChanged(@NonNull PlaybackParameters playbackParameters) {
                    if (isHost) pushSessionState();
                }

                @Override
                public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
                    if (isHost && reason == Player.DISCONTINUITY_REASON_SEEK) {
                        pushSessionState();
                    }
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    // Host: Auto-play next video when current ends
                    if (isHost && playbackState == Player.STATE_ENDED) {
                        playNextVideoInQueue();
                    }
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
        if (player != null) {
            player.release();
            player = null;
        }
        stopHeartbeat();
        stopGuestUiUpdater();
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
        tvRoomCodeDisplay.setText("Room: " + code);

        updateUserInterface(isHost);

        // 1. Register Participant
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
        if (isHost) {
            currentSessionRef.child("hostID").onDisconnect().removeValue();
        }

        listenToSessionChanges();
        listenToQueue();
    }

    private void leaveSession() {
        stopHeartbeat();
        stopGuestUiUpdater();
        if (currentSessionRef != null) {
            currentSessionRef.child("participants").child(myUid).removeValue();
        }
        layoutLobby.setVisibility(View.VISIBLE);
        layoutRoom.setVisibility(View.GONE);
        player.pause();
        currentSessionId = null;
        isHost = false;
        currentQueue.clear();
        queueAdapter.setQueueList(currentQueue);
    }

    // ==========================================
    // UI UPDATES
    // ==========================================

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

    private Runnable guestUiRunnable = new Runnable() {
        @Override
        public void run() {
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

    private void startGuestUiUpdater() {
        uiHandler.removeCallbacks(guestUiRunnable);
        guestUiRunnable.run();
    }

    private void stopGuestUiUpdater() {
        uiHandler.removeCallbacks(guestUiRunnable);
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
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

                // If Guest, sync with host.
                // NOTE: Host does NOT sync here, or they would overwrite their own actions.
                if (!isHost) {
                    syncPlayerWithSession(session);
                }
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

        long hostPos = session.getCurrentTimestamp();
        long localPos = player.getCurrentPosition();
        if (Math.abs(hostPos - localPos) > 2000) {
            player.seekTo(hostPos);
        }
    }

    private void pushSessionState() {
        if (!isHost || currentSessionRef == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("videoID", currentVideoUrl);
        updates.put("currentTimestamp", player.getCurrentPosition());
        updates.put("playbackSpeed", player.getPlaybackParameters().speed);

        WatchTogetherSession.PlaybackState state = player.isPlaying()
                ? WatchTogetherSession.PlaybackState.PLAYING
                : WatchTogetherSession.PlaybackState.PAUSED;
        updates.put("playbackState", state);

        currentSessionRef.updateChildren(updates);
    }

    private Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (isHost && player.isPlaying()) {
                pushSessionState();
                heartbeatHandler.postDelayed(this, 3000);
            }
        }
    };
    private void startHeartbeat() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable);
        heartbeatRunnable.run();
    }
    private void stopHeartbeat() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable);
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

        if (oldestUid != null && oldestUid.equals(myUid)) {
            currentSessionRef.child("hostID").setValue(myUid);
            currentSessionRef.child("hostID").onDisconnect().removeValue();
        }
    }

    // ==========================================
    // QUEUE LOGIC & PLAY HELPER
    // ==========================================

    private void showAddVideoDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Add Video");

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        final EditText etUrl = new EditText(getContext());
        etUrl.setHint("Video URL");
        layout.addView(etUrl);

        final EditText etTitle = new EditText(getContext());
        etTitle.setHint("Title (Optional)");
        layout.addView(etTitle);

        builder.setView(layout);
        builder.setPositiveButton("Add", (dialog, which) -> {
            String url = etUrl.getText().toString().trim();
            String title = etTitle.getText().toString().trim();
            if (!TextUtils.isEmpty(url)) {
                addToQueue(title, url);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void addToQueue(String title, String url) {
        if (currentSessionRef == null) return;

        // Auto-play improvement: If host, queue empty, and player idle -> Play directly
        boolean shouldAutoPlay = isHost && currentQueue.isEmpty() && (!player.isPlaying() && currentVideoUrl.isEmpty());

        if (shouldAutoPlay) {
            QueueItem item = new QueueItem(title.isEmpty() ? "Unknown" : title, url, myUid);
            playQueueItem(item);
        } else {
            String key = currentSessionRef.child("queue").push().getKey();
            QueueItem item = new QueueItem(title.isEmpty() ? "Unknown" : title, url, myUid);
            currentSessionRef.child("queue").child(key).setValue(item);
        }
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

    /**
     * CRITICAL FIX: Handles playing a specific item.
     * 1. Removes it from Queue DB.
     * 2. Plays it locally (Host's device).
     * 3. Updates Session DB (For guests).
     */
    private void playQueueItem(QueueItem item) {
        if (item == null) return;

        // 1. Remove from Queue
        if (item.getKey() != null) {
            currentSessionRef.child("queue").child(item.getKey()).removeValue();
        }

        // 2. Play Locally
        playVideo(item.getUrl());

        // 3. Update Remote Session
        Map<String, Object> updates = new HashMap<>();
        updates.put("videoID", item.getUrl());
        updates.put("videoTitle", item.getTitle());
        updates.put("currentTimestamp", 0);
        updates.put("playbackState", WatchTogetherSession.PlaybackState.PLAYING);
        updates.put("playbackSpeed", player.getPlaybackParameters().speed);

        currentSessionRef.updateChildren(updates);
    }

    private void playNextVideoInQueue() {
        if (currentQueue.isEmpty()) return;
        // Use the helper method to ensure consistency
        playQueueItem(currentQueue.get(0));
    }
}