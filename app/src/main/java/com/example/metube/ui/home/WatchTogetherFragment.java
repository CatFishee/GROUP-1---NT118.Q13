package com.example.metube.ui.home;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.metube.R;

// Firebase
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

// ExoPlayer Imports
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.ui.PlayerView;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class WatchTogetherFragment extends Fragment {

    // UI
    private View layoutLobby, layoutRoom;
    private EditText etRoomCode;
    private TextView tvRoomCodeDisplay;
    private PlayerView playerView;

    // Firebase
    private DatabaseReference dbRef;
    private DatabaseReference roomRef;
    private String currentRoomId;
    private String myUid;

    // Logic
    private boolean isHost = false;
    private Handler heartbeatHandler = new Handler(Looper.getMainLooper());

    // ExoPlayer
    private ExoPlayer player;
    private String currentVideoUrl = ""; // To track video changes

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_watch_together, container, false);

        // Init Firebase
        dbRef = FirebaseDatabase.getInstance().getReference("rooms");
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        } else {
            // Fallback for testing if not logged in
            myUid = "anon_" + new Random().nextInt(1000);
        }

        // Init UI
        layoutLobby = view.findViewById(R.id.layoutLobby);
        layoutRoom = view.findViewById(R.id.layoutRoom);
        etRoomCode = view.findViewById(R.id.etRoomCode);
        tvRoomCodeDisplay = view.findViewById(R.id.tvRoomCodeDisplay);
        playerView = view.findViewById(R.id.playerView);

        // Init Player
        initializePlayer();

        // Buttons
        view.findViewById(R.id.btnCreate).setOnClickListener(v -> createRoom());
        view.findViewById(R.id.btnJoin).setOnClickListener(v -> joinRoom(etRoomCode.getText().toString().trim()));
        view.findViewById(R.id.btnLeave).setOnClickListener(v -> leaveRoom());

        return view;
    }

    // ==========================================
    // EXOPLAYER SETUP
    // ==========================================

    private void initializePlayer() {
        if (player == null) {
            player = new ExoPlayer.Builder(requireContext()).build();
            playerView.setPlayer(player);

            // Add Listener to detect Host actions
            player.addListener(new Player.Listener() {
                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    if (isHost) {
                        pushStatusToFirebase();
                        if (isPlaying) startHeartbeat();
                        else stopHeartbeat();
                    }
                }

                @Override
                public void onPlaybackParametersChanged(@NonNull PlaybackParameters playbackParameters) {
                    if (isHost) pushStatusToFirebase();
                }

                @Override
                public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
                    // Sync Seek (Reason 1 is usually user seek)
                    if (isHost && reason == Player.DISCONTINUITY_REASON_SEEK) {
                        pushStatusToFirebase();
                    }
                }

                @Override
                public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                    // Host changed video (e.g., auto-play next)
                    if (isHost) pushStatusToFirebase();
                }
            });
        }
    }

    // Helper to load video
    private void playVideo(String url) {
        if (!url.equals(currentVideoUrl)) {
            currentVideoUrl = url;
            MediaItem mediaItem = MediaItem.fromUri(url);
            player.setMediaItem(mediaItem);
            player.prepare();
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
    }

    // ==========================================
    // LOBBY LOGIC (Same as before)
    // ==========================================

    private void createRoom() {
        String code = String.format("%08d", new Random().nextInt(100000000));
        dbRef.child(code).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) createRoom();
                else setupRoom(code, true);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void joinRoom(String code) {
        if (TextUtils.isEmpty(code) || code.length() != 8) return;
        dbRef.child(code).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) setupRoom(code, false);
                else Toast.makeText(getContext(), "Room not found", Toast.LENGTH_SHORT).show();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // ==========================================
    // ROOM LOGIC
    // ==========================================

    private void setupRoom(String code, boolean amIHost) {
        currentRoomId = code;
        isHost = amIHost;
        roomRef = dbRef.child(code);

        layoutLobby.setVisibility(View.GONE);
        layoutRoom.setVisibility(View.VISIBLE);
        tvRoomCodeDisplay.setText("Room: " + code);

        // Permissions: Hide controls if guest
        playerView.setUseController(isHost);

        // 1. Add User
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("joinedAt", ServerValue.TIMESTAMP);
        roomRef.child("users").child(myUid).setValue(userMap);
        roomRef.child("users").child(myUid).onDisconnect().removeValue();

        // 2. Host Setup
        if (isHost) {
            roomRef.child("hostId").setValue(myUid);
            roomRef.child("hostId").onDisconnect().removeValue();

            // Example: Load a default video if creating new room
            String defaultUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4";
            playVideo(defaultUrl);
            // We need to push the initial URL to status so guests see it
            pushStatusToFirebase();

            startHeartbeat();
        }

        // 3. Listeners
        listenToStatus();
        listenToUsers();
    }

    private void leaveRoom() {
        stopHeartbeat();
        if (roomRef != null) roomRef.child("users").child(myUid).removeValue();
        layoutLobby.setVisibility(View.VISIBLE);
        layoutRoom.setVisibility(View.GONE);
        player.pause();
        currentRoomId = null;
    }

    // ==========================================
    // SYNC LOGIC (The "Watch Together" Part)
    // ==========================================

    private void listenToStatus() {
        roomRef.child("status").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                // If I am host, I ignore this (I am the source of truth)
                if (isHost) return;

                // Extract Data
                String url = snapshot.child("videoUrl").getValue(String.class);
                boolean hostPlaying = Boolean.TRUE.equals(snapshot.child("isPlaying").getValue(Boolean.class));
                long hostPos = snapshot.child("currentPosition").getValue(Long.class) != null ?
                        snapshot.child("currentPosition").getValue(Long.class) : 0;
                float hostSpeed = snapshot.child("speed").getValue(Float.class) != null ?
                        snapshot.child("speed").getValue(Float.class) : 1.0f;

                // 1. Sync Video URL
                if (url != null && !url.isEmpty()) {
                    playVideo(url);
                }

                // 2. Sync Speed
                if (player.getPlaybackParameters().speed != hostSpeed) {
                    player.setPlaybackParameters(new PlaybackParameters(hostSpeed));
                }

                // 3. Sync Play/Pause
                // setPlayWhenReady(true) = Play, setPlayWhenReady(false) = Pause
                if (hostPlaying != player.getPlayWhenReady()) {
                    player.setPlayWhenReady(hostPlaying);
                }

                // 4. Sync Position (2 Second Tolerance)
                long localPos = player.getCurrentPosition();
                long diff = Math.abs(hostPos - localPos);

                if (diff > 2000) {
                    player.seekTo(hostPos);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void pushStatusToFirebase() {
        if (!isHost || roomRef == null) return;

        Map<String, Object> updates = new HashMap<>();
        // Save URL so late joiners know what to play
        updates.put("videoUrl", currentVideoUrl);
        updates.put("isPlaying", player.getPlayWhenReady() && player.getPlaybackState() == Player.STATE_READY);
        updates.put("currentPosition", player.getCurrentPosition());
        updates.put("speed", player.getPlaybackParameters().speed);

        roomRef.child("status").updateChildren(updates);
    }

    // HOST HEARTBEAT (Every 3s)
    private Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (isHost && player.isPlaying()) {
                pushStatusToFirebase();
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

    // ==========================================
    // HOST TRANSFER (Auto-promote)
    // ==========================================
    private void listenToUsers() {
        roomRef.child("users").orderByChild("joinedAt").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                roomRef.child("hostId").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot hostSnapshot) {
                        if (!hostSnapshot.exists()) {
                            // Current host is missing, find oldest user
                            for (DataSnapshot user : snapshot.getChildren()) {
                                String candidateUid = user.getKey();
                                if (candidateUid != null && candidateUid.equals(myUid)) {
                                    becomeHost();
                                }
                                break; // First one is the oldest due to orderByChild
                            }
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void becomeHost() {
        isHost = true;
        roomRef.child("hostId").setValue(myUid);
        roomRef.child("hostId").onDisconnect().removeValue();

        Toast.makeText(getContext(), "You are now the Host", Toast.LENGTH_LONG).show();
        playerView.setUseController(true); // Enable controls
        pushStatusToFirebase(); // Sync immediately
    }
}