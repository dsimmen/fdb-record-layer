/*
 * SubspaceProvider.java
 *
 * This source file is part of the FoundationDB open source project
 *
 * Copyright 2015-2018 Apple Inc. and the FoundationDB project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.apple.foundationdb.record.provider.foundationdb;

import com.apple.foundationdb.API;
import com.apple.foundationdb.record.logging.LogMessageKeys;
import com.apple.foundationdb.subspace.Subspace;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * Subspace provider can provide a subspace (might be blocking) and logging information to the subspace (non-blocking).
 */
@API(API.Status.MAINTAINED)
public interface SubspaceProvider {
    /**
     * This might be blocking if the subspace is never fetched before.
     *
     * @return Subspace
     */
    @Nonnull
    Subspace getSubspace();

    @Nonnull
    CompletableFuture<Subspace> getSubspaceAsync();

    @Nonnull
    SubspaceProvider getRebasedCopy(FDBRecordContext context);

    @Nonnull
    LogMessageKeys logKey();

    @Override
    String toString();
}
