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

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.james.core.StringEnumerationElement;

public class QuotaComponentEnumeration {

    private static final List<QuotaComponent> LIST = new ArrayList<>();

    public static final QuotaComponent MAILBOX = add("MAILBOX");
    public static final QuotaComponent SIEVE = add("SIEVE");
    public static final QuotaComponent JMAP_UPLOADS = add("JMAP_UPLOADS");

    public static final QuotaComponent valueOf(String value) {
        for (QuotaComponent quotaComponent : LIST) {
            if (quotaComponent.getValue().equals(value)) {
                return quotaComponent;
            }
        }
        throw new NoSuchElementException();
    }

    protected static final QuotaComponent add(String value) {
        QuotaComponent quotaComponent = new QuotaComponent(value);
        LIST.add(quotaComponent);
        return quotaComponent;
    }

    public static class QuotaComponent extends StringEnumerationElement {
        private QuotaComponent(String value) {
            super(value);
        }
    }

}
