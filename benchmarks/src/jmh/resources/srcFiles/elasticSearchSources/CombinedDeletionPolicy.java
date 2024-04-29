/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.engine;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.index.IndexDeletionPolicy;
import org.apache.lucene.index.SegmentInfos;
import org.elasticsearch.common.lucene.FilterIndexCommit;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.index.seqno.SequenceNumbers;
import org.elasticsearch.index.translog.Translog;
import org.elasticsearch.index.translog.TranslogDeletionPolicy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * An {@link IndexDeletionPolicy} that coordinates between Lucene's commits and the retention of translog generation files,
 * making sure that all translog files that are needed to recover from the Lucene commit are not deleted.
 * <p>
 * In particular, this policy will delete index commits whose max sequence number is at most
 * the current global checkpoint except the index commit which has the highest max sequence number among those.
 */
public class CombinedDeletionPolicy extends IndexDeletionPolicy {
    private final Logger logger;
    private final TranslogDeletionPolicy translogDeletionPolicy;
    private final SoftDeletesPolicy softDeletesPolicy;
    private final LongSupplier globalCheckpointSupplier;
    private final Map<IndexCommit, Integer> snapshottedCommits; 

    interface CommitsListener {

        void onNewAcquiredCommit(IndexCommit commit, Set<String> additionalFiles);

        void onDeletedCommit(IndexCommit commit);
    }

    @Nullable
    private final CommitsListener commitsListener;

    private volatile IndexCommit safeCommit; 
    private volatile long maxSeqNoOfNextSafeCommit;
    private volatile IndexCommit lastCommit; 
    private volatile SafeCommitInfo safeCommitInfo = SafeCommitInfo.EMPTY;

    CombinedDeletionPolicy(
        Logger logger,
        TranslogDeletionPolicy translogDeletionPolicy,
        SoftDeletesPolicy softDeletesPolicy,
        LongSupplier globalCheckpointSupplier,
        @Nullable CommitsListener commitsListener
    ) {
        this.logger = logger;
        this.translogDeletionPolicy = translogDeletionPolicy;
        this.softDeletesPolicy = softDeletesPolicy;
        this.globalCheckpointSupplier = globalCheckpointSupplier;
        this.commitsListener = commitsListener;
        this.snapshottedCommits = new HashMap<>();
    }

    @Override
    public void onInit(List<? extends IndexCommit> commits) throws IOException {
        assert commits.isEmpty() == false : "index is opened, but we have no commits";
        onCommit(commits);
        if (safeCommit != commits.get(commits.size() - 1)) {
            throw new IllegalStateException(
                "Engine is opened, but the last commit isn't safe. Global checkpoint ["
                    + globalCheckpointSupplier.getAsLong()
                    + "], seqNo is last commit ["
                    + SequenceNumbers.loadSeqNoInfoFromLuceneCommit(lastCommit.getUserData().entrySet())
                    + "], "
                    + "seqNos in safe commit ["
                    + SequenceNumbers.loadSeqNoInfoFromLuceneCommit(safeCommit.getUserData().entrySet())
                    + "]"
            );
        }
    }

    @Override
    public void onCommit(List<? extends IndexCommit> commits) throws IOException {
        assert Thread.holdsLock(this) == false : "should not block concurrent acquire or release";
        final int keptPosition = indexOfKeptCommits(commits, globalCheckpointSupplier.getAsLong());
        final IndexCommit safeCommit = commits.get(keptPosition);
        final var newSafeCommitInfo = getNewSafeCommitInfo(safeCommit);
        final IndexCommit newCommit;
        final IndexCommit previousLastCommit;
        List<IndexCommit> deletedCommits = null;
        synchronized (this) {
            this.safeCommitInfo = newSafeCommitInfo;
            previousLastCommit = this.lastCommit;
            this.lastCommit = commits.get(commits.size() - 1);
            this.safeCommit = safeCommit;
            updateRetentionPolicy();
            if (keptPosition == commits.size() - 1) {
                this.maxSeqNoOfNextSafeCommit = Long.MAX_VALUE;
            } else {
                this.maxSeqNoOfNextSafeCommit = Long.parseLong(commits.get(keptPosition + 1).getUserData().get(SequenceNumbers.MAX_SEQ_NO));
            }
            if (commitsListener != null && previousLastCommit != this.lastCommit) {
                newCommit = acquireIndexCommit(false);
            } else {
                newCommit = null;
            }
            for (int i = 0; i < keptPosition; i++) {
                final IndexCommit commit = commits.get(i);
                if (snapshottedCommits.containsKey(commit) == false) {
                    deleteCommit(commit);
                    if (deletedCommits == null) {
                        deletedCommits = new ArrayList<>();
                    }
                    deletedCommits.add(commit);
                }
            }
        }
        assert assertSafeCommitUnchanged(safeCommit);
        if (commitsListener != null) {
            if (newCommit != null) {
                final Set<String> additionalFiles = listOfNewFileNames(previousLastCommit, newCommit);
                commitsListener.onNewAcquiredCommit(newCommit, additionalFiles);
            }
            if (deletedCommits != null) {
                for (IndexCommit deletedCommit : deletedCommits) {
                    commitsListener.onDeletedCommit(deletedCommit);
                }
            }
        }
    }

    private SafeCommitInfo getNewSafeCommitInfo(IndexCommit newSafeCommit) {
        final var currentSafeCommitInfo = this.safeCommitInfo;
        final long newSafeCommitLocalCheckpoint;
        try {
            newSafeCommitLocalCheckpoint = Long.parseLong(newSafeCommit.getUserData().get(SequenceNumbers.LOCAL_CHECKPOINT_KEY));
        } catch (Exception ex) {
            logger.info("failed to get the local checkpoint from the safe commit; use the info from the previous safe commit", ex);
            return currentSafeCommitInfo;
        }

        if (currentSafeCommitInfo.localCheckpoint == newSafeCommitLocalCheckpoint) {
            return currentSafeCommitInfo;
        }

        try {
            return new SafeCommitInfo(newSafeCommitLocalCheckpoint, getDocCountOfCommit(newSafeCommit));
        } catch (IOException ex) {
            logger.info("failed to get the total docs from the safe commit; use the total docs from the previous safe commit", ex);
            return new SafeCommitInfo(newSafeCommitLocalCheckpoint, currentSafeCommitInfo.docCount);
        }
    }

    private boolean assertSafeCommitUnchanged(IndexCommit safeCommit) {
        final IndexCommit newSafeCommit = this.safeCommit;
        assert safeCommit == newSafeCommit
            : "onCommit called concurrently? " + safeCommit.getGeneration() + " vs " + newSafeCommit.getGeneration();
        return true;
    }

    private void deleteCommit(IndexCommit commit) throws IOException {
        assert commit.isDeleted() == false : "Index commit [" + commitDescription(commit) + "] is deleted twice";
        logger.debug("Delete index commit [{}]", commitDescription(commit));
        commit.delete();
        assert commit.isDeleted() : "Deletion commit [" + commitDescription(commit) + "] was suppressed";
    }

    private void updateRetentionPolicy() throws IOException {
        assert Thread.holdsLock(this);
        logger.debug("Safe commit [{}], last commit [{}]", commitDescription(safeCommit), commitDescription(lastCommit));
        assert safeCommit.isDeleted() == false : "The safe commit must not be deleted";
        assert lastCommit.isDeleted() == false : "The last commit must not be deleted";
        final long localCheckpointOfSafeCommit = Long.parseLong(safeCommit.getUserData().get(SequenceNumbers.LOCAL_CHECKPOINT_KEY));
        softDeletesPolicy.setLocalCheckpointOfSafeCommit(localCheckpointOfSafeCommit);
        translogDeletionPolicy.setLocalCheckpointOfSafeCommit(localCheckpointOfSafeCommit);
    }

    protected int getDocCountOfCommit(IndexCommit indexCommit) throws IOException {
        return SegmentInfos.readCommit(indexCommit.getDirectory(), indexCommit.getSegmentsFileName()).totalMaxDoc();
    }

    SafeCommitInfo getSafeCommitInfo() {
        return safeCommitInfo;
    }

    /**
     * Captures the most recent commit point {@link #lastCommit} or the most recent safe commit point {@link #safeCommit}.
     * Index files of the capturing commit point won't be released until the commit reference is closed.
     *
     * @param acquiringSafeCommit captures the most recent safe commit point if true; otherwise captures the most recent commit point.
     */
    synchronized IndexCommit acquireIndexCommit(boolean acquiringSafeCommit) {
        assert safeCommit != null : "Safe commit is not initialized yet";
        assert lastCommit != null : "Last commit is not initialized yet";
        final IndexCommit snapshotting = acquiringSafeCommit ? safeCommit : lastCommit;
        snapshottedCommits.merge(snapshotting, 1, Integer::sum); 
        return wrapCommit(snapshotting);
    }

    protected IndexCommit wrapCommit(IndexCommit indexCommit) {
        return new SnapshotIndexCommit(indexCommit);
    }

    /**
     * Releases an index commit that acquired by {@link #acquireIndexCommit(boolean)}.
     *
     * @return true if the snapshotting commit can be clean up.
     */
    synchronized boolean releaseCommit(final IndexCommit snapshotCommit) {
        final IndexCommit releasingCommit = ((SnapshotIndexCommit) snapshotCommit).getIndexCommit();
        assert snapshottedCommits.containsKey(releasingCommit)
            : "Release non-snapshotted commit;"
                + "snapshotted commits ["
                + snapshottedCommits
                + "], releasing commit ["
                + releasingCommit
                + "]";
        final Integer refCount = snapshottedCommits.compute(releasingCommit, (key, count) -> {
            if (count == 1) {
                return null;
            }
            return count - 1;
        });

        assert refCount == null || refCount > 0 : "Number of snapshots can not be negative [" + refCount + "]";
        return refCount == null && releasingCommit.equals(safeCommit) == false && releasingCommit.equals(lastCommit) == false;
    }

    /**
     * Find a safe commit point from a list of existing commits based on the supplied global checkpoint.
     * The max sequence number of a safe commit point should be at most the global checkpoint.
     * If an index was created before 6.2 or recovered from remote, we might not have a safe commit.
     * In this case, this method will return the oldest index commit.
     *
     * @param commits          a list of existing commit points
     * @param globalCheckpoint the persisted global checkpoint from the translog, see {@link Translog#readGlobalCheckpoint(Path, String)}
     * @return a safe commit or the oldest commit if a safe commit is not found
     */
    public static IndexCommit findSafeCommitPoint(List<IndexCommit> commits, long globalCheckpoint) throws IOException {
        if (commits.isEmpty()) {
            throw new IllegalArgumentException("Commit list must not empty");
        }
        final int keptPosition = indexOfKeptCommits(commits, globalCheckpoint);
        return commits.get(keptPosition);
    }

    /**
     * Find the highest index position of a safe index commit whose max sequence number is not greater than the global checkpoint.
     * Index commits with different translog UUID will be filtered out as they don't belong to this engine.
     */
    private static int indexOfKeptCommits(List<? extends IndexCommit> commits, long globalCheckpoint) throws IOException {
        final String expectedTranslogUUID = commits.get(commits.size() - 1).getUserData().get(Translog.TRANSLOG_UUID_KEY);

        for (int i = commits.size() - 1; i >= 0; i--) {
            final Map<String, String> commitUserData = commits.get(i).getUserData();
            if (expectedTranslogUUID.equals(commitUserData.get(Translog.TRANSLOG_UUID_KEY)) == false) {
                return i + 1;
            }
            final long maxSeqNoFromCommit = Long.parseLong(commitUserData.get(SequenceNumbers.MAX_SEQ_NO));
            if (maxSeqNoFromCommit <= globalCheckpoint) {
                return i;
            }
        }
        return 0;
    }

    private static Set<String> listOfNewFileNames(IndexCommit previous, IndexCommit current) throws IOException {
        final Set<String> previousFiles = previous != null ? new HashSet<>(previous.getFileNames()) : Set.of();
        return current.getFileNames().stream().filter(f -> previousFiles.contains(f) == false).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Checks whether the deletion policy is holding on to snapshotted commits
     */
    synchronized boolean hasSnapshottedCommits() {
        return snapshottedCommits.isEmpty() == false;
    }

    /**
     * Checks if the deletion policy can delete some index commits with the latest global checkpoint.
     */
    boolean hasUnreferencedCommits() {
        return maxSeqNoOfNextSafeCommit <= globalCheckpointSupplier.getAsLong();
    }

    /**
     * Returns a description for a given {@link IndexCommit}. This should be only used for logging and debugging.
     */
    public static String commitDescription(IndexCommit commit) throws IOException {
        return String.format(Locale.ROOT, "CommitPoint{segment[%s], userData[%s]}", commit.getSegmentsFileName(), commit.getUserData());
    }

    /**
     * A wrapper of an index commit that prevents it from being deleted.
     */
    private static class SnapshotIndexCommit extends FilterIndexCommit {
        SnapshotIndexCommit(IndexCommit delegate) {
            super(delegate);
        }

        @Override
        public void delete() {
            throw new UnsupportedOperationException("A snapshot commit does not support deletion");
        }
    }
}
