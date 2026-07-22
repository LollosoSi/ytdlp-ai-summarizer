package ciabbale.summariser;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SettingsActivity extends AppCompatActivity {

    private EditText editServerUrl, editModelSingle, editModelMacro;
    private EditText editPromptSingle, editPromptMacro, editPromptFinal;
    private Button btnSave, btnTest;

    private static final String PREFS_NAME = "OllamaPrefs";

    // Prompt di default
    private final String defaultSingle = "Summarize the following video transcript using clear bullet points. Write entirely in English. Transcript:\n";
    private final String defaultMacro = "Analyze the following sequential video summaries. Create a consolidated, well-structured summary of this batch in English. Batch:\n";
    private final String defaultFinal = "Analyze the following macro-summaries of a video playlist. Create the ultimate, comprehensive final summary of the entire content in English. Structure it professionally using Markdown headers and bullet points. Macro-Summaries:\n";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        editServerUrl = findViewById(R.id.et_server_url);
        editModelSingle = findViewById(R.id.et_model_single);
        editModelMacro = findViewById(R.id.et_model_macro);

        editPromptSingle = findViewById(R.id.edit_prompt_single);
        editPromptMacro = findViewById(R.id.edit_prompt_macro);
        editPromptFinal = findViewById(R.id.edit_prompt_final);

        btnSave = findViewById(R.id.btn_save_settings);
        btnTest = findViewById(R.id.btn_test_connection);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        editServerUrl.setText(prefs.getString("server_url", "http://192.168.1.100:11434"));
        editModelSingle.setText(prefs.getString("model_single", "llama3.1"));
        editModelMacro.setText(prefs.getString("model_macro", "qwen2.5:14b"));

        editPromptSingle.setText(prefs.getString("prompt_single", defaultSingle));
        editPromptMacro.setText(prefs.getString("prompt_macro", defaultMacro));
        editPromptFinal.setText(prefs.getString("prompt_final", defaultFinal));

        btnSave.setOnClickListener(v -> {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("server_url", editServerUrl.getText().toString().trim());
            editor.putString("model_single", editModelSingle.getText().toString().trim());
            editor.putString("model_macro", editModelMacro.getText().toString().trim());

            editor.putString("prompt_single", editPromptSingle.getText().toString().trim());
            editor.putString("prompt_macro", editPromptMacro.getText().toString().trim());
            editor.putString("prompt_final", editPromptFinal.getText().toString().trim());
            editor.apply();

            Toast.makeText(this, "Impostazioni salvate", Toast.LENGTH_SHORT).show();
            finish();
        });

        btnTest.setOnClickListener(v -> testConnection(editServerUrl.getText().toString().trim()));
    }

    private void testConnection(String baseUrl) {
        btnTest.setEnabled(false);
        btnTest.setText("Test in corso...");

        // Assicuriamoci che l'URL sia formattato bene per chiamare l'endpoint tags
        String testUrl = baseUrl.endsWith("/") ? baseUrl + "api/tags" : baseUrl + "/api/tags";

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(testUrl).get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(SettingsActivity.this, "Errore: Impossibile connettersi\n" + e.getMessage(), Toast.LENGTH_LONG).show();
                    resetTestButton();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(SettingsActivity.this, "✅ Connessione a Ollama OK!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(SettingsActivity.this, "❌ Errore Server: " + response.code(), Toast.LENGTH_LONG).show();
                    }
                    resetTestButton();
                });
            }
        });
    }

    private void resetTestButton() {
        btnTest.setEnabled(true);
        btnTest.setText("Test Connessione Ollama");
    }
}