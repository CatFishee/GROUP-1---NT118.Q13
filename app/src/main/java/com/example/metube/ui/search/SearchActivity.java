package com.example.metube.ui.search;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.metube.R;
import com.example.metube.model.Video;
import com.example.metube.ui.home.VideoAdapter;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SearchActivity extends AppCompatActivity implements SearchHistoryAdapter.OnHistoryItemClickListener {

    private EditText etSearchInput;
    private RecyclerView rvSearchHistory, rvSearchResults;
    private SearchHistoryAdapter historyAdapter;
    private VideoAdapter resultsAdapter;
    private List<String> searchHistory;
    private SharedPreferences sharedPreferences;

    // Tên file để lưu trữ lịch sử
    private static final String HISTORY_PREFS = "SearchHistoryPrefs";
    private static final String HISTORY_KEY = "history";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        sharedPreferences = getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE);
        etSearchInput = findViewById(R.id.et_search_input);
        rvSearchHistory = findViewById(R.id.rv_search_history);
        rvSearchResults = findViewById(R.id.rv_search_results);

        Toolbar toolbar = findViewById(R.id.toolbar_search);
        toolbar.setNavigationOnClickListener(v -> finish());

        setupHistoryView();
        setupResultsView();

        setupSearchListener();
        loadSearchHistory();

    }

    private void setupSearchListener() {
        etSearchInput.setOnEditorActionListener((v, actionId, event) -> {
            // Kiểm tra xem hành động có phải là "Search" không
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                // Lấy text từ ô nhập liệu
                String query = etSearchInput.getText().toString().trim();
                if (!query.isEmpty()) {
                    // Thực hiện tìm kiếm
                    saveSearchQuery(query);
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

        // In ra log để kiểm tra
        Log.d("SearchActivity", "Searching for: " + query);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String lowercaseQuery = query.toLowerCase();

        db.collection("videos")
                .whereArrayContains("searchKeywords", lowercaseQuery) // SỬ DỤNG TRUY VẤN NÀY
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.d("SearchActivity", "Firestore query successful! Found " + queryDocumentSnapshots.size() + " results.");
                    List<Video> results = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        results.add(document.toObject(Video.class));
                    }
                    resultsAdapter.setVideos(results);
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
        searchHistory = new ArrayList<>();
        historyAdapter = new SearchHistoryAdapter(searchHistory, this);
        rvSearchHistory.setLayoutManager(new LinearLayoutManager(this));
        rvSearchHistory.setAdapter(historyAdapter);
    }
    private void setupResultsView() {
        resultsAdapter = new VideoAdapter(this, new ArrayList<>(), null);
        rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        rvSearchResults.setAdapter(resultsAdapter);
    }
    private void loadSearchHistory() {
        Set<String> historySet = sharedPreferences.getStringSet(HISTORY_KEY, new HashSet<>());
        searchHistory.clear();
        searchHistory.addAll(historySet);
        Collections.reverse(searchHistory); // Đảo ngược để từ mới nhất hiện lên trên
        historyAdapter.notifyDataSetChanged();
    }
    private void saveSearchQuery(String query) {
        // Lấy danh sách lịch sử hiện có
        Set<String> historySet = new HashSet<>(sharedPreferences.getStringSet(HISTORY_KEY, new HashSet<>()));

        // Thêm từ khóa mới
        historySet.add(query);

        // Lưu lại vào SharedPreferences
        sharedPreferences.edit().putStringSet(HISTORY_KEY, historySet).apply();

        loadSearchHistory();
    }

    @Override
    public void onHistoryItemClick(String query) {
        etSearchInput.setText(query);
        performSearch(query);
        hideKeyboard();
    }

}