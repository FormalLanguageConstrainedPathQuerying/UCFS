/*
 * Copyright (C) 2013 The Guava Authors
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

package com.google.common.collect.testing.testers;

import static com.google.common.collect.testing.features.CollectionFeature.ALLOWS_NULL_VALUES;
import static com.google.common.collect.testing.features.CollectionFeature.KNOWN_ORDER;
import static com.google.common.collect.testing.features.CollectionFeature.SUPPORTS_ADD;
import static com.google.common.collect.testing.features.CollectionFeature.SUPPORTS_REMOVE;
import static com.google.common.collect.testing.features.CollectionSize.ZERO;

import com.google.common.annotations.GwtCompatible;
import com.google.common.annotations.GwtIncompatible;
import com.google.common.annotations.J2ktIncompatible;
import com.google.common.collect.testing.AbstractCollectionTester;
import com.google.common.collect.testing.Helpers;
import com.google.common.collect.testing.SpliteratorTester;
import com.google.common.collect.testing.features.CollectionFeature;
import com.google.common.collect.testing.features.CollectionSize;
import java.lang.reflect.Method;
import java.util.Spliterator;
import org.junit.Ignore;

/**
 * A generic JUnit test which tests {@code spliterator} operations on a collection. Can't be invoked
 * directly; please see {@link com.google.common.collect.testing.CollectionTestSuiteBuilder}.
 *
 * @author Louis Wasserman
 */
@GwtCompatible(emulated = true)
@Ignore 
public class CollectionSpliteratorTester<E> extends AbstractCollectionTester<E> {

  @CollectionFeature.Require(absent = KNOWN_ORDER)
  public void testSpliteratorUnknownOrder() {
    synchronized (collection) {
      SpliteratorTester.of(collection::spliterator).expect(getSampleElements());
    }
  }

  @CollectionFeature.Require(KNOWN_ORDER)
  public void testSpliteratorKnownOrder() {
    synchronized (collection) {
      SpliteratorTester.of(collection::spliterator).expect(getOrderedElements()).inOrder();
    }
  }

  @CollectionFeature.Require(ALLOWS_NULL_VALUES)
  @CollectionSize.Require(absent = ZERO)
  public void testSpliteratorNullable() {
    initCollectionWithNullElement();
    synchronized (collection) { 
      assertFalse(collection.spliterator().hasCharacteristics(Spliterator.NONNULL));
    }
  }

  @CollectionFeature.Require(SUPPORTS_ADD)
  public void testSpliteratorNotImmutable_CollectionAllowsAdd() {
    synchronized (collection) { 
      assertFalse(collection.spliterator().hasCharacteristics(Spliterator.IMMUTABLE));
    }
  }

  @CollectionFeature.Require(SUPPORTS_REMOVE)
  public void testSpliteratorNotImmutable_CollectionAllowsRemove() {
    synchronized (collection) { 
      assertFalse(collection.spliterator().hasCharacteristics(Spliterator.IMMUTABLE));
    }
  }

  @J2ktIncompatible
  @GwtIncompatible 
  public static Method getSpliteratorNotImmutableCollectionAllowsAddMethod() {
    return Helpers.getMethod(
        CollectionSpliteratorTester.class, "testSpliteratorNotImmutable_CollectionAllowsAdd");
  }

  @J2ktIncompatible
  @GwtIncompatible 
  public static Method getSpliteratorNotImmutableCollectionAllowsRemoveMethod() {
    return Helpers.getMethod(
        CollectionSpliteratorTester.class, "testSpliteratorNotImmutable_CollectionAllowsRemove");
  }
}
