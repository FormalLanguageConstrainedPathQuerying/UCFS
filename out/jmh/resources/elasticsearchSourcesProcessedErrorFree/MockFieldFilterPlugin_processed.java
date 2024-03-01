/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.mapper;

import org.elasticsearch.plugins.MapperPlugin;
import org.elasticsearch.plugins.Plugin;

import java.util.function.Function;
import java.util.function.Predicate;

public class MockFieldFilterPlugin extends Plugin implements MapperPlugin {

    @Override
    public Function<String, Predicate<String>> getFieldFilter() {
        return index -> field -> true;
    }
}
