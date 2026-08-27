package dev.xorsirenz.joindate.common;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class JoinDateDatabase implements AutoCloseable {

    private final File databaseFile;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "JoinDate-Database");
                thread.setDaemon(true);
                return thread;
            });

    private Connection connection;

    public JoinDateDatabase(File databaseFile) {
        this.databaseFile = databaseFile;
    }

    public void open() throws SQLException {
        File parent = databaseFile.getParentFile();

        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs() && !parent.exists()) {
                throw new SQLException(
                        "Could not create database directory: "
                                + parent);
            }
        }

        connection = DriverManager.getConnection(
                "jdbc:sqlite:"
                        + databaseFile.getAbsolutePath());

        try (PreparedStatement statement = connection.prepareStatement(
                "PRAGMA journal_mode=WAL")) {
            statement.execute();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "PRAGMA busy_timeout=5000")) {
            statement.execute();
        }

        createTable();
        migrateDatabase();
    }

    private void createTable() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS players (
                    uuid TEXT PRIMARY KEY NOT NULL,
                    last_known_name TEXT NOT NULL,
                    first_join INTEGER NOT NULL
                )
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
        createNameIndex();
    }

    private void createNameIndex() throws SQLException {
        String sql = """
                CREATE INDEX IF NOT EXISTS
                idx_players_last_known_name
                ON players(last_known_name COLLATE NOCASE)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }

    private void migrateDatabase() throws SQLException {
        boolean hasLastKnownName = false;

        String sql = "PRAGMA table_info(players)";

        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                String column = result.getString("name");

                if ("last_known_name".equalsIgnoreCase(column)) {
                    hasLastKnownName = true;
                    break;
                }
            }
        }

        if (!hasLastKnownName) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "ALTER TABLE players "
                            + "ADD COLUMN "
                            + "last_known_name TEXT")) {
                statement.executeUpdate();
            }
        }
        createNameIndex();
    }

    public void recordJoin(UUID uuid, String playerName, long timestamp) {
        executor.execute(() -> {
            String sql = """
                    INSERT INTO players
                    (uuid, last_known_name, first_join)
                    VALUES (?, ?, ?)
                    ON CONFLICT(uuid)
                    DO UPDATE SET
                        last_known_name = excluded.last_known_name
                    """;

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, playerName);
                statement.setLong(3, timestamp);
                statement.executeUpdate();

            } catch (SQLException exception) {
                exception.printStackTrace();
            }
        });
    }

    public void findUuidByName(
            String playerName,
            UUIDCallback callback) {
        executor.execute(() -> {

            String sql = """
                    SELECT uuid
                    FROM players
                    WHERE last_known_name = ?
                    COLLATE NOCASE
                    LIMIT 1
                    """;

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerName);

                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        UUID uuid = UUID.fromString(result.getString("uuid"));
                        callback.accept(uuid, null);
                    } else {
                        callback.accept(null, null);
                    }
                }

            } catch (Exception exception) {
                callback.accept(null, exception);
            }
        });
    }

    public void getFirstJoin(UUID uuid, ResultCallback callback) {
        executor.execute(() -> {
            String sql = """
                    SELECT first_join
                    FROM players
                    WHERE uuid = ?
                    LIMIT 1
                    """;

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        callback.accept(result.getLong("first_join"), null);
                    } else {
                        callback.accept(null, null);
                    }
                }

            } catch (SQLException exception) {
                callback.accept(null, exception);
            }
        });
    }

    @Override
    public void close() {
        executor.shutdown();

        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException exception) {
                exception.printStackTrace();
            }
            connection = null;
        }
    }

    @FunctionalInterface
    public interface ResultCallback {
        void accept(Long timestamp, Throwable error);
    }

    @FunctionalInterface
    public interface UUIDCallback {
        void accept(UUID uuid, Throwable error);
    }
}
