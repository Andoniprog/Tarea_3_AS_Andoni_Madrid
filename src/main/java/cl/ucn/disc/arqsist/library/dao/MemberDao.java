package cl.ucn.disc.arqsist.library.dao;

import cl.ucn.disc.arqsist.library.model.Member;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.field.SqlType;
import com.j256.ormlite.stmt.SelectArg;
import com.j256.ormlite.support.ConnectionSource;

import java.sql.SQLException;
import java.util.List;

/**
 * Provides ORMLite persistence operations for members.
 *
 * <p>Every method except the constructor wraps {@link SQLException} in a {@link RuntimeException}, so the
 * member service and the gRPC adapter do not handle database checked exceptions.
 * A database failure reaches a gRPC client as INTERNAL.</p>
 */
public final class MemberDao {

    /** ORMLite DAO backing member operations. */
    private final Dao<Member, Integer> dao;

    /**
     * Creates a member DAO using the supplied connection source.
     *
     * @param connectionSource database connections used by ORMLite.
     * @throws SQLException if ORMLite cannot create the DAO.
     */
    public MemberDao(ConnectionSource connectionSource) throws SQLException {
        this.dao = DaoManager.createDao(connectionSource, Member.class);
    }

    /**
     * Retrieves all members.
     *
     * @return all persisted members.
     * @throws RuntimeException if the query fails.
     */
    public List<Member> findAll() {
        try {
            return dao.queryForAll();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load members", e);
        }
    }

    /**
     * Finds a member by its identifier.
     *
     * @param id member identifier.
     * @return the matching member, or {@code null} if none exists.
     * @throws RuntimeException if the query fails.
     */
    public Member findById(int id) {
        try {
            return dao.queryForId(id);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load member " + id, e);
        }
    }

    /**
     * Finds the members whose name contains a fragment, ordered by id ascending.
     *
     * <p>The match is literal: {@code instr} finds the fragment as plain text, so
     * {@code %} and {@code _} are not wildcards. {@code lower} folds ASCII letters
     * only, so the match is case-insensitive for ASCII. The fragment travels as a
     * bound {@link SelectArg}, never inside the SQL text.</p>
     *
     * @param fragment the text to find inside the name.
     * @return the matching members, or an empty list when none matches.
     * @throws RuntimeException if the query fails.
     */
    public List<Member> searchByName(String fragment) {
        try {
            return dao.queryBuilder()
                    .orderBy("id", true)
                    .where()
                    .raw("instr(lower(name), lower(?)) > 0", new SelectArg(SqlType.STRING, fragment))
                    .query();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to search members", e);
        }
    }

    /**
     * Persists a new member.
     *
     * @param member member to create.
     * @throws RuntimeException if the insert fails.
     */
    public void create(Member member) {
        try {
            dao.create(member);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create member", e);
        }
    }

    /**
     * Persists updates to a member.
     *
     * @param member member to update.
     * @throws RuntimeException if the update fails.
     */
    public void update(Member member) {
        try {
            dao.update(member);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update member " + member.getId(), e);
        }
    }
}
