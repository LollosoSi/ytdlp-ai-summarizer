package ciabbale.summariser.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SummariserDao {

    // --- Metodi per le Sessioni ---
    @Insert
    long insertSession(Session session);

    @Query("SELECT * FROM sessions ORDER BY timestamp DESC")
    List<Session> getAllSessions();

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    void deleteSession(int sessionId);

    // --- Metodi per i File ---
    @Insert
    void insertSummaryFile(SummaryFile file);

    @Query("SELECT * FROM summary_files WHERE sessionId = :sessionId ORDER BY type, fileName ASC")
    List<SummaryFile> getFilesForSession(int sessionId);

    @Query("SELECT * FROM summary_files WHERE sessionId = :sessionId AND type = 'FINAL' LIMIT 1")
    SummaryFile getFinalSummary(int sessionId);

    @Query("DELETE FROM summary_files WHERE sessionId = :sessionId")
    void deleteFilesForSession(int sessionId);

    @Query("DELETE FROM sessions")
    void deleteAllSessions();

    @Query("DELETE FROM summary_files")
    void deleteAllFiles();

    @Query("UPDATE sessions SET title = :newTitle WHERE id = :sessionId")
    void updateSessionTitle(int sessionId, String newTitle);
}