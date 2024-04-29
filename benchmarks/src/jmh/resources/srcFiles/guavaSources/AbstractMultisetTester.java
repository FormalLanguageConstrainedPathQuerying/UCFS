/*
 * Copyright (C) 2008 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http:
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.collect.testing.google;

import com.google.common.annotations.GwtCompatible;
import com.google.common.collect.Multiset;
import com.google.common.collect.testing.AbstractCollectionTester;
import org.junit.Ignore;

/**
 * Base class for multiset collection tests.
 *
 * @author Jared Levy
 */
@GwtCompatible
@Ignore 
public class AbstractMultisetTester<E> extends AbstractCollectionTester<E> {
  protected final Multiset<E> getMultiset() {
    return (Multiset<E>) collection;
  }

  protected void initThreeCopies() {
    collection = getSubjectGenerator().create(e0(), e0(), e0());
  }
}
