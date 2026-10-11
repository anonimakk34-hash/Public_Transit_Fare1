package validator;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TapRepository {
    private static final String DEFAULT_FILE_PATH = "taps.json";
    private final String filePath;
    private final Gson gson = new Gson();

    public TapRepository() {
        this(DEFAULT_FILE_PATH);
    }

    /** Lets tests (and a restart simulation) point the repository at any file. */
    public TapRepository(String filePath) {
        this.filePath = filePath;
    }

    /**
     * Saves a new tap or overwrites an existing one to update its state.
     */
    public void saveTap(TapRecord tap) {
        List<TapRecord> taps = loadAllTaps();

        // Remove existing entry if we are updating state (e.g., NEW -> VALIDATED)
        taps.removeIf(t -> t.getTapId().equals(tap.getTapId()));
        taps.add(tap);

        try (Writer writer = new FileWriter(filePath)) {
            gson.toJson(taps, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write to durable storage: " + filePath, e);
        }
    }

    /**
     * Called by the Facade on restart. VALIDATED means "chain still in flight":
     * COMPLETED and VOIDED taps are terminal and never returned here.
     */
    public List<TapRecord> loadPendingTaps() {
        return loadAllTaps().stream()
                .filter(t -> t.getStatus() == TapState.VALIDATED)
                .collect(Collectors.toList());
    }

    public TapRecord findById(String tapId) {
        return loadAllTaps().stream()
                .filter(t -> t.getTapId().equals(tapId))
                .findFirst()
                .orElse(null);
    }

    public List<TapRecord> loadAllTaps() {
        File file = new File(filePath);
        if (!file.exists()) {
            return new ArrayList<>();
        }

        try (Reader reader = new FileReader(file)) {
            Type listType = new TypeToken<ArrayList<TapRecord>>(){}.getType();
            List<TapRecord> taps = gson.fromJson(reader, listType);
            return taps != null ? taps : new ArrayList<>();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read durable storage: " + filePath, e);
        }
    }
}