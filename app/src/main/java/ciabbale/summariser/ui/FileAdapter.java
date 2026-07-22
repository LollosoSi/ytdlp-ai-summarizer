package ciabbale.summariser.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import ciabbale.summariser.R;
import ciabbale.summariser.database.SummaryFile;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

    private List<SummaryFile> files = new ArrayList<>();
    private final OnFileClickListener listener;

    public interface OnFileClickListener {
        void onFileClick(SummaryFile file);
    }

    public FileAdapter(OnFileClickListener listener) {
        this.listener = listener;
    }

    public void setFiles(List<SummaryFile> files) {
        this.files = files;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SummaryFile file = files.get(position);
        holder.nameText.setText(file.fileName);
        holder.itemView.setOnClickListener(v -> listener.onFileClick(file));
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView nameText;
        ViewHolder(View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.text_file_name);
        }
    }
}