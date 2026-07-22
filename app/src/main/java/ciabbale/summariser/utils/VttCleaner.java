package ciabbale.summariser.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class VttCleaner {

    public static String cleanVtt(File vttFile) throws IOException {
        StringBuilder cleanedText = new StringBuilder();
        String lastLine = "";

        try (BufferedReader br = new BufferedReader(new FileReader(vttFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();

                // 1. Scarta righe WEBVTT, timestamp con frecce (-->) e righe di soli numeri/punti
                if (line.isEmpty() ||
                        line.contains("WEBVTT") ||
                        line.contains("-->") ||
                        line.matches("^[0-9:.]+$")) {
                    continue;
                }

                // 2. Rimuove tag inline come <00:00:00.240> e </c> (equivalente del sed 's/<[^>]*>//g')
                line = line.replaceAll("<[^>]*>", "").trim();

                // 3. Scarta righe vuote o righe identiche a quella precedente (equivalente di uniq)
                if (line.isEmpty() || line.equalsIgnoreCase(lastLine)) {
                    continue;
                }

                cleanedText.append(line).append(" ");
                lastLine = line;
            }
        }
        return cleanedText.toString().trim();
    }
}