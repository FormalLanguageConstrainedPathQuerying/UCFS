/*
 * Copyright (c) 2016-present, RxJava Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http:
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package io.reactivex.rxjava3.exceptions;

/**
 * Explicitly named exception to indicate a Reactive-Streams
 * protocol violation.
 * <p>History: 2.0.6 - experimental; 2.1 - beta
 * @since 2.2
 */
public final class ProtocolViolationException extends IllegalStateException {

    private static final long serialVersionUID = 1644750035281290266L;

    /**
     * Creates an instance with the given message.
     * @param message the message
     */
    public ProtocolViolationException(String message) {
        super(message);
    }
}
