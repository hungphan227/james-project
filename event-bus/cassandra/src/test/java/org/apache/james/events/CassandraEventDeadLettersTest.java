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

package org.apache.james.events;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.events.EventBusTestFixture.TestEventSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableSet;

class CassandraEventDeadLettersTest implements EventDeadLettersContract.AllContracts {

    @RegisterExtension
    static CassandraClusterExtension cassandraClusterExtension = new CassandraClusterExtension(CassandraEventDeadLettersModule.MODULE);

    private CassandraEventDeadLetters eventDeadLetters;

    @BeforeEach
    void setUp(CassandraCluster cassandraCluster) {
        EventSerializer eventSerializer = new TestEventSerializer();
        eventDeadLetters = new CassandraEventDeadLetters(new CassandraEventDeadLettersDAO(cassandraCluster.getConf()),
            new CassandraEventDeadLettersGroupDAO(cassandraCluster.getConf()),
            eventSerializer,
            new EventDeserializers(ImmutableSet.of(eventSerializer)));
    }

    @Override
    public EventDeadLetters eventDeadLetters() {
        return eventDeadLetters;
    }
}
