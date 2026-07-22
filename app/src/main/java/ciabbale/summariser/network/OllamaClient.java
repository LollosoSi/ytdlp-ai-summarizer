package ciabbale.summariser.network;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import ciabbale.summariser.ui.ChatMessage;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OllamaClient {

    private static final String PREFS_NAME = "OllamaPrefs";
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final Context context;

    public interface OllamaCallback {
        void onChunk(String currentText); // Chiamato ad ogni nuova parola ricevuta
        void onSuccess(String finalResponse);
        void onError(String errorMessage);
    }

    public OllamaClient(Context context) {
        this.context = context;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .build();
    }

    // Aggiunto il parametro booleano "stream"
    // Abbiamo rimosso 'boolean isMacro' e aggiunto 'String model' e 'int numCtx'
    public void generateSummary(String prompt, String model, int numCtx, boolean stream, OllamaCallback callback) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String serverUrl = prefs.getString("server_url", "http://192.168.1.100:11434");
        String apiUrl = serverUrl.endsWith("/") ? serverUrl + "api/generate" : serverUrl + "/api/generate";

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", model);
            jsonBody.put("prompt", prompt);
            jsonBody.put("stream", stream);

            JSONObject options = new JSONObject();
            options.put("num_ctx", numCtx); // Usiamo il contesto dinamico!
            options.put("temperature", 0.3);
            jsonBody.put("options", options);

        } catch (JSONException e) {
            callback.onError("Errore JSON: " + e.getMessage());
            return;
        }

        RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
        Request request = new Request.Builder().url(apiUrl).post(body).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Errore di rete: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String err = response.body() != null ? response.body().string() : "";
                    callback.onError("Errore Server " + response.code() + ": " + err);
                    return;
                }

                if (stream) {
                    // --- LOGICA DI STREAMING ---
                    BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()));
                    String line;
                    StringBuilder fullText = new StringBuilder();

                    while ((line = reader.readLine()) != null) {
                        try {
                            JSONObject jsonChunk = new JSONObject(line);
                            String chunk = jsonChunk.optString("response", "");
                            fullText.append(chunk);

                            // Invia il testo parziale accumulato alla UI
                            callback.onChunk(fullText.toString());
                        } catch (JSONException e) {
                            // Ignora errori di parsing sui singoli chunk
                        }
                    }
                    callback.onSuccess(fullText.toString());

                } else {
                    // --- LOGICA STANDARD (Tutto insieme alla fine) ---
                    try {
                        JSONObject jsonResponse = new JSONObject(response.body().string());
                        callback.onSuccess(jsonResponse.getString("response"));
                    } catch (JSONException e) {
                        callback.onError("Errore parsing: " + e.getMessage());
                    }
                }
            }
        });
    }

    public void sendChatWithMemory(java.util.List<ChatMessage> conversationHistory, boolean stream, OllamaCallback callback) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String serverUrl = prefs.getString("server_url", "http://192.168.1.100:11434");
        // NOTA: Usiamo /api/chat invece di /api/generate
        String apiUrl = serverUrl.endsWith("/") ? serverUrl + "api/chat" : serverUrl + "/api/chat";

        String model = prefs.getString("model_macro", "qwen2.5:14b"); // Per la chat usiamo il modello avanzato

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", model);
            jsonBody.put("stream", stream);

            JSONArray messagesArray = new JSONArray();

            // Opzionale: Diamo un ruolo all'AI all'inizio
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", "Sei un assistente utile. Rispondi in italiano a meno che non ti venga richiesto altrimenti. Basati sui riassunti forniti in questa conversazione.");
            messagesArray.put(systemMsg);

            // Inseriamo gli ultimi messaggi per la memoria (limitiamo a 15 per non esplodere la context window)
            int historyLimit = 15;
            int startIdx = Math.max(0, conversationHistory.size() - historyLimit);

            for (int i = startIdx; i < conversationHistory.size(); i++) {
                ChatMessage msg = conversationHistory.get(i);

                // Saltiamo i messaggi di "caricamento" e le bolle temporanee dell'app
                if (msg.text.startsWith("⚙️") || msg.text.startsWith("⏳") || msg.text.startsWith("⬇️") || msg.text.startsWith("✍️") || msg.text.startsWith("👋")) {
                    continue;
                }

                JSONObject msgObj = new JSONObject();
                msgObj.put("role", msg.isUser ? "user" : "assistant");

                // Ripuliamo il testo da eventuali flag grafiche che abbiamo aggiunto
                String cleanText = msg.text.replace("✅ Elaborazione completata!\n\n", "");
                msgObj.put("content", cleanText);

                messagesArray.put(msgObj);
            }

            jsonBody.put("messages", messagesArray);

            JSONObject options = new JSONObject();
            options.put("num_ctx", prefs.getInt("context_window", 16384));
            options.put("temperature", 0.4);
            jsonBody.put("options", options);

        } catch (JSONException e) {
            callback.onError("Errore JSON: " + e.getMessage());
            return;
        }

        RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
        Request request = new Request.Builder().url(apiUrl).post(body).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Errore di rete: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String err = response.body() != null ? response.body().string() : "";
                    callback.onError("Errore Server " + response.code() + ": " + err);
                    return;
                }

                if (stream) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()));
                    String line;
                    StringBuilder fullText = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        try {
                            JSONObject jsonChunk = new JSONObject(line);
                            // NOTA: il formato di /api/chat è diverso. Il testo è dentro message -> content
                            if (jsonChunk.has("message")) {
                                String chunk = jsonChunk.getJSONObject("message").optString("content", "");
                                fullText.append(chunk);
                                callback.onChunk(fullText.toString());
                            }
                        } catch (JSONException ignored) {}
                    }
                    callback.onSuccess(fullText.toString());
                } else {
                    try {
                        JSONObject jsonResponse = new JSONObject(response.body().string());
                        callback.onSuccess(jsonResponse.getJSONObject("message").getString("content"));
                    } catch (JSONException e) {
                        callback.onError("Errore parsing: " + e.getMessage());
                    }
                }
            }
        });
    }
}