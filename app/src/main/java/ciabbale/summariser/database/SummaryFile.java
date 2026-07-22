package ciabbale.summariser.database;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

@Entity(tableName = "summary_files",
        foreignKeys = @ForeignKey(entity = Session.class,
                parentColumns = "id",
                childColumns = "sessionId",
                onDelete = ForeignKey.CASCADE))
public class SummaryFile {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int sessionId; // Chiave esterna che collega il file alla sessione

    public String fileName; // es: "01 - Intro.txt", "Riassunto Finale"
    public String content;  // Il testo Markdown generato da Ollama
    public String type;     // Può essere "SINGLE", "MACRO", "FINAL"
}