package ciabbale.summariser.utils;

import android.content.Context;
import android.content.Intent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

public class TermuxBridge {

    public static void executeYtDlp(Context context, String url, String outputDir) {

        // 1. Creiamo lo script Bash intelligente dinamicamente
        File scriptFile = new File(outputDir, "smart_download.sh");
        try {
            FileOutputStream fos = new FileOutputStream(scriptFile);
            OutputStreamWriter writer = new OutputStreamWriter(fos);

            writer.write("#!/bin/bash\n");
            writer.write("URL=\"$1\"\n");
            writer.write("OUTDIR=\"$2\"\n");
            writer.write("echo \"Avvio ricerca video...\" > \"$OUTDIR/termux_log.txt\"\n");

            // Estrae solo l'indice e l'ID del video in modo pulito
            writer.write("/data/data/com.termux/files/usr/bin/yt-dlp --flat-playlist --print \"%(playlist_index|00)s|%(id)s\" \"$URL\" > \"$OUTDIR/videos.txt\"\n");

            writer.write("while IFS=\"|\" read -r idx vid; do\n");
            writer.write("    if [ -z \"$vid\" ]; then continue; fi\n");
            writer.write("    echo \"Cerco sottotitoli IT/EN per il video: $vid\" >> \"$OUTDIR/termux_log.txt\"\n");

            // TENTATIVO 1: Italiano o Inglese contemporaneamente (Scarica 1 o 2 file leggerissimi, velocissimo)
            writer.write("    /data/data/com.termux/files/usr/bin/yt-dlp --ignore-errors --skip-download --write-subs --write-auto-subs --sub-langs \"it,en\" --sub-format vtt -o \"$OUTDIR/$idx - %(title)s.%(ext)s\" \"https://youtu.be/$vid\" >> \"$OUTDIR/termux_log.txt\" 2>&1\n");

            // Controlla se il download primario è andato a buon fine
            writer.write("    if ls \"$OUTDIR/$idx - \"*.vtt 1> /dev/null 2>&1; then\n");
            writer.write("        echo \"Sottotitoli IT/EN trovati per $vid.\" >> \"$OUTDIR/termux_log.txt\"\n");
            writer.write("        continue\n");
            writer.write("    fi\n");

            // TENTATIVO 2 (Fallback): Estrae il primo sottotitolo automatico disponibile
            writer.write("    echo \"Nessun sub IT/EN. Interrogo YouTube per la prima lingua automatica...\" >> \"$OUTDIR/termux_log.txt\"\n");
            // Questo comando estrae chirurgicamente il codice lingua (es: 'es', 'ja') dalla tabella di yt-dlp
            writer.write("    first_lang=$(/data/data/com.termux/files/usr/bin/yt-dlp --list-subs \"https://youtu.be/$vid\" | grep -A 100 \"Available automatic captions\" | grep -v \"Available\" | grep -v \"Language\" | head -n 1 | awk '{print $1}')\n");

            writer.write("    if [ -n \"$first_lang\" ]; then\n");
            writer.write("        echo \"Lingua autogenerata trovata: $first_lang\" >> \"$OUTDIR/termux_log.txt\"\n");
            writer.write("        /data/data/com.termux/files/usr/bin/yt-dlp --ignore-errors --skip-download --write-auto-subs --sub-langs \"$first_lang\" --sub-format vtt -o \"$OUTDIR/$idx - %(title)s.%(ext)s\" \"https://youtu.be/$vid\" >> \"$OUTDIR/termux_log.txt\" 2>&1\n");
            writer.write("    else\n");
            writer.write("        echo \"Nessun sottotitolo disponibile in assoluto per $vid\" >> \"$OUTDIR/termux_log.txt\"\n");
            writer.write("    fi\n");

            writer.write("done < \"$OUTDIR/videos.txt\"\n");

            // Semaforo per avvisare l'Orchestratore
            writer.write("touch \"$OUTDIR/yt_dlp_done.txt\"\n");

            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2. Ordiniamo a Termux di eseguire lo script passandogli i parametri (URL e Cartella)
        Intent intent = new Intent();
        intent.setClassName("com.termux", "com.termux.app.RunCommandService");
        intent.setAction("com.termux.RUN_COMMAND");

        // L'eseguibile è bash
        intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");

        // Passiamo: 1. Percorso Script | 2. Argomento URL | 3. Argomento Output Directory
        String[] args = new String[]{scriptFile.getAbsolutePath(), url, outputDir};
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", args);

        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
}