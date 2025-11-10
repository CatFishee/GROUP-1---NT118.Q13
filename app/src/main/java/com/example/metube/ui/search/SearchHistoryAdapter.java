package com.example.metube.ui.search;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.metube.R;
import java.util.List;
import com.example.metube.R;

import java.util.List;

// Tương tự như VideoAdapter, nhưng đơn giản hơn rất nhiều
public class SearchHistoryAdapter extends RecyclerView.Adapter<SearchHistoryAdapter.HistoryViewHolder> {
    // Interface để thông báo cho Activity khi một item được click
    public interface OnHistoryItemClickListener {
        void onHistoryItemClick(String query);
    }

    private final List<String> historyList;
    private final OnHistoryItemClickListener clickListener;

    // Constructor
    public SearchHistoryAdapter(List<String> historyList, OnHistoryItemClickListener clickListener) {
        this.historyList = historyList;
        this.clickListener = clickListener;
    }

    // --- HÀM SỐ 1: onCreateViewHolder (BẮT BUỘC) ---
    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_history, parent, false);
        return new HistoryViewHolder(view);
    }

    // --- HÀM SỐ 2: onBindViewHolder (BẮT BUỘC) ---
    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        String currentQuery = historyList.get(position);
        holder.bind(currentQuery, clickListener);
    }

    // --- HÀM SỐ 3: getItemCount (BẮT BUỘC) ---
    @Override
    public int getItemCount() {
        return historyList.size();
    }

    // ViewHolder
    public static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView tvQuery;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvQuery = itemView.findViewById(R.id.tv_history_query);
        }

        // Hàm bind để gắn dữ liệu và sự kiện click
        public void bind(final String query, final OnHistoryItemClickListener listener) {
            tvQuery.setText(query);
            itemView.setOnClickListener(v -> listener.onHistoryItemClick(query));
        }
    }
}
