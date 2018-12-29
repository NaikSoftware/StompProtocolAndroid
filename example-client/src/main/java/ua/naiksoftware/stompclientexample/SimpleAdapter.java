package ua.naiksoftware.stompclientexample;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Created by Naik on 24.02.17.
 */
public class SimpleAdapter extends RecyclerView.Adapter<SimpleAdapter.SimpleViewHolder> {

    private final List<String> mDataSet;

    public SimpleAdapter(List<String> dataSet) {
        mDataSet = dataSet;
    }

    @Override
    public SimpleViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new SimpleViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_layout, parent, false));
    }

    @Override
    public void onBindViewHolder(SimpleViewHolder holder, int position) {
        holder.mTextView.setText(mDataSet.get(position));
    }

    @Override
    public int getItemCount() {
        return mDataSet.size();
    }

    @Override
    public long getItemId(int position) {
        return mDataSet.get(position).hashCode();
    }

    static class SimpleViewHolder extends RecyclerView.ViewHolder {

        final TextView mTextView;

        public SimpleViewHolder(View itemView) {
            super(itemView);
            mTextView = (TextView) itemView.findViewById(R.id.text);
        }
    }
}
