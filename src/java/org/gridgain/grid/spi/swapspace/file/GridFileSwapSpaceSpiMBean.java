// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.swapspace.file;

import org.gridgain.grid.spi.*;
import org.gridgain.grid.util.mbean.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Management bean that provides general administrative and configuration information
 * on file-based swapspace SPI.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@GridMBeanDescription("MBean that provides administrative and configuration information on file-based swapspace SPI.")
public interface GridFileSwapSpaceSpiMBean extends GridSpiManagementMBean {
    /**
     * Gets path to the directory where all swap space values are saved.
     *
     * @return Path to the swap space directory.
     */
    @GridMBeanDescription("Path to the directory where all swap space values are saved.")
    public String getRootFolderPath();

    /**
     * Gets root folder index range.
     *
     * @return Root folder index range.
     */
    @GridMBeanDescription("Root folder index range.")
    public int getRootFolderIndexRange();

    /**
     * Data will not be removed from disk on SPI stop
     * if this property is {@code true}.
     *
     * @return {@code True} if data must not be deleted at SPI stop, otherwise {@code false}.
     */
    @GridMBeanDescription("Remove data from disk on SPI start and stop.")
    public boolean isPersistent();

    /**
     * If this property is {@code true} then SPI deletes corrupted files when
     * corruption is detected.
     *
     * @return {@code True} if corrupted files should be deleted,
     *      {@code false} otherwise (only reported).
     */
    @GridMBeanDescription("Whether or not to delete corrupted files from disk when corruption is detected.")
    public boolean isDeleteCorrupted();

    /**
     * Gets maximum size in bytes for data to store in all spaces.
     *
     * @return Maximum size in bytes for data to store in all spaces.
     */
    @GridMBeanDescription("Maximum size in bytes for data to store in each swap space.")
    public long getMaxSwapSize();

    /**
     * Gets maximum count (number of entries) for all swap spaces.
     *
     * @return Maximum count for all swap spaces.
     */
    @GridMBeanDescription("Maximum count for each swap spaces.")
    public long getMaxSwapCount();

    /**
     * Gets maximum index file size for all spaces.
     *
     * @return Maximum index file size for all spaces.
     */
    @GridMBeanDescription("Maximum index file size.")
    public long getMaxIndexFileSize();

    /**
     * Gets max index entries count in index buffer (per space).
     *
     * @return Max index entries count in buffer.
     */
    @GridMBeanDescription("Maximum index buffer size (per space).")
    public int getMaxIndexBufferSize();

    /**
     * Gets size overflow ratio.
     *
     * @return Overflow ratio.
     */
    @GridMBeanDescription("Size overflow ratio.")
    public double getSizeOverflowRatio();

    /**
     * Gets count overflow ratio.
     * <p>
     * When space entries count grows more than
     * {@link #getMaxSwapCount()} * {@code getCountOverflowRatio()}
     * SPI will start evicting oldest entries in space.
     *
     * @return cntOverflowRatio Overflow ratio.
     */
    @GridMBeanDescription("Count overflow ratio.")
    public double getCountOverflowRatio();

    /**
     * Gets current task queue size.
     *
     * @return Current task queue size.
     */
    @GridMBeanDescription("Current task queue size.")
    public int getTaskQueueSize();

    /**
     * Gets unreserved tasks count in queue.
     *
     * @return Current unreserved tasks count in queue.
     */
    @GridMBeanDescription("Current task queue size.")
    public int getUnreservedTasksCount();

    /**
     * Gets sub-folders count on each nested level to distribute
     * swap entries.
     *
     * @return Nested sub-folders count.
     */
    @GridMBeanDescription("Nested sub-folders count.")
    public int getSubFoldersCount();

    /**
     * Gets nested path length (nesting levels count
     * for swap entries distribution).
     *
     * @return Nested path length.
     */
    @GridMBeanDescription("Nested path length.")
    public int getNestedPathLength();

    /**
     * Gets task queue capacity.
     * <p>
     * If at some moment queue is full, tasks are processed synchronously.
     *
     * @return Task queue capacity.
     */
    @GridMBeanDescription("Task queue capacity.")
    public int getTaskQueueCapacity();

    /**
     * Gets task queue flush ratio.
     * <p/>
     * This parameter is used to calculate flush
     * size of the queue. When the size of the queue exceeds 
     * {@link #getTaskQueueCapacity()} * {@code getTaskQueueFlushRatio()} then
     * worker threads are waken up and start to perform asynchronous task processing.
     *
     * @return Task queue flush ratio.
     */
    @GridMBeanDescription("Task queue flush ratio.")
    public float getTaskQueueFlushRatio();

    /**
     * Gets task queue flush frequency.
     * <p/>
     * Each worker thread will wait at most this amount of milliseconds before
     * it starts to process tasks. Note, however, that if queue size exceeds
     * {@link #getTaskQueueCapacity()} * {@link #getTaskQueueFlushRatio()}, then
     * worker threads will wake up immediately.
     *
     * @return Task queue flush frequency in milliseconds.
     */
    @GridMBeanDescription("Task queue flush frequency.")
    public int getTaskQueueFlushFrequency();

    /**
     * Gets maximum size for individual spaces.
     * <p>
     * If map is set and contains value for some space, this value takes
     * precedence over {@link #getMaxSwapSize()}.
     * <p>
     * Map should accept {@code null} keys.
     *
     * @return maxSwapSizeMap Maximum size for individual swap spaces.
     */
    @GridMBeanDescription("Swap sizes map.")
    @Nullable public Map<String, Long> getMaxSwapSizeMap();

    /**
     * Gets maximum count (number of entries) for individual spaces.
     * <p>
     * If map is set and contains value for some space, this value takes
     * precedence over {@link #getMaxSwapCount()}.
     * <p>
     * Map should accept {@code null} keys.
     *
     * @return Maximum count for individual swap spaces.
     */
    @GridMBeanDescription("Swap counts map.")
    @Nullable public Map<String, Long> getMaxSwapCountMap();

    /**
     * Gets internal workers pool size.
     *
     * @return Pool size.
     */
    @GridMBeanDescription("Workers pool size.")
    public int getPoolSize();

    /**
     * Gets index overflow ratio.
     * <p>
     * When total index files size grows for space and becomes greater than
     * {@code space entries count} * {@code average index entry length} *
     * {@code #getIndexOverflowRatio()}
     * SPI initiates index compacting by removing obsolete index entries.
     *
     * @return Index overflow ratio.
     */
    @GridMBeanDescription("Index overflow ratio.")
    public double getIndexOverflowRatio();

    /**
     * Gets index read batch size. Whenever index files are read on evictions,
     * SPI will read this number of entries at a time.
     *
     * @return Index read batch size.
     */
    @GridMBeanDescription("Index read batch size.")
    public int getIndexReadBatchSize();

    /**
     * Gets eviction session timeout. If session is not finished within this timeout
     * by assigned worker, other workers join the session.
     *
     * @return Eviction session timeout.
     */
    @GridMBeanDescription("Eviction session timeout.")
    public int getEvictionSessionTimeout();

    /**
     * Gets sync delay in ms.
     * <p>
     * This property is critical for evictions.
     * <p>
     * When SPI writes entry to file it does not force underlying
     * storage sync for better performance. Index entry for corresponding
     * swap entry is written with {@code System.currentTimeMillis()}
     * timestamp. There probably can be a delay before all OS buffers associated
     * with entry file are flushed and actual file modification date may be a bit
     * greater than one in the index. If {@code index timestamp} + {@code syncDelay}
     * is greater tha file modification date, then index entry is considered to be
     * valid and file can be evicted.
     *
     * @return Sync delay.
     */
    @GridMBeanDescription("Sync delay.")
    int getSyncDelay();

    /**
     * Gets current total swap entries size in all spaces.
     *
     * @return Total swap entries size (in all spaces).
     */
    @GridMBeanDescription("Total swap entries size (in all spaces).")
    public long getTotalSize();

    /**
     * Gets current total entries count (in all spaces).
     *
     * @return Total entries count (in all spaces).
     */
    @GridMBeanDescription("Total entries count (in all spaces).")
    public long getTotalCount();

    /**
     * Gets total data size ever written to swap (to all spaces).
     *
     * @return Total data size ever written (to all spaces).
     */
    @GridMBeanDescription("Total data size ever written (to all spaces).")
    public long getTotalStoredSize();

    /**
     * Gets total entries count ever written to swap (to all spaces).
     *
     * @return Total entries count ever written (to all spaces).
     */
    @GridMBeanDescription("Total entries count ever written (to all spaces).")
    public long getTotalStoredCount();

    /**
     * Prints space stats to log with INFO level.
     */
    @GridMBeanDescription("Prints space stats to log with INFO level.")
    public void printSpacesStats();

    /**
     * Gets buffer hit count.
     *
     * @return Buffer hit count.
     */
    @GridMBeanDescription("Buffer hit count.")
    public int getBufferHitCount();

    /**
     * Gets disk access count.
     *
     * @return Disk access count.
     */
    @GridMBeanDescription("Disk access count.")
    public int getDiskReadCount();

    /**
     * Gets rejected store and remove tasks count.
     * These tasks were processed synchronously.
     *
     * @return Rejected tasks count.
     */
    @GridMBeanDescription("Rejected tasks count.")
    public int getRejectedTasksCount();

    /**
     * Gets max entries eviction session time.
     *
     * @return Max entries eviction session time.
     */
    @GridMBeanDescription("Max entries eviction session time.")
    public long getMaxEntriesEvictionSessionTime();

    /**
     * Gets max index eviction session time.
     *
     * @return Max index eviction session time.
     */
    @GridMBeanDescription("Max index eviction session time.")
    public long getMaxIndexEvictionSessionTime();

    /**
     * Gets storing threads count.
     *
     * @return Storing threads count.
     */
    @GridMBeanDescription("Storing threads count.")
    public int getStoringThreadsCount();

    /**
     * Gets removing threads count.
     *
     * @return Removing threads count.
     */
    @GridMBeanDescription("Removing threads count.")
    public int getRemovingThreadsCount();

    /**
     * Gets evicting threads count.
     *
     * @return Evicting threads count.
     */
    @GridMBeanDescription("Evicting threads count.")
    public int getEvictingThreadsCount();

    /**
     * Gets submitted store tasks count.
     *
     * @return Submitted store tasks count.
     */
    @GridMBeanDescription("Submitted store tasks count.")
    public int getSubmittedStoreTasksCount();

    /**
     * Gets submitted remove tasks count.
     *
     * @return Submitted remove tasks count.
     */
    @GridMBeanDescription("Submitted remove tasks count.")
    public int getSubmittedRemoveTasksCount();

    /**
     * Gets submitted evict tasks count.
     *
     * @return Submitted evict tasks count.
     */
    @GridMBeanDescription("Submitted evict tasks count.")
    public int getSubmittedEvictTasksCount();
}
