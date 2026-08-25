package net.emberhold.core.impl;

import net.emberhold.core.api.Db;
import net.emberhold.core.util.BlockingGuard;
import org.bukkit.plugin.Plugin;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Async-only DB facade (spec 01 §6). Boots HikariCP pool + Flyway migration.
 * Fails boot if migration fails. All methods are async; calling on the game thread
 * throws via {@link BlockingGuard}.
 */
public final class DbImpl implements Db {

    private final com.zaxxer.hikari.HikariDataSource dataSource;

    private DbImpl(com.zaxxer.hikari.HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Build the pool from config and run Flyway migrations.
     *
     * @throws IllegalStateException if migration fails (boot must abort).
     */
    public static DbImpl create(Plugin plugin, String jdbcUrl, String user, String password) {
        var cfg = new com.zaxxer.hikari.HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(user);
        cfg.setPassword(password);
        cfg.setMaximumPoolSize(10);
        cfg.setPoolName("ember-pool");
        cfg.setConnectionTimeout(10_000);
        cfg.setDriverClassName("org.postgresql.Driver");

        var pool = new com.zaxxer.hikari.HikariDataSource(cfg);

        try {
            // Flyway's classpath scanner cannot reliably parse migration filenames when they
            // live inside a fat/uber plugin jar (it reports "Unrecognised migration name
            // format"). So the SQL migrations are packaged under `ember/migrations` (NOT the
            // Flyway-default `db/migration`, which Flyway auto-scans from the classpath and
            // chokes on). We extract them to disk and point Flyway at a filesystem: location.
            // In tests the resource is already a directory, so no-op.
            String migrationDir = extractMigrations(plugin);
            FluentConfiguration fc = new FluentConfiguration(resolveClassLoader(plugin))
                .dataSource(pool)
                .baselineOnMigrate(true)
                .locations("filesystem:" + migrationDir)
                .validateMigrationNaming(true);
            Flyway flyway = fc.load();
            flyway.migrate();
        } catch (RuntimeException e) {
            pool.close();
            // Surface the Flyway cause (e.g. migration naming/DDL errors) for operators.
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String detail = cause.getMessage() != null ? cause.getMessage() : e.getMessage();
            if (plugin != null && plugin.getLogger() != null) {
                plugin.getLogger().severe("[Db] Flyway migration failed: " + detail);
            }
            throw new IllegalStateException("Flyway migration failed — aborting boot: " + detail, e);
        }

        return new DbImpl(pool);
    }

    private static ClassLoader resolveClassLoader(Plugin plugin) {
        return plugin != null ? plugin.getClass().getClassLoader() : Thread.currentThread().getContextClassLoader();
    }

    /** Classpath-relative location of the SQL migrations (kept away from Flyway's default). */
    private static final String MIGRATION_RESOURCE = "ember/migrations";

    /**
     * Resolve the {@code ember/migrations} resource to a real filesystem directory Flyway can
     * scan. If the resource is a classpath directory (unit tests: {@code build/resources/main}),
     * return it directly. If it lives inside a jar (fat deployment), extract {@code .sql} files
     * under the plugin data folder and return that path.
     */
    private static String extractMigrations(Plugin plugin) {
        try {
            var resources = resolveClassLoader(plugin).getResources(MIGRATION_RESOURCE);
            boolean anyJar = false;
            while (resources.hasMoreElements()) {
                var url = resources.nextElement();
                if ("jar".equalsIgnoreCase(url.getProtocol())) {
                    anyJar = true;
                }
            }
            if (!anyJar) {
                // Classpath directory (tests): Flyway can scan it directly. Use the first
                // filesystem element if present; otherwise fall back to a known path.
                var dirResources = resolveClassLoader(plugin).getResources(MIGRATION_RESOURCE);
                while (dirResources.hasMoreElements()) {
                    var url = dirResources.nextElement();
                    if ("file".equalsIgnoreCase(url.getProtocol())) {
                        return java.nio.file.Path.of(url.toURI()).toString();
                    }
                }
            }

            // Jar deployment: extract migrations under the data folder.
            java.nio.file.Path target = targetDir(plugin).resolve("migrations");
            java.nio.file.Files.createDirectories(target);
            if (plugin != null) {
                jarMigrationEntries(resolveClassLoader(plugin)).forEach(entry -> {
                    String name = MIGRATION_RESOURCE + "/" + entry;
                    var in = plugin.getClass().getClassLoader().getResourceAsStream(name);
                    if (in != null) {
                        try (in) {
                            java.nio.file.Files.copy(in, target.resolve(entry),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        } catch (java.io.IOException e) {
                            throw new RuntimeException("extract migration " + entry, e);
                        }
                    }
                });
            }
            return target.toString();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("resolve " + MIGRATION_RESOURCE, e);
        }
    }

    private static java.nio.file.Path targetDir(Plugin plugin) {
        if (plugin != null && plugin.getDataFolder() != null) {
            return plugin.getDataFolder().toPath();
        }
        return java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "ember-hold");
    }

    /** List {@code *.sql} basenames under {@code ember/migrations} within the plugin jar. */
    private static java.util.List<String> jarMigrationEntries(ClassLoader cl) {
        var out = new java.util.ArrayList<String>();
        try {
            var resources = cl.getResources(MIGRATION_RESOURCE);
            while (resources.hasMoreElements()) {
                var url = resources.nextElement();
                if (!"jar".equalsIgnoreCase(url.getProtocol())) {
                    continue;
                }
                var jarUrl = (java.net.JarURLConnection) url.openConnection();
                try (var jar = jarUrl.getJarFile()) {
                    var entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        var e = entries.nextElement();
                        String name = e.getName();
                        if (name.startsWith(MIGRATION_RESOURCE + "/") && name.endsWith(".sql")) {
                            out.add(name.substring(MIGRATION_RESOURCE.length() + 1));
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("list migrations in jar", e);
        }
        return out;
    }

    @Override
    public <T> CompletableFuture<T> withConnection(Function<Connection, T> fn) {
        BlockingGuard.allowAsync(); // throws if enabled on game thread
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection()) {
                return fn.apply(c);
            } catch (SQLException e) {
                throw new RuntimeException("DB withConnection failed", e);
            }
        });
    }

    @Override
    public <T> CompletableFuture<T> inTransaction(Function<Connection, T> fn) {
        BlockingGuard.allowAsync();
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection()) {
                boolean auto = c.getAutoCommit();
                c.setAutoCommit(false);
                try {
                    T result = fn.apply(c);
                    c.commit();
                    return result;
                } catch (Throwable t) {
                    c.rollback();
                    throw new RuntimeException("DB transaction failed", t);
                } finally {
                    c.setAutoCommit(auto);
                }
            } catch (SQLException e) {
                throw new RuntimeException("DB transaction failed", e);
            }
        });
    }

    @Override
    public boolean isGameThread() {
        return BlockingGuard.onGameThread();
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public void close() {
        dataSource.close();
    }
}
