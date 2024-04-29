/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */
package org.elasticsearch.cluster.metadata;

import org.apache.lucene.tests.util.LuceneTestCase;
import org.elasticsearch.action.admin.indices.rollover.MaxAgeCondition;
import org.elasticsearch.action.admin.indices.rollover.RolloverConfiguration;
import org.elasticsearch.action.admin.indices.rollover.RolloverConfigurationTests;
import org.elasticsearch.action.admin.indices.rollover.RolloverInfo;
import org.elasticsearch.action.downsample.DownsampleConfig;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.UUIDs;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexMode;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.IndexVersion;
import org.elasticsearch.index.mapper.DateFieldMapper;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.test.AbstractXContentSerializingTestCase;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.XContentType;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.elasticsearch.cluster.metadata.DataStream.getDefaultBackingIndexName;
import static org.elasticsearch.cluster.metadata.DataStreamTestHelper.newInstance;
import static org.elasticsearch.cluster.metadata.DataStreamTestHelper.randomGlobalRetention;
import static org.elasticsearch.cluster.metadata.DataStreamTestHelper.randomIndexInstances;
import static org.elasticsearch.cluster.metadata.DataStreamTestHelper.randomNonEmptyIndexInstances;
import static org.elasticsearch.index.IndexSettings.LIFECYCLE_ORIGINATION_DATE;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class DataStreamTests extends AbstractXContentSerializingTestCase<DataStream> {

    @Override
    protected DataStream doParseInstance(XContentParser parser) throws IOException {
        return DataStream.fromXContent(parser);
    }

    @Override
    protected Writeable.Reader<DataStream> instanceReader() {
        return DataStream::new;
    }

    @Override
    protected DataStream createTestInstance() {
        return DataStreamTestHelper.randomInstance();
    }

    @Override
    protected DataStream mutateInstance(DataStream instance) {
        var name = instance.getName();
        var indices = instance.getIndices();
        var generation = instance.getGeneration();
        var metadata = instance.getMetadata();
        var isHidden = instance.isHidden();
        var isReplicated = instance.isReplicated();
        var isSystem = instance.isSystem();
        var allowsCustomRouting = instance.isAllowCustomRouting();
        var indexMode = instance.getIndexMode();
        var lifecycle = instance.getLifecycle();
        var failureStore = instance.isFailureStoreEnabled();
        var failureIndices = instance.getFailureIndices();
        var rolloverOnWrite = instance.rolloverOnWrite();
        var autoShardingEvent = instance.getAutoShardingEvent();
        switch (between(0, 12)) {
            case 0 -> name = randomAlphaOfLength(10);
            case 1 -> indices = randomNonEmptyIndexInstances();
            case 2 -> generation = instance.getGeneration() + randomIntBetween(1, 10);
            case 3 -> metadata = randomBoolean() && metadata != null ? null : Map.of("key", randomAlphaOfLength(10));
            case 4 -> {
                if (isHidden) {
                    isHidden = false;
                    isSystem = false; 
                } else {
                    isHidden = true;
                }
            }
            case 5 -> {
                isReplicated = isReplicated == false;
                rolloverOnWrite = isReplicated == false && rolloverOnWrite;
            }
            case 6 -> {
                if (isSystem == false) {
                    isSystem = true;
                    isHidden = true; 
                } else {
                    isSystem = false;
                }
            }
            case 7 -> allowsCustomRouting = allowsCustomRouting == false;
            case 8 -> indexMode = randomBoolean() && indexMode != null
                ? null
                : randomValueOtherThan(indexMode, () -> randomFrom(IndexMode.values()));
            case 9 -> lifecycle = randomBoolean() && lifecycle != null
                ? null
                : DataStreamLifecycle.newBuilder().dataRetention(randomMillisUpToYear9999()).build();
            case 10 -> {
                failureIndices = randomValueOtherThan(failureIndices, DataStreamTestHelper::randomIndexInstances);
                failureStore = failureIndices.isEmpty() == false;
            }
            case 11 -> {
                rolloverOnWrite = rolloverOnWrite == false;
                isReplicated = rolloverOnWrite == false && isReplicated;
            }
            case 12 -> {
                autoShardingEvent = randomBoolean() && autoShardingEvent != null
                    ? null
                    : new DataStreamAutoShardingEvent(
                        indices.get(indices.size() - 1).getName(),
                        randomIntBetween(1, 10),
                        randomMillisUpToYear9999()
                    );
            }
        }

        return new DataStream(
            name,
            indices,
            generation,
            metadata,
            isHidden,
            isReplicated,
            isSystem,
            allowsCustomRouting,
            indexMode,
            lifecycle,
            failureStore,
            failureIndices,
            rolloverOnWrite,
            autoShardingEvent
        );
    }

    public void testRollover() {
        DataStream ds = DataStreamTestHelper.randomInstance().promoteDataStream();
        Tuple<String, Long> newCoordinates = ds.nextWriteIndexAndGeneration(Metadata.EMPTY_METADATA);
        final DataStream rolledDs = ds.rollover(new Index(newCoordinates.v1(), UUIDs.randomBase64UUID()), newCoordinates.v2(), false, null);
        assertThat(rolledDs.getName(), equalTo(ds.getName()));
        assertThat(rolledDs.getGeneration(), equalTo(ds.getGeneration() + 1));
        assertThat(rolledDs.getIndices().size(), equalTo(ds.getIndices().size() + 1));
        assertTrue(rolledDs.getIndices().containsAll(ds.getIndices()));
        assertTrue(rolledDs.getIndices().contains(rolledDs.getWriteIndex()));
        assertFalse(rolledDs.rolloverOnWrite());
    }

    public void testRolloverWithConflictingBackingIndexName() {
        DataStream ds = DataStreamTestHelper.randomInstance(() -> 0L).promoteDataStream();

        int numConflictingIndices = randomIntBetween(1, 10);
        Metadata.Builder builder = Metadata.builder();
        for (int k = 1; k <= numConflictingIndices; k++) {
            IndexMetadata im = IndexMetadata.builder(DataStream.getDefaultBackingIndexName(ds.getName(), ds.getGeneration() + k, 0L))
                .settings(settings(IndexVersion.current()))
                .numberOfShards(1)
                .numberOfReplicas(1)
                .build();
            builder.put(im, false);
        }

        final Tuple<String, Long> newCoordinates = ds.nextWriteIndexAndGeneration(builder.build());
        final DataStream rolledDs = ds.rollover(new Index(newCoordinates.v1(), UUIDs.randomBase64UUID()), newCoordinates.v2(), false, null);
        assertThat(rolledDs.getName(), equalTo(ds.getName()));
        assertThat(rolledDs.getGeneration(), equalTo(ds.getGeneration() + numConflictingIndices + 1));
        assertThat(rolledDs.getIndices().size(), equalTo(ds.getIndices().size() + 1));
        assertTrue(rolledDs.getIndices().containsAll(ds.getIndices()));
        assertTrue(rolledDs.getIndices().contains(rolledDs.getWriteIndex()));
        assertThat(rolledDs.getIndexMode(), equalTo(ds.getIndexMode()));
    }

    public void testRolloverUpgradeToTsdbDataStream() {
        DataStream ds = DataStreamTestHelper.randomInstance()
            .copy()
            .setReplicated(false)
            .setIndexMode(randomBoolean() ? IndexMode.STANDARD : null)
            .build();
        var newCoordinates = ds.nextWriteIndexAndGeneration(Metadata.EMPTY_METADATA);

        var rolledDs = ds.rollover(new Index(newCoordinates.v1(), UUIDs.randomBase64UUID()), newCoordinates.v2(), true, null);
        assertThat(rolledDs.getName(), equalTo(ds.getName()));
        assertThat(rolledDs.getGeneration(), equalTo(ds.getGeneration() + 1));
        assertThat(rolledDs.getIndices().size(), equalTo(ds.getIndices().size() + 1));
        assertTrue(rolledDs.getIndices().containsAll(ds.getIndices()));
        assertTrue(rolledDs.getIndices().contains(rolledDs.getWriteIndex()));
        assertThat(rolledDs.getIndexMode(), equalTo(IndexMode.TIME_SERIES));
    }

    public void testRolloverDowngradeToRegularDataStream() {
        DataStream ds = DataStreamTestHelper.randomInstance().copy().setReplicated(false).setIndexMode(IndexMode.TIME_SERIES).build();
        var newCoordinates = ds.nextWriteIndexAndGeneration(Metadata.EMPTY_METADATA);

        var rolledDs = ds.rollover(new Index(newCoordinates.v1(), UUIDs.randomBase64UUID()), newCoordinates.v2(), false, null);
        assertThat(rolledDs.getName(), equalTo(ds.getName()));
        assertThat(rolledDs.getGeneration(), equalTo(ds.getGeneration() + 1));
        assertThat(rolledDs.getIndices().size(), equalTo(ds.getIndices().size() + 1));
        assertTrue(rolledDs.getIndices().containsAll(ds.getIndices()));
        assertTrue(rolledDs.getIndices().contains(rolledDs.getWriteIndex()));
        assertThat(rolledDs.getIndexMode(), nullValue());
    }

    public void testRolloverFailureStore() {
        DataStream ds = DataStreamTestHelper.randomInstance(true).promoteDataStream();
        Tuple<String, Long> newCoordinates = ds.nextFailureStoreWriteIndexAndGeneration(Metadata.EMPTY_METADATA);
        final DataStream rolledDs = ds.rolloverFailureStore(new Index(newCoordinates.v1(), UUIDs.randomBase64UUID()), newCoordinates.v2());
        assertThat(rolledDs.getName(), equalTo(ds.getName()));
        assertThat(rolledDs.getGeneration(), equalTo(ds.getGeneration() + 1));
        assertThat(rolledDs.getIndices().size(), equalTo(ds.getIndices().size()));
        assertThat(rolledDs.rolloverOnWrite(), equalTo(ds.rolloverOnWrite()));
        assertThat(rolledDs.getFailureIndices().size(), equalTo(ds.getFailureIndices().size() + 1));
        assertTrue(rolledDs.getIndices().containsAll(ds.getIndices()));
        assertTrue(rolledDs.getIndices().contains(rolledDs.getWriteIndex()));
        assertTrue(rolledDs.getFailureIndices().containsAll(ds.getFailureIndices()));
        assertTrue(rolledDs.getFailureIndices().contains(rolledDs.getFailureStoreWriteIndex()));
    }

    public void testRemoveBackingIndex() {
        DataStream original = createRandomDataStream();
        int indexToRemove = randomIntBetween(1, original.getIndices().size() - 1);

        DataStream updated = original.removeBackingIndex(original.getIndices().get(indexToRemove - 1));
        assertThat(updated.getName(), equalTo(original.getName()));
        assertThat(updated.getGeneration(), equalTo(original.getGeneration() + 1));
        assertThat(updated.getIndices().size(), equalTo(original.getIndices().size() - 1));
        for (int k = 0; k < (original.getIndices().size() - 1); k++) {
            assertThat(updated.getIndices().get(k), equalTo(original.getIndices().get(k < (indexToRemove - 1) ? k : k + 1)));
        }
    }

    public void testRemoveBackingIndexThatDoesNotExist() {
        DataStream original = createRandomDataStream();
        final Index indexToRemove = new Index(randomAlphaOfLength(4), UUIDs.randomBase64UUID(random()));

        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> original.removeBackingIndex(indexToRemove));
        assertThat(
            e.getMessage(),
            equalTo(Strings.format("index [%s] is not part of data stream [%s]", indexToRemove.getName(), original.getName()))
        );
    }

    public void testRemoveBackingWriteIndex() {
        DataStream original = createRandomDataStream();

        IllegalArgumentException e = expectThrows(
            IllegalArgumentException.class,
            () -> original.removeBackingIndex(original.getIndices().get(original.getIndices().size() - 1))
        );
        assertThat(
            e.getMessage(),
            equalTo(
                String.format(
                    Locale.ROOT,
                    "cannot remove backing index [%s] of data stream [%s] because it is the write index",
                    original.getIndices().get(original.getIndices().size() - 1).getName(),
                    original.getName()
                )
            )
        );
    }

    public void testRemoveFailureStoreIndex() {
        DataStream original = createRandomDataStream();
        int indexToRemove = randomIntBetween(1, original.getFailureIndices().size() - 1);

        DataStream updated = original.removeFailureStoreIndex(original.getFailureIndices().get(indexToRemove - 1));
        assertThat(updated.getName(), equalTo(original.getName()));
        assertThat(updated.getGeneration(), equalTo(original.getGeneration() + 1));
        assertThat(updated.getIndices().size(), equalTo(original.getIndices().size()));
        assertThat(updated.getFailureIndices().size(), equalTo(original.getFailureIndices().size() - 1));
        for (int k = 0; k < (original.getFailureIndices().size() - 1); k++) {
            assertThat(updated.getFailureIndices().get(k), equalTo(original.getFailureIndices().get(k < (indexToRemove - 1) ? k : k + 1)));
        }
    }

    public void testRemoveFailureStoreIndexThatDoesNotExist() {
        DataStream original = createRandomDataStream();
        final Index indexToRemove = new Index(randomAlphaOfLength(4), UUIDs.randomBase64UUID(random()));

        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> original.removeFailureStoreIndex(indexToRemove));
        assertThat(
            e.getMessage(),
            equalTo(Strings.format("index [%s] is not part of data stream [%s] failure store", indexToRemove.getName(), original.getName()))
        );
    }

    public void testRemoveFailureStoreWriteIndex() {
        DataStream original = createRandomDataStream();

        IllegalArgumentException e = expectThrows(
            IllegalArgumentException.class,
            () -> original.removeFailureStoreIndex(original.getFailureIndices().get(original.getFailureIndices().size() - 1))
        );
        assertThat(
            e.getMessage(),
            equalTo(
                String.format(
                    Locale.ROOT,
                    "cannot remove backing index [%s] of data stream [%s] because it is the write index",
                    original.getFailureIndices().get(original.getFailureIndices().size() - 1).getName(),
                    original.getName()
                )
            )
        );
    }

    public void testAddBackingIndex() {
        Metadata.Builder builder = Metadata.builder();

        DataStream original = createRandomDataStream();
        builder.put(original);

        createMetadataForIndices(builder, original.getIndices());

        Index indexToAdd = new Index(randomAlphaOfLength(4), UUIDs.randomBase64UUID(random()));
        builder.put(
            IndexMetadata.builder(indexToAdd.getName())
                .settings(settings(IndexVersion.current()))
                .numberOfShards(1)
                .numberOfReplicas(1)
                .build(),
            false
        );

        DataStream updated = original.addBackingIndex(builder.build(), indexToAdd);
        assertThat(updated.getName(), equalTo(original.getName()));
        assertThat(updated.getGeneration(), equalTo(original.getGeneration() + 1));
        assertThat(updated.getIndices().size(), equalTo(original.getIndices().size() + 1));
        for (int k = 1; k <= original.getIndices().size(); k++) {
            assertThat(updated.getIndices().get(k), equalTo(original.getIndices().get(k - 1)));
        }
        assertThat(updated.getIndices().get(0), equalTo(indexToAdd));
    }

    public void testAddBackingIndexThatIsPartOfAnotherDataStream() {
        Metadata.Builder builder = Metadata.builder();

        DataStream ds1 = createRandomDataStream();
        DataStream ds2 = createRandomDataStream();

        builder.put(ds1);
        builder.put(ds2);

        createMetadataForIndices(builder, ds1.getIndices());
        createMetadataForIndices(builder, ds1.getFailureIndices());
        createMetadataForIndices(builder, ds2.getIndices());
        createMetadataForIndices(builder, ds2.getFailureIndices());

        Index indexToAdd = randomFrom(ds2.getIndices().toArray(Index.EMPTY_ARRAY));

        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> ds1.addBackingIndex(builder.build(), indexToAdd));
        assertThat(
            e.getMessage(),
            equalTo(
                String.format(
                    Locale.ROOT,
                    "cannot add index [%s] to data stream [%s] because it is already a backing index on data stream [%s]",
                    indexToAdd.getName(),
                    ds1.getName(),
                    ds2.getName()
                )
            )
        );
    }

    public void testAddBackingIndexThatIsPartOfDataStreamFailureStore() {
        Metadata.Builder builder = Metadata.builder();

        DataStream ds1 = createRandomDataStream();
        DataStream ds2 = createRandomDataStream();
        builder.put(ds1);
        builder.put(ds2);

        createMetadataForIndices(builder, ds1.getIndices());
        createMetadataForIndices(builder, ds1.getFailureIndices());
        createMetadataForIndices(builder, ds2.getIndices());
        createMetadataForIndices(builder, ds2.getFailureIndices());

        Index indexToAdd = randomFrom(ds2.getFailureIndices().toArray(Index.EMPTY_ARRAY));

        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> ds1.addBackingIndex(builder.build(), indexToAdd));
        assertThat(
            e.getMessage(),
            equalTo(
                String.format(
                    Locale.ROOT,
                    "cannot add index [%s] to data stream [%s] because it is already a failure store index on data stream [%s]",
                    indexToAdd.getName(),
                    ds1.getName(),
                    ds2.getName()
                )
            )
        );
    }

    public void testAddExistingBackingIndex() {
        Metadata.Builder builder = Metadata.builder();

        DataStream original = createRandomDataStream();
        builder.put(original);

        createMetadataForIndices(builder, original.getIndices());

        Index indexToAdd = randomFrom(original.getIndices().toArray(Index.EMPTY_ARRAY));

        DataStream updated = original.addBackingIndex(builder.build(), indexToAdd);
        assertThat(updated.getName(), equalTo(original.getName()));
        assertThat(updated.getGeneration(), equalTo(original.getGeneration()));
        assertThat(updated.getIndices().size(), equalTo(original.getIndices().size()));
        for (int k = 0; k < original.getIndices().size(); k++) {
            assertThat(updated.getIndices().get(k), equalTo(original.getIndices().get(k)));
        }
    }

    public void testAddBackingIndexWithAliases() {
        Metadata.Builder builder = Metadata.builder();

        DataStream original = createRandomDataStream();
        builder.put(original);

        createMetadataForIndices(builder, original.getIndices());

        Index indexToAdd = new Index(randomAlphaOfLength(4), UUIDs.randomBase64UUID(random()));
        IndexMetadata.Builder b = IndexMetadata.builder(indexToAdd.getName())
            .settings(settings(IndexVersion.current()))
            .numberOfShards(1)
            .numberOfReplicas(1);
        final int numAliases = randomIntBetween(1, 3);
        final String[] aliasNames = new String[numAliases];
        for (int k = 0; k < numAliases; k++) {
            aliasNames[k] = randomAlphaOfLength(6);
            b.putAlias(AliasMetadata.builder(aliasNames[k]));
        }
        builder.put(b.build(), false);
        Arrays.sort(aliasNames);

        IllegalArgumentException e = expectThrows(
            IllegalArgumentException.class,
            () -> original.addBackingIndex(builder.build(), indexToAdd)
        );
        assertThat(
            e.getMessage(),
            equalTo(
                String.format(
                    Locale.ROOT,
                    "cannot add index [%s] to data stream [%s] until its %s [%s] %s removed",
                    indexToAdd.getName(),
                    original.getName(),
                    numAliases > 1 ? "aliases" : "alias",
                    Strings.arrayToCommaDelimitedString(aliasNames),
                    numAliases > 1 ? "are" : "is"
                )
            )
        );
    }

    public void testAddFailureStoreIndex() {
        Metadata.Builder builder = Metadata.builder();

        DataStream original = createRandomDataStream();
        builder.put(original);

        createMetadataForIndices(builder, original.getIndices());
        createMetadataForIndices(builder, original.getFailureIndices());

        Index indexToAdd = new Index(randomAlphaOfLength(4), UUIDs.randomBase64UUID(random()));
        builder.put(
            IndexMetadata.builder(indexToAdd.getName())
                .settings(settings(IndexVersion.current()))
                .numberOfShards(1)
                .numberOfReplicas(1)
                .build(),
            false
        );

        DataStream updated = original.addFailureStoreIndex(builder.build(), indexToAdd);
        assertThat(updated.getName(), equalTo(original.getName()));
        assertThat(updated.getGeneration(), equalTo(original.getGeneration() + 1));
        assertThat(updated.getIndices().size(), equalTo(original.getIndices().size()));
        assertThat(updated.getFailureIndices().size(), equalTo(original.getFailureIndices().size() + 1));
        for (int k = 1; k <= original.getFailureIndices().size(); k++) {
            assertThat(updated.getFailureIndices().get(k), equalTo(original.getFailureIndices().get(k - 1)));
        }
        assertThat(updated.getFailureIndices().get(0), equalTo(indexToAdd));
    }

    public void testAddFailureStoreIndexThatIsPartOfAnotherDataStream() {
        Metadata.Builder builder = Metadata.builder();

        DataStream ds1 = createRandomDataStream();
        DataStream ds2 = createRandomDataStream();
        builder.put(ds1);
        builder.put(ds2);

        createMetadataForIndices(builder, ds1.getIndices());
        createMetadataForIndices(builder, ds1.getFailureIndices());
        createMetadataForIndices(builder, ds2.getIndices());
        createMetadataForIndices(builder, ds2.getFailureIndices());

        Index indexToAdd = randomFrom(ds2.getFailureIndices().toArray(Index.EMPTY_ARRAY));

        IllegalArgumentException e = expectThrows(
            IllegalArgumentException.class,
            () -> ds1.addFailureStoreIndex(builder.build(), indexToAdd)
        );
        assertThat(
            e.getMessage(),
            equalTo(
                String.format(
                    Locale.ROOT,
                    "cannot add index [%s] to data stream [%s] because it is already a failure store index on data stream [%s]",
                    indexToAdd.getName(),
                    ds1.getName(),
                    ds2.getName()
                )
            )
        );
    }

    public void testAddFailureStoreIndexThatIsPartOfDataStreamBackingIndices() {
        Metadata.Builder builder = Metadata.builder();

        DataStream ds1 = createRandomDataStream();
        DataStream ds2 = createRandomDataStream();
        builder.put(ds1);
        builder.put(ds2);

        createMetadataForIndices(builder, ds1.getIndices());
        createMetadataForIndices(builder, ds1.getFailureIndices());
        createMetadataForIndices(builder, ds2.getIndices());
        createMetadataForIndices(builder, ds2.getFailureIndices());

        Index indexToAdd = randomFrom(ds2.getIndices().toArray(Index.EMPTY_ARRAY));

        IllegalArgumentException e = expectThrows(
            IllegalArgumentException.class,
            () -> ds1.addFailureStoreIndex(builder.build(), indexToAdd)
        );
        assertThat(
            e.getMessage(),
            equalTo(
                String.format(
                    Locale.ROOT,
                    "cannot add index [%s] to data stream [%s] because it is already a backing index on data stream [%s]",
                    indexToAdd.getName(),
                    ds1.getName(),
                    ds2.getName()
                )
            )
        );
    }

    public void testAddExistingFailureStoreIndex() {
        Metadata.Builder builder = Metadata.builder();

        DataStream original = createRandomDataStream();
        builder.put(original);

        createMetadataForIndices(builder, original.getIndices());
        createMetadataForIndices(builder, original.getFailureIndices());

        Index indexToAdd = randomFrom(original.getFailureIndices().toArray(Index.EMPTY_ARRAY));

        DataStream updated = original.addFailureStoreIndex(builder.build(), indexToAdd);
        assertThat(updated.getName(), equalTo(original.getName()));
        assertThat(updated.getGeneration(), equalTo(original.getGeneration()));
        assertThat(updated.getIndices().size(), equalTo(original.getIndices().size()));
        assertThat(updated.getFailureIndices().size(), equalTo(original.getFailureIndices().size()));
        assertThat(updated.getFailureIndices(), equalTo(original.getFailureIndices()));
    }

    public void testAddFailureStoreIndexWithAliases() {
        Metadata.Builder builder = Metadata.builder();

        DataStream original = createRandomDataStream();
        builder.put(original);

        createMetadataForIndices(builder, original.getIndices());
        createMetadataForIndices(builder, original.getFailureIndices());

        Index indexToAdd = new Index(randomAlphaOfLength(4), UUIDs.randomBase64UUID(random()));
        IndexMetadata.Builder b = IndexMetadata.builder(indexToAdd.getName())
            .settings(settings(IndexVersion.current()))
            .numberOfShards(1)
            .numberOfReplicas(1);
        final int numAliases = randomIntBetween(1, 3);
        final String[] aliasNames = new String[numAliases];
        for (int k = 0; k < numAliases; k++) {
            aliasNames[k] = randomAlphaOfLength(6);
            b.putAlias(AliasMetadata.builder(aliasNames[k]));
        }
        builder.put(b.build(), false);
        Arrays.sort(aliasNames);

        IllegalArgumentException e = expectThrows(
            IllegalArgumentException.class,
            () -> original.addFailureStoreIndex(builder.build(), indexToAdd)
        );
        assertThat(
            e.getMessage(),
            equalTo(
                String.format(
                    Locale.ROOT,
                    "cannot add index [%s] to data stream [%s] until its %s [%s] %s removed",
                    indexToAdd.getName(),
                    original.getName(),
                    numAliases > 1 ? "aliases" : "alias",
                    Strings.arrayToCommaDelimitedString(aliasNames),
                    numAliases > 1 ? "are" : "is"
                )
            )
        );
    }

    public void testDefaultBackingIndexName() {
        long backingIndexNum = randomLongBetween(1, 1000001);
        String dataStreamName = randomAlphaOfLength(6);
        long epochMillis = randomLongBetween(1580536800000L, 1583042400000L);
        String dateString = DataStream.DATE_FORMATTER.formatMillis(epochMillis);
        String defaultBackingIndexName = DataStream.getDefaultBackingIndexName(dataStreamName, backingIndexNum, epochMillis);
        String expectedBackingIndexName = Strings.format(".ds-%s-%s-%06d", dataStreamName, dateString, backingIndexNum);
        assertThat(defaultBackingIndexName, equalTo(expectedBackingIndexName));
    }

    public void testDefaultFailureStoreName() {
        long failureStoreIndexNum = randomLongBetween(1, 1000001);
        String dataStreamName = randomAlphaOfLength(6);
        long epochMillis = randomLongBetween(1580536800000L, 1583042400000L);
        String dateString = DataStream.DATE_FORMATTER.formatMillis(epochMillis);
        String defaultFailureStoreName = DataStream.getDefaultFailureStoreName(dataStreamName, failureStoreIndexNum, epochMillis);
        String expectedFailureStoreName = Strings.format(".fs-%s-%s-%06d", dataStreamName, dateString, failureStoreIndexNum);
        assertThat(defaultFailureStoreName, equalTo(expectedFailureStoreName));
    }

    public void testReplaceBackingIndex() {
        DataStream original = createRandomDataStream();
        int indexToReplace = randomIntBetween(1, original.getIndices().size() - 1) - 1;

        Index newBackingIndex = new Index("replacement-index", UUIDs.randomBase64UUID(random()));
        DataStream updated = original.replaceBackingIndex(original.getIndices().get(indexToReplace), newBackingIndex);
        assertThat(updated.getName(), equalTo(original.getName()));
        assertThat(updated.getGeneration(), equalTo(original.getGeneration() + 1));
        assertThat(updated.getIndices().size(), equalTo(original.getIndices().size()));
        assertThat(updated.getIndices().get(indexToReplace), equalTo(newBackingIndex));

        for (int i = 0; i < original.getIndices().size(); i++) {
            if (i != indexToReplace) {
                assertThat(updated.getIndices().get(i), equalTo(original.getIndices().get(i)));
            }
        }
    }

    public void testReplaceBackingIndexThrowsExceptionIfIndexNotPartOfDataStream() {
        DataStream original = createRandomDataStream();

        Index standaloneIndex = new Index("index-foo", UUIDs.randomBase64UUID(random()));
        Index newBackingIndex = new Index("replacement-index", UUIDs.randomBase64UUID(random()));
        expectThrows(IllegalArgumentException.class, () -> original.replaceBackingIndex(standaloneIndex, newBackingIndex));
    }

    public void testReplaceBackingIndexThrowsExceptionIfReplacingWriteIndex() {
        int numBackingIndices = randomIntBetween(2, 32);
        int writeIndexPosition = numBackingIndices - 1;
        String dataStreamName = randomAlphaOfLength(10).toLowerCase(Locale.ROOT);

        List<Index> indices = new ArrayList<>(numBackingIndices);
        for (int i = 1; i <= numBackingIndices; i++) {
            indices.add(new Index(DataStream.getDefaultBackingIndexName(dataStreamName, i), UUIDs.randomBase64UUID(random())));
        }
        int generation = randomBoolean() ? numBackingIndices : numBackingIndices + randomIntBetween(1, 5);
        DataStream original = newInstance(dataStreamName, indices, generation, null);

        Index newBackingIndex = new Index("replacement-index", UUIDs.randomBase64UUID(random()));
        IllegalArgumentException e = expectThrows(
            IllegalArgumentException.class,
            () -> original.replaceBackingIndex(indices.get(writeIndexPosition), newBackingIndex)
        );
        assertThat(
            e.getMessage(),
            equalTo(
                String.format(
                    Locale.ROOT,
                    "cannot replace backing index [%s] of data stream [%s] because it is the write index",
                    indices.get(writeIndexPosition).getName(),
                    dataStreamName
                )
            )
        );
    }

    public void testSnapshot() {
        var preSnapshotDataStream = DataStreamTestHelper.randomInstance();
        var indicesToRemove = randomSubsetOf(preSnapshotDataStream.getIndices());
        if (indicesToRemove.size() == preSnapshotDataStream.getIndices().size()) {
            indicesToRemove.remove(0);
        }
        var indicesToAdd = randomIndexInstances();
        var postSnapshotIndices = new ArrayList<>(preSnapshotDataStream.getIndices());
        postSnapshotIndices.removeAll(indicesToRemove);
        postSnapshotIndices.addAll(indicesToAdd);

        var replicated = preSnapshotDataStream.isReplicated() && randomBoolean();
        var postSnapshotDataStream = preSnapshotDataStream.copy()
            .setIndices(postSnapshotIndices)
            .setGeneration(preSnapshotDataStream.getGeneration() + randomIntBetween(0, 5))
            .setMetadata(preSnapshotDataStream.getMetadata() == null ? null : new HashMap<>(preSnapshotDataStream.getMetadata()))
            .setReplicated(replicated)
            .setRolloverOnWrite(replicated == false && preSnapshotDataStream.rolloverOnWrite())
            .build();

        var reconciledDataStream = postSnapshotDataStream.snapshot(
            preSnapshotDataStream.getIndices().stream().map(Index::getName).toList()
        );

        assertThat(reconciledDataStream.getName(), equalTo(postSnapshotDataStream.getName()));
        assertThat(reconciledDataStream.getGeneration(), equalTo(postSnapshotDataStream.getGeneration()));
        if (reconciledDataStream.getMetadata() != null) {
            assertThat(
                new HashSet<>(reconciledDataStream.getMetadata().entrySet()),
                hasItems(postSnapshotDataStream.getMetadata().entrySet().toArray())
            );
        } else {
            assertNull(postSnapshotDataStream.getMetadata());
        }
        assertThat(reconciledDataStream.isHidden(), equalTo(postSnapshotDataStream.isHidden()));
        assertThat(reconciledDataStream.isReplicated(), equalTo(postSnapshotDataStream.isReplicated()));
        assertThat(reconciledDataStream.getIndices(), everyItem(not(in(indicesToRemove))));
        assertThat(reconciledDataStream.getIndices(), everyItem(not(in(indicesToAdd))));
        assertThat(reconciledDataStream.getIndices().size(), equalTo(preSnapshotDataStream.getIndices().size() - indicesToRemove.size()));
    }

    public void testSnapshotWithAllBackingIndicesRemoved() {
        var preSnapshotDataStream = DataStreamTestHelper.randomInstance();
        var indicesToAdd = randomNonEmptyIndexInstances();

        var postSnapshotDataStream = preSnapshotDataStream.copy().setIndices(indicesToAdd).build();

        assertNull(postSnapshotDataStream.snapshot(preSnapshotDataStream.getIndices().stream().map(Index::getName).toList()));
    }

    public void testSelectTimeSeriesWriteIndex() {
        Instant currentTime = Instant.now();

        Instant start1 = currentTime.minus(6, ChronoUnit.HOURS);
        Instant end1 = currentTime.minus(2, ChronoUnit.HOURS);
        Instant start2 = currentTime.minus(2, ChronoUnit.HOURS);
        Instant end2 = currentTime.plus(2, ChronoUnit.HOURS);

        String dataStreamName = "logs_my-app_prod";
        ClusterState clusterState = DataStreamTestHelper.getClusterStateWithDataStream(
            dataStreamName,
            List.of(Tuple.tuple(start1, end1), Tuple.tuple(start2, end2))
        );

        DataStream dataStream = clusterState.getMetadata().dataStreams().get(dataStreamName);
        Index result = dataStream.selectTimeSeriesWriteIndex(currentTime, clusterState.getMetadata());
        assertThat(result, equalTo(dataStream.getIndices().get(1)));
        assertThat(result.getName(), equalTo(DataStream.getDefaultBackingIndexName(dataStreamName, 2, start2.toEpochMilli())));

        result = dataStream.selectTimeSeriesWriteIndex(currentTime.minus(2, ChronoUnit.HOURS), clusterState.getMetadata());
        assertThat(result, equalTo(dataStream.getIndices().get(1)));
        assertThat(result.getName(), equalTo(DataStream.getDefaultBackingIndexName(dataStreamName, 2, start2.toEpochMilli())));

        result = dataStream.selectTimeSeriesWriteIndex(currentTime.minus(3, ChronoUnit.HOURS), clusterState.getMetadata());
        assertThat(result, equalTo(dataStream.getIndices().get(0)));
        assertThat(result.getName(), equalTo(DataStream.getDefaultBackingIndexName(dataStreamName, 1, start1.toEpochMilli())));

        result = dataStream.selectTimeSeriesWriteIndex(currentTime.minus(6, ChronoUnit.HOURS), clusterState.getMetadata());
        assertThat(result, equalTo(dataStream.getIndices().get(0)));
        assertThat(result.getName(), equalTo(DataStream.getDefaultBackingIndexName(dataStreamName, 1, start1.toEpochMilli())));
    }

    public void testValidate() {
        {
            Instant currentTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);

            Instant start1 = currentTime.minus(6, ChronoUnit.HOURS);
            Instant end1 = currentTime.minus(2, ChronoUnit.HOURS);
            Instant start2 = currentTime.minus(2, ChronoUnit.HOURS);
            Instant end2 = currentTime.plus(2, ChronoUnit.HOURS);

            String dataStreamName = "logs_my-app_prod";
            var clusterState = DataStreamTestHelper.getClusterStateWithDataStream(
                dataStreamName,
                List.of(Tuple.tuple(start1, end1), Tuple.tuple(start2, end2))
            );
            DataStream dataStream = clusterState.getMetadata().dataStreams().get(dataStreamName);
            assertThat(dataStream, notNullValue());
            assertThat(dataStream.getIndices(), hasSize(2));
            assertThat(
                IndexSettings.TIME_SERIES_START_TIME.get(clusterState.getMetadata().index(dataStream.getIndices().get(0)).getSettings()),
                equalTo(start1)
            );
            assertThat(
                IndexSettings.TIME_SERIES_END_TIME.get(clusterState.getMetadata().index(dataStream.getIndices().get(0)).getSettings()),
                equalTo(end1)
            );
            assertThat(
                IndexSettings.TIME_SERIES_START_TIME.get(clusterState.getMetadata().index(dataStream.getIndices().get(1)).getSettings()),
                equalTo(start2)
            );
            assertThat(
                IndexSettings.TIME_SERIES_END_TIME.get(clusterState.getMetadata().index(dataStream.getIndices().get(1)).getSettings()),
                equalTo(end2)
            );

            DataStreamTestHelper.getClusterStateWithDataStream(
                dataStreamName,
                List.of(Tuple.tuple(start1, end1.minus(1, ChronoUnit.MINUTES)), Tuple.tuple(start2.plus(1, ChronoUnit.MINUTES), end2))
            );
        }
        {
            Instant currentTime = Instant.now();

            Instant start1 = currentTime.minus(6, ChronoUnit.HOURS);
            Instant end1 = currentTime.minus(2, ChronoUnit.HOURS);
            Instant start2 = currentTime.minus(3, ChronoUnit.HOURS);
            Instant end2 = currentTime.plus(2, ChronoUnit.HOURS);

            String dataStreamName = "logs_my-app_prod";
            var e = expectThrows(
                IllegalArgumentException.class,
                () -> DataStreamTestHelper.getClusterStateWithDataStream(
                    dataStreamName,
                    List.of(Tuple.tuple(start1, end1), Tuple.tuple(start2, end2))
                )
            );
            var formatter = DateFieldMapper.DEFAULT_DATE_TIME_FORMATTER;
            assertThat(
                e.getMessage(),
                equalTo(
                    "backing index ["
                        + DataStream.getDefaultBackingIndexName(dataStreamName, 1, start1.toEpochMilli())
                        + "] with range ["
                        + formatter.format(start1)
                        + " TO "
                        + formatter.format(end1)
                        + "] is overlapping with backing index ["
                        + DataStream.getDefaultBackingIndexName(dataStreamName, 2, start2.toEpochMilli())
                        + "] with range ["
                        + formatter.format(start2)
                        + " TO "
                        + formatter.format(end2)
                        + "]"
                )
            );
        }
        {
            Instant currentTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);

            Instant start1 = currentTime.minus(6, ChronoUnit.HOURS);
            Instant end1 = currentTime.minus(2, ChronoUnit.HOURS);
            Instant start2 = currentTime.minus(2, ChronoUnit.HOURS);
            Instant end2 = currentTime.plus(2, ChronoUnit.HOURS);

            String dataStreamName = "logs_my-app_prod";
            var clusterState = DataStreamTestHelper.getClusterStateWithDataStream(
                dataStreamName,
                List.of(Tuple.tuple(start1, end1), Tuple.tuple(start2, end2))
            );
            DataStream dataStream = clusterState.getMetadata().dataStreams().get(dataStreamName);

            {
                var e = expectThrows(IllegalStateException.class, () -> dataStream.validate((index) -> null));
                assertThat(
                    e.getMessage(),
                    equalTo(
                        "index ["
                            + DataStream.getDefaultBackingIndexName(dataStreamName, 1, start1.toEpochMilli())
                            + "] is not found in the index metadata supplier"
                    )
                );
            }

            {
                dataStream.validate(
                    (index) -> IndexMetadata.builder(index)
                        .settings(
                            Settings.builder()
                                .put(IndexMetadata.INDEX_NUMBER_OF_SHARDS_SETTING.getKey(), 1)
                                .put(IndexMetadata.INDEX_NUMBER_OF_REPLICAS_SETTING.getKey(), 1)
                                .put(IndexMetadata.SETTING_INDEX_VERSION_CREATED.getKey(), IndexVersion.current())
                                .build()
                        )
                        .build()
                );
            }

            {
                Instant start3 = currentTime.minus(6, ChronoUnit.HOURS);
                Instant end3 = currentTime.plus(2, ChronoUnit.HOURS);
                var e = expectThrows(
                    IllegalArgumentException.class,
                    () -> dataStream.validate(
                        (index) -> IndexMetadata.builder(index)
                            .settings(
                                Settings.builder()
                                    .put(IndexMetadata.INDEX_NUMBER_OF_SHARDS_SETTING.getKey(), 1)
                                    .put(IndexMetadata.INDEX_NUMBER_OF_REPLICAS_SETTING.getKey(), 1)
                                    .put(IndexMetadata.SETTING_INDEX_VERSION_CREATED.getKey(), IndexVersion.current())
                                    .put(IndexSettings.MODE.getKey(), IndexMode.TIME_SERIES)
                                    .put(IndexSettings.TIME_SERIES_START_TIME.getKey(), start3.toEpochMilli())
                                    .put(IndexSettings.TIME_SERIES_END_TIME.getKey(), end3.toEpochMilli())
                                    .build()
                            )
                            .build()
                    )
                );
                var formatter = DateFieldMapper.DEFAULT_DATE_TIME_FORMATTER;
                assertThat(
                    e.getMessage(),
                    equalTo(
                        "backing index ["
                            + DataStream.getDefaultBackingIndexName(dataStreamName, 1, start1.toEpochMilli())
                            + "] with range ["
                            + formatter.format(start3)
                            + " TO "
                            + formatter.format(end3)
                            + "] is overlapping with backing index ["
                            + DataStream.getDefaultBackingIndexName(dataStreamName, 2, start2.toEpochMilli())
                            + "] with range ["
                            + formatter.format(start3)
                            + " TO "
                            + formatter.format(end3)
                            + "]"
                    )
                );
            }
        }
    }

    public void testGetGenerationLifecycleDate() {
        String dataStreamName = "metrics-foo";
        long now = System.currentTimeMillis();
        long creationTimeMillis = now - 3000L;
        long rolloverTimeMills = now - 2000L;

        {
            IndexMetadata.Builder indexMetaBuilder = IndexMetadata.builder(DataStream.getDefaultBackingIndexName(dataStreamName, 1))
                .settings(settings(IndexVersion.current()))
                .numberOfShards(1)
                .numberOfReplicas(1)
                .creationDate(creationTimeMillis);
            IndexMetadata indexMetadata = indexMetaBuilder.build();
            DataStream dataStream = createDataStream(dataStreamName, List.of(indexMetadata.getIndex()));
            assertNull(dataStream.getGenerationLifecycleDate(indexMetadata));
        }

        {
            IndexMetadata.Builder writeIndexMetaBuilder = IndexMetadata.builder(DataStream.getDefaultBackingIndexName(dataStreamName, 2))
                .settings(settings(IndexVersion.current()))
                .numberOfShards(1)
                .numberOfReplicas(1)
                .creationDate(now - 3000L);
            IndexMetadata.Builder indexMetaBuilder = IndexMetadata.builder(DataStream.getDefaultBackingIndexName(dataStreamName, 1))
                .settings(settings(IndexVersion.current()))
                .numberOfShards(1)
                .numberOfReplicas(1)
                .creationDate(now - 3000L);

            MaxAgeCondition rolloverCondition = new MaxAgeCondition(TimeValue.timeValueMillis(rolloverTimeMills));
            indexMetaBuilder.putRolloverInfo(new RolloverInfo(dataStreamName, List.of(rolloverCondition), now - 2000L));
            IndexMetadata indexMetadata = indexMetaBuilder.build();
            DataStream dataStream = createDataStream(
                dataStreamName,
                List.of(indexMetadata.getIndex(), writeIndexMetaBuilder.build().getIndex())
            );
            assertThat(dataStream.getGenerationLifecycleDate(indexMetadata).millis(), is(rolloverTimeMills));
        }

        {
            IndexMetadata.Builder writeIndexMetaBuilder = IndexMetadata.builder(DataStream.getDefaultBackingIndexName(dataStreamName, 2))
                .settings(settings(IndexVersion.current()))
                .numberOfShards(1)
                .numberOfReplicas(1)
                .creationDate(now - 3000L);
            IndexMetadata.Builder indexMetaBuilder = IndexMetadata.builder(DataStream.getDefaultBackingIndexName(dataStreamName, 1))
                .settings(settings(IndexVersion.current()))
                .numberOfShards(1)
                .numberOfReplicas(1)
                .creationDate(creationTimeMillis);

            MaxAgeCondition rolloverCondition = new MaxAgeCondition(TimeValue.timeValueMillis(rolloverTimeMills));
            indexMetaBuilder.putRolloverInfo(new RolloverInfo("some-alias-name", List.of(rolloverCondition), now - 2000L));
            IndexMetadata indexMetadata = indexMetaBuilder.build();
            DataStream dataStream = createDataStream(
                dataStreamName,
                List.of(indexMetadata.getIndex(), writeIndexMetaBuilder.build().getIndex())
            );
            assertThat(dataStream.getGenerationLifecycleDate(indexMetadata).millis(), is(creationTimeMillis));
        }
        {
            long originTimeMillis = creationTimeMillis - 3000L;
            IndexMetadata.Builder indexMetaBuilder = IndexMetadata.builder(DataStream.getDefaultBackingIndexName(dataStreamName, 1))
                .settings(settings(IndexVersion.current()).put(LIFECYCLE_ORIGINATION_DATE, originTimeMillis))
                .numberOfShards(1)
                .numberOfReplicas(1)
                .creationDate(creationTimeMillis);
            IndexMetadata indexMetadata = indexMetaBuilder.build();
            DataStream dataStream = createDataStream(dataStreamName, List.of(indexMetadata.getIndex()));

            assertNull(dataStream.getGenerationLifecycleDate(indexMetadata));
        }
        {
            IndexMetadata.Builder writeIndexMetaBuilder = IndexMetadata.builder(DataStream.getDefaultBackingIndexName(dataStreamName, 2))
                .settings(settings(IndexVersion.current()))
                .numberOfShards(1)
                .numberOfReplicas(1)
                .creationDate(now - 3000L);
            long originTimeMillis = creationTimeMillis - 3000L;
            IndexMetadata.Builder indexMetaBuilder = IndexMetadata.builder(DataStream.getDefaultBackingIndexName(dataStreamName, 1))
                .settings(settings(IndexVersion.current()).put(LIFECYCLE_ORIGINATION_DATE, originTimeMillis))
                .numberOfShards(1)
                .numberOfReplicas(1)
                .creationDate(creationTimeMillis);
            IndexMetadata indexMetadata = indexMetaBuilder.build();
            DataStream dataStream = createDataStream(
                dataStreamName,
                List.of(indexMetadata.getIndex(), writeIndexMetaBuilder.build().getIndex())
            );
            assertThat(dataStream.getGenerationLifecycleDate(indexMetadata).millis(), is(originTimeMillis));
        }
        {
            IndexMetadata.Builder writeIndexMetaBuilder = IndexMetadata.builder(DataStream.getDefaultBackingIndexName(dataStreamName, 2))
                .settings(settings(IndexVersion.current()))
                .numberOfShards(1)
                .numberOfReplicas(1)
                .creationDate(now - 3000L);
            long originTimeMillis = creationTimeMillis - 3000L;
            IndexMetadata.Builder indexMetaBuilder = IndexMetadata.builder(DataStream.getDefaultBackingIndexName(dataStreamName, 1))
                .settings(settings(IndexVersion.current()).put(LIFECYCLE_ORIGINATION_DATE, originTimeMillis))
                .numberOfShards(1)
                .numberOfReplicas(1)
                .creationDate(creationTimeMillis);

            MaxAgeCondition rolloverCondition = new MaxAgeCondition(TimeValue.timeValueMillis(rolloverTimeMills));
            indexMetaBuilder.putRolloverInfo(new RolloverInfo(dataStreamName, List.of(rolloverCondition), now - 2000L));
            IndexMetadata indexMetadata = indexMetaBuilder.build();
            DataStream dataStream = createDataStream(
                dataStreamName,
                List.of(indexMetadata.getIndex(), writeIndexMetaBuilder.build().getIndex())
            );
            assertThat(dataStream.getGenerationLifecycleDate(indexMetadata).millis(), is(originTimeMillis));
        }
        {
            IndexMetadata.Builder writeIndexMetaBuilder = IndexMetadata.builder(DataStream.getDefaultBackingIndexName(dataStreamName, 2))
                .settings(settings(IndexVersion.current()))
                .numberOfShards(1)
                .numberOfReplicas(1)
                .creationDate(now - 3000L);
            long originTimeMillis = creationTimeMillis - 3000L;
            IndexMetadata.Builder indexMetaBuilder = IndexMetadata.builder(DataStream.getDefaultBackingIndexName(dataStreamName, 1))
                .settings(settings(IndexVersion.current()).put(LIFECYCLE_ORIGINATION_DATE, originTimeMillis))
                .numberOfShards(1)
                .numberOfReplicas(1)
                .creationDate(creationTimeMillis);

            MaxAgeCondition rolloverCondition = new MaxAgeCondition(TimeValue.timeValueMillis(rolloverTimeMills));
            indexMetaBuilder.putRolloverInfo(new RolloverInfo("some-alias-name", List.of(rolloverCondition), now - 2000L));
            IndexMetadata indexMetadata = indexMetaBuilder.build();
            DataStream dataStream = createDataStream(
                dataStreamName,
                List.of(indexMetadata.getIndex(), writeIndexMetaBuilder.build().getIndex())
            );
            assertThat(dataStream.getGenerationLifecycleDate(indexMetadata).millis(), is(originTimeMillis));
        }
    }

    private DataStream createDataStream(String name, List<Index> indices) {
        return DataStream.builder(name, indices)
            .setMetadata(Map.of())
            .setReplicated(randomBoolean())
            .setAllowCustomRouting(randomBoolean())
            .setIndexMode(IndexMode.STANDARD)
            .build();
    }

    public void testGetIndicesOlderThan() {
        String dataStreamName = "metrics-foo";
        long now = System.currentTimeMillis();

        List<DataStreamMetadata> creationAndRolloverTimes = List.of(
            DataStreamMetadata.dataStreamMetadata(now - 5000, now - 4000),
            DataStreamMetadata.dataStreamMetadata(now - 4000, now - 3000),
            DataStreamMetadata.dataStreamMetadata(now - 3000, now - 2000),
            DataStreamMetadata.dataStreamMetadata(now - 2000, now - 1000),
            DataStreamMetadata.dataStreamMetadata(now, null)
        );

        Metadata.Builder builder = Metadata.builder();
        DataStream dataStream = createDataStream(
            builder,
            dataStreamName,
            creationAndRolloverTimes,
            settings(IndexVersion.current()),
            new DataStreamLifecycle()
        );
        Metadata metadata = builder.build();
        {
            List<Index> backingIndices = dataStream.getNonWriteIndicesOlderThan(
                TimeValue.timeValueMillis(2500),
                metadata::index,
                null,
                () -> now
            );
            assertThat(backingIndices.size(), is(2));
            assertThat(backingIndices.get(0).getName(), is(DataStream.getDefaultBackingIndexName(dataStreamName, 1)));
            assertThat(backingIndices.get(1).getName(), is(DataStream.getDefaultBackingIndexName(dataStreamName, 2)));
        }

        {
            List<Index> backingIndices = dataStream.getNonWriteIndicesOlderThan(
                TimeValue.timeValueMillis(0),
                metadata::index,
                null,
                () -> now
            );
            assertThat(backingIndices.size(), is(4));
            assertThat(backingIndices.get(0).getName(), is(DataStream.getDefaultBackingIndexName(dataStreamName, 1)));
            assertThat(backingIndices.get(1).getName(), is(DataStream.getDefaultBackingIndexName(dataStreamName, 2)));
            assertThat(backingIndices.get(2).getName(), is(DataStream.getDefaultBackingIndexName(dataStreamName, 3)));
            assertThat(backingIndices.get(3).getName(), is(DataStream.getDefaultBackingIndexName(dataStreamName, 4)));
        }

        {
            List<Index> backingIndices = dataStream.getNonWriteIndicesOlderThan(
                TimeValue.timeValueMillis(6000),
                metadata::index,
                null,
                () -> now
            );
            assertThat(backingIndices.isEmpty(), is(true));
        }

        {
            Predicate<IndexMetadata> genThreeAndFivePredicate = indexMetadata -> indexMetadata.getIndex().getName().endsWith("00003")
                || indexMetadata.getIndex().getName().endsWith("00005");

            List<Index> backingIndices = dataStream.getNonWriteIndicesOlderThan(
                TimeValue.timeValueMillis(0),
                metadata::index,
                genThreeAndFivePredicate,
                () -> now
            );
            assertThat(backingIndices.size(), is(1));
            assertThat(backingIndices.get(0).getName(), is(DataStream.getDefaultBackingIndexName(dataStreamName, 3)));
        }

    }

    public void testGetIndicesPastRetention() {
        String dataStreamName = "metrics-foo";
        long now = System.currentTimeMillis();

        List<DataStreamMetadata> creationAndRolloverTimes = List.of(
            DataStreamMetadata.dataStreamMetadata(now - 5000_000, now - 4000_000),
            DataStreamMetadata.dataStreamMetadata(now - 4000_000, now - 3000_000),
            DataStreamMetadata.dataStreamMetadata(now - 3000_000, now - 2000_000),
            DataStreamMetadata.dataStreamMetadata(now - 2000_000, now - 1000_000),
            DataStreamMetadata.dataStreamMetadata(now, null)
        );

        {
            Metadata.Builder builder = Metadata.builder();
            DataStream dataStream = createDataStream(
                builder,
                dataStreamName,
                creationAndRolloverTimes,
                settings(IndexVersion.current()),
                null
            );
            Metadata metadata = builder.build();

            assertThat(dataStream.getIndicesPastRetention(metadata::index, () -> now, randomGlobalRetention()).isEmpty(), is(true));
        }

        {
            DataStreamGlobalRetention globalRetention = new DataStreamGlobalRetention(
                TimeValue.timeValueSeconds(2500),
                randomBoolean() ? TimeValue.timeValueSeconds(randomIntBetween(2500, 5000)) : null
            );
            Metadata.Builder builder = Metadata.builder();
            DataStream dataStream = createDataStream(
                builder,
                dataStreamName,
                creationAndRolloverTimes,
                settings(IndexVersion.current()),
                new DataStreamLifecycle()
            );
            Metadata metadata = builder.build();

            List<Index> backingIndices = dataStream.getIndicesPastRetention(metadata::index, () -> now, globalRetention);
            assertThat(backingIndices.size(), is(2));
            assertThat(backingIndices.get(0).getName(), is(dataStream.getIndices().get(0).getName()));
            assertThat(backingIndices.get(1).getName(), is(dataStream.getIndices().get(1).getName()));
        }

        {
            DataStreamGlobalRetention globalRetention = new DataStreamGlobalRetention(null, TimeValue.timeValueSeconds(2500));
            Metadata.Builder builder = Metadata.builder();
            DataStream dataStream = createDataStream(
                builder,
                dataStreamName,
                creationAndRolloverTimes,
                settings(IndexVersion.current()),
                new DataStreamLifecycle()
            );
            Metadata metadata = builder.build();

            List<Index> backingIndices = dataStream.getIndicesPastRetention(metadata::index, () -> now, globalRetention);
            assertThat(backingIndices.size(), is(2));
            assertThat(backingIndices.get(0).getName(), is(dataStream.getIndices().get(0).getName()));
            assertThat(backingIndices.get(1).getName(), is(dataStream.getIndices().get(1).getName()));
        }

        {
            Metadata.Builder builder = Metadata.builder();
            DataStream dataStream = createDataStream(
                builder,
                dataStreamName,
                creationAndRolloverTimes,
                settings(IndexVersion.current()),
                DataStreamLifecycle.newBuilder().dataRetention(TimeValue.timeValueSeconds(2500)).build()
            );
            Metadata metadata = builder.build();

            List<Index> backingIndices = dataStream.getIndicesPastRetention(metadata::index, () -> now, randomGlobalRetention());
            assertThat(backingIndices.size(), is(2));
            assertThat(backingIndices.get(0).getName(), is(dataStream.getIndices().get(0).getName()));
            assertThat(backingIndices.get(1).getName(), is(dataStream.getIndices().get(1).getName()));
        }

        {
            Metadata.Builder builder = Metadata.builder();
            DataStream dataStream = createDataStream(
                builder,
                dataStreamName,
                creationAndRolloverTimes,
                settings(IndexVersion.current()),
                DataStreamLifecycle.newBuilder().dataRetention(0).build()
            );
            Metadata metadata = builder.build();

            List<Index> backingIndices = dataStream.getIndicesPastRetention(metadata::index, () -> now, randomGlobalRetention());

            assertThat(backingIndices.size(), is(4));
            assertThat(backingIndices.get(0).getName(), is(dataStream.getIndices().get(0).getName()));
            assertThat(backingIndices.get(1).getName(), is(dataStream.getIndices().get(1).getName()));
            assertThat(backingIndices.get(2).getName(), is(dataStream.getIndices().get(2).getName()));
            assertThat(backingIndices.get(3).getName(), is(dataStream.getIndices().get(3).getName()));
        }

        {
            Metadata.Builder builder = Metadata.builder();
            DataStream dataStream = createDataStream(
                builder,
                dataStreamName,
                creationAndRolloverTimes,
                settings(IndexVersion.current()),
                DataStreamLifecycle.newBuilder().dataRetention(TimeValue.timeValueSeconds(6000)).build()
            );
            Metadata metadata = builder.build();

            List<Index> backingIndices = dataStream.getIndicesPastRetention(metadata::index, () -> now, randomGlobalRetention());
            assertThat(backingIndices.isEmpty(), is(true));
        }

        {
            Metadata.Builder builder = Metadata.builder();
            DataStream dataStream = createDataStream(
                builder,
                dataStreamName,
                creationAndRolloverTimes,
                Settings.builder()
                    .put(IndexMetadata.LIFECYCLE_NAME, "ILM_policy")
                    .put(IndexMetadata.SETTING_VERSION_CREATED, IndexVersion.current()),
                DataStreamLifecycle.newBuilder().dataRetention(0).build()
            );
            Metadata metadata = builder.build();

            List<Index> backingIndices = dataStream.getIndicesPastRetention(metadata::index, () -> now, randomGlobalRetention());
            assertThat(backingIndices.isEmpty(), is(true));
        }
    }

    public void testGetIndicesPastRetentionWithOriginationDate() {
        String dataStreamName = "metrics-foo";
        long now = System.currentTimeMillis();
        List<DataStreamMetadata> creationAndRolloverTimes = List.of(
            DataStreamMetadata.dataStreamMetadata(now - 5000, now - 4000),
            DataStreamMetadata.dataStreamMetadata(now - 4000, now - 3000),
            DataStreamMetadata.dataStreamMetadata(now - 3000, now - 2000),
            DataStreamMetadata.dataStreamMetadata(now - 2000, now - 1000),
            DataStreamMetadata.dataStreamMetadata(now, null, now - 8000), 
            DataStreamMetadata.dataStreamMetadata(now, null, now - 1000), 
            DataStreamMetadata.dataStreamMetadata(now, null)
        );
        Metadata.Builder metadataBuilder = Metadata.builder();
        AtomicReference<TimeValue> testRetentionReference = new AtomicReference<>(null);
        DataStream dataStream = createDataStream(
            metadataBuilder,
            dataStreamName,
            creationAndRolloverTimes,
            settings(IndexVersion.current()),
            new DataStreamLifecycle() {
                public TimeValue getDataStreamRetention() {
                    return testRetentionReference.get();
                }
            }
        );
        Metadata metadata = metadataBuilder.build();
        {
            testRetentionReference.set(null);
            assertThat(dataStream.getIndicesPastRetention(metadata::index, () -> now, null).isEmpty(), is(true));
        }

        {
            testRetentionReference.set(TimeValue.timeValueMillis(2500));
            List<Index> backingIndices = dataStream.getIndicesPastRetention(metadata::index, () -> now, null);
            assertThat(backingIndices.size(), is(3));
            assertThat(backingIndices.get(0).getName(), is(dataStream.getIndices().get(0).getName()));
            assertThat(backingIndices.get(1).getName(), is(dataStream.getIndices().get(1).getName()));
            assertThat(backingIndices.get(2).getName(), is(dataStream.getIndices().get(5).getName()));
        }

        {
            testRetentionReference.set(TimeValue.timeValueMillis(0));
            List<Index> backingIndices = dataStream.getIndicesPastRetention(metadata::index, () -> now, null);

            assertThat(backingIndices.size(), is(6));
            assertThat(backingIndices.get(0).getName(), is(dataStream.getIndices().get(0).getName()));
            assertThat(backingIndices.get(1).getName(), is(dataStream.getIndices().get(1).getName()));
            assertThat(backingIndices.get(2).getName(), is(dataStream.getIndices().get(2).getName()));
            assertThat(backingIndices.get(3).getName(), is(dataStream.getIndices().get(3).getName()));
            assertThat(backingIndices.get(4).getName(), is(dataStream.getIndices().get(4).getName()));
            assertThat(backingIndices.get(5).getName(), is(dataStream.getIndices().get(5).getName()));
        }

        {
            testRetentionReference.set(TimeValue.timeValueMillis(9000));
            List<Index> backingIndices = dataStream.getIndicesPastRetention(metadata::index, () -> now, null);
            assertThat(backingIndices.isEmpty(), is(true));
        }
    }

    public void testGetDownsampleRounds() {
        String dataStreamName = "metrics-foo";
        long now = System.currentTimeMillis();

        List<DataStreamMetadata> creationAndRolloverTimes = List.of(
            DataStreamMetadata.dataStreamMetadata(now - 5000, now - 4000),
            DataStreamMetadata.dataStreamMetadata(now - 4000, now - 3000),
            DataStreamMetadata.dataStreamMetadata(now - 3000, now - 2000),
            DataStreamMetadata.dataStreamMetadata(now - 2000, now - 1000),
            DataStreamMetadata.dataStreamMetadata(now, null)
        );

        {
            Metadata.Builder builder = Metadata.builder();
            DataStream dataStream = createDataStream(
                builder,
                dataStreamName,
                creationAndRolloverTimes,
                settings(IndexVersion.current()).put(IndexSettings.MODE.getKey(), IndexMode.TIME_SERIES)
                    .put("index.routing_path", "@timestamp"),
                DataStreamLifecycle.newBuilder()
                    .downsampling(
                        new DataStreamLifecycle.Downsampling(
                            List.of(
                                new DataStreamLifecycle.Downsampling.Round(
                                    TimeValue.timeValueMillis(2000),
                                    new DownsampleConfig(new DateHistogramInterval("10m"))
                                ),
                                new DataStreamLifecycle.Downsampling.Round(
                                    TimeValue.timeValueMillis(3200),
                                    new DownsampleConfig(new DateHistogramInterval("100m"))
                                ),
                                new DataStreamLifecycle.Downsampling.Round(
                                    TimeValue.timeValueMillis(3500),
                                    new DownsampleConfig(new DateHistogramInterval("1000m"))
                                )
                            )
                        )
                    )
                    .build()
            );
            Metadata metadata = builder.build();

            String thirdGeneration = DataStream.getDefaultBackingIndexName(dataStreamName, 3);
            Index thirdIndex = metadata.index(thirdGeneration).getIndex();
            List<DataStreamLifecycle.Downsampling.Round> roundsForThirdIndex = dataStream.getDownsamplingRoundsFor(
                thirdIndex,
                metadata::index,
                () -> now
            );

            assertThat(roundsForThirdIndex.size(), is(1));
            assertThat(roundsForThirdIndex.get(0).after(), is(TimeValue.timeValueMillis(2000)));

            String firstGeneration = DataStream.getDefaultBackingIndexName(dataStreamName, 1);
            Index firstIndex = metadata.index(firstGeneration).getIndex();
            List<DataStreamLifecycle.Downsampling.Round> roundsForFirstIndex = dataStream.getDownsamplingRoundsFor(
                firstIndex,
                metadata::index,
                () -> now
            );
            assertThat(roundsForFirstIndex.size(), is(3));
            assertThat(
                roundsForFirstIndex.stream().map(DataStreamLifecycle.Downsampling.Round::after).toList(),
                is(List.of(TimeValue.timeValueMillis(2000), TimeValue.timeValueMillis(3200), TimeValue.timeValueMillis(3500)))
            );
        }

        {
            Metadata.Builder builder = Metadata.builder();
            DataStream dataStream = createDataStream(
                builder,
                dataStreamName,
                creationAndRolloverTimes,
                settings(IndexVersion.current()),
                DataStreamLifecycle.newBuilder()
                    .downsampling(
                        new DataStreamLifecycle.Downsampling(
                            List.of(
                                new DataStreamLifecycle.Downsampling.Round(
                                    TimeValue.timeValueMillis(2000),
                                    new DownsampleConfig(new DateHistogramInterval("10m"))
                                ),
                                new DataStreamLifecycle.Downsampling.Round(
                                    TimeValue.timeValueMillis(3200),
                                    new DownsampleConfig(new DateHistogramInterval("100m"))
                                ),
                                new DataStreamLifecycle.Downsampling.Round(
                                    TimeValue.timeValueMillis(3500),
                                    new DownsampleConfig(new DateHistogramInterval("1000m"))
                                )
                            )
                        )
                    )
                    .build()
            );
            Metadata metadata = builder.build();

            String firstGeneration = DataStream.getDefaultBackingIndexName(dataStreamName, 1);
            Index firstIndex = metadata.index(firstGeneration).getIndex();
            List<DataStreamLifecycle.Downsampling.Round> roundsForFirstIndex = dataStream.getDownsamplingRoundsFor(
                firstIndex,
                metadata::index,
                () -> now
            );
            assertThat(roundsForFirstIndex.size(), is(0));
        }

        {
            Metadata.Builder builder = Metadata.builder();
            DataStream dataStream = createDataStream(
                builder,
                dataStreamName,
                creationAndRolloverTimes,
                settings(IndexVersion.current()).put(IndexSettings.MODE.getKey(), IndexMode.TIME_SERIES)
                    .put("index.routing_path", "@timestamp"),
                null
            );
            Metadata metadata = builder.build();

            String firstGeneration = DataStream.getDefaultBackingIndexName(dataStreamName, 1);
            Index firstIndex = metadata.index(firstGeneration).getIndex();
            List<DataStreamLifecycle.Downsampling.Round> roundsForFirstIndex = dataStream.getDownsamplingRoundsFor(
                firstIndex,
                metadata::index,
                () -> now
            );
            assertThat(roundsForFirstIndex.size(), is(0));
        }

        {
            Metadata.Builder builder = Metadata.builder();
            DataStream dataStream = createDataStream(
                builder,
                dataStreamName,
                creationAndRolloverTimes,
                settings(IndexVersion.current()).put(IndexSettings.MODE.getKey(), IndexMode.TIME_SERIES)
                    .put("index.routing_path", "@timestamp"),
                DataStreamLifecycle.newBuilder().build()
            );
            Metadata metadata = builder.build();

            String firstGeneration = DataStream.getDefaultBackingIndexName(dataStreamName, 1);
            Index firstIndex = metadata.index(firstGeneration).getIndex();
            List<DataStreamLifecycle.Downsampling.Round> roundsForFirstIndex = dataStream.getDownsamplingRoundsFor(
                firstIndex,
                metadata::index,
                () -> now
            );
            assertThat(roundsForFirstIndex.size(), is(0));
        }
    }

    public void testIsIndexManagedByDataStreamLifecycle() {
        String dataStreamName = "metrics-foo";
        long now = System.currentTimeMillis();

        List<DataStreamMetadata> creationAndRolloverTimes = List.of(
            DataStreamMetadata.dataStreamMetadata(now - 5000, now - 4000),
            DataStreamMetadata.dataStreamMetadata(now - 4000, now - 3000),
            DataStreamMetadata.dataStreamMetadata(now - 3000, now - 2000),
            DataStreamMetadata.dataStreamMetadata(now - 2000, now - 1000),
            DataStreamMetadata.dataStreamMetadata(now, null)
        );
        Metadata.Builder builder = Metadata.builder();
        DataStream dataStream = createDataStream(
            builder,
            dataStreamName,
            creationAndRolloverTimes,
            settings(IndexVersion.current()),
            DataStreamLifecycle.newBuilder().dataRetention(0).build()
        );
        Metadata metadata = builder.build();

        {
            assertThat(dataStream.isIndexManagedByDataStreamLifecycle(new Index("standalone_index", "uuid"), metadata::index), is(false));
        }

        {
            assertThat(dataStream.isIndexManagedByDataStreamLifecycle(dataStream.getIndices().get(1), (index) -> null), is(false));
        }

        {
            Metadata.Builder newBuilder = Metadata.builder();
            DataStream unmanagedDataStream = createDataStream(
                newBuilder,
                dataStreamName,
                creationAndRolloverTimes,
                settings(IndexVersion.current()),
                null
            );
            Metadata newMetadata = newBuilder.build();
            assertThat(
                unmanagedDataStream.isIndexManagedByDataStreamLifecycle(unmanagedDataStream.getIndices().get(1), newMetadata::index),
                is(false)
            );
        }

        {
            Metadata.Builder builderWithIlm = Metadata.builder();
            DataStream ds = createDataStream(
                builderWithIlm,
                dataStreamName,
                creationAndRolloverTimes,
                Settings.builder()
                    .put(IndexMetadata.LIFECYCLE_NAME, "ILM_policy")
                    .put(IndexMetadata.SETTING_VERSION_CREATED, IndexVersion.current()),
                new DataStreamLifecycle()
            );
            Metadata metadataIlm = builderWithIlm.build();
            for (Index index : ds.getIndices()) {
                assertThat(ds.isIndexManagedByDataStreamLifecycle(index, metadataIlm::index), is(false));
            }
        }

        {
            {
                Metadata.Builder builderWithIlm = Metadata.builder();
                DataStream ds = createDataStream(
                    builderWithIlm,
                    dataStreamName,
                    creationAndRolloverTimes,
                    Settings.builder()
                        .put(IndexMetadata.LIFECYCLE_NAME, "ILM_policy")
                        .put(IndexMetadata.SETTING_VERSION_CREATED, IndexVersion.current())
                        .put(IndexSettings.PREFER_ILM, false),
                    new DataStreamLifecycle()
                );
                Metadata metadataIlm = builderWithIlm.build();
                for (Index index : ds.getIndices()) {
                    assertThat(ds.isIndexManagedByDataStreamLifecycle(index, metadataIlm::index), is(true));
                }
            }
        }

        {
            for (Index index : dataStream.getIndices()) {
                assertThat(dataStream.isIndexManagedByDataStreamLifecycle(index, metadata::index), is(true));
            }
        }
    }

    public void testGetIndicesOlderThanWithOriginationDate() {
        String dataStreamName = "metrics-foo";
        long now = System.currentTimeMillis();
        List<DataStreamMetadata> creationAndRolloverTimes = List.of(
            DataStreamMetadata.dataStreamMetadata(now - 5000, now - 4000),
            DataStreamMetadata.dataStreamMetadata(now - 4000, now - 3000),
            DataStreamMetadata.dataStreamMetadata(now - 3000, now - 2000),
            DataStreamMetadata.dataStreamMetadata(now - 2000, now - 1000),
            DataStreamMetadata.dataStreamMetadata(now, null, now - 7000), 
            DataStreamMetadata.dataStreamMetadata(now, null, now - 1000), 
            DataStreamMetadata.dataStreamMetadata(now, null, now - 7000) 
        );
        Metadata.Builder builder = Metadata.builder();
        DataStream dataStream = createDataStream(
            builder,
            dataStreamName,
            creationAndRolloverTimes,
            settings(IndexVersion.current()),
            new DataStreamLifecycle()
        );
        Metadata metadata = builder.build();

        List<Index> backingIndices = dataStream.getNonWriteIndicesOlderThan(
            TimeValue.timeValueMillis(2500),
            metadata::index,
            null,
            () -> now
        );
        assertThat(backingIndices.size(), is(3));
        assertThat(backingIndices.get(0).getName(), is(DataStream.getDefaultBackingIndexName(dataStreamName, 1)));
        assertThat(backingIndices.get(1).getName(), is(DataStream.getDefaultBackingIndexName(dataStreamName, 2)));
        assertThat(backingIndices.get(2).getName(), is(DataStream.getDefaultBackingIndexName(dataStreamName, 6)));
    }

    private DataStream createDataStream(
        Metadata.Builder builder,
        String dataStreamName,
        List<DataStreamMetadata> creationAndRolloverTimes,
        Settings.Builder backingIndicesSettings,
        @Nullable DataStreamLifecycle lifecycle
    ) {
        int backingIndicesCount = creationAndRolloverTimes.size();
        final List<Index> backingIndices = new ArrayList<>();
        for (int k = 1; k <= backingIndicesCount; k++) {
            DataStreamMetadata creationRolloverTime = creationAndRolloverTimes.get(k - 1);
            IndexMetadata.Builder indexMetaBuilder = IndexMetadata.builder(DataStream.getDefaultBackingIndexName(dataStreamName, k))
                .settings(backingIndicesSettings)
                .numberOfShards(1)
                .numberOfReplicas(1)
                .creationDate(creationRolloverTime.creationTimeInMillis());
            if (k < backingIndicesCount) {
                Long rolloverTimeMillis = creationRolloverTime.rolloverTimeInMillis();
                if (rolloverTimeMillis != null) {
                    MaxAgeCondition rolloverCondition = new MaxAgeCondition(TimeValue.timeValueMillis(rolloverTimeMillis));
                    indexMetaBuilder.putRolloverInfo(new RolloverInfo(dataStreamName, List.of(rolloverCondition), rolloverTimeMillis));
                }
            }
            Long originationTimeInMillis = creationRolloverTime.originationTimeInMillis;
            if (originationTimeInMillis != null) {
                backingIndicesSettings.put(LIFECYCLE_ORIGINATION_DATE, originationTimeInMillis);
            }
            IndexMetadata indexMetadata = indexMetaBuilder.build();
            builder.put(indexMetadata, false);
            backingIndices.add(indexMetadata.getIndex());
        }
        return newInstance(dataStreamName, backingIndices, backingIndicesCount, null, false, lifecycle);
    }

    public void testXContentSerializationWithRolloverAndEffectiveRetention() throws IOException {
        String dataStreamName = randomAlphaOfLength(10).toLowerCase(Locale.ROOT);
        List<Index> indices = randomIndexInstances();
        long generation = indices.size() + ESTestCase.randomLongBetween(1, 128);
        indices.add(new Index(getDefaultBackingIndexName(dataStreamName, generation), UUIDs.randomBase64UUID(LuceneTestCase.random())));
        Map<String, Object> metadata = null;
        if (randomBoolean()) {
            metadata = Map.of("key", "value");
        }
        boolean failureStore = randomBoolean();
        List<Index> failureIndices = List.of();
        if (failureStore) {
            failureIndices = randomNonEmptyIndexInstances();
        }

        DataStreamLifecycle lifecycle = new DataStreamLifecycle();
        DataStream dataStream = new DataStream(
            dataStreamName,
            indices,
            generation,
            metadata,
            randomBoolean(),
            randomBoolean(),
            false, 
            System::currentTimeMillis,
            randomBoolean(),
            randomBoolean() ? IndexMode.STANDARD : null, 
            lifecycle,
            failureStore,
            failureIndices,
            false,
            null
        );

        try (XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent())) {
            builder.humanReadable(true);
            RolloverConfiguration rolloverConfiguration = RolloverConfigurationTests.randomRolloverConditions();
            DataStreamGlobalRetention globalRetention = DataStreamGlobalRetentionTests.randomGlobalRetention();

            ToXContent.Params withEffectiveRetention = new ToXContent.MapParams(DataStreamLifecycle.INCLUDE_EFFECTIVE_RETENTION_PARAMS);
            dataStream.toXContent(builder, withEffectiveRetention, rolloverConfiguration, globalRetention);
            String serialized = Strings.toString(builder);
            assertThat(serialized, containsString("rollover"));
            for (String label : rolloverConfiguration.resolveRolloverConditions(lifecycle.getEffectiveDataRetention(globalRetention))
                .getConditions()
                .keySet()) {
                assertThat(serialized, containsString(label));
            }
            assertThat(serialized, not(containsString("data_retention")));
            assertThat(serialized, containsString("effective_retention"));
        }
    }

    public void testGetIndicesWithinMaxAgeRange() {
        final TimeValue maxIndexAge = TimeValue.timeValueDays(7);

        final Metadata.Builder metadataBuilder = Metadata.builder();
        final int numberOfBackingIndicesOlderThanMinAge = randomIntBetween(0, 10);
        final int numberOfBackingIndicesWithinMinAnge = randomIntBetween(0, 10);
        final int numberOfShards = 1;
        final List<Index> backingIndices = new ArrayList<>();
        final String dataStreamName = "logs-es";
        final List<Index> backingIndicesOlderThanMinAge = new ArrayList<>();
        for (int i = 0; i < numberOfBackingIndicesOlderThanMinAge; i++) {
            long creationDate = System.currentTimeMillis() - maxIndexAge.millis() * 2;
            final IndexMetadata indexMetadata = createIndexMetadata(
                DataStream.getDefaultBackingIndexName(dataStreamName, backingIndices.size(), creationDate),
                randomIndexWriteLoad(numberOfShards),
                creationDate
            );
            backingIndices.add(indexMetadata.getIndex());
            backingIndicesOlderThanMinAge.add(indexMetadata.getIndex());
            metadataBuilder.put(indexMetadata, false);
        }

        final List<Index> backingIndicesWithinMinAge = new ArrayList<>();
        for (int i = 0; i < numberOfBackingIndicesWithinMinAnge; i++) {
            final long createdAt = System.currentTimeMillis() - (maxIndexAge.getMillis() / 2);
            final IndexMetadata indexMetadata = createIndexMetadata(
                DataStream.getDefaultBackingIndexName(dataStreamName, backingIndices.size(), createdAt),
                randomIndexWriteLoad(numberOfShards),
                createdAt
            );
            backingIndices.add(indexMetadata.getIndex());
            backingIndicesWithinMinAge.add(indexMetadata.getIndex());
            metadataBuilder.put(indexMetadata, false);
        }

        final String writeIndexName = DataStream.getDefaultBackingIndexName(dataStreamName, backingIndices.size());
        final IndexMetadata writeIndexMetadata = createIndexMetadata(writeIndexName, null, System.currentTimeMillis());
        backingIndices.add(writeIndexMetadata.getIndex());
        metadataBuilder.put(writeIndexMetadata, false);

        final DataStream dataStream = DataStream.builder(dataStreamName, backingIndices)
            .setGeneration(backingIndices.size())
            .setMetadata(Map.of())
            .setIndexMode(randomBoolean() ? IndexMode.STANDARD : IndexMode.TIME_SERIES)
            .build();

        metadataBuilder.put(dataStream);

        final List<Index> indicesWithinMaxAgeRange = DataStream.getIndicesWithinMaxAgeRange(
            dataStream,
            metadataBuilder::getSafe,
            maxIndexAge,
            System::currentTimeMillis
        );

        final List<Index> expectedIndicesWithinMaxAgeRange = new ArrayList<>();
        if (numberOfBackingIndicesOlderThanMinAge > 0) {
            expectedIndicesWithinMaxAgeRange.add(backingIndicesOlderThanMinAge.get(backingIndicesOlderThanMinAge.size() - 1));
        }
        expectedIndicesWithinMaxAgeRange.addAll(backingIndicesWithinMinAge);
        expectedIndicesWithinMaxAgeRange.add(writeIndexMetadata.getIndex());

        assertThat(indicesWithinMaxAgeRange, is(equalTo(expectedIndicesWithinMaxAgeRange)));
    }

    public void testGetIndicesWithinMaxAgeRangeAllIndicesOutsideRange() {
        final TimeValue maxIndexAge = TimeValue.timeValueDays(7);

        final Metadata.Builder metadataBuilder = Metadata.builder();
        final int numberOfBackingIndicesOlderThanMinAge = randomIntBetween(5, 10);
        final int numberOfShards = 1;
        final List<Index> backingIndices = new ArrayList<>();
        final String dataStreamName = "logs-es";
        final List<Index> backingIndicesOlderThanMinAge = new ArrayList<>();
        for (int i = 0; i < numberOfBackingIndicesOlderThanMinAge; i++) {
            long creationDate = System.currentTimeMillis() - maxIndexAge.millis() * 2;
            final IndexMetadata indexMetadata = createIndexMetadata(
                DataStream.getDefaultBackingIndexName(dataStreamName, backingIndices.size(), creationDate),
                randomIndexWriteLoad(numberOfShards),
                creationDate
            );
            backingIndices.add(indexMetadata.getIndex());
            backingIndicesOlderThanMinAge.add(indexMetadata.getIndex());
            metadataBuilder.put(indexMetadata, false);
        }

        final String writeIndexName = DataStream.getDefaultBackingIndexName(dataStreamName, backingIndices.size());
        final IndexMetadata writeIndexMetadata = createIndexMetadata(
            writeIndexName,
            null,
            System.currentTimeMillis() - maxIndexAge.millis() * 2
        );
        backingIndices.add(writeIndexMetadata.getIndex());
        metadataBuilder.put(writeIndexMetadata, false);

        final DataStream dataStream = DataStream.builder(dataStreamName, backingIndices)
            .setGeneration(backingIndices.size())
            .setMetadata(Map.of())
            .setIndexMode(randomBoolean() ? IndexMode.STANDARD : IndexMode.TIME_SERIES)
            .build();

        metadataBuilder.put(dataStream);

        final List<Index> indicesWithinMaxAgeRange = DataStream.getIndicesWithinMaxAgeRange(
            dataStream,
            metadataBuilder::getSafe,
            maxIndexAge,
            System::currentTimeMillis
        );

        final List<Index> expectedIndicesWithinMaxAgeRange = new ArrayList<>();
        if (numberOfBackingIndicesOlderThanMinAge > 0) {
            expectedIndicesWithinMaxAgeRange.add(backingIndicesOlderThanMinAge.get(backingIndicesOlderThanMinAge.size() - 1));
        }
        expectedIndicesWithinMaxAgeRange.add(writeIndexMetadata.getIndex());
        assertThat(indicesWithinMaxAgeRange, is(equalTo(expectedIndicesWithinMaxAgeRange)));
        assertThat(indicesWithinMaxAgeRange.get(indicesWithinMaxAgeRange.size() - 1).getName(), is(writeIndexName));
    }

    private IndexWriteLoad randomIndexWriteLoad(int numberOfShards) {
        IndexWriteLoad.Builder builder = IndexWriteLoad.builder(numberOfShards);
        for (int shardId = 0; shardId < numberOfShards; shardId++) {
            builder.withShardWriteLoad(shardId, randomDoubleBetween(0, 64, true), randomLongBetween(1, 10));
        }
        return builder.build();
    }

    private IndexMetadata createIndexMetadata(String indexName, IndexWriteLoad indexWriteLoad, long createdAt) {
        return IndexMetadata.builder(indexName)
            .settings(
                Settings.builder()
                    .put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, 1)
                    .put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 0)
                    .put(IndexMetadata.SETTING_VERSION_CREATED, IndexVersion.current())
                    .build()
            )
            .stats(indexWriteLoad == null ? null : new IndexMetadataStats(indexWriteLoad, 1, 1))
            .creationDate(createdAt)
            .build();
    }

    public void testWriteFailureIndex() {
        boolean hidden = randomBoolean();
        boolean system = hidden && randomBoolean();
        boolean replicated = randomBoolean();
        DataStream noFailureStoreDataStream = new DataStream(
            randomAlphaOfLength(10),
            randomNonEmptyIndexInstances(),
            randomNonNegativeInt(),
            null,
            hidden,
            replicated,
            system,
            System::currentTimeMillis,
            randomBoolean(),
            randomBoolean() ? IndexMode.STANDARD : IndexMode.TIME_SERIES,
            DataStreamLifecycleTests.randomLifecycle(),
            false,
            null,
            replicated == false && randomBoolean(),
            null
        );
        assertThat(noFailureStoreDataStream.getFailureStoreWriteIndex(), nullValue());

        DataStream failureStoreDataStreamWithEmptyFailureIndices = new DataStream(
            randomAlphaOfLength(10),
            randomNonEmptyIndexInstances(),
            randomNonNegativeInt(),
            null,
            hidden,
            replicated,
            system,
            System::currentTimeMillis,
            randomBoolean(),
            randomBoolean() ? IndexMode.STANDARD : IndexMode.TIME_SERIES,
            DataStreamLifecycleTests.randomLifecycle(),
            true,
            List.of(),
            replicated == false && randomBoolean(),
            null
        );
        assertThat(failureStoreDataStreamWithEmptyFailureIndices.getFailureStoreWriteIndex(), nullValue());

        List<Index> failureIndices = randomIndexInstances();
        String dataStreamName = randomAlphaOfLength(10);
        Index writeFailureIndex = new Index(
            getDefaultBackingIndexName(dataStreamName, randomNonNegativeInt()),
            UUIDs.randomBase64UUID(LuceneTestCase.random())
        );
        failureIndices.add(writeFailureIndex);
        DataStream failureStoreDataStream = new DataStream(
            dataStreamName,
            randomNonEmptyIndexInstances(),
            randomNonNegativeInt(),
            null,
            hidden,
            replicated,
            system,
            System::currentTimeMillis,
            randomBoolean(),
            randomBoolean() ? IndexMode.STANDARD : IndexMode.TIME_SERIES,
            DataStreamLifecycleTests.randomLifecycle(),
            true,
            failureIndices,
            replicated == false && randomBoolean(),
            null
        );
        assertThat(failureStoreDataStream.getFailureStoreWriteIndex(), is(writeFailureIndex));
    }

    public void testIsFailureIndex() {
        boolean hidden = randomBoolean();
        boolean system = hidden && randomBoolean();
        List<Index> backingIndices = randomNonEmptyIndexInstances();
        boolean replicated = randomBoolean();
        DataStream noFailureStoreDataStream = new DataStream(
            randomAlphaOfLength(10),
            backingIndices,
            randomNonNegativeInt(),
            null,
            hidden,
            replicated,
            system,
            System::currentTimeMillis,
            randomBoolean(),
            randomBoolean() ? IndexMode.STANDARD : IndexMode.TIME_SERIES,
            DataStreamLifecycleTests.randomLifecycle(),
            false,
            null,
            replicated == false && randomBoolean(),
            null
        );
        assertThat(
            noFailureStoreDataStream.isFailureStoreIndex(backingIndices.get(randomIntBetween(0, backingIndices.size() - 1)).getName()),
            is(false)
        );

        backingIndices = randomNonEmptyIndexInstances();
        DataStream failureStoreDataStreamWithEmptyFailureIndices = new DataStream(
            randomAlphaOfLength(10),
            backingIndices,
            randomNonNegativeInt(),
            null,
            hidden,
            replicated,
            system,
            System::currentTimeMillis,
            randomBoolean(),
            randomBoolean() ? IndexMode.STANDARD : IndexMode.TIME_SERIES,
            DataStreamLifecycleTests.randomLifecycle(),
            true,
            List.of(),
            replicated == false && randomBoolean(),
            null
        );
        assertThat(
            failureStoreDataStreamWithEmptyFailureIndices.isFailureStoreIndex(
                backingIndices.get(randomIntBetween(0, backingIndices.size() - 1)).getName()
            ),
            is(false)
        );

        backingIndices = randomNonEmptyIndexInstances();
        List<Index> failureIndices = randomIndexInstances();
        String dataStreamName = randomAlphaOfLength(10);
        Index writeFailureIndex = new Index(
            getDefaultBackingIndexName(dataStreamName, randomNonNegativeInt()),
            UUIDs.randomBase64UUID(LuceneTestCase.random())
        );
        failureIndices.add(writeFailureIndex);
        DataStream failureStoreDataStream = new DataStream(
            dataStreamName,
            backingIndices,
            randomNonNegativeInt(),
            null,
            hidden,
            replicated,
            system,
            System::currentTimeMillis,
            randomBoolean(),
            randomBoolean() ? IndexMode.STANDARD : IndexMode.TIME_SERIES,
            DataStreamLifecycleTests.randomLifecycle(),
            true,
            failureIndices,
            replicated == false && randomBoolean(),
            null
        );
        assertThat(failureStoreDataStream.isFailureStoreIndex(writeFailureIndex.getName()), is(true));
        assertThat(
            failureStoreDataStream.isFailureStoreIndex(failureIndices.get(randomIntBetween(0, failureIndices.size() - 1)).getName()),
            is(true)
        );
        assertThat(
            failureStoreDataStreamWithEmptyFailureIndices.isFailureStoreIndex(
                backingIndices.get(randomIntBetween(0, backingIndices.size() - 1)).getName()
            ),
            is(false)
        );
        assertThat(failureStoreDataStreamWithEmptyFailureIndices.isFailureStoreIndex(randomAlphaOfLength(10)), is(false));
    }

    private record DataStreamMetadata(Long creationTimeInMillis, Long rolloverTimeInMillis, Long originationTimeInMillis) {
        public static DataStreamMetadata dataStreamMetadata(Long creationTimeInMillis, Long rolloverTimeInMillis) {
            return new DataStreamMetadata(creationTimeInMillis, rolloverTimeInMillis, null);
        }

        public static DataStreamMetadata dataStreamMetadata(
            Long creationTimeInMillis,
            Long rolloverTimeInMillis,
            Long originationTimeInMillis
        ) {
            return new DataStreamMetadata(creationTimeInMillis, rolloverTimeInMillis, originationTimeInMillis);
        }
    }

    private static DataStream createRandomDataStream() {
        long currentTimeMillis = System.currentTimeMillis();
        int numBackingIndices = randomIntBetween(2, 32);
        int numFailureIndices = randomIntBetween(2, 32);
        String dataStreamName = randomAlphaOfLength(10).toLowerCase(Locale.ROOT);

        List<Index> indices = new ArrayList<>(numBackingIndices);
        for (int k = 1; k <= numBackingIndices; k++) {
            indices.add(new Index(DataStream.getDefaultBackingIndexName(dataStreamName, k), UUIDs.randomBase64UUID(random())));
        }
        List<Index> failureIndices = new ArrayList<>(numFailureIndices);
        for (int k = 1; k <= numFailureIndices; k++) {
            failureIndices.add(
                new Index(DataStream.getDefaultFailureStoreName(dataStreamName, k, currentTimeMillis), UUIDs.randomBase64UUID(random()))
            );
        }
        return DataStreamTestHelper.newInstance(dataStreamName, indices, failureIndices);
    }

    private static void createMetadataForIndices(Metadata.Builder builder, List<Index> indices) {
        for (Index index : indices) {
            IndexMetadata im = IndexMetadata.builder(index.getName())
                .settings(settings(IndexVersion.current()))
                .numberOfShards(1)
                .numberOfReplicas(1)
                .build();
            builder.put(im, false);
        }
    }
}
