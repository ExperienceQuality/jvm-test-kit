package com.xq.jvmtestkit.postgres;

import java.util.Objects;

/**
 * Immutable configuration for a disposable PostgreSQL fixture.
 */
public final class PostgresFixtureConfig {
    /**
     * Official Docker Hub {@code postgres:16-bookworm} manifest digest recorded at implement time.
     */
    public static final String APPROVED_POSTGRES_16_DIGEST =
            "sha256:92620daddcd947f8d5ab5ba66e848702fe443d87fed30c4cea8e389fd78dfc55";

    private final String imageName;
    private final String databaseName;
    private final String username;
    private final String password;

    private PostgresFixtureConfig(String imageName, String databaseName, String username, String password) {
        this.imageName = imageName;
        this.databaseName = databaseName;
        this.username = username;
        this.password = password;
    }

    public static PostgresFixtureConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String imageName() {
        return imageName;
    }

    public String databaseName() {
        return databaseName;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public String sanitizedDiagnostics() {
        return "image=<digest-pinned>; database=" + databaseName + "; username=<redacted>; password=<redacted>";
    }

    public static final class Builder {
        private String imageName = "postgres@" + APPROVED_POSTGRES_16_DIGEST;
        private String databaseName = "jvmtestkit";
        private String username = "jvmtestkit";
        private String password = "jvmtestkit-secret";

        private Builder() {
        }

        public Builder imageName(String imageName) {
            this.imageName = Objects.requireNonNull(imageName, "imageName");
            if (!imageName.contains("@sha256:")) {
                throw new IllegalArgumentException("imageName must be digest-pinned");
            }
            return this;
        }

        public Builder databaseName(String databaseName) {
            this.databaseName = requireIdentifier(databaseName, "databaseName");
            return this;
        }

        public Builder username(String username) {
            this.username = requireIdentifier(username, "username");
            return this;
        }

        public Builder password(String password) {
            this.password = Objects.requireNonNull(password, "password");
            if (password.isBlank()) {
                throw new IllegalArgumentException("password must not be blank");
            }
            return this;
        }

        public PostgresFixtureConfig build() {
            return new PostgresFixtureConfig(imageName, databaseName, username, password);
        }

        private static String requireIdentifier(String value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value;
        }
    }
}
