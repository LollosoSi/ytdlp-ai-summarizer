package ciabbale.summariser.logic;

import android.content.Context;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ciabbale.summariser.database.AppDatabase;
import ciabbale.summariser.database.Session;
import ciabbale.summariser.database.SummaryFile;
import ciabbale.summariser.network.OllamaClient;
import ciabbale.summariser.utils.VttCleaner;

public class PipelineOrchestrator {

    private final Context context;
    private final OllamaClient ollamaClient;
    private final AppDatabase db;

    private final String promptSingle;
    private final String promptMacro;
    private final String promptFinal;

    public interface PipelineCallback {
        void onProgress(String statusMessage);
        void onSummaryChunk(String currentText);
        void onComplete(int sessionId, String finalSummaryText);
        void onError(String error);
    }

    public PipelineOrchestrator(Context context) {
        this.context = context;
        this.ollamaClient = new OllamaClient(context);
        this.db = AppDatabase.getInstance(context);

        android.content.SharedPreferences prefs = context.getSharedPreferences("OllamaPrefs", Context.MODE_PRIVATE);
        this.promptSingle = prefs.getString("prompt_single", "Summarize the following video transcript using clear bullet points. Write entirely in English. Transcript:\n");
        this.promptMacro = prefs.getString("prompt_macro", "Analyze the following sequential video summaries. Create a consolidated, well-structured summary of this batch in English. Batch:\n");
        this.promptFinal = prefs.getString("prompt_final", "Analyze the following macro-summaries of a video playlist. Create the ultimate, comprehensive final summary of the entire content in English. Structure it professionally using Markdown headers and bullet points. Macro-Summaries:\n");
    }

    // Unico metodo unificato e corretto con la diagnostica
    public void processDownloadedFolder(File workDir, String youtubeUrl, int existingSessionId, PipelineCallback callback) {
        callback.onProgress("Avvio Orchestratore...");

        new Thread(() -> {
            try {
                // 1. Leggiamo TUTTI i file VTT scaricati (potrebbero essere decine per un solo video)
                File[] rawFiles = workDir.listFiles((dir, name) -> name.endsWith(".vtt"));

                if (rawFiles == null) {
                    callback.onError("Errore critico: Impossibile leggere la cartella dei download.");
                    return;
                }
                if (rawFiles.length == 0) {
                    callback.onError("Nessun sottotitolo .vtt trovato. Il video non ha sottotitoli (nemmeno automatici).");
                    return;
                }

                callback.onProgress("Filtraggio lingue in corso...");

                // Li ordiniamo alfabeticamente
                Arrays.sort(rawFiles, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));

                // 2. DEDUPLICAZIONE INTELLIGENTE: Teniamo 1 solo file per video
                List<File> uniqueFiles = new ArrayList<>();
                String lastPrefix = "";

                for (File f : rawFiles) {
                    // yt-dlp nomina i file così: "00 - Titolo.en.vtt". Prendiamo solo il numero "00"
                    String prefix = f.getName().split(" - ")[0];

                    if (!prefix.equals(lastPrefix)) {
                        uniqueFiles.add(f); // È un nuovo video, lo aggiungiamo
                        lastPrefix = prefix;
                    } else {
                        // È un'altra lingua dello stesso video.
                        // Sovrascriviamo solo se è inglese o italiano, per dare loro la priorità
                        String name = f.getName().toLowerCase();
                        if (name.contains(".en.") || name.contains(".en-") || name.contains(".it.")) {
                            uniqueFiles.set(uniqueFiles.size() - 1, f);
                        }
                    }
                }

                // Trasformiamo la lista pulita di nuovo in un Array
                File[] files = uniqueFiles.toArray(new File[0]);
                callback.onProgress("Trovati " + files.length + " video unici da elaborare.");

                int sessionId = existingSessionId;

                // Creazione della sessione nel Database
                if (sessionId == -1) {
                    callback.onProgress("Creazione nuova sessione nel Database...");
                    Session session = new Session();
                    session.youtubeUrl = youtubeUrl;
                    session.title = files[0].getName().replace(".vtt", "");
                    session.timestamp = System.currentTimeMillis();
                    session.isPlaylist = files.length > 1;
                    sessionId = (int) db.summariserDao().insertSession(session);
                }

                if (files.length == 1) {
                    // --- GESTIONE VIDEO SINGOLO ---
                    callback.onProgress("Pulizia del file VTT...");
                    String cleanedText = VttCleaner.cleanVtt(files[0]);

                    if (cleanedText == null || cleanedText.trim().isEmpty()) {
                        callback.onError("Il file dei sottotitoli risulta vuoto dopo la pulizia.");
                        return;
                    }

                    String prompt = promptSingle + "\n" + cleanedText;

                    // Calcolo dinamico
                    ModelSetup setup = calculateOptimalSetup(prompt);
                    callback.onProgress("Avvio AI: Modello " + setup.modelName + " (CTX: " + setup.contextWindow + ")");

                    final int finalSessionId = sessionId;

                    ollamaClient.generateSummary(prompt, setup.modelName, setup.contextWindow, true, new OllamaClient.OllamaCallback() {
                        @Override
                        public void onChunk(String currentText) {
                            callback.onSummaryChunk(currentText);
                        }

                        @Override
                        public void onSuccess(String responseText) {
                            saveFileToDb(finalSessionId, files[0].getName(), responseText, "FINAL");
                            callback.onComplete(finalSessionId, responseText);
                        }

                        @Override
                        public void onError(String errorMessage) {
                            callback.onError("Errore AI: " + errorMessage);
                        }
                    });

                } else {
                    // --- GESTIONE PLAYLIST ---
                    callback.onProgress("Avvio logica per Playlist...");
                    processPlaylistPhase1(sessionId, files, callback);
                }

            } catch (Exception e) {
                callback.onError("Crash interno: " + e.toString());
            }
        }).start();
    }

    // FASE 1: Riassunto di ogni singolo video
    private void processPlaylistPhase1(int sessionId, File[] files, PipelineCallback callback) {
        List<SummaryFile> singleSummaries = new ArrayList<>();
        int total = files.length;

        for (int i = 0; i < total; i++) {
            File f = files[i];
            int currentIndex = i + 1;
            callback.onProgress("Fase 1: Elaborazione video " + currentIndex + " di " + total);

            try {
                String cleanedText = VttCleaner.cleanVtt(f);
                String prompt = promptSingle + "\n" + cleanedText;

                final String[] resultHolder = new String[1];
                final boolean[] success = {false};

                ModelSetup setup = calculateOptimalSetup(prompt);
                callback.onProgress("Avvio AI (" + currentIndex + "): Modello " + setup.modelName + " (CTX: " + setup.contextWindow + ")");

                ollamaClient.generateSummary(prompt, setup.modelName, setup.contextWindow, false, new OllamaClient.OllamaCallback() {
                    @Override
                    public void onChunk(String currentText) {}

                    @Override
                    public void onSuccess(String responseText) {
                        resultHolder[0] = responseText;
                        success[0] = true;
                    }

                    @Override
                    public void onError(String errorMessage) {
                        success[0] = false;
                    }
                });

                int timeout = 0;
                while (resultHolder[0] == null && timeout < 180) { // Aumentato a 3 minuti per modelli pesanti
                    Thread.sleep(1000);
                    timeout++;
                }

                if (success[0]) {
                    SummaryFile sf = new SummaryFile();
                    sf.sessionId = sessionId;
                    sf.fileName = f.getName().replace(".vtt", ".txt");
                    sf.content = resultHolder[0];
                    sf.type = "SINGLE";
                    db.summariserDao().insertSummaryFile(sf);
                    singleSummaries.add(sf);
                }

            } catch (Exception e) {
                callback.onError("Errore nel file " + f.getName() + ": " + e.getMessage());
                return;
            }
        }

        processPlaylistPhase2(sessionId, singleSummaries, callback);
    }

    // FASE 2: Macro-Riassunti a blocchi di 10
    private void processPlaylistPhase2(int sessionId, List<SummaryFile> singles, PipelineCallback callback) {
        callback.onProgress("Fase 2: Creazione dei Macro-Riassunti...");
        List<SummaryFile> macroSummaries = new ArrayList<>();

        int blockSize = 10;
        int totalSingles = singles.size();
        int batchCount = 1;

        for (int i = 0; i < totalSingles; i += blockSize) {
            int end = Math.min(i + blockSize, totalSingles);
            List<SummaryFile> batch = singles.subList(i, end);

            StringBuilder batchText = new StringBuilder();
            for (SummaryFile sf : batch) {
                batchText.append("--- Summary from ").append(sf.fileName).append(" ---\n")
                        .append(sf.content).append("\n\n");
            }

            String prompt = promptMacro + "\n" + batchText.toString();
            final String[] resultHolder = new String[1];

            ModelSetup setup = calculateOptimalSetup(prompt);
            callback.onProgress("Avvio AI (Macro " + batchCount + "): Modello " + setup.modelName + " (CTX: " + setup.contextWindow + ")");

            ollamaClient.generateSummary(prompt, setup.modelName, setup.contextWindow, false, new OllamaClient.OllamaCallback() {
                @Override
                public void onChunk(String currentText) {}

                @Override
                public void onSuccess(String responseText) {
                    resultHolder[0] = responseText;
                }

                @Override
                public void onError(String errorMessage) {
                    resultHolder[0] = "";
                }
            });

            try {
                int timeout = 0;
                while (resultHolder[0] == null && timeout < 300) { // 5 minuti max
                    Thread.sleep(1000);
                    timeout++;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (!resultHolder[0].isEmpty()) {
                SummaryFile macroSf = new SummaryFile();
                macroSf.sessionId = sessionId;
                macroSf.fileName = "Batch " + batchCount + ".txt";
                macroSf.content = resultHolder[0];
                macroSf.type = "MACRO";
                db.summariserDao().insertSummaryFile(macroSf);
                macroSummaries.add(macroSf);
            }

            batchCount++;
        }

        processPlaylistPhase3(sessionId, macroSummaries, callback);
    }

    // FASE 3: Riassunto Finale della Playlist
    private void processPlaylistPhase3(int sessionId, List<SummaryFile> macros, PipelineCallback callback) {
        callback.onProgress("Fase 3: Generazione del Riassunto Finale...");

        StringBuilder finalText = new StringBuilder();
        for (SummaryFile sf : macros) {
            finalText.append("--- ").append(sf.fileName).append(" ---\n")
                    .append(sf.content).append("\n\n");
        }

        String prompt = promptFinal + "\n" + finalText.toString();

        ModelSetup setup = calculateOptimalSetup(prompt);
        callback.onProgress("Avvio AI: Modello " + setup.modelName + " (CTX: " + setup.contextWindow + ")");

        ollamaClient.generateSummary(prompt, setup.modelName, setup.contextWindow, true, new OllamaClient.OllamaCallback() {
            @Override
            public void onChunk(String currentText) {
                callback.onSummaryChunk(currentText);
            }
            @Override
            public void onSuccess(String responseText) {
                saveFileToDb(sessionId, "Riassunto Finale", responseText, "FINAL");
                callback.onComplete(sessionId, responseText);
            }
            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    private void saveFileToDb(int sessionId, String fileName, String content, String type) {
        SummaryFile sf = new SummaryFile();
        sf.sessionId = sessionId;
        sf.fileName = fileName;
        sf.content = content;
        sf.type = type;
        db.summariserDao().insertSummaryFile(sf);
    }

    private static class ModelSetup {
        String modelName;
        int contextWindow;
    }

    private ModelSetup calculateOptimalSetup(String text) {
        ModelSetup setup = new ModelSetup();

        if (text == null) text = "";

        int wordCount = text.split("\\s+").length;
        int inputTokens = (int) (wordCount * 1.4);
        int requiredCtx = inputTokens + 1500;

        if (requiredCtx <= 4096) setup.contextWindow = 4096;
        else if (requiredCtx <= 8192) setup.contextWindow = 8192;
        else if (requiredCtx <= 16384) setup.contextWindow = 16384;
        else setup.contextWindow = 32768;

        android.content.SharedPreferences prefs = context.getSharedPreferences("OllamaPrefs", Context.MODE_PRIVATE);
        String heavyModel = prefs.getString("model_macro", "qwen2.5:14b");
        String lightModel = prefs.getString("model_single", "llama3.1:8b");

        if (setup.contextWindow > 8192) {
            setup.modelName = lightModel;
        } else {
            setup.modelName = heavyModel;
        }

        return setup;
    }
}