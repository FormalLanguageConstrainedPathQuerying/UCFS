/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.search.fetch.subphase;

import org.apache.lucene.index.LeafReaderContext;
import org.elasticsearch.common.document.DocumentField;
import org.elasticsearch.common.regex.Regex;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.mapper.NestedValueFetcher;
import org.elasticsearch.index.mapper.ValueFetcher;
import org.elasticsearch.index.query.SearchExecutionContext;
import org.elasticsearch.search.NestedUtils;
import org.elasticsearch.search.fetch.StoredFieldsSpec;
import org.elasticsearch.search.lookup.Source;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A helper class to {@link FetchFieldsPhase} that's initialized with a list of field patterns to fetch.
 * Then given a specific document, it can retrieve the corresponding fields through their corresponding {@link ValueFetcher}s.
 */
public class FieldFetcher {

    private record ResolvedField(String field, String matchingPattern, MappedFieldType ft, String format) {}

    /**
     * Build a FieldFetcher for a given search context and collection of fields and formats
     */
    public static FieldFetcher create(SearchExecutionContext context, Collection<FieldAndFormat> fieldAndFormats) {

        List<String> unmappedFetchPattern = new ArrayList<>();
        List<ResolvedField> resolvedFields = new ArrayList<>();

        for (FieldAndFormat fieldAndFormat : fieldAndFormats) {
            String fieldPattern = fieldAndFormat.field;
            String matchingPattern = Regex.isSimpleMatchPattern(fieldPattern) ? fieldPattern : null;
            if (fieldAndFormat.includeUnmapped != null && fieldAndFormat.includeUnmapped) {
                unmappedFetchPattern.add(fieldAndFormat.field);
            }

            for (String field : context.getMatchingFieldNames(fieldPattern)) {
                MappedFieldType ft = context.getFieldType(field);
                if (context.isMetadataField(field) && matchingPattern != null) {
                    continue;
                }
                resolvedFields.add(new ResolvedField(field, matchingPattern, ft, fieldAndFormat.format));
            }
        }

        resolvedFields.sort(Comparator.comparing(f -> f.field));

        Map<String, FieldContext> fieldContexts = buildFieldContexts(context, "", resolvedFields, unmappedFetchPattern);

        UnmappedFieldFetcher unmappedFieldFetcher = buildUnmappedFieldFetcher(context, fieldContexts.keySet(), "", unmappedFetchPattern);

        return new FieldFetcher(fieldContexts, unmappedFieldFetcher);
    }

    private static UnmappedFieldFetcher buildUnmappedFieldFetcher(
        SearchExecutionContext context,
        Set<String> mappedFields,
        String nestedScope,
        List<String> unmappedFetchPatterns
    ) {
        if (unmappedFetchPatterns.isEmpty()) {
            return UnmappedFieldFetcher.EMPTY;
        }
        return new UnmappedFieldFetcher(mappedFields, context.nestedLookup().getImmediateChildMappers(nestedScope), unmappedFetchPatterns);
    }

    private static ValueFetcher buildValueFetcher(SearchExecutionContext context, ResolvedField fieldAndFormat) {
        try {
            return fieldAndFormat.ft.valueFetcher(context, fieldAndFormat.format);
        } catch (IllegalArgumentException e) {
            StringBuilder error = new StringBuilder("error fetching [").append(fieldAndFormat.field).append(']');
            if (fieldAndFormat.matchingPattern != null) {
                error.append(" which matched [").append(fieldAndFormat.matchingPattern).append(']');
            }
            error.append(": ").append(e.getMessage());
            throw new IllegalArgumentException(error.toString(), e);
        }
    }

    private static Map<String, FieldContext> buildFieldContexts(
        SearchExecutionContext context,
        String nestedScope,
        List<ResolvedField> fields,
        List<String> unmappedFetchPatterns
    ) {

        final boolean includeUnmapped = unmappedFetchPatterns.isEmpty() == false;

        Map<String, List<ResolvedField>> fieldsByNestedMapper = NestedUtils.partitionByChildren(
            nestedScope,
            context.nestedLookup().getImmediateChildMappers(nestedScope),
            fields,
            f -> f.field
        );

        Map<String, FieldContext> output = new LinkedHashMap<>();
        for (String scope : fieldsByNestedMapper.keySet()) {
            if (nestedScope.equals(scope)) {
                for (ResolvedField ff : fieldsByNestedMapper.get(nestedScope)) {
                    output.put(ff.field, new FieldContext(ff.field, buildValueFetcher(context, ff)));
                }
            } else {
                if (includeUnmapped || fieldsByNestedMapper.get(scope).isEmpty() == false) {
                    Map<String, FieldContext> scopedFields = buildFieldContexts(
                        context,
                        scope,
                        fieldsByNestedMapper.get(scope),
                        unmappedFetchPatterns
                    );
                    UnmappedFieldFetcher unmappedFieldFetcher = buildUnmappedFieldFetcher(
                        context,
                        scopedFields.keySet(),
                        scope,
                        unmappedFetchPatterns
                    );
                    NestedValueFetcher nvf = new NestedValueFetcher(scope, new FieldFetcher(scopedFields, unmappedFieldFetcher));
                    output.put(scope, new FieldContext(scope, nvf));
                }
            }
        }
        return output;
    }

    private final Map<String, FieldContext> fieldContexts;
    private final UnmappedFieldFetcher unmappedFieldFetcher;
    private final StoredFieldsSpec storedFieldsSpec;

    private FieldFetcher(Map<String, FieldContext> fieldContexts, UnmappedFieldFetcher unmappedFieldFetcher) {
        this.fieldContexts = fieldContexts;
        this.unmappedFieldFetcher = unmappedFieldFetcher;
        this.storedFieldsSpec = StoredFieldsSpec.build(fieldContexts.values(), fc -> fc.valueFetcher.storedFieldsSpec());
    }

    public StoredFieldsSpec storedFieldsSpec() {
        return storedFieldsSpec;
    }

    public Map<String, DocumentField> fetch(Source source, int doc) throws IOException {
        Map<String, DocumentField> documentFields = new HashMap<>();
        for (FieldContext context : fieldContexts.values()) {
            String field = context.fieldName;
            ValueFetcher valueFetcher = context.valueFetcher;
            final DocumentField docField = valueFetcher.fetchDocumentField(field, source, doc);
            if (docField != null) {
                documentFields.put(field, docField);
            }
        }
        unmappedFieldFetcher.collectUnmapped(documentFields, source);
        return documentFields;
    }

    public void setNextReader(LeafReaderContext readerContext) {
        for (FieldContext field : fieldContexts.values()) {
            field.valueFetcher.setNextReader(readerContext);
        }
    }

    private record FieldContext(String fieldName, ValueFetcher valueFetcher) {}
}
