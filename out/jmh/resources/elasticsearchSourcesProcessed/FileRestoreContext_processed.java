/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */
package org.elasticsearch.repositories.blobstore;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.common.util.CollectionUtils;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.index.snapshots.IndexShardRestoreFailedException;
import org.elasticsearch.index.snapshots.blobstore.BlobStoreIndexShardSnapshot;
import org.elasticsearch.index.snapshots.blobstore.SnapshotFiles;
import org.elasticsearch.index.store.ImmutableDirectoryException;
import org.elasticsearch.index.store.Store;
import org.elasticsearch.index.store.StoreFileMetadata;
import org.elasticsearch.indices.recovery.RecoveryState;
import org.elasticsearch.snapshots.SnapshotId;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;
import static org.elasticsearch.core.Strings.format;

/**
 * This context will execute a file restore of the lucene files. It is primarily designed to be used to
 * restore from some form of a snapshot. It will setup a new store, identify files that need to be copied
 * for the source, and perform the copies. Implementers must implement the functionality of opening the
 * underlying file streams for snapshotted lucene file.
 */
public abstract class FileRestoreContext {

    protected static final Logger logger = LogManager.getLogger(FileRestoreContext.class);

    protected final String repositoryName;
    protected final RecoveryState recoveryState;
    protected final SnapshotId snapshotId;
    protected final ShardId shardId;

    /**
     * Constructs new restore context
     *
     * @param shardId       shard id to restore into
     * @param snapshotId    snapshot id
     * @param recoveryState recovery state to report progress
     */
    protected FileRestoreContext(String repositoryName, ShardId shardId, SnapshotId snapshotId, RecoveryState recoveryState) {
        this.repositoryName = repositoryName;
        this.recoveryState = recoveryState;
        this.snapshotId = snapshotId;
        this.shardId = shardId;
    }

    /**
     * Performs restore operation
     */
    public void restore(SnapshotFiles snapshotFiles, Store store, ActionListener<Void> listener) {
        store.incRef();
        try {
            final List<BlobStoreIndexShardSnapshot.FileInfo> filesToRecover = new ArrayList<>();
            if (store.indexSettings().getIndexMetadata().isSearchableSnapshot()) {
                for (BlobStoreIndexShardSnapshot.FileInfo fileInfo : snapshotFiles.indexFiles()) {
                    assert store.directory().fileLength(fileInfo.physicalName()) == fileInfo.length();
                    recoveryState.getIndex().addFileDetail(fileInfo.physicalName(), fileInfo.length(), true);
                }
            } else {
                logger.debug("[{}] [{}] restoring to [{}] ...", snapshotId, repositoryName, shardId);
                Store.MetadataSnapshot recoveryTargetMetadata;
                try {
                    recoveryTargetMetadata = store.getMetadata(null, true);
                } catch (org.apache.lucene.index.IndexNotFoundException e) {
                    logger.trace("[{}] [{}] restoring from to an empty shard", shardId, snapshotId);
                    recoveryTargetMetadata = Store.MetadataSnapshot.EMPTY;
                } catch (IOException e) {
                    logger.warn(
                        () -> format(
                            "[%s] [%s] Can't read metadata from store, will not reuse local files during restore",
                            shardId,
                            snapshotId
                        ),
                        e
                    );
                    recoveryTargetMetadata = Store.MetadataSnapshot.EMPTY;
                }
                final Map<String, StoreFileMetadata> snapshotMetadata = new HashMap<>();
                final Map<String, BlobStoreIndexShardSnapshot.FileInfo> fileInfos = new HashMap<>();
                for (final BlobStoreIndexShardSnapshot.FileInfo fileInfo : snapshotFiles.indexFiles()) {
                    snapshotMetadata.put(fileInfo.metadata().name(), fileInfo.metadata());
                    fileInfos.put(fileInfo.metadata().name(), fileInfo);
                }

                final Store.MetadataSnapshot sourceMetadata = new Store.MetadataSnapshot(unmodifiableMap(snapshotMetadata), emptyMap(), 0);

                final StoreFileMetadata restoredSegmentsFile = sourceMetadata.getSegmentsFile();
                if (restoredSegmentsFile == null) {
                    throw new IndexShardRestoreFailedException(shardId, "Snapshot has no segments file");
                }

                final Store.RecoveryDiff diff = sourceMetadata.recoveryDiff(recoveryTargetMetadata);
                for (StoreFileMetadata md : diff.identical) {
                    BlobStoreIndexShardSnapshot.FileInfo fileInfo = fileInfos.get(md.name());
                    recoveryState.getIndex().addFileDetail(fileInfo.physicalName(), fileInfo.length(), true);
                    if (logger.isTraceEnabled()) {
                        logger.trace(
                            "[{}] [{}] not_recovering file [{}] from [{}], exists in local store and is same",
                            shardId,
                            snapshotId,
                            fileInfo.physicalName(),
                            fileInfo.name()
                        );
                    }
                }

                for (StoreFileMetadata md : concat(diff)) {
                    BlobStoreIndexShardSnapshot.FileInfo fileInfo = fileInfos.get(md.name());
                    filesToRecover.add(fileInfo);
                    recoveryState.getIndex().addFileDetail(fileInfo.physicalName(), fileInfo.length(), false);
                    if (logger.isTraceEnabled()) {
                        logger.trace("[{}] [{}] recovering [{}] from [{}]", shardId, snapshotId, fileInfo.physicalName(), fileInfo.name());
                    }
                }
            }

            recoveryState.getIndex().setFileDetailsComplete();

            if (filesToRecover.isEmpty()) {
                logger.trace("[{}] [{}] no files to recover, all exist within the local store", shardId, snapshotId);
            }

            try {
                final List<String> deleteIfExistFiles = Arrays.asList(store.directory().listAll());

                for (final BlobStoreIndexShardSnapshot.FileInfo fileToRecover : filesToRecover) {
                    final String physicalName = fileToRecover.physicalName();
                    if (deleteIfExistFiles.contains(physicalName)) {
                        logger.trace("[{}] [{}] deleting pre-existing file [{}]", shardId, snapshotId, physicalName);
                        store.directory().deleteFile(physicalName);
                    }
                }

                restoreFiles(filesToRecover, store, listener.delegateFailureAndWrap((l, v) -> {
                    store.incRef();
                    try {
                        afterRestore(snapshotFiles, store);
                        l.onResponse(null);
                    } finally {
                        store.decRef();
                    }
                }));
            } catch (IOException ex) {
                throw new IndexShardRestoreFailedException(shardId, "Failed to recover index", ex);
            }
        } catch (Exception e) {
            listener.onFailure(e);
        } finally {
            store.decRef();
        }
    }

    private void afterRestore(SnapshotFiles snapshotFiles, Store store) {
        try {
            for (String storeFile : store.directory().listAll()) {
                if (Store.isAutogenerated(storeFile) || snapshotFiles.containPhysicalIndexFile(storeFile)) {
                    continue; 
                }
                try {
                    store.directory().deleteFile(storeFile);
                } catch (ImmutableDirectoryException e) {
                    assert snapshotFiles.indexFiles().size() <= 1 : snapshotFiles;
                } catch (IOException e) {
                    logger.warn("[{}] [{}] failed to delete file [{}] during snapshot cleanup", shardId, snapshotId, storeFile);
                }
            }
        } catch (IOException e) {
            logger.warn("[{}] [{}] failed to list directory - some of files might not be deleted", shardId, snapshotId);
        }
    }

    /**
     * Restores given list of {@link BlobStoreIndexShardSnapshot.FileInfo} to the given {@link Store}.
     *
     * @param filesToRecover List of files to restore
     * @param store          Store to restore into
     */
    protected abstract void restoreFiles(
        List<BlobStoreIndexShardSnapshot.FileInfo> filesToRecover,
        Store store,
        ActionListener<Void> listener
    );

    @SuppressWarnings("unchecked")
    private static Iterable<StoreFileMetadata> concat(Store.RecoveryDiff diff) {
        return CollectionUtils.concatLists(diff.different, diff.missing);
    }
}
