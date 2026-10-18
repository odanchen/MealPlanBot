package app.mealplan.web;

import app.mealplan.persistence.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class IdempotentOperations {

    private final RequestDeduplicationRepository repository;
    private final JsonMapper json;

    public IdempotentOperations(RequestDeduplicationRepository repository, JsonMapper json) {
        this.repository = repository;
        this.json = json;
    }

    public <T> T execute(
        Connection c,
        String user,
        String operation,
        String key,
        Object payload,
        Instant now,
        Class<T> type,
        SqliteDatabase.Work<T> work
    ) throws SQLException {
        if (key == null || key.isBlank() || key.length() > 200) throw ApiErrors.invalid(
            "Idempotency-Key is required (maximum 200 characters)"
        );
        String hash;
        try {
            hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    json.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        String saved = repository.find(c, user, operation, key, hash, now);
        if (saved != null) return json.readValue(saved, type);
        T result = work.run(c);
        repository.save(c, user, operation, key, hash, json.writeValueAsString(result), now);
        return result;
    }
}
