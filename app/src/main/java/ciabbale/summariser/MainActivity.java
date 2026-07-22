package ciabbale.summariser;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import ciabbale.summariser.database.AppDatabase;
import ciabbale.summariser.database.Session;
import ciabbale.summariser.database.SummaryFile;
import ciabbale.summariser.network.OllamaClient;
import ciabbale.summariser.ui.ChatAdapter;
import ciabbale.summariser.ui.ChatMessage;
import ciabbale.summariser.ui.FileAdapter;
import ciabbale.summariser.ui.SessionAdapter;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private Button btnNewChat, btnSettings, btnSetupTermux, btnDeleteAll;
    private RecyclerView chatRecyclerView, historyRecyclerView, filesRecyclerView;
    private EditText inputEditText;
    private ImageButton sendButton;

    private ChatAdapter chatAdapter;
    private SessionAdapter sessionAdapter;
    private FileAdapter fileAdapter;
    private AppDatabase db;
    private int currentSessionId = -1;

    private BroadcastReceiver serviceReceiver;
    private int summaryBubbleIndex = -1;
    private int loadingBubbleIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPermissionsAndSetup();

        drawerLayout = findViewById(R.id.drawer_layout);
        btnNewChat = findViewById(R.id.btn_new_chat);
        btnSettings = findViewById(R.id.btn_settings);
        btnSetupTermux = findViewById(R.id.btn_setup_termux);
        btnDeleteAll = findViewById(R.id.btn_delete_all);

        chatRecyclerView = findViewById(R.id.chat_recycler_view);
        inputEditText = findViewById(R.id.input_edit_text);
        sendButton = findViewById(R.id.send_button);

        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatAdapter = new ChatAdapter(this);
        chatRecyclerView.setAdapter(chatAdapter);
        chatRecyclerView.setItemAnimator(null);

        db = AppDatabase.getInstance(this);
        historyRecyclerView = findViewById(R.id.history_recycler_view);
        filesRecyclerView = findViewById(R.id.files_recycler_view);

        historyRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        sessionAdapter = new SessionAdapter(new SessionAdapter.OnSessionClickListener() {
            @Override
            public void onSessionClick(Session session) {
                currentSessionId = session.id;
                chatAdapter.clearMessages();
                loadSessionFiles(session.id);
                chatAdapter.addMessage(new ChatMessage("📂 Sessione caricata: " + session.title, false));
                drawerLayout.closeDrawer(GravityCompat.START);
            }

            @Override
            public void onSessionLongClick(Session session) {
                showSessionOptionsDialog(session);
            }
        });
        historyRecyclerView.setAdapter(sessionAdapter);

        filesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        fileAdapter = new FileAdapter(file -> {
            chatAdapter.addMessage(new ChatMessage("📄 File: " + file.fileName + "\n\n" + file.content, false));
            chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
            drawerLayout.closeDrawer(GravityCompat.END);
        });
        filesRecyclerView.setAdapter(fileAdapter);

        loadSessionsHistory();
        setupListeners();

        handleSharedIntent(getIntent());
        handleIntentForNotification(getIntent());
    }

    private void checkPermissionsAndSetup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            }
        } else {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, 100);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        if (checkSelfPermission("com.termux.permission.RUN_COMMAND") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"com.termux.permission.RUN_COMMAND"}, 102);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        SummariserService.updateListener = (type, text, newSessionId) -> {
            runOnUiThread(() -> {
                if ("progress".equals(type)) {
                    if (text != null && text.contains("Inizializzazione") || loadingBubbleIndex == -1) {
                        chatAdapter.addMessage(new ChatMessage(text, false));
                        loadingBubbleIndex = chatAdapter.getItemCount() - 1;
                        chatRecyclerView.smoothScrollToPosition(loadingBubbleIndex);
                    } else {
                        chatAdapter.updateMessage(loadingBubbleIndex, text);
                    }
                } else if ("chunk".equals(type)) {
                    if (summaryBubbleIndex == -1) {
                        chatAdapter.addMessage(new ChatMessage(text, false));
                        summaryBubbleIndex = chatAdapter.getItemCount() - 1;
                        chatRecyclerView.smoothScrollToPosition(summaryBubbleIndex);
                    } else {
                        chatAdapter.updateMessage(summaryBubbleIndex, text);
                    }
                } else if ("complete".equals(type)) {
                    currentSessionId = newSessionId;
                    if (summaryBubbleIndex != -1) {
                        // FIX: Inviamo il segnale di chiusura e formattazione!
                        chatAdapter.finalizeMessage(summaryBubbleIndex, text);
                    } else {
                        chatAdapter.addMessage(new ChatMessage(text, false));
                    }
                    summaryBubbleIndex = -1;
                    loadingBubbleIndex = -1;
                    loadSessionsHistory();
                    loadSessionFiles(currentSessionId);
                } else if ("error".equals(type)) {
                    chatAdapter.addMessage(new ChatMessage(text, false));
                }
            });
        };
    }

    @Override
    protected void onPause() {
        super.onPause();
        SummariserService.updateListener = null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleSharedIntent(intent);
        handleIntentForNotification(intent);
    }

    private void handleSharedIntent(Intent intent) {
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sharedText != null && (sharedText.contains("youtube.com") || sharedText.contains("youtu.be"))) {
                    drawerLayout.closeDrawers();
                    launchBackgroundService(sharedText);
                } else {
                    Toast.makeText(this, "Link non valido. Usa un link di YouTube.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void handleIntentForNotification(Intent intent) {
        if (intent != null && intent.hasExtra("open_session_id")) {
            int sessionIdToOpen = intent.getIntExtra("open_session_id", -1);
            if (sessionIdToOpen != -1) {
                currentSessionId = sessionIdToOpen;
                chatAdapter.clearMessages();
                loadSessionFiles(sessionIdToOpen);
                chatAdapter.addMessage(new ChatMessage("📂 Sessione caricata dal completamento in background.", false));
            }
        }
    }

    private void setupListeners() {
        sendButton.setOnClickListener(v -> {
            String userMessage = inputEditText.getText().toString().trim();
            if (!userMessage.isEmpty()) {
                chatAdapter.addMessage(new ChatMessage(userMessage, true));
                chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);

                if (userMessage.contains("youtube.com") || userMessage.contains("youtu.be")) {
                    launchBackgroundService(userMessage);
                } else {
                    sendMessageToOllama(userMessage);
                }
                inputEditText.setText("");
            }
        });

        btnNewChat.setOnClickListener(v -> {
            currentSessionId = -1;
            chatAdapter.clearMessages();
            chatAdapter.addMessage(new ChatMessage("👋 Nuova sessione avviata! Incolla un link o fai una domanda.", false));
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        btnSetupTermux.setOnClickListener(v -> showTermuxSetupDialog());

        btnDeleteAll.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Elimina Tutto")
                    .setMessage("Sei sicuro di voler eliminare TUTTE le sessioni e i file scaricati? L'azione è irreversibile.")
                    .setPositiveButton("Elimina", (dialog, which) -> {
                        new Thread(() -> {
                            db.summariserDao().deleteAllFiles();
                            db.summariserDao().deleteAllSessions();
                            runOnUiThread(() -> {
                                currentSessionId = -1;
                                chatAdapter.clearMessages();
                                loadSessionsHistory();
                                loadSessionFiles(-1);
                                drawerLayout.closeDrawer(GravityCompat.START);
                                Toast.makeText(MainActivity.this, "Tutte le sessioni eliminate", Toast.LENGTH_SHORT).show();
                            });
                        }).start();
                    })
                    .setNegativeButton("Annulla", null)
                    .show();
        });
    }

    private void showSessionOptionsDialog(Session session) {
        String[] options = {"Rinomina", "Elimina"};
        new AlertDialog.Builder(this)
                .setTitle("Opzioni: " + session.title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showRenameDialog(session);
                    } else if (which == 1) {
                        deleteSession(session);
                    }
                }).show();
    }

    private void showRenameDialog(Session session) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rinomina Sessione");

        final EditText input = new EditText(this);
        input.setText(session.title);
        builder.setView(input);

        builder.setPositiveButton("Salva", (dialog, which) -> {
            String newTitle = input.getText().toString().trim();
            if (!newTitle.isEmpty()) {
                new Thread(() -> {
                    db.summariserDao().updateSessionTitle(session.id, newTitle);
                    loadSessionsHistory();
                }).start();
            }
        });
        builder.setNegativeButton("Annulla", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void deleteSession(Session session) {
        new AlertDialog.Builder(this)
                .setTitle("Elimina Sessione")
                .setMessage("Eliminare '" + session.title + "' e i relativi file?")
                .setPositiveButton("Elimina", (dialog, which) -> {
                    new Thread(() -> {
                        db.summariserDao().deleteFilesForSession(session.id);
                        db.summariserDao().deleteSession(session.id);
                        runOnUiThread(() -> {
                            if (currentSessionId == session.id) {
                                currentSessionId = -1;
                                chatAdapter.clearMessages();
                                loadSessionFiles(-1);
                            }
                            loadSessionsHistory();
                            Toast.makeText(MainActivity.this, "Sessione eliminata", Toast.LENGTH_SHORT).show();
                        });
                    }).start();
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void showTermuxSetupDialog() {
        String command = "mkdir -p ~/.termux && echo \"allow-external-apps = true\" >> ~/.termux/termux.properties && termux-reload-settings";

        new AlertDialog.Builder(this)
                .setTitle("Sblocco Termux")
                .setMessage("Per permettere a Summariser di controllare yt-dlp, Termux deve accettare comandi esterni.\n\nPremi il tasto qui sotto per copiare il comando di sblocco e aprire Termux, poi incollalo e premi Invio.")
                .setPositiveButton("Copia e Apri", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Comando Termux", command);
                    clipboard.setPrimaryClip(clip);

                    Intent intent = getPackageManager().getLaunchIntentForPackage("com.termux");
                    if (intent != null) {
                        startActivity(intent);
                    } else {
                        Toast.makeText(this, "Termux non è installato!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Chiudi", null)
                .show();
    }

    private void launchBackgroundService(String url) {
        Intent serviceIntent = new Intent(this, SummariserService.class);
        serviceIntent.putExtra("url", url);
        serviceIntent.putExtra("sessionId", currentSessionId);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void sendMessageToOllama(String message) {
        chatAdapter.addMessage(new ChatMessage("✍️ Sto pensando...", false));
        int targetPosition = chatAdapter.getItemCount() - 1;
        chatRecyclerView.smoothScrollToPosition(targetPosition);

        OllamaClient ollamaClient = new OllamaClient(this);
        List<ChatMessage> history = chatAdapter.getMessages();

        ollamaClient.sendChatWithMemory(history, true, new OllamaClient.OllamaCallback() {
            @Override
            public void onChunk(String currentText) {
                runOnUiThread(() -> chatAdapter.updateMessage(targetPosition, currentText));
            }

            @Override
            public void onSuccess(String finalResponse) {
                // FIX: Anche la chat chiude e formatta il messaggio!
                runOnUiThread(() -> chatAdapter.finalizeMessage(targetPosition, finalResponse));
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> chatAdapter.updateMessage(targetPosition, "❌ Errore: " + errorMessage));
            }
        });
    }

    private void loadSessionsHistory() {
        new Thread(() -> {
            List<Session> sessions = db.summariserDao().getAllSessions();
            runOnUiThread(() -> sessionAdapter.setSessions(sessions));
        }).start();
    }

    private void loadSessionFiles(int sessionId) {
        new Thread(() -> {
            List<SummaryFile> files = db.summariserDao().getFilesForSession(sessionId);
            runOnUiThread(() -> fileAdapter.setFiles(files));
        }).start();
    }
}