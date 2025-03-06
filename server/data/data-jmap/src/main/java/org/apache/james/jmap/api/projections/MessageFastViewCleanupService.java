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

package org.apache.james.jmap.api.projections;

import jakarta.inject.Inject;

import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MessageFastViewCleanupService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageFastViewCleanupService.class);
    private static final int DEFAULT_MESSAGE_IDS_PER_SECOND = 1000;

    private final MessageFastViewProjection messageFastViewProjection;
    private final MessageService messageService;

    @Inject
    public MessageFastViewCleanupService(MessageFastViewProjection messageFastViewProjection, MessageService messageService) {
        this.messageFastViewProjection = messageFastViewProjection;
        this.messageService = messageService;
    }

    public Mono<Void> cleanup() {
        return Flux.from(messageFastViewProjection.getAllMessageIds())
            .flatMap(messageId -> exist(messageId)
                    .filter(exist -> !exist)
                    .map(any -> messageId),
                DEFAULT_MESSAGE_IDS_PER_SECOND)
            .flatMap(messageId -> Mono.from(messageFastViewProjection.delete(messageId)))
            .then()
            .doFinally(any -> LOGGER.info("Message fast view cleanup complete"));
    }

    private Mono<Boolean> exist(MessageId messageId) {
        return messageService.exist(messageId);
    }
}
