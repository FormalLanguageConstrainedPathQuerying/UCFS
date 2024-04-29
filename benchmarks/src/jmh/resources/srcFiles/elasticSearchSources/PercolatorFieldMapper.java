/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */
package org.elasticsearch.percolator;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.PointValues;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.sandbox.search.CoveringQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LongValuesSource;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;
import org.elasticsearch.TransportVersion;
import org.elasticsearch.action.support.PlainActionFuture;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.hash.MurmurHash3;
import org.elasticsearch.common.io.stream.OutputStreamStreamOutput;
import org.elasticsearch.common.lucene.search.Queries;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.index.IndexVersion;
import org.elasticsearch.index.IndexVersions;
import org.elasticsearch.index.mapper.BinaryFieldMapper;
import org.elasticsearch.index.mapper.DocumentParserContext;
import org.elasticsearch.index.mapper.FieldMapper;
import org.elasticsearch.index.mapper.KeywordFieldMapper;
import org.elasticsearch.index.mapper.LuceneDocument;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.mapper.Mapper;
import org.elasticsearch.index.mapper.MapperBuilderContext;
import org.elasticsearch.index.mapper.MapperParsingException;
import org.elasticsearch.index.mapper.MappingParserContext;
import org.elasticsearch.index.mapper.NumberFieldMapper;
import org.elasticsearch.index.mapper.RangeFieldMapper;
import org.elasticsearch.index.mapper.RangeType;
import org.elasticsearch.index.mapper.SourceValueFetcher;
import org.elasticsearch.index.mapper.TextSearchInfo;
import org.elasticsearch.index.mapper.ValueFetcher;
import org.elasticsearch.index.query.FilteredSearchExecutionContext;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryShardException;
import org.elasticsearch.index.query.Rewriteable;
import org.elasticsearch.index.query.SearchExecutionContext;
import org.elasticsearch.search.vectors.KnnVectorQueryBuilder;
import org.elasticsearch.xcontent.XContentParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import static org.elasticsearch.index.query.AbstractQueryBuilder.parseTopLevelQuery;

public class PercolatorFieldMapper extends FieldMapper {

    static final Setting<Boolean> INDEX_MAP_UNMAPPED_FIELDS_AS_TEXT_SETTING = Setting.boolSetting(
        "index.percolator.map_unmapped_fields_as_text",
        false,
        Setting.Property.IndexScope
    );
    static final String CONTENT_TYPE = "percolator";

    static final byte FIELD_VALUE_SEPARATOR = 0;  
    static final String EXTRACTION_COMPLETE = "complete";
    static final String EXTRACTION_PARTIAL = "partial";
    static final String EXTRACTION_FAILED = "failed";

    static final String EXTRACTED_TERMS_FIELD_NAME = "extracted_terms";
    static final String EXTRACTION_RESULT_FIELD_NAME = "extraction_result";
    static final String QUERY_BUILDER_FIELD_NAME = "query_builder_field";
    static final String RANGE_FIELD_NAME = "range_field";
    static final String MINIMUM_SHOULD_MATCH_FIELD_NAME = "minimum_should_match_field";

    @Override
    public FieldMapper.Builder getMergeBuilder() {
        return new Builder(simpleName(), searchExecutionContext, mapUnmappedFieldsAsText, indexCreatedVersion, clusterTransportVersion)
            .init(this);
    }

    static class Builder extends FieldMapper.Builder {

        private final Parameter<Map<String, String>> meta = Parameter.metaParam();

        private final Supplier<SearchExecutionContext> searchExecutionContext;
        private final boolean mapUnmappedFieldsAsText;

        private final IndexVersion indexCreatedVersion;
        private final Supplier<TransportVersion> clusterTransportVersion;

        Builder(
            String fieldName,
            Supplier<SearchExecutionContext> searchExecutionContext,
            boolean mapUnmappedFieldsAsText,
            IndexVersion indexCreatedVersion,
            Supplier<TransportVersion> clusterTransportVersion
        ) {
            super(fieldName);
            this.searchExecutionContext = searchExecutionContext;
            this.mapUnmappedFieldsAsText = mapUnmappedFieldsAsText;
            this.indexCreatedVersion = Objects.requireNonNull(indexCreatedVersion);
            this.clusterTransportVersion = clusterTransportVersion;
        }

        @Override
        protected Parameter<?>[] getParameters() {
            return new Parameter<?>[] { meta };
        }

        @Override
        public PercolatorFieldMapper build(MapperBuilderContext context) {
            PercolatorFieldType fieldType = new PercolatorFieldType(context.buildFullName(name()), meta.getValue());
            MultiFields multiFields = multiFieldsBuilder.build(this, context);
            context = context.createChildContext(name(), null);
            KeywordFieldMapper extractedTermsField = createExtractQueryFieldBuilder(
                EXTRACTED_TERMS_FIELD_NAME,
                context,
                indexCreatedVersion
            );
            fieldType.queryTermsField = extractedTermsField.fieldType();
            KeywordFieldMapper extractionResultField = createExtractQueryFieldBuilder(
                EXTRACTION_RESULT_FIELD_NAME,
                context,
                indexCreatedVersion
            );
            fieldType.extractionResultField = extractionResultField.fieldType();
            BinaryFieldMapper queryBuilderField = createQueryBuilderFieldBuilder(context);
            fieldType.queryBuilderField = queryBuilderField.fieldType();
            RangeFieldMapper rangeFieldMapper = createExtractedRangeFieldBuilder(RANGE_FIELD_NAME, RangeType.IP, context);
            fieldType.rangeField = rangeFieldMapper.fieldType();
            NumberFieldMapper minimumShouldMatchFieldMapper = createMinimumShouldMatchField(context, indexCreatedVersion);
            fieldType.minimumShouldMatchField = minimumShouldMatchFieldMapper.fieldType();
            fieldType.mapUnmappedFieldsAsText = mapUnmappedFieldsAsText;

            return new PercolatorFieldMapper(
                name(),
                fieldType,
                multiFields,
                copyTo,
                searchExecutionContext,
                extractedTermsField,
                extractionResultField,
                queryBuilderField,
                rangeFieldMapper,
                minimumShouldMatchFieldMapper,
                mapUnmappedFieldsAsText,
                indexCreatedVersion,
                clusterTransportVersion
            );
        }

        static KeywordFieldMapper createExtractQueryFieldBuilder(
            String name,
            MapperBuilderContext context,
            IndexVersion indexCreatedVersion
        ) {
            KeywordFieldMapper.Builder queryMetadataFieldBuilder = new KeywordFieldMapper.Builder(name, indexCreatedVersion);
            queryMetadataFieldBuilder.docValues(false);
            return queryMetadataFieldBuilder.build(context);
        }

        static BinaryFieldMapper createQueryBuilderFieldBuilder(MapperBuilderContext context) {
            BinaryFieldMapper.Builder builder = new BinaryFieldMapper.Builder(QUERY_BUILDER_FIELD_NAME, context.isSourceSynthetic())
                .docValues(true);
            return builder.build(context);
        }

        static RangeFieldMapper createExtractedRangeFieldBuilder(String name, RangeType rangeType, MapperBuilderContext context) {
            RangeFieldMapper.Builder builder = new RangeFieldMapper.Builder(name, rangeType, true);
            builder.docValues(false);
            return builder.build(context);
        }

        static NumberFieldMapper createMinimumShouldMatchField(MapperBuilderContext context, IndexVersion indexCreatedVersion) {
            NumberFieldMapper.Builder builder = NumberFieldMapper.Builder.docValuesOnly(
                MINIMUM_SHOULD_MATCH_FIELD_NAME,
                NumberFieldMapper.NumberType.INTEGER,
                indexCreatedVersion
            );
            return builder.build(context);
        }

    }

    static class TypeParser implements Mapper.TypeParser {

        @Override
        public Builder parse(String name, Map<String, Object> node, MappingParserContext parserContext) throws MapperParsingException {
            return new Builder(
                name,
                parserContext.searchExecutionContext(),
                getMapUnmappedFieldAsText(parserContext.getSettings()),
                parserContext.indexVersionCreated(),
                parserContext.clusterTransportVersion()
            );
        }
    }

    private static boolean getMapUnmappedFieldAsText(Settings indexSettings) {
        return INDEX_MAP_UNMAPPED_FIELDS_AS_TEXT_SETTING.get(indexSettings);
    }

    static class PercolatorFieldType extends MappedFieldType {

        MappedFieldType queryTermsField;
        MappedFieldType extractionResultField;
        MappedFieldType queryBuilderField;
        MappedFieldType minimumShouldMatchField;

        RangeFieldMapper.RangeFieldType rangeField;
        boolean mapUnmappedFieldsAsText;

        private PercolatorFieldType(String name, Map<String, String> meta) {
            super(name, false, false, false, TextSearchInfo.NONE, meta);
        }

        @Override
        public String typeName() {
            return CONTENT_TYPE;
        }

        @Override
        public Query termQuery(Object value, SearchExecutionContext context) {
            throw new QueryShardException(context, "Percolator fields are not searchable directly, use a percolate query instead");
        }

        @Override
        public ValueFetcher valueFetcher(SearchExecutionContext context, String format) {
            return SourceValueFetcher.identity(name(), context, format);
        }

        Query percolateQuery(
            String name,
            PercolateQuery.QueryStore queryStore,
            List<BytesReference> documents,
            IndexSearcher searcher,
            boolean excludeNestedDocuments,
            IndexVersion indexVersion
        ) throws IOException {
            IndexReader indexReader = searcher.getIndexReader();
            Tuple<BooleanQuery, Boolean> t = createCandidateQuery(indexReader);
            Query candidateQuery = t.v1();
            boolean canUseMinimumShouldMatchField = t.v2();

            Query verifiedMatchesQuery;
            if (canUseMinimumShouldMatchField && indexReader.maxDoc() == 1) {
                verifiedMatchesQuery = new TermQuery(new Term(extractionResultField.name(), EXTRACTION_COMPLETE));
            } else {
                verifiedMatchesQuery = new MatchNoDocsQuery("multiple or nested docs or CoveringQuery could not be used");
            }
            Query filter = null;
            if (excludeNestedDocuments) {
                filter = Queries.newNonNestedFilter(indexVersion);
            }
            return new PercolateQuery(name, queryStore, documents, candidateQuery, searcher, filter, verifiedMatchesQuery);
        }

        Tuple<BooleanQuery, Boolean> createCandidateQuery(IndexReader indexReader) throws IOException {
            Tuple<List<BytesRef>, Map<String, List<byte[]>>> t = extractTermsAndRanges(indexReader);
            List<BytesRef> extractedTerms = t.v1();
            Map<String, List<byte[]>> encodedPointValuesByField = t.v2();
            boolean canUseMinimumShouldMatchField = 1 + extractedTerms.size() + encodedPointValuesByField.size() <= BooleanQuery
                .getMaxClauseCount();

            List<Query> subQueries = new ArrayList<>();
            for (Map.Entry<String, List<byte[]>> entry : encodedPointValuesByField.entrySet()) {
                String rangeFieldName = entry.getKey();
                List<byte[]> encodedPointValues = entry.getValue();
                byte[] min = encodedPointValues.get(0);
                byte[] max = encodedPointValues.get(1);
                Query query = BinaryRange.newIntersectsQuery(rangeField.name(), encodeRange(rangeFieldName, min, max));
                subQueries.add(query);
            }

            BooleanQuery.Builder candidateQuery = new BooleanQuery.Builder();
            if (canUseMinimumShouldMatchField) {
                LongValuesSource valuesSource = LongValuesSource.fromIntField(minimumShouldMatchField.name());
                for (BytesRef extractedTerm : extractedTerms) {
                    subQueries.add(new TermQuery(new Term(queryTermsField.name(), extractedTerm)));
                }
                candidateQuery.add(new CoveringQuery(subQueries, valuesSource), BooleanClause.Occur.SHOULD);
            } else {
                candidateQuery.add(new TermInSetQuery(queryTermsField.name(), extractedTerms), BooleanClause.Occur.SHOULD);
                for (Query subQuery : subQueries) {
                    candidateQuery.add(subQuery, BooleanClause.Occur.SHOULD);
                }
            }
            candidateQuery.add(new TermQuery(new Term(extractionResultField.name(), EXTRACTION_FAILED)), BooleanClause.Occur.SHOULD);
            return new Tuple<>(candidateQuery.build(), canUseMinimumShouldMatchField);
        }

        static Tuple<List<BytesRef>, Map<String, List<byte[]>>> extractTermsAndRanges(IndexReader indexReader) throws IOException {
            List<BytesRef> extractedTerms = new ArrayList<>();
            Map<String, List<byte[]>> encodedPointValuesByField = new HashMap<>();

            LeafReader reader = indexReader.leaves().get(0).reader();
            for (FieldInfo info : reader.getFieldInfos()) {
                Terms terms = reader.terms(info.name);
                if (terms != null) {
                    BytesRef fieldBr = new BytesRef(info.name);
                    TermsEnum tenum = terms.iterator();
                    for (BytesRef term = tenum.next(); term != null; term = tenum.next()) {
                        BytesRefBuilder builder = new BytesRefBuilder();
                        builder.append(fieldBr);
                        builder.append(FIELD_VALUE_SEPARATOR);
                        builder.append(term);
                        extractedTerms.add(builder.toBytesRef());
                    }
                }
                if (info.getPointIndexDimensionCount() == 1) { 
                    PointValues values = reader.getPointValues(info.name);
                    List<byte[]> encodedPointValues = new ArrayList<>();
                    encodedPointValues.add(values.getMinPackedValue().clone());
                    encodedPointValues.add(values.getMaxPackedValue().clone());
                    encodedPointValuesByField.put(info.name, encodedPointValues);
                }
            }
            return new Tuple<>(extractedTerms, encodedPointValuesByField);
        }

    }

    private final Supplier<SearchExecutionContext> searchExecutionContext;
    private final KeywordFieldMapper queryTermsField;
    private final KeywordFieldMapper extractionResultField;
    private final BinaryFieldMapper queryBuilderField;
    private final NumberFieldMapper minimumShouldMatchFieldMapper;
    private final RangeFieldMapper rangeFieldMapper;
    private final boolean mapUnmappedFieldsAsText;
    private final IndexVersion indexCreatedVersion;
    private final Supplier<TransportVersion> clusterTransportVersion;

    PercolatorFieldMapper(
        String simpleName,
        MappedFieldType mappedFieldType,
        MultiFields multiFields,
        CopyTo copyTo,
        Supplier<SearchExecutionContext> searchExecutionContext,
        KeywordFieldMapper queryTermsField,
        KeywordFieldMapper extractionResultField,
        BinaryFieldMapper queryBuilderField,
        RangeFieldMapper rangeFieldMapper,
        NumberFieldMapper minimumShouldMatchFieldMapper,
        boolean mapUnmappedFieldsAsText,
        IndexVersion indexCreatedVersion,
        Supplier<TransportVersion> clusterTransportVersion
    ) {
        super(simpleName, mappedFieldType, multiFields, copyTo);
        this.searchExecutionContext = searchExecutionContext;
        this.queryTermsField = queryTermsField;
        this.extractionResultField = extractionResultField;
        this.queryBuilderField = queryBuilderField;
        this.minimumShouldMatchFieldMapper = minimumShouldMatchFieldMapper;
        this.rangeFieldMapper = rangeFieldMapper;
        this.mapUnmappedFieldsAsText = mapUnmappedFieldsAsText;
        this.indexCreatedVersion = indexCreatedVersion;
        this.clusterTransportVersion = clusterTransportVersion;
    }

    @Override
    protected boolean supportsParsingObject() {
        return true;
    }

    @Override
    public void parse(DocumentParserContext context) throws IOException {
        SearchExecutionContext executionContext = this.searchExecutionContext.get();
        if (context.doc().getField(queryBuilderField.name()) != null) {
            throw new IllegalArgumentException("a document can only contain one percolator query");
        }

        executionContext = configureContext(executionContext, isMapUnmappedFieldAsText());

        QueryBuilder queryBuilder = parseQueryBuilder(context);
        PlainActionFuture<QueryBuilder> future = new PlainActionFuture<>();
        Rewriteable.rewriteAndFetch(queryBuilder, executionContext, future);
        queryBuilder = future.actionGet();

        IndexVersion indexVersion = context.indexSettings().getIndexVersionCreated();
        createQueryBuilderField(indexVersion, clusterTransportVersion.get(), queryBuilderField, queryBuilder, context);

        QueryBuilder queryBuilderForProcessing = queryBuilder.rewrite(new SearchExecutionContext(executionContext));
        Query query = queryBuilderForProcessing.toQuery(executionContext);
        processQuery(query, context);
    }

    static QueryBuilder parseQueryBuilder(DocumentParserContext context) {
        XContentParser parser = context.parser();
        try {
            context.path().setWithinLeafObject(true);
            return parseTopLevelQuery(parser, queryName -> {
                if (queryName.equals("has_child")) {
                    throw new IllegalArgumentException("the [has_child] query is unsupported inside a percolator query");
                } else if (queryName.equals("has_parent")) {
                    throw new IllegalArgumentException("the [has_parent] query is unsupported inside a percolator query");
                } else if (queryName.equals(KnnVectorQueryBuilder.NAME)) {
                    throw new IllegalArgumentException("the [knn] query is unsupported inside a percolator query");
                }
            });
        } catch (IOException e) {
            throw new ParsingException(parser.getTokenLocation(), "Failed to parse", e);
        } finally {
            context.path().setWithinLeafObject(false);
        }
    }

    static void createQueryBuilderField(
        IndexVersion indexVersion,
        TransportVersion clusterTransportVersion,
        BinaryFieldMapper qbField,
        QueryBuilder queryBuilder,
        DocumentParserContext context
    ) throws IOException {
        try (
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            OutputStreamStreamOutput out = new OutputStreamStreamOutput(stream)
        ) {
            if (indexVersion.before(IndexVersions.V_8_8_0)) {
                out.setTransportVersion(TransportVersion.fromId(indexVersion.id()));
            } else {
                TransportVersion.writeVersion(clusterTransportVersion, out);
                out.setTransportVersion(clusterTransportVersion);
            }

            out.writeNamedWriteable(queryBuilder);
            qbField.indexValue(context, stream.toByteArray());
        }
    }

    void processQuery(Query query, DocumentParserContext context) {
        LuceneDocument doc = context.doc();
        PercolatorFieldType pft = (PercolatorFieldType) this.fieldType();
        QueryAnalyzer.Result result;
        result = QueryAnalyzer.analyze(query);
        if (result == QueryAnalyzer.Result.UNKNOWN) {
            doc.add(new StringField(pft.extractionResultField.name(), EXTRACTION_FAILED, Field.Store.NO));
            return;
        }
        for (QueryAnalyzer.QueryExtraction extraction : result.extractions) {
            if (extraction.term != null) {
                BytesRefBuilder builder = new BytesRefBuilder();
                builder.append(new BytesRef(extraction.field()));
                builder.append(FIELD_VALUE_SEPARATOR);
                builder.append(extraction.bytes());
                doc.add(new StringField(queryTermsField.name(), builder.toBytesRef(), Field.Store.NO));
            } else if (extraction.range != null) {
                byte[] min = extraction.range.lowerPoint;
                byte[] max = extraction.range.upperPoint;
                doc.add(new BinaryRange(rangeFieldMapper.name(), encodeRange(extraction.range.fieldName, min, max)));
            }
        }

        if (result.matchAllDocs) {
            doc.add(new StringField(extractionResultField.name(), EXTRACTION_FAILED, Field.Store.NO));
            if (result.verified) {
                doc.add(new StringField(extractionResultField.name(), EXTRACTION_COMPLETE, Field.Store.NO));
            }
        } else if (result.verified) {
            doc.add(new StringField(extractionResultField.name(), EXTRACTION_COMPLETE, Field.Store.NO));
        } else {
            doc.add(new StringField(extractionResultField.name(), EXTRACTION_PARTIAL, Field.Store.NO));
        }

        context.addToFieldNames(fieldType().name());
        doc.add(new NumericDocValuesField(minimumShouldMatchFieldMapper.name(), result.minimumShouldMatch));
    }

    static SearchExecutionContext configureContext(SearchExecutionContext context, boolean mapUnmappedFieldsAsString) {
        SearchExecutionContext wrapped = wrapAllEmptyTextFields(context);
        wrapped.setAllowUnmappedFields(false);
        wrapped.setMapUnmappedFieldAsString(mapUnmappedFieldsAsString);
        wrapped.setRewriteToNamedQueries();
        return wrapped;
    }

    @Override
    public Iterator<Mapper> iterator() {
        return Arrays.<Mapper>asList(
            queryTermsField,
            extractionResultField,
            queryBuilderField,
            minimumShouldMatchFieldMapper,
            rangeFieldMapper
        ).iterator();
    }

    @Override
    protected void parseCreateField(DocumentParserContext context) {
        throw new UnsupportedOperationException("should not be invoked");
    }

    @Override
    protected String contentType() {
        return CONTENT_TYPE;
    }

    boolean isMapUnmappedFieldAsText() {
        return mapUnmappedFieldsAsText;
    }

    static byte[] encodeRange(String rangeFieldName, byte[] minEncoded, byte[] maxEncoded) {
        assert minEncoded.length == maxEncoded.length;
        byte[] bytes = new byte[BinaryRange.BYTES * 2];

        BytesRef fieldAsBytesRef = new BytesRef(rangeFieldName);
        MurmurHash3.Hash128 hash = new MurmurHash3.Hash128();
        MurmurHash3.hash128(fieldAsBytesRef.bytes, fieldAsBytesRef.offset, fieldAsBytesRef.length, 0, hash);
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        bb.putLong(hash.h1).putLong(hash.h2).putLong(hash.h1).putLong(hash.h2);
        assert bb.position() == bb.limit();

        int offset = BinaryRange.BYTES - minEncoded.length;
        System.arraycopy(minEncoded, 0, bytes, offset, minEncoded.length);
        System.arraycopy(maxEncoded, 0, bytes, BinaryRange.BYTES + offset, maxEncoded.length);
        return bytes;
    }

    static SearchExecutionContext wrapAllEmptyTextFields(SearchExecutionContext searchExecutionContext) {
        return new FilteredSearchExecutionContext(searchExecutionContext) {
            @Override
            public boolean fieldExistsInIndex(String fieldname) {
                return true;
            }
        };
    }
}
