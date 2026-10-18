package app.mealplan.shopping;

import app.mealplan.web.ApiErrors;
import java.sql.*;
import org.springframework.stereotype.Component;

@Component
public class ListAccessPolicy {

    public void member(Connection c, String user, String list) throws SQLException {
        try (
            var q = c.prepareStatement(
                "SELECT 1 FROM shopping_lists l JOIN memberships m ON m.list_id=l.id WHERE l.id=? AND m.user_id=?"
            )
        ) {
            q.setString(1, list);
            q.setString(2, user);
            try (var r = q.executeQuery()) {
                if (!r.next()) throw ApiErrors.notFound();
            }
        }
    }

    public void owner(Connection c, String user, String list) throws SQLException {
        member(c, user, list);
        try (var q = c.prepareStatement("SELECT owner_user_id FROM shopping_lists WHERE id=?")) {
            q.setString(1, list);
            try (var r = q.executeQuery()) {
                if (!r.next()) throw ApiErrors.notFound();
                if (!user.equals(r.getString(1))) throw ApiErrors.forbidden();
            }
        }
    }
}
