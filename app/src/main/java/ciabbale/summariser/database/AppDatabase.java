package ciabbale.summariser.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {Session.class, SummaryFile.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {

    public abstract SummariserDao summariserDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "summariser_db")
                            .fallbackToDestructiveMigration() // Ricrea il DB se cambi la versione
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}