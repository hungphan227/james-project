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

package org.apache.james.mailbox.cassandra.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import javax.mail.Flags;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.modules.CassandraApplicableFlagsModule;
import org.apache.james.mailbox.cassandra.modules.CassandraDeletedMessageModule;
import org.apache.james.mailbox.cassandra.modules.CassandraFirstUnseenModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxCounterModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxRecentsModule;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.steveash.guavate.Guavate;

public class CassandraIndexTableHandlerTest {

    public static final CassandraId MAILBOX_ID = CassandraId.timeBased();
    public static final MessageUid MESSAGE_UID = MessageUid.of(18L);
    public static final CassandraMessageId CASSANDRA_MESSAGE_ID = new CassandraMessageId.Factory().generate();
    public static final int UID_VALIDITY = 15;
    public static final long MODSEQ = 17;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(
            CassandraMailboxCounterModule.MODULE,
            CassandraMailboxRecentsModule.MODULE,
            CassandraFirstUnseenModule.MODULE,
            CassandraApplicableFlagsModule.MODULE,
            CassandraDeletedMessageModule.MODULE));

    private CassandraMailboxCounterDAO mailboxCounterDAO;
    private CassandraMailboxRecentsDAO mailboxRecentsDAO;
    private CassandraApplicableFlagDAO applicableFlagDAO;
    private CassandraFirstUnseenDAO firstUnseenDAO;
    private CassandraIndexTableHandler testee;
    private CassandraDeletedMessageDAO deletedMessageDAO;
    private Mailbox mailbox;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        mailboxCounterDAO = new CassandraMailboxCounterDAO(cassandra.getConf());
        mailboxRecentsDAO = new CassandraMailboxRecentsDAO(cassandra.getConf());
        firstUnseenDAO = new CassandraFirstUnseenDAO(cassandra.getConf());
        applicableFlagDAO = new CassandraApplicableFlagDAO(cassandra.getConf());
        deletedMessageDAO = new CassandraDeletedMessageDAO(cassandra.getConf());

        testee = new CassandraIndexTableHandler(mailboxRecentsDAO,
                                                mailboxCounterDAO,
                                                firstUnseenDAO,
                                                applicableFlagDAO,
                                                deletedMessageDAO);

        mailbox = new SimpleMailbox(MailboxPath.forUser("user", "name"),
            UID_VALIDITY,
            MAILBOX_ID);
    }

    @Test
    void updateIndexOnAddShouldIncrementMessageCount() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);

        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        Optional<Long> actual = mailboxCounterDAO.countMessagesInMailbox(mailbox).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(1);
    }

    @Test
    void updateIndexOnAddShouldIncrementUnseenMessageCountWhenUnseen() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);

        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        Optional<Long> actual = mailboxCounterDAO.countUnseenMessagesInMailbox(mailbox).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(1);
    }

    @Test
    void updateIndexOnAddShouldNotIncrementUnseenMessageCountWhenSeen() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags(Flags.Flag.SEEN));
        when(message.getUid()).thenReturn(MESSAGE_UID);

        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        Optional<Long> actual = mailboxCounterDAO.countUnseenMessagesInMailbox(mailbox).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(0);
    }

    @Test
    void updateIndexOnAddShouldNotAddRecentWhenNoRecent() {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);

        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        assertThat(mailboxRecentsDAO.getRecentMessageUidsInMailbox(MAILBOX_ID).join()
            .collect(Guavate.toImmutableList()))
            .isEmpty();
    }

    @Test
    void updateIndexOnAddShouldAddRecentWhenRecent() {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags(Flags.Flag.RECENT));
        when(message.getUid()).thenReturn(MESSAGE_UID);

        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        assertThat(mailboxRecentsDAO.getRecentMessageUidsInMailbox(MAILBOX_ID).join()
            .collect(Guavate.toImmutableList()))
            .containsOnly(MESSAGE_UID);
    }

    @Test
    void updateIndexOnDeleteShouldDecrementMessageCount() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnDelete(new ComposedMessageIdWithMetaData(
                new ComposedMessageId(MAILBOX_ID, CASSANDRA_MESSAGE_ID, MESSAGE_UID),
                new Flags(Flags.Flag.RECENT),
                MODSEQ),
            MAILBOX_ID).join();

        Optional<Long> actual = mailboxCounterDAO.countMessagesInMailbox(mailbox).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(0);
    }

    @Test
    void updateIndexOnDeleteShouldDecrementUnseenMessageCountWhenUnseen() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnDelete(new ComposedMessageIdWithMetaData(
                new ComposedMessageId(MAILBOX_ID, CASSANDRA_MESSAGE_ID, MESSAGE_UID),
                new Flags(),
                MODSEQ),
            MAILBOX_ID).join();

        Optional<Long> actual = mailboxCounterDAO.countUnseenMessagesInMailbox(mailbox).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(0);
    }

    @Test
    void updateIndexOnDeleteShouldNotDecrementUnseenMessageCountWhenSeen() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnDelete(new ComposedMessageIdWithMetaData(
                new ComposedMessageId(MAILBOX_ID, CASSANDRA_MESSAGE_ID, MESSAGE_UID),
                new Flags(Flags.Flag.SEEN),
                MODSEQ),
            MAILBOX_ID).join();

        Optional<Long> actual = mailboxCounterDAO.countUnseenMessagesInMailbox(mailbox).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(1);
    }

    @Test
    void updateIndexOnDeleteShouldRemoveRecentWhenRecent() {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags(Flags.Flag.RECENT));
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnDelete(new ComposedMessageIdWithMetaData(
                new ComposedMessageId(MAILBOX_ID, CASSANDRA_MESSAGE_ID, MESSAGE_UID),
                new Flags(Flags.Flag.RECENT),
                MODSEQ),
            MAILBOX_ID).join();

        assertThat(mailboxRecentsDAO.getRecentMessageUidsInMailbox(MAILBOX_ID).join()
            .collect(Guavate.toImmutableList()))
            .isEmpty();
    }

    @Test
    void updateIndexOnDeleteShouldRemoveUidFromRecentAnyway() {
        // Clean up strategy if some flags updates missed
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags(Flags.Flag.RECENT));
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnDelete(new ComposedMessageIdWithMetaData(
                new ComposedMessageId(MAILBOX_ID, CASSANDRA_MESSAGE_ID, MESSAGE_UID),
                new Flags(),
                MODSEQ),
            MAILBOX_ID).join();

        assertThat(mailboxRecentsDAO.getRecentMessageUidsInMailbox(MAILBOX_ID).join()
            .collect(Guavate.toImmutableList()))
            .isEmpty();
    }

    @Test
    void updateIndexOnDeleteShouldDeleteMessageFromDeletedMessage() {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.getUid()).thenReturn(MESSAGE_UID);
        deletedMessageDAO.addDeleted(MAILBOX_ID, MESSAGE_UID).join();

        testee.updateIndexOnDelete(new ComposedMessageIdWithMetaData(
                new ComposedMessageId(MAILBOX_ID, CASSANDRA_MESSAGE_ID, MESSAGE_UID),
                new Flags(),
                MODSEQ),
            MAILBOX_ID).join();

        assertThat(
            deletedMessageDAO
                .retrieveDeletedMessage(MAILBOX_ID, MessageRange.all())
                .join()
                .collect(Guavate.toImmutableList()))
            .isEmpty();
    }

    @Test
    void updateIndexOnFlagsUpdateShouldNotChangeMessageCount() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags(Flags.Flag.RECENT))
            .oldFlags(new Flags())
            .modSeq(MODSEQ)
            .build()).join();

        Optional<Long> actual = mailboxCounterDAO.countMessagesInMailbox(mailbox).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(1);
    }

    @Test
    void updateIndexOnFlagsUpdateShouldDecrementUnseenMessageCountWhenSeenIsSet() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags(Flags.Flag.SEEN))
            .oldFlags(new Flags())
            .modSeq(MODSEQ)
            .build()).join();

        Optional<Long> actual = mailboxCounterDAO.countUnseenMessagesInMailbox(mailbox).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(0);
    }

    @Test
    void updateIndexOnFlagsUpdateShouldSaveMessageInDeletedMessageWhenDeletedFlagIsSet() {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags(Flags.Flag.DELETED))
            .oldFlags(new Flags())
            .modSeq(MODSEQ)
            .build()).join();

        assertThat(
            deletedMessageDAO
                .retrieveDeletedMessage(MAILBOX_ID, MessageRange.all())
                .join()
                .collect(Guavate.toImmutableList()))
            .containsExactly(MESSAGE_UID);
    }

    @Test
    void updateIndexOnFlagsUpdateShouldRemoveMessageInDeletedMessageWhenDeletedFlagIsUnset() {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        deletedMessageDAO.addDeleted(MAILBOX_ID, MESSAGE_UID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags())
            .oldFlags(new Flags(Flags.Flag.DELETED))
            .modSeq(MODSEQ)
            .build()).join();

        assertThat(
            deletedMessageDAO
                .retrieveDeletedMessage(MAILBOX_ID, MessageRange.all())
                .join()
                .collect(Guavate.toImmutableList()))
            .isEmpty();
    }

    @Test
    void updateIndexOnFlagsUpdateShouldNotRemoveMessageInDeletedMessageWhenDeletedFlagIsNotUnset() {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        deletedMessageDAO.addDeleted(MAILBOX_ID, MESSAGE_UID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags())
            .oldFlags(new Flags(Flags.Flag.SEEN))
            .modSeq(MODSEQ)
            .build()).join();

        assertThat(
            deletedMessageDAO
                .retrieveDeletedMessage(MAILBOX_ID, MessageRange.all())
                .join()
                .collect(Guavate.toImmutableList()))
            .containsExactly(MESSAGE_UID);
    }

    @Test
    void updateIndexOnFlagsUpdateShouldNotSaveMessageInDeletedMessageWhenDeletedFlagIsNotSet() {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags(Flags.Flag.RECENT))
            .oldFlags(new Flags())
            .modSeq(MODSEQ)
            .build()).join();

        assertThat(
            deletedMessageDAO
                .retrieveDeletedMessage(MAILBOX_ID, MessageRange.all())
                .join()
                .collect(Guavate.toImmutableList()))
            .isEmpty();
    }

    @Test
    void updateIndexOnFlagsUpdateShouldIncrementUnseenMessageCountWhenSeenIsUnset() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags(Flags.Flag.SEEN));
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags())
            .oldFlags(new Flags(Flags.Flag.SEEN))
            .modSeq(MODSEQ)
            .build()).join();

        Optional<Long> actual = mailboxCounterDAO.countUnseenMessagesInMailbox(mailbox).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(1);
    }

    @Test
    void updateIndexOnFlagsUpdateShouldNotChangeUnseenCountWhenBothSeen() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags(Flags.Flag.SEEN));
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags(Flags.Flag.SEEN))
            .oldFlags(new Flags(Flags.Flag.SEEN))
            .modSeq(MODSEQ)
            .build()).join();

        Optional<Long> actual = mailboxCounterDAO.countUnseenMessagesInMailbox(mailbox).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(0);
    }

    @Test
    void updateIndexOnFlagsUpdateShouldNotChangeUnseenCountWhenBothUnSeen() throws Exception {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags())
            .oldFlags(new Flags())
            .modSeq(MODSEQ)
            .build()).join();

        Optional<Long> actual = mailboxCounterDAO.countUnseenMessagesInMailbox(mailbox).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(1);
    }

    @Test
    void updateIndexOnFlagsUpdateShouldAddRecentOnSettingRecentFlag() {
        // Clean up strategy if some flags updates missed
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags(Flags.Flag.RECENT))
            .oldFlags(new Flags())
            .modSeq(MODSEQ)
            .build()).join();

        assertThat(mailboxRecentsDAO.getRecentMessageUidsInMailbox(MAILBOX_ID).join()
            .collect(Guavate.toImmutableList()))
            .containsOnly(MESSAGE_UID);
    }

    @Test
    void updateIndexOnFlagsUpdateShouldRemoveRecentOnUnsettingRecentFlag() {
        // Clean up strategy if some flags updates missed
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags(Flags.Flag.RECENT));
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags())
            .oldFlags(new Flags(Flags.Flag.RECENT))
            .modSeq(MODSEQ)
            .build()).join();

        assertThat(mailboxRecentsDAO.getRecentMessageUidsInMailbox(MAILBOX_ID).join()
            .collect(Guavate.toImmutableList()))
            .isEmpty();
    }

    @Test
    void updateIndexOnAddShouldUpdateFirstUnseenWhenUnseen() {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        Optional<MessageUid> actual = firstUnseenDAO.retrieveFirstUnread(MAILBOX_ID).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(MESSAGE_UID);
    }

    @Test
    void updateIndexOnAddShouldSaveMessageInDeletedWhenDeleted() {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags(Flags.Flag.DELETED));
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        assertThat(
            deletedMessageDAO
                .retrieveDeletedMessage(MAILBOX_ID, MessageRange.all())
                .join()
                .collect(Guavate.toImmutableList()))
            .containsExactly(MESSAGE_UID);
    }

    @Test
    void updateIndexOnAddShouldNotSaveMessageInDeletedWhenNotDeleted() {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        assertThat(
            deletedMessageDAO
                .retrieveDeletedMessage(MAILBOX_ID, MessageRange.all())
                .join()
                .collect(Guavate.toImmutableList()))
            .isEmpty();
    }

    @Test
    void updateIndexOnAddShouldNotUpdateFirstUnseenWhenSeen() {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags(Flags.Flag.SEEN));
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        Optional<MessageUid> actual = firstUnseenDAO.retrieveFirstUnread(MAILBOX_ID).join();
        assertThat(actual.isPresent()).isFalse();
    }

    @Test
    void updateIndexOnFlagsUpdateShouldUpdateLastUnseenWhenSetToSeen() {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags(Flags.Flag.SEEN))
            .oldFlags(new Flags())
            .modSeq(MODSEQ)
            .build()).join();

        Optional<MessageUid> actual = firstUnseenDAO.retrieveFirstUnread(MAILBOX_ID).join();
        assertThat(actual.isPresent()).isFalse();
    }

    @Test
    void updateIndexOnFlagsUpdateShouldUpdateLastUnseenWhenSetToUnseen() {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags(Flags.Flag.SEEN));
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags())
            .oldFlags(new Flags(Flags.Flag.SEEN))
            .modSeq(MODSEQ)
            .build()).join();

        Optional<MessageUid> actual = firstUnseenDAO.retrieveFirstUnread(MAILBOX_ID).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(MESSAGE_UID);
    }

    @Test
    void updateIndexOnFlagsUpdateShouldNotUpdateLastUnseenWhenKeepUnseen() {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags())
            .oldFlags(new Flags())
            .modSeq(MODSEQ)
            .build()).join();

        Optional<MessageUid> actual = firstUnseenDAO.retrieveFirstUnread(MAILBOX_ID).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(MESSAGE_UID);
    }

    @Test
    void updateIndexOnFlagsUpdateShouldNotUpdateLastUnseenWhenKeepSeen() {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags(Flags.Flag.SEEN));
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags(Flags.Flag.SEEN))
            .oldFlags(new Flags(Flags.Flag.SEEN))
            .modSeq(MODSEQ)
            .build()).join();

        Optional<MessageUid> actual = firstUnseenDAO.retrieveFirstUnread(MAILBOX_ID).join();
        assertThat(actual.isPresent()).isFalse();
    }

    @Test
    void updateIndexOnDeleteShouldUpdateFirstUnseenWhenUnseen() {
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(new Flags());
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnDelete(new ComposedMessageIdWithMetaData(
            new ComposedMessageId(MAILBOX_ID, CASSANDRA_MESSAGE_ID, MESSAGE_UID),
            new Flags(),
            MODSEQ), MAILBOX_ID).join();

        Optional<MessageUid> actual = firstUnseenDAO.retrieveFirstUnread(MAILBOX_ID).join();
        assertThat(actual.isPresent()).isFalse();
    }

    @Test
    void updateIndexOnAddShouldUpdateApplicableFlag() {
        Flags customFlags = new Flags("custom");
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(customFlags);
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        Flags applicableFlag = applicableFlagDAO.retrieveApplicableFlag(MAILBOX_ID).join().get();

        assertThat(applicableFlag).isEqualTo(customFlags);
    }

    @Test
    void updateIndexOnFlagsUpdateShouldUnionApplicableFlag() {
        Flags customFlag = new Flags("custom");
        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(customFlag);
        when(message.getUid()).thenReturn(MESSAGE_UID);
        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        Flags customBis = new Flags("customBis");
        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(customBis)
            .oldFlags(customFlag)
            .modSeq(MODSEQ)
            .build()).join();

        Flags applicableFlag = applicableFlagDAO.retrieveApplicableFlag(MAILBOX_ID).join().get();

        assertThat(applicableFlag).isEqualTo(new FlagsBuilder().add(customFlag, customBis).build());
    }

    @Test
    void applicableFlagShouldKeepAllFlagsEvenTheMessageRemovesFlag() {
        Flags messageFlags = FlagsBuilder.builder()
            .add("custom1", "custom2", "custom3")
            .build();

        MailboxMessage message = mock(MailboxMessage.class);
        when(message.createFlags()).thenReturn(messageFlags);
        when(message.getUid()).thenReturn(MESSAGE_UID);

        testee.updateIndexOnAdd(message, MAILBOX_ID).join();

        testee.updateIndexOnFlagsUpdate(MAILBOX_ID, UpdatedFlags.builder()
            .uid(MESSAGE_UID)
            .newFlags(new Flags())
            .oldFlags(messageFlags)
            .modSeq(MODSEQ)
            .build()).join();

        Flags applicableFlag = applicableFlagDAO.retrieveApplicableFlag(MAILBOX_ID).join().get();
        assertThat(applicableFlag).isEqualTo(messageFlags);
    }
}
