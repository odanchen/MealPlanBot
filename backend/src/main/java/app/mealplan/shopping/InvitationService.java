package app.mealplan.shopping;

import app.mealplan.config.RuntimeProperties;
import app.mealplan.persistence.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class InvitationService {

    public record Created(Invitation invitation, String link) {}

    public record Accepted(String listId) {}

    private final SqliteDatabase db;
    private final InvitationRepository invitations;
    private final ListAccessPolicy access;
    private final Clock clock;
    private final RuntimeProperties runtime;
    private final SecureRandom random = new SecureRandom();

    public InvitationService(
        SqliteDatabase db,
        MigrationRunner migrations,
        InvitationRepository invitations,
        ListAccessPolicy access,
        Clock clock,
        RuntimeProperties runtime
    ) {
        this.db = db;
        this.invitations = invitations;
        this.access = access;
        this.clock = clock;
        this.runtime = runtime;
    }

    public Created create(String user, String list) {
        return db.write(c -> {
            access.owner(c, user, list);
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            var invite = invitations.create(c, list, user, hash(token), clock.instant());
            String origin =
                runtime.publicBaseUrl() == null
                    ? ""
                    : runtime.publicBaseUrl().toString().replaceAll("/$", "");
            return new Created(invite, origin + "/join#token=" + token);
        });
    }

    public List<Invitation> list(String user, String list) {
        return db.read(c -> {
            access.owner(c, user, list);
            return invitations.list(c, list);
        });
    }

    public void withdraw(String user, String list, String id) {
        db.write(c -> {
            access.owner(c, user, list);
            invitations.withdraw(c, list, id, clock.instant());
            return null;
        });
    }

    public Accepted accept(String user, String token) {
        if (
            token == null || !token.matches("[A-Za-z0-9_-]{43}")
        ) throw InvitationRepository.unusable();
        return db.write(c -> {
            var invitation = invitations.usable(c, hash(token), clock.instant());
            try (
                var q = c.prepareStatement(
                    "INSERT INTO memberships(list_id,user_id,joined_at) VALUES(?,?,?) ON CONFLICT(list_id,user_id) DO NOTHING"
                )
            ) {
                q.setString(1, invitation.listId());
                q.setString(2, user);
                q.setLong(3, clock.instant().toEpochMilli());
                q.executeUpdate();
            }
            return new Accepted(invitation.listId());
        });
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
