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

package org.apache.james.core.quota;

import java.util.Objects;
import java.util.Optional;

public class QuotaLimit {

    public static QuotaLimit of(QuotaComponent component, QuotaScope scope, String identifier, QuotaType quotaType, Long limit) {
        return new QuotaLimit(component, scope, identifier, quotaType, limit);
    }

    private final QuotaComponent quotaComponent;
    private final QuotaScope quotaScope;
    private final String identifier;
    private final QuotaType quotaType;
    private final Optional<Long> quotaLimit;

    private QuotaLimit(QuotaComponent quotaComponent, QuotaScope quotaScope, String identifier, QuotaType quotaType, Long quotaLimit) {
        this.quotaComponent = quotaComponent;
        this.quotaScope = quotaScope;
        this.identifier = identifier;
        this.quotaType = quotaType;
        this.quotaLimit = Optional.ofNullable(quotaLimit);
    }

    public QuotaComponent getQuotaComponent() {
        return quotaComponent;
    }

    public QuotaScope getQuotaScope() {
        return quotaScope;
    }

    public String getIdentifier() {
        return identifier;
    }

    public QuotaType getQuotaType() {
        return quotaType;
    }

    public Optional<Long> getQuotaLimit() {
        return quotaLimit;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(quotaComponent, quotaScope, identifier, quotaType, quotaLimit);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof QuotaLimit) {
            QuotaLimit other = (QuotaLimit) o;
            return Objects.equals(quotaComponent, other.quotaComponent)
                && Objects.equals(quotaScope, other.quotaScope)
                && Objects.equals(identifier, other.identifier)
                && Objects.equals(quotaType, other.quotaType)
                && Objects.equals(quotaLimit, other.quotaLimit);
        }
        return false;
    }
}