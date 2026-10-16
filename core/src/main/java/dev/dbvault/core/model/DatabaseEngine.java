package dev.dbvault.core.model;

/** Database engines with a complete logical backup and restore adapter. */
public enum DatabaseEngine {
    MYSQL("MySQL", 3306),
    POSTGRESQL("PostgreSQL", 5432),
    MONGODB("MongoDB", 27017);

    private final String displayName;
    private final int defaultPort;

    DatabaseEngine(String displayName, int defaultPort) {
        this.displayName = displayName;
        this.defaultPort = defaultPort;
    }

    public String displayName() {
        return displayName;
    }

    public int defaultPort() {
        return defaultPort;
    }

    /** Every engine dbvault backs up can be restored into a throwaway server and checked. */
    public boolean supportsRestoreVerification() {
        return true;
    }
}
