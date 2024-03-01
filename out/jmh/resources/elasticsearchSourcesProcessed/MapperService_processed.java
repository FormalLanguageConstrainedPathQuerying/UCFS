/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.mapper;

import org.elasticsearch.TransportVersion;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.metadata.MappingMetadata;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.common.compress.CompressorFactory;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Setting.Property;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.index.AbstractIndexComponent;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.IndexVersion;
import org.elasticsearch.index.analysis.AnalysisRegistry;
import org.elasticsearch.index.analysis.IndexAnalyzers;
import org.elasticsearch.index.analysis.NamedAnalyzer;
import org.elasticsearch.index.query.SearchExecutionContext;
import org.elasticsearch.index.similarity.SimilarityService;
import org.elasticsearch.indices.IndicesModule;
import org.elasticsearch.plugins.internal.DocumentParsingObserver;
import org.elasticsearch.script.ScriptCompiler;
import org.elasticsearch.xcontent.NamedXContentRegistry;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.XContentParserConfiguration;
import org.elasticsearch.xcontent.XContentType;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public class MapperService extends AbstractIndexComponent implements Closeable {

    /**
     * The reason why a mapping is being merged.
     */
    public enum MergeReason {
        /**
         * Pre-flight check before sending a dynamic mapping update to the master
         */
        MAPPING_AUTO_UPDATE_PREFLIGHT {
            @Override
            public boolean isAutoUpdate() {
                return true;
            }
        },
        /**
         * Dynamic mapping updates
         */
        MAPPING_AUTO_UPDATE {
            @Override
            public boolean isAutoUpdate() {
                return true;
            }
        },
        /**
         * Create or update a mapping.
         */
        MAPPING_UPDATE,
        /**
         * Merge mappings from a composable index template.
         */
        INDEX_TEMPLATE,
        /**
         * Recovery of an existing mapping, for instance because of a restart,
         * if a shard was moved to a different node or for administrative
         * purposes.
         */
        MAPPING_RECOVERY;

        public boolean isAutoUpdate() {
            return false;
        }
    }

    public static final String SINGLE_MAPPING_NAME = "_doc";
    public static final String TYPE_FIELD_NAME = "_type";
    public static final Setting<Long> INDEX_MAPPING_NESTED_FIELDS_LIMIT_SETTING = Setting.longSetting(
        "index.mapping.nested_fields.limit",
        50L,
        0,
        Property.Dynamic,
        Property.IndexScope
    );
    public static final Setting<Long> INDEX_MAPPING_NESTED_DOCS_LIMIT_SETTING = Setting.longSetting(
        "index.mapping.nested_objects.limit",
        10000L,
        0,
        Property.Dynamic,
        Property.IndexScope
    );
    public static final Setting<Long> INDEX_MAPPING_TOTAL_FIELDS_LIMIT_SETTING = Setting.longSetting(
        "index.mapping.total_fields.limit",
        1000L,
        0,
        Property.Dynamic,
        Property.IndexScope,
        Property.ServerlessPublic
    );
    public static final Setting<Boolean> INDEX_MAPPING_IGNORE_DYNAMIC_BEYOND_LIMIT_SETTING = Setting.boolSetting(
        "index.mapping.total_fields.ignore_dynamic_beyond_limit",
        false,
        Property.Dynamic,
        Property.IndexScope
    );
    public static final Setting<Long> INDEX_MAPPING_DEPTH_LIMIT_SETTING = Setting.longSetting(
        "index.mapping.depth.limit",
        20L,
        1,
        Property.Dynamic,
        Property.IndexScope
    );
    public static final Setting<Long> INDEX_MAPPING_FIELD_NAME_LENGTH_LIMIT_SETTING = Setting.longSetting(
        "index.mapping.field_name_length.limit",
        Long.MAX_VALUE,
        1L,
        Property.Dynamic,
        Property.IndexScope
    );
    public static final Setting<Long> INDEX_MAPPING_DIMENSION_FIELDS_LIMIT_SETTING = Setting.longSetting(
        "index.mapping.dimension_fields.limit",
        32_768,
        0,
        Property.Dynamic,
        Property.IndexScope
    );

    private final IndexAnalyzers indexAnalyzers;
    private final MappingParser mappingParser;
    private final DocumentParser documentParser;
    private final IndexVersion indexVersionCreated;
    private final MapperRegistry mapperRegistry;
    private final Supplier<MappingParserContext> mappingParserContextSupplier;

    private volatile DocumentMapper mapper;
    private volatile long mappingVersion;

    public MapperService(
        ClusterService clusterService,
        IndexSettings indexSettings,
        IndexAnalyzers indexAnalyzers,
        XContentParserConfiguration parserConfiguration,
        SimilarityService similarityService,
        MapperRegistry mapperRegistry,
        Supplier<SearchExecutionContext> searchExecutionContextSupplier,
        IdFieldMapper idFieldMapper,
        ScriptCompiler scriptCompiler,
        Supplier<DocumentParsingObserver> documentParsingObserverSupplier
    ) {
        this(
            () -> clusterService.state().getMinTransportVersion(),
            indexSettings,
            indexAnalyzers,
            parserConfiguration,
            similarityService,
            mapperRegistry,
            searchExecutionContextSupplier,
            idFieldMapper,
            scriptCompiler,
            documentParsingObserverSupplier
        );
    }

    @SuppressWarnings("this-escape")
    public MapperService(
        Supplier<TransportVersion> clusterTransportVersion,
        IndexSettings indexSettings,
        IndexAnalyzers indexAnalyzers,
        XContentParserConfiguration parserConfiguration,
        SimilarityService similarityService,
        MapperRegistry mapperRegistry,
        Supplier<SearchExecutionContext> searchExecutionContextSupplier,
        IdFieldMapper idFieldMapper,
        ScriptCompiler scriptCompiler,
        Supplier<DocumentParsingObserver> documentParsingObserverSupplier
    ) {
        super(indexSettings);
        this.indexVersionCreated = indexSettings.getIndexVersionCreated();
        this.indexAnalyzers = indexAnalyzers;
        this.mapperRegistry = mapperRegistry;
        this.mappingParserContextSupplier = () -> new MappingParserContext(
            similarityService::getSimilarity,
            type -> mapperRegistry.getMapperParser(type, indexVersionCreated),
            mapperRegistry.getRuntimeFieldParsers()::get,
            indexVersionCreated,
            clusterTransportVersion,
            searchExecutionContextSupplier,
            scriptCompiler,
            indexAnalyzers,
            indexSettings,
            idFieldMapper
        );
        this.documentParser = new DocumentParser(
            parserConfiguration,
            this.mappingParserContextSupplier.get(),
            documentParsingObserverSupplier
        );
        Map<String, MetadataFieldMapper.TypeParser> metadataMapperParsers = mapperRegistry.getMetadataMapperParsers(
            indexSettings.getIndexVersionCreated()
        );
        this.mappingParser = new MappingParser(
            mappingParserContextSupplier,
            metadataMapperParsers,
            this::getMetadataMappers,
            this::resolveDocumentType
        );
    }

    public boolean hasNested() {
        return mappingLookup().nestedLookup() != NestedLookup.EMPTY;
    }

    public IndexAnalyzers getIndexAnalyzers() {
        return this.indexAnalyzers;
    }

    public MappingParserContext parserContext() {
        return mappingParserContextSupplier.get();
    }

    /**
     * Exposes a {@link DocumentParser}
     * @return a document parser to be used to parse incoming documents
     */
    public DocumentParser documentParser() {
        return this.documentParser;
    }

    Map<Class<? extends MetadataFieldMapper>, MetadataFieldMapper> getMetadataMappers() {
        final MappingParserContext mappingParserContext = parserContext();
        final DocumentMapper existingMapper = mapper;
        final Map<String, MetadataFieldMapper.TypeParser> metadataMapperParsers = mapperRegistry.getMetadataMapperParsers(
            indexSettings.getIndexVersionCreated()
        );
        Map<Class<? extends MetadataFieldMapper>, MetadataFieldMapper> metadataMappers = new LinkedHashMap<>();
        if (existingMapper == null) {
            for (MetadataFieldMapper.TypeParser parser : metadataMapperParsers.values()) {
                MetadataFieldMapper metadataFieldMapper = parser.getDefault(mappingParserContext);
                if (metadataFieldMapper != null) {
                    metadataMappers.put(metadataFieldMapper.getClass(), metadataFieldMapper);
                }
            }
        } else {
            metadataMappers.putAll(existingMapper.mapping().getMetadataMappersMap());
        }
        return metadataMappers;
    }

    /**
     * Parses the mappings (formatted as JSON) into a map
     */
    public static Map<String, Object> parseMapping(NamedXContentRegistry xContentRegistry, String mappingSource) throws IOException {
        if ("{}".equals(mappingSource)) {
            return Map.of();
        }
        try (XContentParser parser = XContentType.JSON.xContent().createParser(parserConfig(xContentRegistry), mappingSource)) {
            return parser.map();
        }
    }

    /**
     * Parses the mappings (formatted as JSON) into a map
     */
    public static Map<String, Object> parseMapping(NamedXContentRegistry xContentRegistry, CompressedXContent mappingSource)
        throws IOException {
        try (
            InputStream in = CompressorFactory.COMPRESSOR.threadLocalInputStream(mappingSource.compressedReference().streamInput());
            XContentParser parser = XContentType.JSON.xContent().createParser(parserConfig(xContentRegistry), in)
        ) {
            return parser.map();
        }
    }

    private static XContentParserConfiguration parserConfig(NamedXContentRegistry xContentRegistry) {
        return XContentParserConfiguration.EMPTY.withRegistry(xContentRegistry).withDeprecationHandler(LoggingDeprecationHandler.INSTANCE);
    }

    /**
     * Update local mapping by applying the incoming mapping that have already been merged with the current one on the master
     */
    public void updateMapping(final IndexMetadata currentIndexMetadata, final IndexMetadata newIndexMetadata) {
        assert newIndexMetadata.getIndex().equals(index())
            : "index mismatch: expected " + index() + " but was " + newIndexMetadata.getIndex();

        if (currentIndexMetadata != null && currentIndexMetadata.getMappingVersion() == newIndexMetadata.getMappingVersion()) {
            assert assertNoUpdateRequired(newIndexMetadata);
            return;
        }

        MappingMetadata newMappingMetadata = newIndexMetadata.mapping();
        if (newMappingMetadata != null) {
            String type = newMappingMetadata.type();
            CompressedXContent incomingMappingSource = newMappingMetadata.source();
            Mapping incomingMapping = parseMapping(type, incomingMappingSource);
            DocumentMapper previousMapper;
            synchronized (this) {
                previousMapper = this.mapper;
                assert assertRefreshIsNotNeeded(previousMapper, type, incomingMapping);
                this.mapper = newDocumentMapper(incomingMapping, MergeReason.MAPPING_RECOVERY, incomingMappingSource);
                this.mappingVersion = newIndexMetadata.getMappingVersion();
            }
            String op = previousMapper != null ? "updated" : "added";
            if (logger.isDebugEnabled() && incomingMappingSource.compressed().length < 512) {
                logger.debug("[{}] {} mapping, source [{}]", index(), op, incomingMappingSource.string());
            } else if (logger.isTraceEnabled()) {
                logger.trace("[{}] {} mapping, source [{}]", index(), op, incomingMappingSource.string());
            } else {
                logger.debug("[{}] {} mapping (source suppressed due to length, use TRACE level if needed)", index(), op);
            }
        }
    }

    private boolean assertRefreshIsNotNeeded(DocumentMapper currentMapper, String type, Mapping incomingMapping) {
        Mapping mergedMapping = mergeMappings(currentMapper, incomingMapping, MergeReason.MAPPING_RECOVERY, indexSettings);
        ToXContent.MapParams params = new ToXContent.MapParams(Collections.singletonMap(RootObjectMapper.TOXCONTENT_SKIP_RUNTIME, "true"));
        CompressedXContent mergedMappingSource;
        try {
            mergedMappingSource = new CompressedXContent(mergedMapping, params);
        } catch (Exception e) {
            throw new AssertionError("failed to serialize source for type [" + type + "]", e);
        }
        CompressedXContent incomingMappingSource;
        try {
            incomingMappingSource = new CompressedXContent(incomingMapping, params);
        } catch (Exception e) {
            throw new AssertionError("failed to serialize source for type [" + type + "]", e);
        }
        assert mergedMappingSource.equals(incomingMappingSource)
            : "["
                + index()
                + "] parsed mapping, and got different sources\n"
                + "incoming:\n"
                + incomingMappingSource
                + "\nmerged:\n"
                + mergedMappingSource;
        return true;
    }

    boolean assertNoUpdateRequired(final IndexMetadata newIndexMetadata) {
        MappingMetadata mapping = newIndexMetadata.mapping();
        if (mapping != null) {
            Mapping newMapping = parseMapping(mapping.type(), mapping.source());
            final CompressedXContent currentSource = this.mapper.mappingSource();
            final CompressedXContent newSource = newMapping.toCompressedXContent();
            if (Objects.equals(currentSource, newSource) == false
                && mapper.isSyntheticSourceMalformed(currentSource, indexVersionCreated) == false) {
                throw new IllegalStateException(
                    "expected current mapping [" + currentSource + "] to be the same as new mapping [" + newSource + "]"
                );
            }
        }
        return true;

    }

    public void merge(IndexMetadata indexMetadata, MergeReason reason) {
        assert reason != MergeReason.MAPPING_AUTO_UPDATE_PREFLIGHT;
        MappingMetadata mappingMetadata = indexMetadata.mapping();
        if (mappingMetadata != null) {
            merge(mappingMetadata.type(), mappingMetadata.source(), reason);
        }
    }

    /**
     * Merging the provided mappings. Actual merging is done in the raw, non-parsed, form of the mappings. This allows to do a proper bulk
     * merge, where parsing is done only when all raw mapping settings are already merged.
     */
    public DocumentMapper merge(String type, List<CompressedXContent> mappingSources, MergeReason reason) {
        final DocumentMapper currentMapper = this.mapper;
        if (currentMapper != null && mappingSources.size() == 1 && currentMapper.mappingSource().equals(mappingSources.get(0))) {
            return currentMapper;
        }

        Map<String, Object> mergedRawMapping = null;
        for (CompressedXContent mappingSource : mappingSources) {
            Map<String, Object> rawMapping = MappingParser.convertToMap(mappingSource);

            if (rawMapping.containsKey(type)) {
                if (rawMapping.size() > 1) {
                    throw new MapperParsingException("cannot merge a map with multiple roots, one of which is [" + type + "]");
                }
            } else {
                rawMapping = Map.of(type, rawMapping);
            }

            if (mergedRawMapping == null) {
                mergedRawMapping = rawMapping;
            } else {
                XContentHelper.merge(type, mergedRawMapping, rawMapping, RawFieldMappingMerge.INSTANCE);
            }
        }
        if (mergedRawMapping != null && mergedRawMapping.size() > 1) {
            throw new MapperParsingException("cannot merge mapping sources with different roots");
        }
        return (mergedRawMapping != null) ? doMerge(type, reason, mergedRawMapping) : null;
    }

    /**
     * A {@link org.elasticsearch.common.xcontent.XContentHelper.CustomMerge} for raw map merges that are suitable for index/field mappings.
     * The default raw map merge algorithm doesn't override values - if there are multiple values for a key, then:
     * <ul>
     *     <li> if the values are of map type, the old and new values will be merged recursively
     *     <li> otherwise, the original value will be maintained
     * </ul>
     * When merging field mappings, we want something else. Specifically:
     * <ul>
     *     <li> within field mappings node (which is nested within a {@code properties} node):
     *     <ul>
     *         <li> if both the base mapping and the mapping to merge into it are of mergeable types (e.g {@code object -> object},
     *         {@code object -> nested}), then we only want to merge specific mapping entries:
     *         <ul>
     *             <li> {@code properties} node - merging fields from both mappings
     *             <li> {@code subobjects} entry - since this setting affects an entire subtree, we need to keep it when merging
     *         </ul>
     *         <li> otherwise, for any couple of non-mergeable types ((e.g {@code object -> long}, {@code long -> long}) - we just want
     *         to replace the entire mappings subtree, let the last one win
     *     </ul>
     *     <li> any other map values that are not encountered within a {@code properties} node (e.g. "_doc", "_meta" or "properties"
     *     itself) - apply recursive merge as the default algorithm would apply
     *     <li> any non-map values - override the value of the base map with the value of the merged map
     * </ul>
     */
    private static class RawFieldMappingMerge implements XContentHelper.CustomMerge {
        private static final XContentHelper.CustomMerge INSTANCE = new RawFieldMappingMerge();

        private static final Set<String> MERGEABLE_OBJECT_TYPES = Set.of(ObjectMapper.CONTENT_TYPE, NestedObjectMapper.CONTENT_TYPE);

        private RawFieldMappingMerge() {}

        @SuppressWarnings("unchecked")
        @Override
        public Object merge(String parent, String key, Object oldValue, Object newValue) {
            if (oldValue instanceof Map && newValue instanceof Map) {
                if ("properties".equals(parent)) {
                    Map<String, Object> baseMap = (Map<String, Object>) oldValue;
                    Map<String, Object> mapToMerge = (Map<String, Object>) newValue;
                    if (shouldMergeFieldMappings(baseMap, mapToMerge)) {
                        Map<String, Object> mergedMappings = new HashMap<>();
                        if (baseMap.containsKey("properties")) {
                            mergedMappings.put("properties", new HashMap<>((Map<String, Object>) baseMap.get("properties")));
                        }
                        if (baseMap.containsKey("subobjects")) {
                            mergedMappings.put("subobjects", baseMap.get("subobjects"));
                        }
                        XContentHelper.merge(key, mergedMappings, mapToMerge, INSTANCE);
                        return mergedMappings;
                    } else {
                        return mapToMerge;
                    }
                }
                return null;
            } else {
                if (key.equals("required")) {
                    if ("_routing".equals(parent) && oldValue != newValue) {
                        throw new MapperParsingException("contradicting `_routing.required` settings");
                    }
                }
                return newValue;
            }
        }

        /**
         * Normally, we don't want to merge raw field mappings, however there are cases where we do, for example - two
         * "object" (or "nested") mappings.
         *
         * @param mappings1 first mapping of a field
         * @param mappings2 second mapping of a field
         * @return {@code true} if the second mapping should be merged into the first mapping
         */
        private boolean shouldMergeFieldMappings(Map<String, Object> mappings1, Map<String, Object> mappings2) {
            String type1 = (String) mappings1.get("type");
            if (type1 == null && mappings1.get("properties") != null) {
                type1 = ObjectMapper.CONTENT_TYPE;
            }
            String type2 = (String) mappings2.get("type");
            if (type2 == null && mappings2.get("properties") != null) {
                type2 = ObjectMapper.CONTENT_TYPE;
            }
            if (type1 == null || type2 == null) {
                return false;
            }
            return MERGEABLE_OBJECT_TYPES.contains(type1) && MERGEABLE_OBJECT_TYPES.contains(type2);
        }
    }

    public DocumentMapper merge(String type, CompressedXContent mappingSource, MergeReason reason) {
        final DocumentMapper currentMapper = this.mapper;
        if (currentMapper != null && currentMapper.mappingSource().equals(mappingSource)) {
            return currentMapper;
        }
        Map<String, Object> mappingSourceAsMap = MappingParser.convertToMap(mappingSource);
        return doMerge(type, reason, mappingSourceAsMap);
    }

    private synchronized DocumentMapper doMerge(String type, MergeReason reason, Map<String, Object> mappingSourceAsMap) {
        Mapping incomingMapping = parseMapping(type, mappingSourceAsMap);
        Mapping mapping = mergeMappings(this.mapper, incomingMapping, reason, this.indexSettings);
        DocumentMapper newMapper = newDocumentMapper(mapping, reason, mapping.toCompressedXContent());
        if (reason == MergeReason.MAPPING_AUTO_UPDATE_PREFLIGHT) {
            return newMapper;
        }
        this.mapper = newMapper;
        assert assertSerialization(newMapper);
        return newMapper;
    }

    private DocumentMapper newDocumentMapper(Mapping mapping, MergeReason reason, CompressedXContent mappingSource) {
        DocumentMapper newMapper = new DocumentMapper(documentParser, mapping, mappingSource, indexVersionCreated);
        newMapper.validate(indexSettings, reason != MergeReason.MAPPING_RECOVERY);
        return newMapper;
    }

    public Mapping parseMapping(String mappingType, CompressedXContent mappingSource) {
        try {
            return mappingParser.parse(mappingType, mappingSource);
        } catch (Exception e) {
            throw new MapperParsingException("Failed to parse mapping: {}", e, e.getMessage());
        }
    }

    /**
     * A method to parse mapping from a source in a map form.
     *
     * @param mappingType   the mapping type
     * @param mappingSource mapping source already converted to a map form, but not yet processed otherwise
     * @return a parsed mapping
     */
    public Mapping parseMapping(String mappingType, Map<String, Object> mappingSource) {
        try {
            return mappingParser.parse(mappingType, mappingSource);
        } catch (Exception e) {
            throw new MapperParsingException("Failed to parse mapping: {}", e, e.getMessage());
        }
    }

    public static Mapping mergeMappings(
        DocumentMapper currentMapper,
        Mapping incomingMapping,
        MergeReason reason,
        IndexSettings indexSettings
    ) {
        return mergeMappings(currentMapper, incomingMapping, reason, getMaxFieldsToAddDuringMerge(currentMapper, indexSettings, reason));
    }

    private static long getMaxFieldsToAddDuringMerge(DocumentMapper currentMapper, IndexSettings indexSettings, MergeReason reason) {
        if (reason.isAutoUpdate() && indexSettings.isIgnoreDynamicFieldsBeyondLimit()) {
            long totalFieldsLimit = indexSettings.getMappingTotalFieldsLimit();
            return Optional.ofNullable(currentMapper)
                .map(DocumentMapper::mappers)
                .map(ml -> ml.remainingFieldsUntilLimit(totalFieldsLimit))
                .orElse(totalFieldsLimit);
        } else {
            return Long.MAX_VALUE;
        }
    }

    static Mapping mergeMappings(DocumentMapper currentMapper, Mapping incomingMapping, MergeReason reason, long newFieldsBudget) {
        Mapping newMapping;
        if (currentMapper == null) {
            newMapping = incomingMapping.withFieldsBudget(newFieldsBudget);
        } else {
            newMapping = currentMapper.mapping().merge(incomingMapping, reason, newFieldsBudget);
        }
        return newMapping;
    }

    private boolean assertSerialization(DocumentMapper mapper) {
        final CompressedXContent mappingSource = mapper.mappingSource();
        Mapping newMapping = parseMapping(mapper.type(), mappingSource);
        if (newMapping.toCompressedXContent().equals(mappingSource) == false) {
            throw new AssertionError(
                "Mapping serialization result is different from source. \n--> Source ["
                    + mappingSource
                    + "]\n--> Result ["
                    + newMapping.toCompressedXContent()
                    + "]"
            );
        }
        return true;
    }

    /**
     * Return the document mapper, or {@code null} if no mapping has been put yet
     * or no documents have been indexed in the current index yet (which triggers a dynamic mapping update)
     */
    public DocumentMapper documentMapper() {
        return mapper;
    }

    public long mappingVersion() {
        return mappingVersion;
    }

    /**
     * Returns {@code true} if the given {@code mappingSource} includes a type
     * as a top-level object.
     */
    public static boolean isMappingSourceTyped(String type, Map<String, Object> mapping) {
        return mapping.size() == 1 && mapping.keySet().iterator().next().equals(type);
    }

    /**
     * Resolves a type from a mapping-related request into the type that should be used when
     * merging and updating mappings.
     *
     * If the special `_doc` type is provided, then we replace it with the actual type that is
     * being used in the mappings. This allows typeless APIs such as 'index' or 'put mappings'
     * to work against indices with a custom type name.
     */
    private String resolveDocumentType(String type) {
        if (MapperService.SINGLE_MAPPING_NAME.equals(type)) {
            if (mapper != null) {
                return mapper.type();
            }
        }
        return type;
    }

    /**
     * Given the full name of a field, returns its {@link MappedFieldType}.
     */
    public MappedFieldType fieldType(String fullName) {
        return mappingLookup().fieldTypesLookup().get(fullName);
    }

    /**
     * Exposes a snapshot of the mappings for the current index.
     * If no mappings have been registered for the current index, an empty {@link MappingLookup} instance is returned.
     * An index does not have mappings only if it was created without providing mappings explicitly,
     * and no documents have yet been indexed in it.
     */
    public MappingLookup mappingLookup() {
        DocumentMapper mapper = this.mapper;
        return mapper == null ? MappingLookup.EMPTY : mapper.mappers();
    }

    /**
     * Returns field types that have eager global ordinals.
     */
    public Iterable<MappedFieldType> getEagerGlobalOrdinalsFields() {
        DocumentMapper mapper = this.mapper;
        if (mapper == null) {
            return Collections.emptySet();
        }
        MappingLookup mappingLookup = mapper.mappers();
        return mappingLookup.getMatchingFieldNames("*")
            .stream()
            .map(mappingLookup::getFieldType)
            .filter(MappedFieldType::eagerGlobalOrdinals)
            .toList();
    }

    /**
     * Return the index-time analyzer associated with a particular field
     * @param field                     the field name
     * @param unindexedFieldAnalyzer    a function to return an Analyzer for a field with no
     *                                  directly associated index-time analyzer
     */
    public NamedAnalyzer indexAnalyzer(String field, Function<String, NamedAnalyzer> unindexedFieldAnalyzer) {
        return mappingLookup().indexAnalyzer(field, unindexedFieldAnalyzer);
    }

    @Override
    public void close() throws IOException {
        indexAnalyzers.close();
    }

    /**
     * @return Whether a field is a metadata field
     * Deserialization of SearchHit objects sent from pre 7.8 nodes and GetResults objects sent from pre 7.3 nodes,
     * uses this method to divide fields into meta and document fields.
     * TODO: remove in v 9.0
     * @deprecated  Use an instance method isMetadataField instead
     */
    @Deprecated
    public static boolean isMetadataFieldStatic(String fieldName) {
        if (IndicesModule.getBuiltInMetadataFields().contains(fieldName)) {
            return true;
        }
        return fieldName.equals("_size");
    }

    /**
     * @return Whether a field is a metadata field.
     * this method considers all mapper plugins
     */
    public boolean isMetadataField(String field) {
        return mapperRegistry.getMetadataMapperParsers(indexVersionCreated).containsKey(field);
    }

    /**
     * @return If this field is defined as a multifield of another field
     */
    public boolean isMultiField(String field) {
        return mappingLookup().isMultiField(field);
    }

    /**
     * Reload any search analyzers that have reloadable components if resource is {@code null},
     * otherwise only the provided resource is reloaded.
     * @param registry the analysis registry
     * @param resource the name of the reloadable resource or {@code null} if all resources should be reloaded.
     * @param preview {@code false} applies analyzer reloading. {@code true} previews the reloading operation, so analyzers are not reloaded
     *               but the results retrieved. This is useful for understanding analyzers usage in the different indices.
     * @return The names of reloaded resources (or resources that would be reloaded if {@code preview} is true).
     * @throws IOException
     */
    public synchronized List<String> reloadSearchAnalyzers(AnalysisRegistry registry, @Nullable String resource, boolean preview)
        throws IOException {
        logger.debug("reloading search analyzers for index [{}]", indexSettings.getIndex().getName());
        return indexAnalyzers.reload(registry, indexSettings, resource, preview);
    }

    /**
     * @return Returns all dynamic templates defined in this mapping.
     */
    public DynamicTemplate[] getAllDynamicTemplates() {
        return documentMapper().mapping().getRoot().dynamicTemplates();
    }

    public MapperRegistry getMapperRegistry() {
        return mapperRegistry;
    }
}
