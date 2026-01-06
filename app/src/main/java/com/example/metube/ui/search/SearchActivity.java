package com.example.metube.ui.search;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.metube.R;
import com.example.metube.model.User;
import com.example.metube.model.Video;
import com.example.metube.ui.home.VideoAdapter;
import com.example.metube.ui.video.VideoActivity;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SearchActivity extends AppCompatActivity implements SearchHistoryAdapter.OnHistoryItemClickListener {

    private static final int REQUEST_CODE_VOICE = 102;
    private EditText etSearchInput;
    private ImageView ivClearText, ivVoiceSearch;
    private RecyclerView rvSearchHistory, rvSearchResults, rvUserResults;
    private View resultsContainer, divider;
    private TextView tvLabelUsers, tvLabelVideos;

    private SearchHistoryAdapter historyAdapter;
    private VideoAdapter videoResultsAdapter;
    private SearchUserAdapter userResultsAdapter;

    private List<String> searchHistoryList;
    private List<User> userResultsList = new ArrayList<>();
    private List<Video> videoResultsList = new ArrayList<>();

    private SharedPreferences sharedPreferences;
    private static final String HISTORY_PREFS = "SearchHistoryPrefs";
    private static final String HISTORY_KEY = "history_string";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        sharedPreferences = getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE);

        initViews();
        setupHistoryView();
        setupResultsView();
        setupSearchListener();

        loadSearchHistory();

        // Xử lý Voice query từ Homepage
        String voiceQuery = getIntent().getStringExtra("search_query");
        if (voiceQuery != null) {
            etSearchInput.setText(voiceQuery);
            performSearch(voiceQuery);
        }
    }

    private void initViews() {
        etSearchInput = findViewById(R.id.et_search_input);
        rvSearchHistory = findViewById(R.id.rv_search_history);
        ivClearText = findViewById(R.id.iv_clear_text);
        ivVoiceSearch = findViewById(R.id.iv_voice_search);

        // Các View mới trong NestedScrollView
        resultsContainer = findViewById(R.id.rv_search_results_container);
        rvUserResults = findViewById(R.id.rv_user_results);
        rvSearchResults = findViewById(R.id.rv_search_results);
        divider = findViewById(R.id.divider_search);
        tvLabelUsers = findViewById(R.id.tv_label_users);
        tvLabelVideos = findViewById(R.id.tv_label_videos);
    }

    private void setupHistoryView() {
        searchHistoryList = new ArrayList<>();
        historyAdapter = new SearchHistoryAdapter(searchHistoryList, this);
        rvSearchHistory.setLayoutManager(new LinearLayoutManager(this));
        rvSearchHistory.setAdapter(historyAdapter);
    }

    private void setupResultsView() {
        // 1. Setup Adapter cho Creator (User)
        userResultsAdapter = new SearchUserAdapter(userResultsList, user -> {
            Intent intent = new Intent(this, com.example.metube.ui.contentcreator.CreatorProfileActivity.class);
            intent.putExtra("creator_id", user.getUserID());
            startActivity(intent);
        });
        rvUserResults.setLayoutManager(new LinearLayoutManager(this));
        rvUserResults.setAdapter(userResultsAdapter);

        // 2. Setup Adapter cho Video
        videoResultsAdapter = new VideoAdapter(this, videoResultsList, new VideoAdapter.OnVideoClickListener() {
            @Override
            public void onVideoClick(Video video) {
                Intent intent = new Intent(SearchActivity.this, VideoActivity.class);
                intent.putExtra("video_id", video.getVideoID());
                startActivity(intent);
            }

            @Override
            public void onAvatarClick(String uploaderId) {
                Intent intent = new Intent(SearchActivity.this, com.example.metube.ui.contentcreator.CreatorProfileActivity.class);
                intent.putExtra("creator_id", uploaderId);
                startActivity(intent);
            }
        });
        rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        rvSearchResults.setAdapter(videoResultsAdapter);
    }

    private void setupSearchListener() {
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        ivClearText.setOnClickListener(v -> etSearchInput.setText(""));
        if (ivVoiceSearch != null) ivVoiceSearch.setOnClickListener(v -> startVoiceRecognition());

        etSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                ivClearText.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (s.length() == 0) {
                    resultsContainer.setVisibility(View.GONE);
                    rvSearchHistory.setVisibility(View.VISIBLE);
                    loadSearchHistory();
                }
            }
        });

        etSearchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String query = etSearchInput.getText().toString().trim();
                if (!query.isEmpty()) {
                    performSearch(query);
                    hideKeyboard();
                }
                return true;
            }
            return false;
        });
    }

    private void performSearch(String query) {
        saveSearchQuery(query);

        rvSearchHistory.setVisibility(View.GONE);
        resultsContainer.setVisibility(View.VISIBLE);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String queryLower = query.toLowerCase().trim();
        String[] words = queryLower.split("\\s+");
        List<String> queryKeywords = Arrays.stream(words)
                .filter(word -> !word.isEmpty())
                .collect(Collectors.toList());

        if (queryKeywords.isEmpty()) return;

        // --- TASK 1: TÌM KIẾM VIDEO ---
        db.collection("videos")
                .whereEqualTo("visibility", "Public")
                .whereArrayContainsAny("searchKeywords", queryKeywords)
                .get()
                .addOnSuccessListener(videoSnaps -> {
                    videoResultsList.clear();
                    for (QueryDocumentSnapshot doc : videoSnaps) {
                        videoResultsList.add(doc.toObject(Video.class));
                    }
                    videoResultsAdapter.setVideos(videoResultsList);
                    tvLabelVideos.setVisibility(videoResultsList.isEmpty() ? View.GONE : View.VISIBLE);
                    updateDividerVisibility();
                });

        // --- TASK 2: TÌM KIẾM CREATOR (USER) ---
        // Giả sử bảng users có trường searchKeywords (mảng lowercase các từ trong tên)
        db.collection("users")
                .whereArrayContainsAny("searchKeywords", queryKeywords)
                .get()
                .addOnSuccessListener(userSnaps -> {
                    userResultsList.clear();
                    for (QueryDocumentSnapshot doc : userSnaps) {
                        userResultsList.add(doc.toObject(User.class));
                    }
                    userResultsAdapter.setUsers(userResultsList);

                    boolean hasUsers = !userResultsList.isEmpty();
                    rvUserResults.setVisibility(hasUsers ? View.VISIBLE : View.GONE);
                    tvLabelUsers.setVisibility(hasUsers ? View.VISIBLE : View.GONE);
                    updateDividerVisibility();
                });
    }

    private void updateDividerVisibility() {
        // Hiện gạch ngang nếu có cả User và Video để phân tách
        if (tvLabelUsers.getVisibility() == View.VISIBLE && tvLabelVideos.getVisibility() == View.VISIBLE) {
            divider.setVisibility(View.VISIBLE);
        } else {
            divider.setVisibility(View.GONE);
        }
    }

    // --- CÁC HÀM TIỆN ÍCH GIỮ NGUYÊN ---
    private void saveSearchQuery(String query) {
        String history = sharedPreferences.getString(HISTORY_KEY, "");
        List<String> list = new ArrayList<>();
        if (!history.isEmpty()) list.addAll(Arrays.asList(history.split(",")));
        list.remove(query);
        list.add(0, query);
        if (list.size() > 10) list = list.subList(0, 10);
        sharedPreferences.edit().putString(HISTORY_KEY, String.join(",", list)).apply();
    }

    private void loadSearchHistory() {
        String history = sharedPreferences.getString(HISTORY_KEY, "");
        searchHistoryList.clear();
        if (!history.isEmpty()) {
            for (String item : history.split(",")) {
                if (!item.trim().isEmpty()) searchHistoryList.add(item);
            }
        }
        historyAdapter.notifyDataSetChanged();
    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...");
        try { startActivityForResult(intent, REQUEST_CODE_VOICE); }
        catch (Exception e) { Toast.makeText(this, "Voice search not supported", Toast.LENGTH_SHORT).show(); }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_VOICE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (result != null && !result.isEmpty()) {
                String query = result.get(0);
                etSearchInput.setText(query);
                performSearch(query);
            }
        }
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override protected void onResume() { super.onResume(); loadSearchHistory(); }
    @Override public void onHistoryItemClick(String query) { etSearchInput.setText(query); performSearch(query); hideKeyboard(); }
}