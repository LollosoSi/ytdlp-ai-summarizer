package ciabbale.summariser.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import ciabbale.summariser.database.Session;

public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.SessionViewHolder> {

    public interface OnSessionClickListener {
        void onSessionClick(Session session);
        void onSessionLongClick(Session session); // Nuovo listener
    }

    private List<Session> sessions = new ArrayList<>();
    private final OnSessionClickListener listener;

    public SessionAdapter(OnSessionClickListener listener) {
        this.listener = listener;
    }

    public void setSessions(List<Session> sessions) {
        this.sessions = sessions;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
        return new SessionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        Session session = sessions.get(position);
        holder.textTitle.setText(session.title);

        // Click normale: apre la sessione
        holder.itemView.setOnClickListener(v -> listener.onSessionClick(session));

        // Click prolungato: apre le opzioni
        holder.itemView.setOnLongClickListener(v -> {
            listener.onSessionLongClick(session);
            return true; // Ritorna true per indicare che il long click è stato gestito
        });
    }

    @Override
    public int getItemCount() { return sessions.size(); }

    static class SessionViewHolder extends RecyclerView.ViewHolder {
        TextView textTitle;
        SessionViewHolder(@NonNull View itemView) {
            super(itemView);
            textTitle = itemView.findViewById(android.R.id.text1);
        }
    }
}