package ciabbale.summariser.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ciabbale.summariser.R;
import io.noties.markwon.Markwon;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_USER = 0;
    private static final int TYPE_AI = 1;

    private final List<ChatMessage> messages = new ArrayList<>();
    private final Context context;
    private final Markwon markwon;

    public ChatAdapter(Context context) {
        this.context = context;
        this.markwon = Markwon.create(context);
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    // Usato durante la digitazione in tempo reale
    public void updateMessage(int position, String newText) {
        messages.get(position).text = newText;
        notifyItemChanged(position, "STREAM");
    }

    // NUOVO METODO: Usato per sigillare il messaggio e renderizzarlo perfettamente
    public void finalizeMessage(int position, String newText) {
        messages.get(position).text = newText;
        notifyItemChanged(position, "FINAL");
    }

    public void clearMessages() {
        messages.clear();
        notifyDataSetChanged();
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isUser ? TYPE_USER : TYPE_AI;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_USER) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_chat_user, parent, false);
            return new UserViewHolder(view, markwon);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_chat_ai, parent, false);
            return new AiViewHolder(view, markwon);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains("STREAM") && holder instanceof AiViewHolder) {
            ((AiViewHolder) holder).animateStreamText(messages.get(position).text);
        } else if (payloads.contains("FINAL") && holder instanceof AiViewHolder) {
            // Se riceviamo il segnale "FINAL", forziamo il bind normale
            ((AiViewHolder) holder).bind(messages.get(position));
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).bind(message);
        } else if (holder instanceof AiViewHolder) {
            ((AiViewHolder) holder).bind(message);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView textMessage;
        Markwon markwon;

        UserViewHolder(@NonNull View itemView, Markwon markwon) {
            super(itemView);
            textMessage = itemView.findViewById(R.id.text_message_user);
            this.markwon = markwon;
        }

        void bind(ChatMessage message) {
            markwon.setMarkdown(textMessage, message.text);
        }
    }

    static class AiViewHolder extends RecyclerView.ViewHolder {
        TextView textMessage;
        ValueAnimator currentAnimator;
        Markwon markwon;

        // Non usiamo più il testo grezzo, ma la LUNGHEZZA del testo renderizzato
        int lastParsedLength = 0;

        AiViewHolder(@NonNull View itemView, Markwon markwon) {
            super(itemView);
            textMessage = itemView.findViewById(R.id.text_message_ai);
            this.markwon = markwon;
        }

        // Questo metodo si occupa dei messaggi completi (o caricati dalla cronologia)
        void bind(ChatMessage message) {
            if (currentAnimator != null) currentAnimator.cancel();

            CharSequence parsedText = markwon.toMarkdown(message.text);
            lastParsedLength = parsedText.length();
            textMessage.setText(parsedText);
        }

        // Questo metodo fa la magia: Markdown IN TEMPO REALE + Fade In
        void animateStreamText(String newText) {
            // Trasformiamo subito il pezzetto arrivato in Markdown visivo
            CharSequence parsedText = markwon.toMarkdown(newText);
            SpannableString spannable = new SpannableString(parsedText);

            int currentLength = spannable.length();

            // Il Markdown a volte "accorcia" il testo (es. **ciao** diventa ciao).
            // Procediamo con l'animazione solo se ci sono davvero caratteri visivi in più.
            if (currentLength > lastParsedLength) {
                int start = lastParsedLength;
                int end = currentLength;

                lastParsedLength = currentLength;

                if (currentAnimator != null) {
                    currentAnimator.cancel();
                }

                int baseColor = textMessage.getCurrentTextColor();
                int r = Color.red(baseColor);
                int g = Color.green(baseColor);
                int b = Color.blue(baseColor);

                currentAnimator = ValueAnimator.ofInt(0, 255);
                currentAnimator.setDuration(400);

                final ForegroundColorSpan[] activeSpan = {null};

                currentAnimator.addUpdateListener(animation -> {
                    int alpha = (int) animation.getAnimatedValue();

                    if (activeSpan[0] != null) {
                        spannable.removeSpan(activeSpan[0]);
                    }

                    int animatedColor = Color.argb(alpha, r, g, b);
                    activeSpan[0] = new ForegroundColorSpan(animatedColor);

                    try {
                        spannable.setSpan(activeSpan[0], start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } catch (IndexOutOfBoundsException e) {
                        // Sicurezza: se il parser Markdown "salta", ignoriamo l'errore grafico
                    }

                    textMessage.setText(spannable);
                });

                currentAnimator.start();
            } else {
                lastParsedLength = currentLength;
                if (currentAnimator != null) currentAnimator.cancel();
                textMessage.setText(spannable);
            }
        }
    }
}