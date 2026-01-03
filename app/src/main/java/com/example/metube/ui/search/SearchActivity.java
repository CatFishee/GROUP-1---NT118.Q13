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
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.metube.R;
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

    private static final int REQUEST_CODE_VOICE = 102; // Khai báo code cho Voice Search
    private EditText etSearchInput;
    private ImageView ivClearText, ivVoiceSearch;
    private RecyclerView rvSearchHistory, rvSearchResults;
    private SearchHistoryAdapter historyAdapter;
    private VideoAdapter resultsAdapter;
    private List<String> searchHistoryList;
    private SharedPreferences sharedPreferences;

    private static final String HISTORY_PREFS = "SearchHistoryPrefs";
    private static final String HISTORY_KEY = "history_string";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        sharedPreferences = getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE);

        // --- ÁNH XẠ VIEW ---
        etSearchInput = findViewById(R.id.et_search_input);
        rvSearchHistory = findViewById(R.id.rv_search_history);
        rvSearchResults = findViewById(R.id.rv_search_results);
        ivClearText = findViewById(R.id.iv_clear_text);
        ivVoiceSearch = findViewById(R.id.iv_voice_search); // Đảm bảo có ID này trong XML

        // --- GỌI CÁC HÀM SETUP ---
        setupHistoryView();
        setupResultsView();
        setupSearchListener();

        // Luôn load lịch sử khi mở
        loadSearchHistory();

        // Xử lý nếu có query truyền từ Voice Search ở Homepage sang
        String voiceQuery = getIntent().getStringExtra("search_query");
        if (voiceQuery != null) {
            etSearchInput.setText(voiceQuery);
            performSearch(voiceQuery);
        }
    }

    private void setupSearchListener() {
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        ivClearText.setOnClickListener(v -> etSearchInput.setText(""));

        // NÚT VOICE SEARCH TRONG THANH KIẾM
        if (ivVoiceSearch != null) {
            ivVoiceSearch.setOnClickListener(v -> startVoiceRecognition());
        }

        etSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                ivClearText.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);

                // Ẩn Mic khi đang gõ để tiết kiệm không gian
//                if (ivVoiceSearch != null) {
//                    ivVoiceSearch.setVisibility(s.length() > 0 ? View.GONE : View.VISIBLE);
//                }

                if (s.length() == 0) {
                    rvSearchResults.setVisibility(View.GONE);
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

    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...");
        try {
            startActivityForResult(intent, REQUEST_CODE_VOICE);
        } catch (Exception e) {
            Toast.makeText(this, "Voice search not supported", Toast.LENGTH_SHORT).show();
        }
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

    private void performSearch(String query) {
        saveSearchQuery(query); // Lưu lịch sử theo cách mới

        rvSearchHistory.setVisibility(View.GONE);
        rvSearchResults.setVisibility(View.VISIBLE);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String[] words = query.toLowerCase().split("\\s+");
        List<String> queryKeywords = Arrays.stream(words)
                .filter(word -> !word.isEmpty())
                .collect(Collectors.toList());

        if (queryKeywords.isEmpty()) return;

        db.collection("videos")
                .whereArrayContainsAny("searchKeywords", queryKeywords)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Video> results = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        results.add(document.toObject(Video.class));
                    }
                    resultsAdapter.setVideos(results);
                });
    }

    // --- FIX LỖI MẤT LỊCH SỬ VÀ SAI THỨ TỰ ---
    private void saveSearchQuery(String query) {
        String history = sharedPreferences.getString(HISTORY_KEY, "");
        List<String> list = new ArrayList<>();

        if (!history.isEmpty()) {
            list.addAll(new ArrayList<>(Arrays.asList(history.split(","))));
        }

        // Nếu từ khóa đã tồn tại, xóa đi để đưa lên đầu (như Youtube)
        list.remove(query);
        list.add(0, query);

        // Chỉ giữ 10 mục gần nhất
        if (list.size() > 10) {
            list = list.subList(0, 10);
        }

        // Chuyển List thành String ngăn cách bởi dấu phẩy
        String newHistory = String.join(",", list);
        sharedPreferences.edit().putString(HISTORY_KEY, newHistory).apply();
    }

    private void loadSearchHistory() {
        String history = sharedPreferences.getString(HISTORY_KEY, "");
        searchHistoryList.clear();
        if (!history.isEmpty()) {
            String[] items = history.split(",");
            for (String item : items) {
                if (!item.trim().isEmpty()) searchHistoryList.add(item);
            }
        }
        historyAdapter.notifyDataSetChanged();
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void setupHistoryView() {
        searchHistoryList = new ArrayList<>();
        historyAdapter = new SearchHistoryAdapter(searchHistoryList, this);
        rvSearchHistory.setLayoutManager(new LinearLayoutManager(this));
        rvSearchHistory.setAdapter(historyAdapter);
    }

    private void setupResultsView() {
        resultsAdapter = new VideoAdapter(this, new ArrayList<>(), video -> {
            if (video != null && video.getVideoID() != null) {
                Intent intent = new Intent(SearchActivity.this, VideoActivity.class);
                intent.putExtra("video_id", video.getVideoID());
                startActivity(intent);
            }
        });
        rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        rvSearchResults.setAdapter(resultsAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSearchHistory();
    }

    @Override
    public void onHistoryItemClick(String query) {
        etSearchInput.setText(query);
        performSearch(query);
        hideKeyboard();
    }
}