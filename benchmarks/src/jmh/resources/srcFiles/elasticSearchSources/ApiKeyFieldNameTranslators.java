/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.security.support;

import org.elasticsearch.common.regex.Regex;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.MatchNoneQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.PrefixQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.SimpleQueryStringBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.index.query.WildcardQueryBuilder;
import org.elasticsearch.index.search.QueryParserHelper;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.elasticsearch.xpack.security.action.apikey.TransportQueryApiKeyAction.API_KEY_TYPE_RUNTIME_MAPPING_FIELD;

/**
 * A class to translate query level field names to index level field names.
 */
public class ApiKeyFieldNameTranslators {
    static final List<FieldNameTranslator> FIELD_NAME_TRANSLATORS;

    static {
        FIELD_NAME_TRANSLATORS = List.of(
            new ExactFieldNameTranslator(s -> "creator.principal", "username"),
            new ExactFieldNameTranslator(s -> "creator.realm", "realm_name"),
            new ExactFieldNameTranslator(s -> "name", "name"),
            new ExactFieldNameTranslator(s -> API_KEY_TYPE_RUNTIME_MAPPING_FIELD, "type"),
            new ExactFieldNameTranslator(s -> "creation_time", "creation"),
            new ExactFieldNameTranslator(s -> "expiration_time", "expiration"),
            new ExactFieldNameTranslator(s -> "api_key_invalidated", "invalidated"),
            new ExactFieldNameTranslator(s -> "invalidation_time", "invalidation"),
            new ExactFieldNameTranslator(s -> "metadata_flattened", "metadata"),
            new PrefixFieldNameTranslator(s -> "metadata_flattened." + s.substring("metadata.".length()), "metadata.")
        );
    }

    /**
     * Adds the {@param fieldSortBuilders} to the {@param searchSourceBuilder}, translating the field names,
     * form query level to index level, see {@link #translate}.
     * The optional {@param visitor} can be used to collect all the translated field names.
     */
    public static void translateFieldSortBuilders(
        List<FieldSortBuilder> fieldSortBuilders,
        SearchSourceBuilder searchSourceBuilder,
        @Nullable Consumer<String> visitor
    ) {
        final Consumer<String> fieldNameVisitor = visitor != null ? visitor : ignored -> {};
        fieldSortBuilders.forEach(fieldSortBuilder -> {
            if (fieldSortBuilder.getNestedSort() != null) {
                throw new IllegalArgumentException("nested sorting is not supported for API Key query");
            }
            if (FieldSortBuilder.DOC_FIELD_NAME.equals(fieldSortBuilder.getFieldName())) {
                searchSourceBuilder.sort(fieldSortBuilder);
            } else {
                final String translatedFieldName = translate(fieldSortBuilder.getFieldName());
                fieldNameVisitor.accept(translatedFieldName);
                if (translatedFieldName.equals(fieldSortBuilder.getFieldName())) {
                    searchSourceBuilder.sort(fieldSortBuilder);
                } else {
                    final FieldSortBuilder translatedFieldSortBuilder = new FieldSortBuilder(translatedFieldName).order(
                        fieldSortBuilder.order()
                    )
                        .missing(fieldSortBuilder.missing())
                        .unmappedType(fieldSortBuilder.unmappedType())
                        .setFormat(fieldSortBuilder.getFormat());

                    if (fieldSortBuilder.sortMode() != null) {
                        translatedFieldSortBuilder.sortMode(fieldSortBuilder.sortMode());
                    }
                    if (fieldSortBuilder.getNestedSort() != null) {
                        translatedFieldSortBuilder.setNestedSort(fieldSortBuilder.getNestedSort());
                    }
                    if (fieldSortBuilder.getNumericType() != null) {
                        translatedFieldSortBuilder.setNumericType(fieldSortBuilder.getNumericType());
                    }
                    searchSourceBuilder.sort(translatedFieldSortBuilder);
                }
            }
        });
    }

    /**
     * Deep copies the passed-in {@param queryBuilder} translating all the field names, from query level to index level,
     * see {@link  #translate}. In general, the returned builder should create the same query as if the query were
     * created by the passed in {@param queryBuilder}, only with the field names translated.
     * Field name patterns (including "*"), are also replaced with the explicit index level field names whose
     * associated query level field names match the pattern.
     * The optional {@param visitor} can be used to collect all the translated field names.
     */
    public static QueryBuilder translateQueryBuilderFields(QueryBuilder queryBuilder, @Nullable Consumer<String> visitor) {
        Objects.requireNonNull(queryBuilder, "unsupported \"null\" query builder for field name translation");
        final Consumer<String> fieldNameVisitor = visitor != null ? visitor : ignored -> {};
        if (queryBuilder instanceof final BoolQueryBuilder query) {
            final BoolQueryBuilder newQuery = QueryBuilders.boolQuery()
                .minimumShouldMatch(query.minimumShouldMatch())
                .adjustPureNegative(query.adjustPureNegative())
                .boost(query.boost())
                .queryName(query.queryName());
            query.must().stream().map(q -> translateQueryBuilderFields(q, fieldNameVisitor)).forEach(newQuery::must);
            query.should().stream().map(q -> translateQueryBuilderFields(q, fieldNameVisitor)).forEach(newQuery::should);
            query.mustNot().stream().map(q -> translateQueryBuilderFields(q, fieldNameVisitor)).forEach(newQuery::mustNot);
            query.filter().stream().map(q -> translateQueryBuilderFields(q, fieldNameVisitor)).forEach(newQuery::filter);
            return newQuery;
        } else if (queryBuilder instanceof final MatchAllQueryBuilder query) {
            return QueryBuilders.matchAllQuery().boost(query.boost()).queryName(query.queryName());
        } else if (queryBuilder instanceof final IdsQueryBuilder query) {
            return QueryBuilders.idsQuery().addIds(query.ids().toArray(new String[0])).boost(query.boost()).queryName(query.queryName());
        } else if (queryBuilder instanceof final TermQueryBuilder query) {
            final String translatedFieldName = translate(query.fieldName());
            fieldNameVisitor.accept(translatedFieldName);
            return QueryBuilders.termQuery(translatedFieldName, query.value())
                .caseInsensitive(query.caseInsensitive())
                .boost(query.boost())
                .queryName(query.queryName());
        } else if (queryBuilder instanceof final ExistsQueryBuilder query) {
            final String translatedFieldName = translate(query.fieldName());
            fieldNameVisitor.accept(translatedFieldName);
            return QueryBuilders.existsQuery(translatedFieldName).boost(query.boost()).queryName(query.queryName());
        } else if (queryBuilder instanceof final TermsQueryBuilder query) {
            if (query.termsLookup() != null) {
                throw new IllegalArgumentException("terms query with terms lookup is not supported for API Key query");
            }
            final String translatedFieldName = translate(query.fieldName());
            fieldNameVisitor.accept(translatedFieldName);
            return QueryBuilders.termsQuery(translatedFieldName, query.getValues()).boost(query.boost()).queryName(query.queryName());
        } else if (queryBuilder instanceof final PrefixQueryBuilder query) {
            final String translatedFieldName = translate(query.fieldName());
            fieldNameVisitor.accept(translatedFieldName);
            return QueryBuilders.prefixQuery(translatedFieldName, query.value())
                .caseInsensitive(query.caseInsensitive())
                .rewrite(query.rewrite())
                .boost(query.boost())
                .queryName(query.queryName());
        } else if (queryBuilder instanceof final WildcardQueryBuilder query) {
            final String translatedFieldName = translate(query.fieldName());
            fieldNameVisitor.accept(translatedFieldName);
            return QueryBuilders.wildcardQuery(translatedFieldName, query.value())
                .caseInsensitive(query.caseInsensitive())
                .rewrite(query.rewrite())
                .boost(query.boost())
                .queryName(query.queryName());
        } else if (queryBuilder instanceof final MatchQueryBuilder query) {
            final String translatedFieldName = translate(query.fieldName());
            fieldNameVisitor.accept(translatedFieldName);
            final MatchQueryBuilder matchQueryBuilder = QueryBuilders.matchQuery(translatedFieldName, query.value());
            if (query.operator() != null) {
                matchQueryBuilder.operator(query.operator());
            }
            if (query.analyzer() != null) {
                matchQueryBuilder.analyzer(query.analyzer());
            }
            if (query.fuzziness() != null) {
                matchQueryBuilder.fuzziness(query.fuzziness());
            }
            if (query.minimumShouldMatch() != null) {
                matchQueryBuilder.minimumShouldMatch(query.minimumShouldMatch());
            }
            if (query.fuzzyRewrite() != null) {
                matchQueryBuilder.fuzzyRewrite(query.fuzzyRewrite());
            }
            if (query.zeroTermsQuery() != null) {
                matchQueryBuilder.zeroTermsQuery(query.zeroTermsQuery());
            }
            matchQueryBuilder.prefixLength(query.prefixLength())
                .maxExpansions(query.maxExpansions())
                .fuzzyTranspositions(query.fuzzyTranspositions())
                .lenient(query.lenient())
                .autoGenerateSynonymsPhraseQuery(query.autoGenerateSynonymsPhraseQuery())
                .boost(query.boost())
                .queryName(query.queryName());
            return matchQueryBuilder;
        } else if (queryBuilder instanceof final RangeQueryBuilder query) {
            if (query.relation() != null) {
                throw new IllegalArgumentException("range query with relation is not supported for API Key query");
            }
            final String translatedFieldName = translate(query.fieldName());
            fieldNameVisitor.accept(translatedFieldName);
            final RangeQueryBuilder newQuery = QueryBuilders.rangeQuery(translatedFieldName);
            if (query.format() != null) {
                newQuery.format(query.format());
            }
            if (query.timeZone() != null) {
                newQuery.timeZone(query.timeZone());
            }
            if (query.from() != null) {
                newQuery.from(query.from()).includeLower(query.includeLower());
            }
            if (query.to() != null) {
                newQuery.to(query.to()).includeUpper(query.includeUpper());
            }
            return newQuery.boost(query.boost()).queryName(query.queryName());
        } else if (queryBuilder instanceof final SimpleQueryStringBuilder query) {
            SimpleQueryStringBuilder simpleQueryStringBuilder = QueryBuilders.simpleQueryStringQuery(query.value());
            Map<String, Float> queryFields = new HashMap<>(query.fields());
            if (queryFields.isEmpty()) {
                queryFields.put("*", AbstractQueryBuilder.DEFAULT_BOOST);
            }
            if (QueryParserHelper.hasAllFieldsWildcard(queryFields.keySet())) {
                simpleQueryStringBuilder.lenient(true);
            } else {
                simpleQueryStringBuilder.lenient(query.lenient());
            }
            for (Map.Entry<String, Float> requestedFieldNameOrPattern : queryFields.entrySet()) {
                for (String translatedField : translatePattern(requestedFieldNameOrPattern.getKey())) {
                    simpleQueryStringBuilder.fields()
                        .compute(
                            translatedField,
                            (k, v) -> (v == null) ? requestedFieldNameOrPattern.getValue() : v * requestedFieldNameOrPattern.getValue()
                        );
                    fieldNameVisitor.accept(translatedField);
                }
            }
            if (simpleQueryStringBuilder.fields().isEmpty()) {
                return new MatchNoneQueryBuilder().boost(simpleQueryStringBuilder.boost()).queryName(simpleQueryStringBuilder.queryName());
            }
            return simpleQueryStringBuilder.analyzer(query.analyzer())
                .defaultOperator(query.defaultOperator())
                .minimumShouldMatch(query.minimumShouldMatch())
                .flags(query.flags())
                .type(query.type())
                .quoteFieldSuffix(query.quoteFieldSuffix())
                .analyzeWildcard(query.analyzeWildcard())
                .autoGenerateSynonymsPhraseQuery(query.autoGenerateSynonymsPhraseQuery())
                .fuzzyTranspositions(query.fuzzyTranspositions())
                .fuzzyMaxExpansions(query.fuzzyMaxExpansions())
                .fuzzyPrefixLength(query.fuzzyPrefixLength())
                .boost(query.boost())
                .queryName(query.queryName());
        } else {
            throw new IllegalArgumentException("Query type [" + queryBuilder.getName() + "] is not supported for API Key query");
        }
    }

    /**
     * Translate the query level field name to index level field names.
     * It throws an exception if the field name is not explicitly allowed.
     */
    protected static String translate(String fieldName) {
        if (Regex.isSimpleMatchPattern(fieldName)) {
            throw new IllegalArgumentException("Field name pattern [" + fieldName + "] is not allowed for API Key query or aggregation");
        }
        for (FieldNameTranslator translator : FIELD_NAME_TRANSLATORS) {
            if (translator.supports(fieldName)) {
                return translator.translate(fieldName);
            }
        }
        throw new IllegalArgumentException("Field [" + fieldName + "] is not allowed for API Key query or aggregation");
    }

    /**
     * Translates a query level field name pattern to the matching index level field names.
     * The result can be the empty set, if the pattern doesn't match any of the allowed index level field names.
     */
    private static Set<String> translatePattern(String fieldNameOrPattern) {
        Set<String> indexFieldNames = new HashSet<>();
        for (FieldNameTranslator translator : FIELD_NAME_TRANSLATORS) {
            if (translator.supports(fieldNameOrPattern)) {
                indexFieldNames.add(translator.translate(fieldNameOrPattern));
            }
        }
        return indexFieldNames;
    }

    abstract static class FieldNameTranslator {

        private final Function<String, String> translationFunc;

        protected FieldNameTranslator(Function<String, String> translationFunc) {
            this.translationFunc = translationFunc;
        }

        String translate(String fieldName) {
            return translationFunc.apply(fieldName);
        }

        abstract boolean supports(String fieldName);
    }

    static class ExactFieldNameTranslator extends FieldNameTranslator {
        private final String name;

        ExactFieldNameTranslator(Function<String, String> translationFunc, String name) {
            super(translationFunc);
            this.name = name;
        }

        @Override
        public boolean supports(String fieldNameOrPattern) {
            if (Regex.isSimpleMatchPattern(fieldNameOrPattern)) {
                return Regex.simpleMatch(fieldNameOrPattern, name);
            } else {
                return name.equals(fieldNameOrPattern);
            }
        }
    }

    static class PrefixFieldNameTranslator extends FieldNameTranslator {
        private final String prefix;

        PrefixFieldNameTranslator(Function<String, String> translationFunc, String prefix) {
            super(translationFunc);
            this.prefix = prefix;
        }

        @Override
        boolean supports(String fieldNamePrefix) {
            if (Regex.isSimpleMatchPattern(fieldNamePrefix)) {
                return false;
            }
            return fieldNamePrefix.startsWith(prefix);
        }
    }
}
