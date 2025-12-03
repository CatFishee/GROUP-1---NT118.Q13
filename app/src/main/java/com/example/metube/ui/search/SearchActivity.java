package com.example.metube.ui.search;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SearchActivity extends AppCompatActivity implements SearchHistoryAdapter.OnHistoryItemClickListener {

    private EditText etSearchInput;
    private ImageView ivClearText;
    private RecyclerView rvSearchHistory, rvSearchResults;
    private SearchHistoryAdapter historyAdapter;
    private VideoAdapter resultsAdapter;
    private List<String> searchHistoryList;
    private SharedPreferences sharedPreferences;

    // Tên file để lưu trữ lịch sử
    private static final String HISTORY_PREFS = "SearchHistoryPrefs";
    private static final String HISTORY_KEY = "history";

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

        // --- GỌI CÁC HÀM SETUP ---
        setupHistoryView();
        setupResultsView();
        setupSearchListener();
        loadSearchHistory();

    }
    private void setupSearchListener() {
        // Nút quay lại
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        // Nút Xóa
        ivClearText.setOnClickListener(v -> etSearchInput.setText(""));

        // Lắng nghe text thay đổi
        etSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                ivClearText.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);

                // QUAN TRỌNG: Nếu người dùng xóa hết chữ, hãy hiện lại lịch sử
                if (s.length() == 0) {
                    rvSearchResults.setVisibility(View.GONE);
                    rvSearchHistory.setVisibility(View.VISIBLE);
                }
            }
        });

        // Lắng nghe nút Search trên bàn phím
        etSearchInput.setOnEditorActionListener((v, actionId, event) -> {
            // Kiểm tra xem hành động có phải là "Search" không
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                // Lấy text từ ô nhập liệu
                String query = etSearchInput.getText().toString().trim();
                if (!query.isEmpty()) {
                    // Thực hiện tìm kiếm
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

        // Ẩn danh sách lịch sử và hiện danh sách kết quả
        rvSearchHistory.setVisibility(View.GONE);
        rvSearchResults.setVisibility(View.VISIBLE);

        Log.d("SearchActivity", "Searching for: " + query);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        // Chuyển toàn bộ query về chữ thường
        String lowercaseQuery = query.toLowerCase();

        // Tách chuỗi query thành một danh sách các từ khóa riêng lẻ
        String[] words = lowercaseQuery.split("\\s+");
        List<String> queryKeywords = Arrays.stream(words)
                .filter(word -> !word.isEmpty())
                .collect(Collectors.toList());

        // Nếu không có từ khóa hợp lệ, không cần tìm kiếm
        if (queryKeywords.isEmpty()) {
            resultsAdapter.setVideos(new ArrayList<>()); // Xóa kết quả cũ
            return;
        }
        db.collection("videos")
                .whereArrayContainsAny("searchKeywords", queryKeywords)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.d("SearchActivity", "Firestore query successful! Found " + queryDocumentSnapshots.size() + " results.");
                    List<Video> results = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        results.add(document.toObject(Video.class));
                    }

                    resultsAdapter.setVideos(results);
                })
                .addOnFailureListener(e -> {
                    Log.e("SearchActivity", "Firestore query failed", e);
                });
    }

    // Hàm để ẩn bàn phím
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
//        resultsAdapter = new VideoAdapter(this, new ArrayList<>(), null);
//        rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
//        rvSearchResults.setAdapter(resultsAdapter);
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
    private void loadSearchHistory() {
        Set<String> historySet = sharedPreferences.getStringSet(HISTORY_KEY, new HashSet<>());
        searchHistoryList.clear();
        searchHistoryList.addAll(historySet);
        Collections.reverse(searchHistoryList); // Đảo ngược để từ mới nhất hiện lên trên
        historyAdapter.notifyDataSetChanged();
    }
    private void saveSearchQuery(String query) {
        // Lấy danh sách lịch sử hiện có
        Set<String> historySet = new HashSet<>(sharedPreferences.getStringSet(HISTORY_KEY, new HashSet<>()));

        // Thêm từ khóa mới
        historySet.add(query);

        // Lưu lại vào SharedPreferences
        sharedPreferences.edit().putStringSet(HISTORY_KEY, historySet).apply();
    }

    @Override
    public void onHistoryItemClick(String query) {
        etSearchInput.setText(query);
        performSearch(query);
        hideKeyboard();
    }

}