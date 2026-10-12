package calculator;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FareRepository {
    private static final String DEFAULT_FILE_PATH = "fares.json";
    private final String filePath;
    private final Gson gson = new Gson();

    public FareRepository() {
        this(DEFAULT_FILE_PATH);
    }

    public FareRepository(String filePath) {
        this.filePath = filePath;
    }

    /**
     * Saves a new fare calculation or overwrites an existing one to update its state.
     */
    public void saveFare(FareCalculation fare) {
        List<FareCalculation> fares = loadAllFares();

        // Remove existing entry if we are updating state (e.g., PENDING -> CALCULATED)
        fares.removeIf(f -> f.getCalculationId().equals(fare.getCalculationId()));
        fares.add(fare);

        try (Writer writer = new FileWriter(filePath)) {
            gson.toJson(fares, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write to durable storage: " + filePath, e);
        }
    }

    /**
     * Called by the Facade on application startup to detect broken chains.
     */
    public List<FareCalculation> loadPendingCalculations() {
        return loadAllFares().stream()
                .filter(f -> f.getStatus() == FareState.PENDING || f.getStatus() == FareState.CALCULATED)
                .collect(Collectors.toList());
    }

    public FareCalculation findByTapId(String tapId) {
        return loadAllFares().stream()
                .filter(f -> tapId.equals(f.getTapId()))
                .findFirst()
                .orElse(null);
    }

    public List<FareCalculation> loadAllFares() {
        File file = new File(filePath);
        if (!file.exists()) {
            return new ArrayList<>();
        }

        try (Reader reader = new FileReader(file)) {
            Type listType = new TypeToken<ArrayList<FareCalculation>>(){}.getType();
            List<FareCalculation> fares = gson.fromJson(reader, listType);
            return fares != null ? fares : new ArrayList<>();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read durable storage: " + filePath, e);
        }
    }

    /** Deletes the persisted file (used by the admin reset). */
    public void clear() {
        File file = new File(filePath);
        if (file.exists() && !file.delete()) {
            throw new RuntimeException("Could not delete durable storage: " + filePath);
        }
    }
}