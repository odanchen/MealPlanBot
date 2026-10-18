package app.mealplan.shopping;

import static org.assertj.core.api.Assertions.*;

import app.mealplan.config.RuntimeProperties;
import app.mealplan.identity.IdentityService;
import app.mealplan.persistence.*;
import app.mealplan.support.MutableClock;
import app.mealplan.web.*;
import java.nio.file.Path;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShoppingPersistenceTest {

    @TempDir
    Path directory;

    SqliteDatabase open() throws Exception {
        return new SqliteDatabase(
            new RuntimeProperties(directory.resolve("app.sqlite"), "test", false, null)
        );
    }

    @Test
    void restartRetainsOwnershipEntriesAndSevenDayRetryResults() throws Exception {
        var clock = new MutableClock(Instant.parse("2026-10-18T12:00:00Z"));
        var repository = new ShoppingRepository();
        var access = new ListAccessPolicy();
        var retry = new RequestDeduplicationRepository();
        var operations = new IdempotentOperations(
            retry,
            new tools.jackson.databind.json.JsonMapper()
        );
        String owner, list, entry;
        try (var db = open()) {
            var migration = new MigrationRunner(db);
            var identities = new IdentityService(db, clock, migration);
            owner = identities
                .resolve(
                    "https://accounts.google.com",
                    "stable-subject",
                    "Cook",
                    "old@example.test"
                )
                .id();
            var service = new ShoppingListService(
                db,
                migration,
                repository,
                access,
                operations,
                clock
            );
            list = service.create(owner, "Persistent groceries", "list-key").id();
            entry = db
                .write(c ->
                    operations.execute(
                        c,
                        owner,
                        "entry:" + list,
                        "entry-key",
                        "Milk",
                        clock.instant(),
                        ShoppingEntry.class,
                        connection ->
                            repository.add(
                                connection,
                                owner,
                                list,
                                "Milk",
                                null,
                                "1",
                                "l",
                                clock.instant()
                            )
                    )
                )
                .id();
            // A failure after inserting an entry rolls back both the mutation and its retry record.
            assertThatThrownBy(() ->
                db.write(c ->
                    operations.execute(
                        c,
                        owner,
                        "entry:" + list,
                        "failed",
                        "Bread",
                        clock.instant(),
                        ShoppingEntry.class,
                        connection -> {
                            repository.add(
                                connection,
                                owner,
                                list,
                                "Bread",
                                null,
                                null,
                                null,
                                clock.instant()
                            );
                            throw new IllegalStateException("abort");
                        }
                    )
                )
            ).isInstanceOf(IllegalStateException.class);
        }
        clock.advance(Duration.ofDays(7).minusMillis(1));
        try (var db = open()) {
            var migration = new MigrationRunner(db);
            var identities = new IdentityService(db, clock, migration);
            var changed = identities.resolve(
                "https://accounts.google.com",
                "stable-subject",
                "Cook",
                "new@example.test"
            );
            assertThat(changed.id()).isEqualTo(owner);
            var service = new ShoppingListService(
                db,
                migration,
                repository,
                access,
                operations,
                clock
            );
            assertThat(service.get(owner, list).owner()).isTrue();
            assertThat(service.create(owner, "Persistent groceries", "list-key").id()).isEqualTo(
                list
            );
            var saved = db.write(c ->
                operations.execute(
                    c,
                    owner,
                    "entry:" + list,
                    "entry-key",
                    "Milk",
                    clock.instant(),
                    ShoppingEntry.class,
                    connection -> {
                        throw new AssertionError("must replay persisted result");
                    }
                )
            );
            assertThat(saved.id()).isEqualTo(entry);
            assertThat(saved.createdBy()).isEqualTo(owner);
            db.read(c -> {
                assertThat(repository.entries(c, list, "active", "alphabetical", true)).hasSize(1);
                return null;
            });
            clock.advance(Duration.ofMillis(1));
            var fresh = db.write(c ->
                operations.execute(
                    c,
                    owner,
                    "entry:" + list,
                    "entry-key",
                    "Milk",
                    clock.instant(),
                    ShoppingEntry.class,
                    connection ->
                        repository.add(
                            connection,
                            owner,
                            list,
                            "Milk",
                            null,
                            "1",
                            "l",
                            clock.instant()
                        )
                )
            );
            assertThat(fresh.id()).isNotEqualTo(entry);
            db.read(c -> {
                assertThat(repository.entries(c, list, "active", "alphabetical", true)).hasSize(2);
                return null;
            });
        }
    }
}
