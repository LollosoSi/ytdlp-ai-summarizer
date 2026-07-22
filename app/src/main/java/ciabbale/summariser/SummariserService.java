package ciabbale.summariser;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;

import ciabbale.summariser.logic.PipelineOrchestrator;
import ciabbale.summariser.utils.TermuxBridge;

public class SummariserService extends Service {

    public static final String ACTION_UPDATE = "ciabbale.summariser.UPDATE";
    private static final String CHANNEL_ID = "SummariserChannel";
    private PowerManager.WakeLock wakeLock;

    // L'interfaccia per la comunicazione diretta
    public interface UpdateListener {
        void onUpdate(String type, String text, int sessionId);
    }

    // Il "ponte" globale
    public static UpdateListener updateListener = null;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        // Mantieni sveglia la CPU anche a schermo spento
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Summariser::ProcessLock");
        wakeLock.acquire(30 * 60 * 1000L /*30 minuti max*/);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Avvia la notifica fissa (Foreground)
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Summariser")
                .setContentText("Elaborazione in background attiva...")
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .build();
        startForeground(1, notification);

        if (intent != null) {
            String url = intent.getStringExtra("url");
            int sessionId = intent.getIntExtra("sessionId", -1);
            if (url != null) {
                startBackgroundProcess(url, sessionId);
            }
        }
        return START_NOT_STICKY;
    }

    private void startBackgroundProcess(String url, int sessionId) {
        File workDir = new File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                "SummariserSubs"
        );

        if (!workDir.exists()) workDir.mkdirs();
        else for (File file : workDir.listFiles()) file.delete();

        // 1. Lancia Termux
        TermuxBridge.executeYtDlp(this, url, workDir.getAbsolutePath());
        broadcastUpdate("progress", "⏳ Inizializzazione yt-dlp in corso...", sessionId);

        // 2. Polling Semaforo
        final Handler handler = new Handler(Looper.getMainLooper());
        final File signalFile = new File(workDir, "yt_dlp_done.txt");
        final int[] secondsWaited = {0};

        Runnable checkRunnable = new Runnable() {
            @Override
            public void run() {
                if (signalFile.exists()) {
                    signalFile.delete();
                    broadcastUpdate("progress", "✅ Download completato in " + secondsWaited[0] + "s!", sessionId);
                    startOrchestrator(workDir, url, sessionId);
                } else if (secondsWaited[0] < 600) {
                    secondsWaited[0]++;
                    //if (secondsWaited[0] % 2 == 0) {
                        broadcastUpdate("progress", "⏳ Scaricamento sottotitoli in corso... (" + secondsWaited[0] + "s)", sessionId);
                        updateNotification("Download sottotitoli: " + secondsWaited[0] + "s");
                    //}
                    handler.postDelayed(this, 1000);
                } else {
                    broadcastUpdate("error", "❌ Timeout download superato.", sessionId);
                    stopSelf(); // Ferma il servizio
                }
            }
        };
        handler.postDelayed(checkRunnable, 1000);
    }

    private void startOrchestrator(File workDir, String url, int sessionId) {
        PipelineOrchestrator orchestrator = new PipelineOrchestrator(this);
        orchestrator.processDownloadedFolder(workDir, url, sessionId, new PipelineOrchestrator.PipelineCallback() {
            @Override
            public void onProgress(String statusMessage) {
                broadcastUpdate("progress", "⚙️ " + statusMessage, sessionId);
                updateNotification(statusMessage);
            }

            @Override
            public void onSummaryChunk(String currentText) {
                broadcastUpdate("chunk", "✍️ " + currentText, sessionId);
            }

            @Override
            public void onComplete(int newSessionId, String finalSummaryText) {
                broadcastUpdate("complete", "✅ Elaborazione completata!\n\n" + finalSummaryText, newSessionId);
                // Aggiungiamo la notifica fissa qui!
                showFinishedNotification("Riassunto Completato!", "Tocca per leggere l'analisi.", newSessionId);
                stopSelf();
            }

            @Override
            public void onError(String error) {
                broadcastUpdate("error", "❌ Errore Pipeline: " + error, sessionId);
                // Aggiungiamo la notifica anche in caso di errore
                showFinishedNotification("Errore di Elaborazione", "Qualcosa è andato storto.", sessionId);
                stopSelf();
            }
        });
    }

    // Invia i messaggi all'interfaccia (MainActivity) se è aperta
    private void broadcastUpdate(String type, String text, int sessionId) {
        // 1. Comunicazione DIRETTA e istantanea (Infallibile)
        if (updateListener != null) {
            updateListener.onUpdate(type, text, sessionId);
        }

        // 2. Manteniamo il vecchio sistema radio di backup (Opzionale)
        Intent intent = new Intent(ACTION_UPDATE);
        intent.setPackage(getPackageName());
        intent.putExtra("type", type);
        intent.putExtra("text", text);
        intent.putExtra("sessionId", sessionId);
        sendBroadcast(intent);
    }

    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Summariser")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .build();
        manager.notify(1, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID, "Stato Elaborazione", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void showFinishedNotification(String title, String message, int sessionId) {
        NotificationManager manager = getSystemService(NotificationManager.class);

        // Creiamo l'Intent per aprire l'app
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (sessionId != -1) {
            intent.putExtra("open_session_id", sessionId);
        }

        // Il PendingIntent è un "lasciapassare" che permette al sistema Android di eseguire il tuo Intent
        int pendingFlags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= android.app.PendingIntent.FLAG_IMMUTABLE;
        }
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent, pendingFlags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setAutoCancel(true) // Fa sparire la notifica quando l'utente la tocca
                .setContentIntent(pendingIntent);

        // Usiamo un ID diverso (2) rispetto a quello del servizio (1) così non viene cancellata
        // quando chiamiamo stopSelf()
        manager.notify(2, builder.build());
    }
}