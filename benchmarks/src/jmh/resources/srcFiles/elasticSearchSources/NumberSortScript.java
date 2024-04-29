/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */
package org.elasticsearch.script;

import org.elasticsearch.search.lookup.SearchLookup;

import java.io.IOException;
import java.util.Map;

public abstract class NumberSortScript extends AbstractSortScript {

    public static final String[] PARAMETERS = {};

    public static final ScriptContext<Factory> CONTEXT = new ScriptContext<>("number_sort", Factory.class);

    public NumberSortScript(Map<String, Object> params, SearchLookup searchLookup, DocReader docReader) {
        super(params, docReader);
    }

    protected NumberSortScript() {
        super();
    }

    public abstract double execute();

    /**
     * A factory to construct {@link NumberSortScript} instances.
     */
    public interface LeafFactory {
        NumberSortScript newInstance(DocReader reader) throws IOException;

        /**
         * Return {@code true} if the script needs {@code _score} calculated, or {@code false} otherwise.
         */
        boolean needs_score();
    }

    /**
     * A factory to construct stateful {@link NumberSortScript} factories for a specific index.
     */
    public interface Factory extends ScriptFactory {
        LeafFactory newFactory(Map<String, Object> params, SearchLookup searchLookup);
    }
}
