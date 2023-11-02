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

import static org.assertj.core.api.Assertions.assertThat;
import java.util.stream.Collectors;

import org.apache.james.backends.postgres.utils.JamesPostgresConnectionFactory;
import org.apache.james.core.Domain;
import org.junit.jupiter.api.Test;

import io.r2dbc.postgresql.api.PostgresqlConnection;

public abstract class JamesPostgresConnectionFactoryTest {

    abstract JamesPostgresConnectionFactory jamesPostgresConnectionFactory();

    @Test
    void getConnectionShouldWork() {
        PostgresqlConnection connection = jamesPostgresConnectionFactory().getConnection().block();
        String actual = connection.createStatement("SELECT 1")
            .execute()
            .flatMap(result -> result.map((row, rowMetadata) -> row.get(0, String.class)))
            .collect(Collectors.toUnmodifiableList())
            .block().get(0);

        assertThat(actual).isEqualTo("1");
    }

    @Test
    void getConnectionWithDomainShouldWork() {
        PostgresqlConnection connection = jamesPostgresConnectionFactory().getConnection(Domain.of("james")).block();
        String actual = connection.createStatement("SELECT 1")
            .execute()
            .flatMap(result -> result.map((row, rowMetadata) -> row.get(0, String.class)))
            .collect(Collectors.toUnmodifiableList())
            .block().get(0);

        assertThat(actual).isEqualTo("1");
    }

    @Test
    void getConnectionShouldSetCurrentDomainAttribute() {
        Domain domain = Domain.of("james");
        PostgresqlConnection connection = jamesPostgresConnectionFactory().getConnection(domain).block();
        String actual = connection.createStatement("show " + JamesPostgresConnectionFactory.DOMAIN_ATTRIBUTE)
            .execute()
            .flatMap(result -> result.map((row, rowMetadata) -> row.get(0, String.class)))
            .collect(Collectors.toUnmodifiableList())
            .block().get(0);

        assertThat(actual).isEqualTo(domain.asString());
    }

}
