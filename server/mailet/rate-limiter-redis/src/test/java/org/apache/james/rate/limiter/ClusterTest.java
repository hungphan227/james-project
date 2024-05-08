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

package org.apache.james.rate.limiter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.james.backends.redis.RedisConfiguration;
import org.apache.james.rate.limiter.api.RateLimiterFactory;
import org.apache.james.rate.limiter.redis.RedisRateLimiterFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

import es.moki.ratelimitj.redis.request.RedisScriptLoader;
import io.lettuce.core.ReadFrom;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.api.sync.RedisScriptingCommands;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import io.lettuce.core.cluster.api.sync.RedisClusterCommands;
import io.lettuce.core.cluster.models.partitions.RedisClusterNode;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.masterslave.MasterSlave;
import io.lettuce.core.masterslave.StatefulRedisMasterSlaveConnection;
import reactor.core.publisher.Flux;

public class ClusterTest {
    RedisConfiguration redisConfiguration;

    @BeforeAll
    static void beforeAll() {
    }

    @AfterAll
    static void afterAll() {
    }

    @BeforeEach
    void beforeEach() {
    }

    @Test
    void test() {
        ClusterTopologyRefreshOptions topologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
            .enableAllAdaptiveRefreshTriggers()
            .build();

        ClusterClientOptions clientOptions = ClusterClientOptions.builder()
            .topologyRefreshOptions(topologyRefreshOptions)
            .build();
        RedisClusterClient clusterClient = RedisClusterClient.create("redis://password@localhost:6379");
        clusterClient.setOptions(clientOptions);
//        List<RedisClusterNode> list = clusterClient.getPartitions().getPartitions();
//        for (int i=0; i<1000; i++) {
//            clusterClient.refreshPartitions();
//            System.out.println();
//        }
        StatefulRedisClusterConnection<String, String> connection = clusterClient.connect();
        RedisAdvancedClusterCommands<String, String> syncCommands = connection.sync();

        for (int i=0; i<1000; i++) {
            try {
                System.out.println(syncCommands.get("key1"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
//            clusterClient.refreshPartitions();
        }

        System.out.println();
    }

    @Test
    void testLuaScript() {
        ClusterTopologyRefreshOptions topologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
            .enableAllAdaptiveRefreshTriggers()
            .build();

        ClusterClientOptions clientOptions = ClusterClientOptions.builder()
            .topologyRefreshOptions(topologyRefreshOptions)
            .build();
        RedisClusterClient client = RedisClusterClient.create("redis://password@localhost:6379");
        client.setOptions(clientOptions);

        StatefulRedisClusterConnection<String, String> connection = client.connect();

//        RedisClient client = RedisClient.create();
//
//        StatefulRedisMasterSlaveConnection<String, String> connection = MasterSlave.connect(client, StringCodec.UTF8,
//            RedisURI.create("redis-sentinel://localhost:26379,localhost:26380,localhost:26381/0#mymaster"));

        RedisScriptingCommands<String, String> redisScriptingCommands = connection.sync();
        redisScriptingCommands.scriptLoad("return 'Immabe a cached script'");

        RedisClusterCommands<String, String> syncCommands = connection.sync();
        System.out.println(syncCommands.get("key1"));

        System.out.println();
    }
}
