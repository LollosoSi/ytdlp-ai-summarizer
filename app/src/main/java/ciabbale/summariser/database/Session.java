package ciabbale.summariser.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sessions")
public class Session {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String youtubeUrl;
    public String title;
    public long timestamp; // Per ordinare cronologicamente nella tendina
    public boolean isPlaylist;
}