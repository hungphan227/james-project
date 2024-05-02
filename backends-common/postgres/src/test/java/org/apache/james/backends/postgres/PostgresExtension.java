/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.backends.postgres;

import static org.apache.james.backends.postgres.PostgresFixture.Database.DEFAULT_DATABASE;
import static org.apache.james.backends.postgres.PostgresFixture.Database.ROW_LEVEL_SECURITY_DATABASE;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.backends.postgres.utils.DomainImplPostgresConnectionFactory;
import org.apache.james.backends.postgres.utils.PoolBackedPostgresConnectionFactory;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.backends.postgres.utils.SinglePostgresConnectionFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.PostgreSQLContainer;

import com.github.fge.lambdas.Throwing;
import com.google.inject.Module;
import com.google.inject.util.Modules;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresExtension implements GuiceModuleTestExtension {
    public enum PoolSize {
        SMALL(1, 2),
        LARGE(10, 20);

        private final int min;
        private final int max;

        PoolSize(int min, int max) {
            this.min = min;
            this.max = max;
        }

        public int getMin() {
            return min;
        }

        public int getMax() {
            return max;
        }
    }

    private static final boolean ROW_LEVEL_SECURITY_ENABLED = true;

    public static PostgresExtension withRowLevelSecurity(PostgresModule module) {
        return new PostgresExtension(module, ROW_LEVEL_SECURITY_ENABLED);
    }

    public static PostgresExtension withoutRowLevelSecurity(PostgresModule module) {
        return withoutRowLevelSecurity(module, PoolSize.SMALL);
    }

    public static PostgresExtension withoutRowLevelSecurity(PostgresModule module, PoolSize poolSize) {
        return new PostgresExtension(module, !ROW_LEVEL_SECURITY_ENABLED, Optional.of(poolSize));
    }

    public static PostgresExtension empty() {
        return withoutRowLevelSecurity(PostgresModule.EMPTY_MODULE);
    }

    public static final PoolSize DEFAULT_POOL_SIZE = PoolSize.SMALL;
    public static PostgreSQLContainer<?> PG_CONTAINER = DockerPostgresSingleton.SINGLETON;
    private final PostgresModule postgresModule;
    private final boolean rlsEnabled;
    private final PostgresFixture.Database selectedDatabase;
    private PoolSize poolSize;
    private PostgresConfiguration postgresConfiguration;
    private PostgresExecutor postgresExecutor;
    private PostgresExecutor nonRLSPostgresExecutor;
    private PostgresqlConnectionFactory connectionFactory;
    private Connection superConnection;
    private PostgresExecutor.Factory executorFactory;
    private PostgresTableManager postgresTableManager;

    public void pause() {
        PG_CONTAINER.getDockerClient().pauseContainerCmd(PG_CONTAINER.getContainerId())
            .exec();
    }

    public void unpause() {
        PG_CONTAINER.getDockerClient().unpauseContainerCmd(PG_CONTAINER.getContainerId())
            .exec();
    }

    private PostgresExtension(PostgresModule postgresModule, boolean rlsEnabled) {
        this(postgresModule, rlsEnabled, Optional.empty());
    }

    private PostgresExtension(PostgresModule postgresModule, boolean rlsEnabled, Optional<PoolSize> maybePoolSize) {
        this.postgresModule = postgresModule;
        this.rlsEnabled = rlsEnabled;
        if (rlsEnabled) {
            this.selectedDatabase = PostgresFixture.Database.ROW_LEVEL_SECURITY_DATABASE;
        } else {
            this.selectedDatabase = DEFAULT_DATABASE;
            this.poolSize = maybePoolSize.orElse(DEFAULT_POOL_SIZE);
        }
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        if (!PG_CONTAINER.isRunning()) {
            PG_CONTAINER.start();
        }
        querySettingRowLevelSecurityIfNeed();
        querySettingExtension();
        initPostgresSession();
    }

    private void querySettingRowLevelSecurityIfNeed() {
        if (rlsEnabled) {
            Throwing.runnable(() -> {
                PG_CONTAINER.execInContainer("psql", "-U", DEFAULT_DATABASE.dbUser(), "-c", "create user " + ROW_LEVEL_SECURITY_DATABASE.dbUser() + " WITH PASSWORD '" + ROW_LEVEL_SECURITY_DATABASE.dbPassword() + "';");
                PG_CONTAINER.execInContainer("psql", "-U", DEFAULT_DATABASE.dbUser(), "-c", "create database " + ROW_LEVEL_SECURITY_DATABASE.dbName() + ";");
                PG_CONTAINER.execInContainer("psql", "-U", DEFAULT_DATABASE.dbUser(), "-c", "grant all privileges on database " + ROW_LEVEL_SECURITY_DATABASE.dbName() + " to " + ROW_LEVEL_SECURITY_DATABASE.dbUser() + ";");
                PG_CONTAINER.execInContainer("psql", "-U", ROW_LEVEL_SECURITY_DATABASE.dbUser(), "-d", ROW_LEVEL_SECURITY_DATABASE.dbName(), "-c", "create schema if not exists " + ROW_LEVEL_SECURITY_DATABASE.schema() + ";");
            }).sneakyThrow().run();
        }
    }

    private void querySettingExtension() throws IOException, InterruptedException {
        PG_CONTAINER.execInContainer("psql", "-U", selectedDatabase.dbUser(), selectedDatabase.dbName(), "-c", String.format("CREATE EXTENSION IF NOT EXISTS hstore SCHEMA %s;", selectedDatabase.schema()));
    }

    private void initPostgresSession() {
        postgresConfiguration = PostgresConfiguration.builder()
            .databaseName(selectedDatabase.dbName())
            .databaseSchema(selectedDatabase.schema())
            .host(getHost())
            .port(getMappedPort())
            .username(selectedDatabase.dbUser())
            .password(selectedDatabase.dbPassword())
            .nonRLSUser(DEFAULT_DATABASE.dbUser())
            .nonRLSPassword(DEFAULT_DATABASE.dbPassword())
            .rowLevelSecurityEnabled(rlsEnabled)
            .build();

        PostgresqlConnectionConfiguration.Builder connectionBaseBuilder = PostgresqlConnectionConfiguration.builder()
            .host(postgresConfiguration.getHost())
            .port(postgresConfiguration.getPort())
            .database(postgresConfiguration.getDatabaseName())
            .schema(postgresConfiguration.getDatabaseSchema());

        connectionFactory = new PostgresqlConnectionFactory(connectionBaseBuilder
            .username(postgresConfiguration.getCredential().getUsername())
            .password(postgresConfiguration.getCredential().getPassword())
            .build());

        superConnection = connectionFactory.create().block();

        if (rlsEnabled) {
            executorFactory = new PostgresExecutor.Factory(new DomainImplPostgresConnectionFactory(connectionFactory), postgresConfiguration, new RecordingMetricFactory());
        } else {
            executorFactory = new PostgresExecutor.Factory(
                new PoolBackedPostgresConnectionFactory(false,
                    poolSize.getMin(),
                    poolSize.getMax(),
                    connectionFactory),
                postgresConfiguration,
                new RecordingMetricFactory());
        }

        postgresExecutor = executorFactory.create();
        if (rlsEnabled) {
            nonRLSPostgresExecutor = Mono.just(connectionBaseBuilder
                    .username(postgresConfiguration.getNonRLSCredential().getUsername())
                    .password(postgresConfiguration.getNonRLSCredential().getPassword())
                    .build())
                .flatMap(configuration -> new PostgresqlConnectionFactory(configuration).create().cache())
                .map(connection -> new PostgresExecutor.Factory(new SinglePostgresConnectionFactory(connection), postgresConfiguration, new RecordingMetricFactory()).create())
                .block();
        } else {
            nonRLSPostgresExecutor = postgresExecutor;
        }

        this.postgresTableManager = new PostgresTableManager(new PostgresExecutor.Factory(new SinglePostgresConnectionFactory(superConnection), postgresConfiguration, new RecordingMetricFactory()).create(),
            postgresModule,
            postgresConfiguration);
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        disposePostgresSession();
    }

    private void disposePostgresSession() {
        postgresExecutor.dispose();
        nonRLSPostgresExecutor.dispose();
        superConnection.close();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        initTablesAndIndexes();
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        resetSchema();
    }

    public void restartContainer() {
        PG_CONTAINER.stop();
        PG_CONTAINER.start();
        initPostgresSession();
    }

    @Override
    public Module getModule() {
        return Modules.combine(binder -> binder.bind(PostgresConfiguration.class)
            .toInstance(postgresConfiguration));
    }

    public String getHost() {
        return PG_CONTAINER.getHost();
    }

    public Integer getMappedPort() {
        return PG_CONTAINER.getMappedPort(PostgresFixture.PORT);
    }

    public Mono<Connection> getConnection() {
        return Mono.just(superConnection);
    }

    public PostgresExecutor getPostgresExecutor() {
        return postgresExecutor;
    }

    public PostgresExecutor getNonRLSPostgresExecutor() {
        return nonRLSPostgresExecutor;
    }

    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public PostgresExecutor.Factory getExecutorFactory() {
        return executorFactory;
    }

    public PostgresConfiguration getPostgresConfiguration() {
        return postgresConfiguration;
    }

    private void initTablesAndIndexes() {
        postgresTableManager.initializeTables().block();
        postgresTableManager.initializeTableIndexes().block();
    }

    private void resetSchema() {
        List<String> tables = postgresTableManager.listExistTables().block();
        dropTables(tables);
    }

    private void dropTables(List<String> tables) {
        String tablesToDelete = tables.stream()
            .map(tableName -> "\"" + tableName + "\"")
            .collect(Collectors.joining(", "));

        Flux.from(superConnection.createStatement(String.format("DROP table if exists %s cascade;", tablesToDelete))
            .execute())
            .then()
            .block();
    }
}
