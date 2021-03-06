/*
 * UnorderedUnionCursor.java
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

package com.apple.foundationdb.record.provider.foundationdb.cursors;

import com.apple.foundationdb.API;
import com.apple.foundationdb.async.AsyncUtil;
import com.apple.foundationdb.record.RecordCursor;
import com.apple.foundationdb.record.RecordCursorResult;
import com.apple.foundationdb.record.provider.foundationdb.FDBStoreTimer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * A cursor that returns the results of two or more cursors that may return elements in any order. This cursor makes
 * no guarantees as to the order of elements it returns, and it may return the same element more than once as it does
 * not make any attempt to de-duplicate elements that appear in multiple of its source cursors. It attempts to return
 * elements from its children "as they come", which means that it might be the case that identical cursors of this type
 * may return results in two different orders even if all of its child cursors all return the same results if different
 * children happen to be faster in one run than the other (due to, for example, non-determinism in sending messages across
 * the network).
 *
 * <p>
 * If there are limits applied to the children of this cursor, this cursor will continue to emit elements as long as
 * there remains at least one child cursor who has not yet returned its last result. (For example, if this cursor
 * has two children and one of them completes faster than the other due to hitting some limit, then the union cursor
 * will continue returning results from the other cursor.) This differs from the behavior of the ordered
 * {@link UnionCursor}.
 * </p>
 *
 * @param <T> the type of elements returned by the cursor
 */
@API(API.Status.EXPERIMENTAL)
public class UnorderedUnionCursor<T> extends UnionCursorBase<T> {

    protected UnorderedUnionCursor(@Nonnull List<CursorState<T>> cursorStates,
                                   @Nullable FDBStoreTimer timer) {
        super(cursorStates, timer);
    }

    @Nonnull
    @Override
    CompletableFuture<Boolean> getIfAnyHaveNext(@Nonnull List<CursorState<T>> cursorStates) {
        AtomicBoolean unionHasNext = new AtomicBoolean(false);
        return AsyncUtil.whileTrue(() -> whenAny(cursorStates).thenApply(vignore -> {
            boolean anyHasNext = false;
            boolean allDone = true;
            for (CursorState<T> cursorState : cursorStates) {
                if (!cursorState.getOnNextFuture().isDone()) {
                    allDone = false;
                    continue;
                }
                final RecordCursorResult<T> result = cursorState.getResult();
                if (result.hasNext()) {
                    // Found a cursor with an element.
                    anyHasNext = true;
                    allDone = false;
                }
            }
            // If any cursor has another element, return that there is another element
            // for the union. If no element was found, it was because the child
            // cursor that became ready was exhausted and the cursor needs to
            // keep looking for another (so this loops again).
            if (anyHasNext) {
                unionHasNext.set(true);
            }
            return !allDone && !anyHasNext;
        }), getExecutor()).thenApply(vignore -> unionHasNext.get());
    }

    @Override
    void chooseStates(@Nonnull List<CursorState<T>> allStates, @Nonnull List<CursorState<T>> chosenStates, @Nonnull List<CursorState<T>> otherStates) {
        boolean found = false;
        for (CursorState<T> cursorState : allStates) {
            if (!found && cursorState.getOnNextFuture().isDone() && cursorState.getResult().hasNext()) {
                chosenStates.add(cursorState);
                found = true;
            } else {
                otherStates.add(cursorState);
            }
        }
    }

    /**
     * Create a union cursor from two or more cursors. Unlike the other {@link UnionCursor}, this does
     * not require that the child cursors return values in any particular order. The trade-off, however,
     * is that this cursor will not attempt to remove any duplicates from its children. It will return
     * results from its children as they become available.
     *
     * @param cursorFunctions a list of functions to produce {@link RecordCursor}s from a continuation
     * @param continuation any continuation from a previous scan
     * @param timer the timer used to instrument events
     * @param <T> the type of elements returned by this cursor
     * @return a cursor containing any records from any child cursor
     */
    @Nonnull
    public static <T> UnorderedUnionCursor<T> create(
            @Nonnull List<Function<byte[], RecordCursor<T>>> cursorFunctions,
            @Nullable byte[] continuation,
            @Nullable FDBStoreTimer timer) {
        return new UnorderedUnionCursor<>(createCursorStates(cursorFunctions, continuation), timer);
    }
}
