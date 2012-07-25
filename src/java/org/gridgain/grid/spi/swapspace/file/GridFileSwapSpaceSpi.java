// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.swapspace.file;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.swapspace.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

import static java.util.concurrent.TimeUnit.*;
import static org.gridgain.grid.GridEventType.*;

/**
 * File-based implementation of swap space SPI.
 * <p>
 * Key-value pairs are stored by this implementation in separate files on disk.
 * <p>
 * Separate directories are created for each space under root folder configured via
 * {@link #setRootFolderPath(String)}. Spaces (and their directory structure) are seamlessly
 * initialized on first store to space. Name reserved for default (or {@code null}) space
 * is represented by {@link #DFLT_SPACE_NAME}.
 * <h1 class="header">Configuration</h1>
 * <h2 class="header">Mandatory</h2>
 * This SPI has no mandatory configuration parameters.
 * <h2 class="header">Optional</h2>
 * <ul>
 *     <li>Root folder path (see {@link #setRootFolderPath(String)}).</li>
 *     <li>Root folder index range (see {@link #setRootFolderIndexRange(int)}).</li>
 *     <li>Persistent (see {@link #setPersistent(boolean)}).</li>
 *     <li>Delete corrupted files (see {@link #setDeleteCorrupted(boolean)}).</li>
 *     <li>Max swap size (see {@link #setMaxSwapSize(long)}).</li>
 *     <li>Max swap size map (see {@link #setMaxSwapSizeMap(Map)}).</li>
 *     <li>Max swap count (see {@link #setMaxSwapCount(long)}).</li>
 *     <li>Max swap count map (see {@link #setMaxSwapSizeMap(Map)}).</li>
 *     <li>Size overflow ratio (see {@link #setSizeOverflowRatio(double)}).</li>
 *     <li>Count overflow ratio (see {@link #setCountOverflowRatio(double)}).</li>
 *     <li>Workers pool size (see {@link #setPoolSize(int)}).</li>
 *     <li>Task queue capacity (see {@link #setTaskQueueCapacity(int)}).</li>
 *     <li>Max index file size (see {@link #setMaxIndexFileSize(int)}).</li>
 *     <li>Max index buffer size (see {@link #setMaxIndexBufferSize(int)}).</li>
 *     <li>Index overflow ratio (see {@link #setIndexOverflowRatio(double)}).</li>
 *     <li>Index read batch size (see {@link #setIndexReadBatchSize(int)}).</li>
 *     <li>Eviction session timeout (see {@link #setEvictionSessionTimeout(int)}).</li>
 *     <li>Sync delay (see {@link #setSyncDelay(int)}).</li>
 *     <li>Sub-folders count (see {@link #setSubFoldersCount(int)}).</li>
 *     <li>Nested path length (see {@link #setNestedPathLength(int)}).</li>
 * </ul>
 * <h2 class="header">Java Example</h2>
 * GridFileSwapSpaceSpi is used by default and should be explicitly configured
 * only if some SPI configuration parameters need to be overridden.
 * <pre name="code" class="java">
 * GridFileSwapSpaceSpi spi = new GridFileSwapSpaceSpi();
 *
 * // Configure root folder path.
 * spi.setRootFolderPath("/path/to/swap/folder");
 *
 * // Set pool size.
 * spi.setPoolSize(20);
 *
 * GridConfigurationAdapter cfg = new GridConfigurationAdapter();
 *
 * // Override default swap space SPI.
 * cfg.setSwapSpaceSpi(spi);
 *
 * // Starts grid.
 * G.start(cfg);
 * </pre>
 * <h2 class="header">Spring Example</h2>
 * GridFileSwapSpaceSpi can be configured from Spring XML configuration file:
 * <pre name="code" class="xml">
 * &lt;bean id=&quot;grid.cfg&quot; class=&quot;org.gridgain.grid.GridConfigurationAdapter&quot; scope=&quot;singleton&quot;&gt;
 *     ...
 *     &lt;property name=&quot;swapSpaceSpi&quot;&gt;
 *         &lt;bean class=&quot;org.gridgain.grid.spi.swapspace.file.GridFileSwapSpaceSpi&quot;&gt;
 *             &lt;property name=&quot;rootFolderPath&quot; value=&quot;/path/to/swap/folder&quot;/&gt;
 *             &lt;property name=&quot;poolSize&quot; value=&quot;20&quot;/&gt;
 *         &lt;/bean&gt;
 *     &lt;/property&gt;
 *     ...
 * &lt;/bean&gt;
 * </pre>
 * <p>
 * <img src="http://www.gridgain.com/images/spring-small.png">
 * <br>
 * For information about Spring framework visit <a href="http://www.springframework.org/">www.springframework.org</a>
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 * @see GridSwapSpaceSpi
 */
@GridSpiInfo(
    author = "GridGain Systems",
    url = "www.gridgain.com",
    email = "support@gridgain.com",
    version = "3.6.0c.13012012")
@GridSpiMultipleInstancesSupport(true)
public class GridFileSwapSpaceSpi extends GridSpiAdapter implements GridSwapSpaceSpi, GridFileSwapSpaceSpiMBean {
    /*
     * Public constants.
     * =================
     */

    /** Name for default (or {@code null}) space. */
    public static final String DFLT_SPACE_NAME = "gg-dflt-space";

    /** Folder for storing index files. */
    public static final String IDX_FOLDER = "swap-index";

    /** Folder for storing index files. */
    public static final String SPACES_FOLDER = "swap-spaces";

    /** File to get lock on when SPI starts to ensure exclusive access. */
    public static final String LOCK_FILE_NAME = "swap-lock";

    /** Separator for collision index. */
    public static final String COLLISION_IDX_SEPARATOR = ".";

    /** Separator for partition ID. */
    public static final String PART_ID_SEPARATOR = "_";

    /**
     * Default directory path for swap files location. Grid name, node ID and
     * index (only if necessary) will be appended to this path using dashes as
     * separators.
     * <p>
     * If {@link #setRootFolderPath(String)} is not configured and {@code GRIDGAIN_HOME}
     * system property is set, this folder will be created under {@code GRIDGAIN_HOME}.
     * <p>
     * If {@link #setRootFolderPath(String)} is not configured and {@code GRIDGAIN_HOME}
     * system property is not set, this folder will be created under {@code java.io.tmpdir}.
     */
    public static final String DFLT_ROOT_FOLDER_PATH = "work/swapspace/";

    /** Default max swap size in bytes ({@code 1024 MB}). */
    public static final long DFLT_MAX_SWAP_SIZE = 1 << 30;

    /** Default maximum entries count for all spaces. */
    public static final long DFLT_MAX_SWAP_CNT = Integer.MAX_VALUE;

    /** Default size/count overflow ratio. */
    public static final double DFLT_OVERFLOW_RATIO = 1.3;

    /** Default max index entries count in index buffer. */
    public static final int DFLT_MAX_IDX_BUF_SIZE = 1024;

    /** Default max index file size. */
    public static final long DFLT_MAX_IDX_SIZE = 1024 * 1024;

    /** Default max root folder index. */
    public static final int DFLT_ROOT_FOLDER_IDX_RANGE = 100;

    /** Default value for persistent flag. */
    public static final boolean DFLT_PERSISTENT = false;

    /** Default value for sub-folders count. */
    public static final int DFLT_SUB_FOLDERS_CNT = 10;

    /** Default value for nested path length. */
    public static final int DFLT_NESTED_PATH_LEN = 2;

    /** Default value for delete corrupted flag. */
    public static final boolean DFLT_DEL_CORRUPT = true;

    /** Default workers pool size. */
    public static final int DFLT_POOL_SIZE = 2;

    /** Default task queue capacity. */
    public static final int DFLT_TASK_QUEUE_CAP = 10000;
    
    /** Default task queue flush frequency. */
    public static final int DFLT_TASK_QUEUE_FLUSH_FREQ = 60 * 1000;

    /** Default task queue flush ratio. */
    public static final float DFLT_TASK_QUEUE_FLUSH_RATIO = 0.5f;

    /** Default session timeout. */
    public static final int DFLT_SES_TIMEOUT = 5000;

    /** Default value for sync delay in ms. */
    public static final int DFLT_SYNC_DELAY = 500;

    /** Default value for index read batch size (in lines). */
    public static final int DFLT_IDX_READ_BATCH_SIZE = 10 * 1024;

    /** Default value for index overflow ratio. */
    public static final int DFLT_IDX_OVERFLOW_RATIO = 100;

    /*
     * Internal constants.
     * ===================
     */

    /** Position is written the following format: {@code %020d%n}. */
    private static final int IDX_DATA_POS = (20 + U.nl().length()) * 3;

    /** Number of file read-write locks to synchronize IO operations. */
    private static final int LOCKS_CNT = 1024;

    /** Debug flag. */
    private static final boolean DEBUG = false;

    /*
     * Configuration parameters.
     * =========================
     */

    /** Swap space directory where all spaces and their files are stored. */
    private String rootFolderPath;

    /** Root folder index range. */
    private int rootFolderIdxRange = DFLT_ROOT_FOLDER_IDX_RANGE;

    /** If true then swap is still warm after SPI restarts. */
    private boolean persistent = DFLT_PERSISTENT;

    /** Delete corrupted. */
    private boolean delCorrupt = DFLT_DEL_CORRUPT;

    /** Maximum swap space size in bytes for all spaces. */
    private long maxSwapSize = DFLT_MAX_SWAP_SIZE;

    /** Maximum entries count for all spaces. */
    private long maxSwapCnt = DFLT_MAX_SWAP_CNT;

    /** Size overflow ratio. */
    private double sizeOverflowRatio = DFLT_OVERFLOW_RATIO;

    /** Count overflow ratio. */
    private double cntOverflowRatio = DFLT_OVERFLOW_RATIO;

    /** Maximum swap space size in bytes for individual spaces. */
    private Map<String, Long> maxSwapSizeMap;

    /** Maximum entries count for individual spaces. */
    private Map<String, Long> maxSwapCntMap;

    /** Max pool size. */
    private int poolSize = DFLT_POOL_SIZE;

    /** Task queue capacity. */
    private int taskQueueCap = DFLT_TASK_QUEUE_CAP;

    /** Queue size that triggers worker threads wake up. */
    private int taskQueueFlushThreshold;

    /** Ratio that defines the queue flush threshold. */
    private float taskQueueFlushRatio = DFLT_TASK_QUEUE_FLUSH_RATIO;

    /** Task queue flush frequency in milliseconds. */
    private int taskQueueFlushFreq = DFLT_TASK_QUEUE_FLUSH_FREQ;

    /** Maximum index file size for all spaces. */
    private long maxIdxSize = DFLT_MAX_IDX_SIZE;

    /** Max entries count in buffer. */
    private int maxIdxBufSize = DFLT_MAX_IDX_BUF_SIZE;

    /** Index overflow ratio. */
    private double idxOverflowRatio = DFLT_IDX_OVERFLOW_RATIO;

    /** Index read batch size (in lines). */
    private int idxReadBatchSize = DFLT_IDX_READ_BATCH_SIZE;

    /** Session timeout. If timeout is elapsed and session is not finished, pool threads will join session. */
    private int sesTimeout = DFLT_SES_TIMEOUT;

    /** Sync delay. */
    private int syncDelay = DFLT_SYNC_DELAY;

    /** Sub-folders count. */
    private int subFoldersCnt = DFLT_SUB_FOLDERS_CNT;

    /** Nested path length. */
    private int nestedPathLen = DFLT_NESTED_PATH_LEN;

    /*
     * SPI Stats.
     * ============
     */

    /** Storing threads count in pool. */
    private final AtomicInteger storeThreadsCnt = new AtomicInteger();

    /** Removing threads count in pool. */
    private final AtomicInteger rmvThreadsCnt = new AtomicInteger();

    /** Evicting threads count in pool. */
    private final AtomicInteger evictThreadsCnt = new AtomicInteger();

    /** Total size. */
    private final AtomicLong totalSize = new AtomicLong();

    /** Total count. */
    private final AtomicLong totalCnt = new AtomicLong();

    /** Total stored data size. */
    private final AtomicLong totalStoredSize = new AtomicLong();

    /** Total stored items count. */
    private final AtomicLong totalStoredCnt = new AtomicLong();

    /** Buffer hit count. */
    private final AtomicInteger bufHitCnt = new AtomicInteger();

    /** Disk access count. */
    private final AtomicInteger diskReadCnt = new AtomicInteger();

    /** Synchronous writes/removes count. */
    private final AtomicInteger rejectedTasksCnt = new AtomicInteger();

    /** Max entries eviction session time. */
    private final GridAtomicLong maxEntriesEvictSesTime = new GridAtomicLong();

    /** Max index eviction (compacting) session time. */
    private final GridAtomicLong maxIdxEvictSesTime = new GridAtomicLong();

    /*
     * SPI Members.
     * ============
     */

    /** Folder for {@link #rootFolderPath}. */
    private File rootFolder;

    /** Lock to ensure exclusive access. */
    private FileLock rootFolderLock;

    /** File to lock with. */
    private RandomAccessFile rootFolderLockFile;

    /** Folder for spaces. */
    private File spacesFolder;

    /** Locks. */
    private final ReadWriteLock[] locks = new ReadWriteLock[LOCKS_CNT];

    /** Spaces. */
    private final ConcurrentMap<String, Space> spaces = new ConcurrentHashMap<String, Space>();

    /** ID generator. */
    private final AtomicInteger sesIdGen = new AtomicInteger();

    /** Task queue. */
    private final TaskQueue taskQueue = new TaskQueue();

    /** Workers. */
    private final Collection<GridSpiThread> wrks = new LinkedList<GridSpiThread>();

    /** SPI stopping flag. */
    private final AtomicBoolean spiStopping = new AtomicBoolean();

    /** Listener. */
    private volatile GridSwapSpaceSpiListener lsnr;

    /** Count waiting worker threads. */
    private volatile int sleepingCnt;

    /** Lock used in wakeup mechanism. */
    private final Lock wakeUpLock = new ReentrantLock();

    /** Condition used in wakeup mechanism. */
    private final Condition wakeUpCond = wakeUpLock.newCondition();

    /** Grid name. */
    @GridNameResource
    private String gridName;

    /** Local node ID. */
    @GridLocalNodeIdResource
    private UUID locNodeId;

    /** Marshaller. */
    @GridMarshallerResource
    private GridMarshaller marsh;

    /** Grid logger. */
    @GridLoggerResource
    private GridLogger log;

    /** {@inheritDoc} */
    @Override public String getRootFolderPath() {
        return rootFolderPath != null ? rootFolderPath : rootFolder.getAbsolutePath();
    }

    /**
     * Sets path to a directory where swap space values will be stored. The
     * path can either be absolute or relative to {@code GRIDGAIN_HOME} system
     * or environment variable.
     * <p>
     * If not provided, default value is {@link #DFLT_ROOT_FOLDER_PATH}.
     *
     * @param rootFolderPath Absolute or GridGain installation home folder relative path
     *      where swap space values will be stored.
     */
    @GridSpiConfiguration(optional = true)
    public void setRootFolderPath(String rootFolderPath) {
        this.rootFolderPath = rootFolderPath;
    }

    /** {@inheritDoc} */
    @Override public int getRootFolderIndexRange() {
        return rootFolderIdxRange;
    }

    /**
     * Sets root folder index range value.
     * <p>
     * If {@link #setRootFolderPath(String)} is not set and default path
     * is locked (e.g. by other grid running on the same host) SPI will
     * try next folder appending index to {@code GRIDGAIN_HOME}/{@link #DFLT_ROOT_FOLDER_PATH}.
     * If {@link #setRootFolderPath(String)} is set, SPI tries to lock configured path only and
     * this parameter is ignored.
     * <p>
     * If not provided, default value is {@link #DFLT_ROOT_FOLDER_IDX_RANGE}.
     *
     * @param rootFolderIdxRange Root folder index range.
     */
    @GridSpiConfiguration(optional = true)
    public void setRootFolderIndexRange(int rootFolderIdxRange) {
        this.rootFolderIdxRange = rootFolderIdxRange;
    }

    /** {@inheritDoc} */
    @Override public boolean isPersistent() {
        return persistent;
    }

    /**
     * Data will not be removed from disk on SPI stop if this property is {@code true}.
     * <p>
     * If not provided, default value is {@link #DFLT_PERSISTENT}.
     *
     * @param persistent {@code True} if data must not be deleted at SPI stop,
     *      otherwise {@code false}.
     */
    @GridSpiConfiguration(optional = true)
    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    /** {@inheritDoc} */
    @Override public boolean isDeleteCorrupted() {
        return delCorrupt;
    }

    /**
     * If this property is {@code true} then SPI deletes corrupted files when
     * corruption is detected.
     *
     * @param delCorrupt {@code True} if corrupted files should be deleted,
     *      {@code false} otherwise (only reported).
     */
    @GridSpiConfiguration(optional = true)
    public void setDeleteCorrupted(boolean delCorrupt) {
        this.delCorrupt = delCorrupt;
    }

    /** {@inheritDoc} */
    @Override public long getMaxSwapSize() {
        return maxSwapSize;
    }

    /**
     * Sets maximum swap space size in bytes for all spaces.
     * If not provided, default value is {@link #DFLT_MAX_SWAP_SIZE}.
     *
     * @param maxSwapSize Maximum swap space size in bytes for all spaces.
     */
    @GridSpiConfiguration(optional = true)
    public void setMaxSwapSize(long maxSwapSize) {
        this.maxSwapSize = maxSwapSize;
    }

    /** {@inheritDoc} */
    @Override public long getMaxSwapCount() {
        return maxSwapCnt;
    }

    /**
     * Sets maximum entries count for all spaces.
     * <p>
     * If not provided, default value is {@link #DFLT_MAX_SWAP_CNT}.
     *
     * @param maxSwapCnt Maximum entries count for all spaces.
     */
    @GridSpiConfiguration(optional = true)
    public void setMaxSwapCount(long maxSwapCnt) {
        this.maxSwapCnt = maxSwapCnt;
    }

    /** {@inheritDoc} */
    @Override public double getSizeOverflowRatio() {
        return sizeOverflowRatio;
    }

    /**
     * Sets size overflow ratio.
     * <p>
     * When space size grows more than
     * {@link #getMaxSwapSize()} * {@link #getSizeOverflowRatio()}
     * SPI will start evicting oldest entries in space.
     * <p>
     * If not provided, default value is {@link #DFLT_OVERFLOW_RATIO}.
     *
     * @param sizeOverflowRatio Overflow ratio.
     */
    @GridSpiConfiguration(optional = true)
    public void setSizeOverflowRatio(double sizeOverflowRatio) {
        this.sizeOverflowRatio = sizeOverflowRatio;
    }

    /** {@inheritDoc} */
    @Override public double getCountOverflowRatio() {
        return cntOverflowRatio;
    }

    /**
     * Sets count overflow ratio.
     * <p>
     * When space entries count grows more than
     * {@link #getMaxSwapCount()} * {@link #getCountOverflowRatio()}
     * SPI will start evicting oldest entries in space.
     * <p>
     * If not provided, default value is {@link #DFLT_OVERFLOW_RATIO}.
     *
     * @param cntOverflowRatio Overflow ratio.
     */
    @GridSpiConfiguration(optional = true)
    public void setCountOverflowRatio(double cntOverflowRatio) {
        this.cntOverflowRatio = cntOverflowRatio;
    }

    /** {@inheritDoc} */
    @Override @Nullable public Map<String, Long> getMaxSwapSizeMap() {
        return maxSwapSizeMap != null ? new HashMap<String, Long>(maxSwapSizeMap) : null;
    }

    /**
     * Sets maximum size for individual spaces.
     * <p>
     * If map is set and contains value for some space, this value takes
     * precedence over {@link #getMaxSwapSize()}.
     * <p>
     * Map should accept {@code null} keys.
     *
     * @param maxSwapSizeMap Maximum size for individual swap spaces.
     */
    @GridSpiConfiguration(optional = true)
    public void setMaxSwapSizeMap(Map<String, Long> maxSwapSizeMap) {
        if (maxSwapSizeMap != null) {
            A.ensure(F.forAll(maxSwapSizeMap.values(), new P1<Long>() {
                @Override public boolean apply(Long l) {
                    return l != null && l > 0;
                }
            }), "'maxSwapSizeMap' should contain only positive values.");
        }

        this.maxSwapSizeMap = maxSwapSizeMap;
    }

    /** {@inheritDoc} */
    @Override @Nullable public Map<String, Long> getMaxSwapCountMap() {
        return maxSwapCntMap != null ? new HashMap<String, Long>(maxSwapCntMap) : null;
    }

    /**
     * Sets maximum count (number of entries) for individual spaces.
     * <p>
     * If map is set and contains value for some space, this value takes
     * precedence over {@link #getMaxSwapCount()}.
     * <p>
     * Map should accept {@code null} keys.
     *
     * @param maxSwapCntMap Maximum count for individual swap spaces.
     */
    @GridSpiConfiguration(optional = true)
    public void setMaxSwapCountMap(Map<String, Long> maxSwapCntMap) {
        if (maxSwapCntMap != null) {
            A.ensure(F.forAll(maxSwapCntMap.values(), new P1<Long>() {
                @Override public boolean apply(Long l) {
                    return l != null && l > 0;
                }
            }), "'maxSwapCntMap' should contain only positive values.");
        }

        this.maxSwapCntMap = maxSwapCntMap;
    }

    /** {@inheritDoc} */
    @Override public int getPoolSize() {
        return poolSize;
    }

    /**
     * Sets internal workers pool size.
     * <p>
     * If not provided, default value is {@link #DFLT_POOL_SIZE}.
     *
     * @param poolSize Pool size.
     */
    @GridSpiConfiguration(optional = true)
    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    /** {@inheritDoc} */
    @Override public int getTaskQueueCapacity() {
        return taskQueueCap;
    }

    /**
     * Sets task queue capacity.
     * <p>
     * If at some moment queue is full, tasks are processed synchronously.
     * <p>
     * If not provided, default value is {@link #DFLT_TASK_QUEUE_CAP}.
     *
     * @param taskQueueCap Task queue capacity.
     */
    @GridSpiConfiguration(optional = true)
    public void setTaskQueueCapacity(int taskQueueCap) {
        this.taskQueueCap = taskQueueCap;
    }

    /** {@inheritDoc} */
    @Override public float getTaskQueueFlushRatio() {
        return taskQueueFlushRatio;
    }

    /**
     * Sets task queue flush ratio.
     * <p/>
     * If queue size exceeds {@link #getTaskQueueCapacity()} * {@link #getTaskQueueFlushRatio()}
     * then worker threads will wake up immediately.
     * <p/>
     * If not provided, default value is {@link #DFLT_TASK_QUEUE_FLUSH_RATIO}.
     *
     * @param taskQueueFlushRatio Flush ratio. Following condition must be satisfied: 0 < flushRatio <= 1.
     */
    @GridSpiConfiguration(optional = true)
    public void setTaskQueueFlushRatio(float taskQueueFlushRatio) {
        this.taskQueueFlushRatio = taskQueueFlushRatio;
    }

    /** {@inheritDoc} */
    @Override public int getTaskQueueFlushFrequency() {
        return taskQueueFlushFreq;
    }

    /**
     * Sets task queue flush frequency.
     * <p/>
     * Each worker thread will wait at most this amount of milliseconds before it starts to
     * process tasks.
     * <p/>
     * If not provided, default value is {@link #DFLT_TASK_QUEUE_FLUSH_FREQ}.
     *
     * @param taskQueueFlushFreq Flush frequency in milliseconds.
     */
    @GridSpiConfiguration(optional = true)
    public void setTaskQueueFlushFrequency(int taskQueueFlushFreq) {
        this.taskQueueFlushFreq = taskQueueFlushFreq;
    }

    /** {@inheritDoc} */
    @Override public long getMaxIndexFileSize() {
        return maxIdxSize;
    }

    /**
     * Sets maximum index file size for all spaces.
     * <p>
     * If current index file exceeds the size, SPI will start next index file.
     * <p>
     * If not provided, default value is {@link #DFLT_MAX_IDX_SIZE}.
     *
     * @param maxIdxSize Maximum index file size for all spaces.
     */
    @GridSpiConfiguration(optional = true)
    public void setMaxIndexFileSize(int maxIdxSize) {
        this.maxIdxSize = maxIdxSize;
    }

    /** {@inheritDoc} */
    @Override public int getMaxIndexBufferSize() {
        return maxIdxBufSize;
    }

    /**
     * Sets max index entries count in index buffer (per space).
     * <p>
     * Whenever swap entry is stored, index entry is added to space buffer.
     * Buffer is flushed to disk once it reaches configured size.
     * <p>
     * If not provided, default value is {@link #DFLT_MAX_IDX_BUF_SIZE}.
     *
     * @param maxIdxBufSize Max index entries count in buffer.
     */
    @GridSpiConfiguration(optional = true)
    public void setMaxIndexBufferSize(int maxIdxBufSize) {
        this.maxIdxBufSize = maxIdxBufSize;
    }

    /** {@inheritDoc} */
    @Override public double getIndexOverflowRatio() {
        return idxOverflowRatio;
    }

    /**
     * Sets index overflow ratio.
     * <p>
     * When total index files size grows for space and becomes greater than
     * {@code space entries count} * {@code average index entry length} *
     * {@link #getIndexOverflowRatio()}
     * SPI initiates index compacting by removing obsolete index entries.
     * <p>
     * If not provided, default value is {@link #DFLT_IDX_OVERFLOW_RATIO}.
     *
     * @param idxOverflowRatio Index overflow ratio.
     */
    @GridSpiConfiguration(optional = true)
    public void setIndexOverflowRatio(double idxOverflowRatio) {
        this.idxOverflowRatio = idxOverflowRatio;
    }

    /** {@inheritDoc} */
    @Override public int getIndexReadBatchSize() {
        return idxReadBatchSize;
    }

    /**
     * Sets index read batch size. Whenever index files are read on evictions,
     * SPI will read this number of entries at a time.
     * <p>
     * If not provided, default value is {@link #DFLT_IDX_READ_BATCH_SIZE}.
     *
     * @param idxReadBatchSize Index read batch size.
     */
    @GridSpiConfiguration(optional = true)
    public void setIndexReadBatchSize(int idxReadBatchSize) {
        this.idxReadBatchSize = idxReadBatchSize;
    }

    /** {@inheritDoc} */
    @Override public int getEvictionSessionTimeout() {
        return sesTimeout;
    }

    /**
     * Sets eviction session timeout. If session is not finished within this timeout
     * by assigned worker, other workers join the session.
     *
     * @param sesTimeout Eviction session timeout.
     */
    @GridSpiConfiguration(optional = true)
    public void setEvictionSessionTimeout(int sesTimeout) {
        this.sesTimeout = sesTimeout;
    }

    /** {@inheritDoc} */
    @Override public int getSyncDelay() {
        return syncDelay;
    }

    /**
     * Sets sync delay in ms.
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
     * <p>
     * If not provided, default value is {@link #DFLT_SYNC_DELAY}.
     *
     * @param syncDelay Sync delay.
     */
    @GridSpiConfiguration(optional = true)
    public void setSyncDelay(int syncDelay) {
        this.syncDelay = syncDelay;
    }

    /** {@inheritDoc} */
    @Override public int getSubFoldersCount() {
        return subFoldersCnt;
    }

    /**
     * Sets sub-folders count on each nested level to distribute swap space entries.
     * <p>
     * If not provided default value is {@link #DFLT_SUB_FOLDERS_CNT}.
     *
     * @param subFoldersCnt Nested sub-folders count.
     */
    @GridSpiConfiguration(optional = true)
    public void setSubFoldersCount(int subFoldersCnt) {
        this.subFoldersCnt = subFoldersCnt;
    }

    /** {@inheritDoc} */
    @Override public int getNestedPathLength() {
        return nestedPathLen;
    }

    /**
     * Sets nested path length (nesting levels count with
     * {@link #setSubFoldersCount(int)} sub-folders on each level)
     * for swap space entries distribution.
     * <p>
     * If not provided default value is {@link #DFLT_NESTED_PATH_LEN}.
     *
     * @param nestedPathLen Nested path length.
     */
    @GridSpiConfiguration(optional = true)
    public void setNestedPathLength(int nestedPathLen) {
        this.nestedPathLen = nestedPathLen;
    }

    /** {@inheritDoc} */
    @Override public void setListener(GridSwapSpaceSpiListener lsnr) {
        this.lsnr = lsnr;
    }

    /** {@inheritDoc} */
    @Override public int getBufferHitCount() {
        return bufHitCnt.get();
    }

    /** {@inheritDoc} */
    @Override public int getDiskReadCount() {
        return diskReadCnt.get();
    }

    /** {@inheritDoc} */
    @Override public int getRejectedTasksCount() {
        return rejectedTasksCnt.get();
    }

    /** {@inheritDoc} */
    @Override public long getTotalSize() {
        return totalSize();
    }

    /** {@inheritDoc} */
    @Override public long getTotalCount() {
        return totalCount();
    }

    /** {@inheritDoc} */
    @Override public void printSpacesStats() {
        if (log.isInfoEnabled()) {
            for (Space space : spaces.values())
                log.info("Space stats: " + space);
        }
    }

    /** {@inheritDoc} */
    @Override public long getMaxEntriesEvictionSessionTime() {
        return maxEntriesEvictSesTime.get();
    }

    /** {@inheritDoc} */
    @Override public long getMaxIndexEvictionSessionTime() {
        return maxIdxEvictSesTime.get();
    }

    /** {@inheritDoc} */
    @Override public long getTotalStoredSize() {
        return totalStoredSize.get();
    }

    /** {@inheritDoc} */
    @Override public long getTotalStoredCount() {
        return totalStoredCnt.get();
    }

    /** {@inheritDoc} */
    @Override public int getTaskQueueSize() {
        return taskQueue.size();
    }

    /** {@inheritDoc} */
    @Override public int getUnreservedTasksCount() {
        return taskQueue.availableTasks();
    }

    /** {@inheritDoc} */
    @Override public int getStoringThreadsCount() {
        return storeThreadsCnt.get();
    }

    /** {@inheritDoc} */
    @Override public int getRemovingThreadsCount() {
        return rmvThreadsCnt.get();
    }

    /** {@inheritDoc} */
    @Override public int getEvictingThreadsCount() {
        return evictThreadsCnt.get();
    }

    /** {@inheritDoc} */
    @Override public int getSubmittedStoreTasksCount() {
        return taskQueue.storeTasksCount();
    }

    /** {@inheritDoc} */
    @Override public int getSubmittedRemoveTasksCount() {
        return taskQueue.removeTasksCount();
    }

    /** {@inheritDoc} */
    @Override public int getSubmittedEvictTasksCount() {
        return taskQueue.evictTasksCount();
    }

    /** {@inheritDoc} */
    @Override public void spiStart(@Nullable final String gridName) throws GridSpiException {
        startStopwatch();

        assertParameter(maxSwapSize > 0, "maxSwapSize > 0");
        assertParameter(maxSwapCnt > 0, "maxSwapCnt > 0");
        assertParameter(sizeOverflowRatio > 1.0, "sizeOverflowRatio > 1.0");
        assertParameter(cntOverflowRatio > 1.0, "cntOverflowRatio > 1.0");
        assertParameter(poolSize > 0, "poolSize > 0");
        assertParameter(taskQueueCap >= 0, "taskQueueCap >= 0");
        assertParameter(taskQueueFlushFreq > 0, "taskQueueFlushFreq > 0");
        assertParameter(taskQueueFlushRatio > 0, "taskQueueFlushRatio > 0");
        assertParameter(taskQueueFlushRatio <= 1, "taskQueueFlushRatio <= 1");
        assertParameter(maxIdxSize > 0, "maxIdxSize > 0");
        assertParameter(maxIdxBufSize >= 0, "maxIdxBufSize >= 0");
        assertParameter(idxOverflowRatio > 1.0, "idxOverflowRatio > 1.0");
        assertParameter(idxReadBatchSize > 0, "idxReadBatchSize > 0");
        assertParameter(sesTimeout > 0, "sesTimeout > 0");
        assertParameter(syncDelay > 0, "syncDelay > 0");
        assertParameter(subFoldersCnt > 0, "subFoldersCnt > 0");
        assertParameter(nestedPathLen > 0, "nestedPathLen > 0");

        if (rootFolderPath == null)
            assertParameter(rootFolderIdxRange > 0, "rootFolderIdxRange > 0");

        initRootFolder();

        // Initialize locks.
        for (int i = 0; i < locks.length; i++)
            locks[i] = new ReentrantReadWriteLock();

        // Start workers.
        for (int i = 0; i < poolSize; i++) {
            GridSpiThread wrk = new Worker();

            wrks.add(wrk);

            wrk.start();
        }

        taskQueueFlushThreshold = (int)(taskQueueCap * taskQueueFlushRatio);

        registerMBean(gridName, this, GridFileSwapSpaceSpiMBean.class);

        if (log.isDebugEnabled()) {
            log.debug(configInfo("rootFolderPath", getRootFolderPath()));
            log.debug(configInfo("maxSwapSize", maxSwapSize));
            log.debug(configInfo("maxSwapCnt", maxSwapCnt));
            log.debug(configInfo("sizeOverflowRatio", sizeOverflowRatio));
            log.debug(configInfo("cntOverflowRatio", cntOverflowRatio));
            log.debug(configInfo("poolSize", poolSize));
            log.debug(configInfo("taskQueueCap", taskQueueCap));
            log.debug(configInfo("taskQueueFlushFreq", taskQueueFlushFreq));
            log.debug(configInfo("taskQueueFlushRatio", taskQueueFlushRatio));
            log.debug(configInfo("maxIdxSize", maxIdxSize));
            log.debug(configInfo("maxIdxBufSize", maxIdxBufSize));
            log.debug(configInfo("idxOverflowRatio", idxOverflowRatio));
            log.debug(configInfo("idxReadBatchSize", idxReadBatchSize));
            log.debug(configInfo("sesTimeout", sesTimeout));
            log.debug(configInfo("syncDelay", syncDelay));
            log.debug(configInfo("subFoldersCnt", subFoldersCnt));
            log.debug(configInfo("nestedPathLen", nestedPathLen));

            log.debug(startInfo());
        }
    }

    /** {@inheritDoc} */
    @Override public void spiStop() throws GridSpiException {
        spiStopping.set(true);

        // Now wake up all worker threads for faster shutdown.
        wakeUpLock.lock();
        
        try {
            wakeUpCond.signalAll();
        }
        finally {
            wakeUpLock.unlock();
        }

        // Join without interruption.
        for (GridSpiThread wrk : wrks)
            U.join(wrk, log);

        unregisterMBean();

        // Finish eviction sessions.
        for (Space space : spaces.values())
            space.finishCurrentEvictionSession(persistent);

        // Process spaces.
        if (persistent) {
            for (Space space : spaces.values()) {
                if (space.size() <= 0 || space.count() <= 0) {
                    if (log.isDebugEnabled())
                        log.debug("Clearing empty space: " + space);

                    space.clear();

                    continue;
                }

                space.flushIndex(true);
            }

            if (totalSize.get() == 0 && rootFolder != null) {
                if (log.isDebugEnabled())
                    log.debug("Deleting root folder since total size is 0.");

                delete(rootFolder);
            }
        }
        else if (rootFolder != null)
            delete(rootFolder);

        spaces.clear();

        sesIdGen.set(0);

        totalSize.set(0);
        totalCnt.set(0);

        totalStoredSize.set(0);
        totalStoredCnt.set(0);

        bufHitCnt.set(0);
        rejectedTasksCnt.set(0);
        diskReadCnt.set(0);

        U.releaseQuiet(rootFolderLock);
        U.closeQuiet(rootFolderLockFile);

        if (log.isDebugEnabled())
            log.debug(stopInfo());
    }

    /**
     * @throws GridSpiException If failed.
     */
    private void initRootFolder() throws GridSpiException {
        if (rootFolderPath == null) {
            String path = DFLT_ROOT_FOLDER_PATH + "-" + gridName + "-" + locNodeId;

            for (int i = 0; i <= rootFolderIdxRange; i++) {
                try {
                    tryInitRootFolder(path + (i > 0 ? "-" + i : ""));

                    // Successful init.
                    break;
                }
                catch (GridSpiException e) {
                    if (i == rootFolderIdxRange)
                        // No more attempts left.
                        throw e;

                    if (log.isDebugEnabled())
                        log.debug("Failed to initialize root folder [path=" + rootFolderPath +
                            ", err=" + e.getMessage() + ']');
                }
            }
        }
        else
            tryInitRootFolder(rootFolderPath);

        if (log.isDebugEnabled())
            log.debug("Initialized root folder: " + rootFolder.getAbsolutePath());
    }

    /**
     * @param path Path.
     * @throws GridSpiException If failed.
     */
    private void tryInitRootFolder(String path) throws GridSpiException {
        assert path != null;

        checkOrCreateRootFolder(path);

        lockRootFolder();

        checkOrCreateSpacesFolder();
    }

    /**
     * @param path Root folder path.
     * @throws GridSpiException If failed.
     */
    private void checkOrCreateRootFolder(String path) throws GridSpiException {
        rootFolder = new File(path);

        if (rootFolder.isAbsolute()) {
            if (!U.mkdirs(rootFolder)) {
                throw new GridSpiException("Swap space directory does not exist and cannot be created: " +
                    rootFolder);
            }
        }
        else if (!F.isEmpty(getGridGainHome())) {
            // Create relative by default.
            rootFolder = new File(getGridGainHome(), path);

            if (!U.mkdirs(rootFolder)) {
                throw new GridSpiException("Swap space directory does not exist and cannot be created: " +
                    rootFolder);
            }
        }
        else {
            String tmpDirPath = System.getProperty("java.io.tmpdir");

            if (tmpDirPath == null) {
                throw new GridSpiException("Failed to initialize swap space directory " +
                    "with unknown GRIDGAIN_HOME (system property 'java.io.tmpdir' does not exist).");
            }

            rootFolder = new File(tmpDirPath, path);

            if (!U.mkdirs(rootFolder)) {
                throw new GridSpiException("Failed to initialize swap space directory " +
                    "with unknown GRIDGAIN_HOME: " + rootFolder);
            }
        }

        if (!rootFolder.isDirectory())
            throw new GridSpiException("Swap space directory path does not represent a valid directory: " + rootFolder);

        if (!rootFolder.canRead() || !rootFolder.canWrite())
            throw new GridSpiException("Can not write or read from swap space directory: " + rootFolder);
    }

    /**
     * @throws GridSpiException If failed.
     */
    private void lockRootFolder() throws GridSpiException {
        assert rootFolder != null;

        File lockFile = new File(rootFolder, LOCK_FILE_NAME);

        boolean err = true;

        try {
            rootFolderLockFile = new RandomAccessFile(lockFile, "rw");

            rootFolderLock = rootFolderLockFile.getChannel().tryLock(0, Long.MAX_VALUE, false);

            if (rootFolderLock == null)
                throw new GridSpiException("Failed to get exclusive lock on lock-file: " + lockFile);

            err = false;

            if (log.isDebugEnabled())
                log.debug("Successfully locked on: " + lockFile);
        }
        catch (FileNotFoundException e) {
            throw new GridSpiException("Failed to create lock-file: " + lockFile, e);
        }
        catch (IOException e) {
            throw new GridSpiException("Failed to get exclusive lock on lock-file: " + lockFile, e);
        }
        catch (OverlappingFileLockException e) {
            throw new GridSpiException("Failed to get exclusive lock on lock-file: " + lockFile, e);
        }
        finally {
            if (err)
                U.closeQuiet(rootFolderLockFile);
        }
    }

    /**
     * @throws GridSpiException If failed.
     */
    private void checkOrCreateSpacesFolder() throws GridSpiException {
        assert rootFolder != null;

        spacesFolder = new File(rootFolder, SPACES_FOLDER);

        if (!U.mkdirs(spacesFolder)) {
            throw new GridSpiException("Swap space folder for spaces does not exist and cannot be created: " +
                spacesFolder);
        }

        if (!spacesFolder.isDirectory())
            throw new GridSpiException("Swap space folder for spaces path does not represent a valid directory: " +
                spacesFolder);

        if (!spacesFolder.canRead() || !spacesFolder.canWrite())
            throw new GridSpiException("Can not write or read from swap space folder for spaces: " + spacesFolder);

        if (persistent) {
            if (log.isDebugEnabled())
                log.debug("Initializing persisted spaces.");

            for (File f : spacesFolder.listFiles()) {
                Space space = new Space(unmaskNull(f.getName()));

                space.init(true);

                spaces.put(f.getName(), space);
            }
        }
        else {
            if (log.isDebugEnabled())
                log.debug("Deleting all files under spaces folder: " + spacesFolder);

            for (File f : spacesFolder.listFiles())
                delete(f);
        }
    }

    /**
     * @param name Name.
     * @return Space (created, if needed and reserved).
     * @throws GridSpiException If failed.
     */
    private Space space(@Nullable String name) throws GridSpiException {
        Space space = space(name, true);

        assert space != null;

        return space;
    }

    /**
     * @param name Name.
     * @param create Create flag.
     * @return Space.
     * @throws GridSpiException If failed.
     */
    @Nullable private Space space(@Nullable String name, boolean create) throws GridSpiException {
        String maskedName = maskNull(name);

        return create ? F.addIfAbsent(spaces, maskedName, new Space(name)) : spaces.get(maskedName);
    }

    /**
     * @param name Name.
     * @return Masked name.
     * @throws GridSpiException If space name is invalid.
     */
    private String maskNull(String name) throws GridSpiException {
        if (name == null)
            return DFLT_SPACE_NAME;

        else if (name.isEmpty())
            throw new GridSpiException("Space name cannot be empty: " + name);

        else if (DFLT_SPACE_NAME.equalsIgnoreCase(name))
            throw new GridSpiException("Space name is reserved for default space: " + name);

        else if (name.contains("/") || name.contains("\\"))
            throw new GridSpiException("Space name contains invalid characters: " + name);

        return name;
    }

    /**
     * @param name Name.
     * @return Masked name.
     */
    @Nullable private String unmaskNull(String name) {
        assert name != null;

        if (DFLT_SPACE_NAME.equals(name))
            return null;

        return name;
    }

    /**
     * @param evtType Event type.
     * @param spaceName Space name.
     * @param keyBytes Key bytes (for eviction notification only).
     */
    private void notifySwapManager(int evtType, @Nullable String spaceName, @Nullable byte[] keyBytes) {
        GridSwapSpaceSpiListener evictLsnr = lsnr;

        if (evictLsnr != null)
            evictLsnr.onSwapEvent(evtType, spaceName, keyBytes);

    }

    /** {@inheritDoc} */
    @Override public long size(@Nullable String spaceName) throws GridSpiException {
        Space space = space(spaceName, false);

        return space == null ? 0 : space.size();
    }

    /** {@inheritDoc} */
    @Nullable @Override public Collection<Integer> partitions(@Nullable String spaceName) throws GridSpiException {
        Space space = space(spaceName, false);

        return space == null ? null : space.partitions();
    }

    /** {@inheritDoc} */
    @Override public long count(@Nullable String spaceName) throws GridSpiException {
        Space space = space(spaceName, false);

        return space == null ? 0 : space.count();
    }

    /** {@inheritDoc} */
    @Override public long totalSize() {
        return totalSize.get();
    }

    /** {@inheritDoc} */
    @Override public long totalCount() {
        return totalCnt.get();
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"TooBroadScope"})
    @Override public void store(@Nullable String spaceName, GridSwapKey key, @Nullable byte[] val,
        GridSwapContext ctx) throws GridSpiException {
        assert key != null;
        assert ctx != null;

        SpaceKey k = new SpaceKey(spaceName, key);

        SwapEntry entry = new SwapEntry(k, val);

        boolean added = taskQueue.addTask(new StoreSwapEntryTask(entry, ctx));

        if (!added) {
            // NOTE: User thread should not evict. This task should be
            // processed by pool.
            if (log.isDebugEnabled())
                log.debug("Entry should be stored synchronously (buffer is full): " + entry);

            rejectedTasksCnt.incrementAndGet();

            Space space;

            ReadWriteLock lock = lock(k.hash());

            lock.writeLock().lock();

            try {
                while (true) {
                    space = space(spaceName);

                    if (space.store(entry, ctx))
                        break;
                    else {
                        try {
                            space.waitClearFinish();
                        }
                        catch (InterruptedException ignored) {
                            throw new GridSpiException("Thread has been interrupted.");
                        }
                    }
                }
            }
            finally {
                lock.writeLock().unlock();
            }

            notifySwapManager(EVT_SWAP_SPACE_DATA_STORED, spaceName, null);

            space.flushIndex(false);
        }
    }

    /** {@inheritDoc} */
    @Override public void storeAll(@Nullable String spaceName, Map<GridSwapKey, byte[]> pairs,
        GridSwapContext ctx)
        throws GridSpiException {
        assert pairs != null;
        assert ctx != null;

        for (Map.Entry<GridSwapKey, byte[]> entry : pairs.entrySet())
            store(spaceName, entry.getKey(), entry.getValue(), ctx);
    }

    /** {@inheritDoc} */
    @Nullable @Override public byte[] read(@Nullable String spaceName, GridSwapKey key, GridSwapContext ctx)
        throws GridSpiException {
        assert key != null;
        assert ctx != null;

        SpaceKey k = new SpaceKey(spaceName, key);

        byte[] res = taskQueue.get(k);

        if (res != null) {
            if (log.isDebugEnabled())
                log.debug("Entry was read from buffer [space=" + spaceName + ", key=" + key + ']');

            notifySwapManager(EVT_SWAP_SPACE_DATA_READ, spaceName, null);

            return res;
        }

        // Read from disk.
        Space space = space(k.space(), false);

        GridTuple<byte[]> t = null;

        if (space == null) {
            if (log.isDebugEnabled())
                log.debug("Failed to read entry (unknown space): " + k);
        }
        else {
            ReadWriteLock lock = lock(k.hash());

            lock.readLock().lock();

            try {
                t = space.read(k, ctx);
            }
            finally {
                lock.readLock().unlock();
            }
        }

        if (t == null) {
            if (log.isDebugEnabled())
                log.debug("Entry was not found (neither in buffer nor on disk): " + k);

            return null;
        }

        notifySwapManager(EVT_SWAP_SPACE_DATA_READ, spaceName, null);

        return t.get();
    }

    /** {@inheritDoc} */
    @Override public Map<GridSwapKey, byte[]> readAll(String spaceName, Iterable<GridSwapKey> keys,
        GridSwapContext ctx) throws GridSpiException {
        assert keys != null;
        assert ctx != null;

        Map<GridSwapKey, byte[]> res = new HashMap<GridSwapKey, byte[]>();

        for (GridSwapKey key : keys)
            res.put(key, read(spaceName, key, ctx));

        return res;
    }

     /** {@inheritDoc} */
    @Override public void remove(@Nullable String spaceName, GridSwapKey key,
        @Nullable GridInClosure<byte[]> c, GridSwapContext ctx) throws GridSpiException {
        assert key != null;
        assert ctx != null;

        SpaceKey k = new SpaceKey(spaceName, key);

        byte[] bufVal = taskQueue.remove(k);

        if (bufVal != null) {
            if (c != null)
                c.apply(bufVal);

            // Value was removed from queue. Schedule async deletion
            // if old value is on disk.
            RemoveSwapEntryTask task = new RemoveSwapEntryTask(k, ctx, true);

            if (!taskQueue.addTask(task))
                task.body(false);

            return;
        }

        if (c == null) {
            // Value was not found in queue. Schedule async deletion
            // if old value is on disk. Caller does not need value.
            RemoveSwapEntryTask task = new RemoveSwapEntryTask(k, ctx, false);

            if (!taskQueue.addTask(task))
                task.body(false);

            return;
        }

        if (taskQueue.containsRemoveTask(k)) {
            if (log.isDebugEnabled())
                log.debug("Remove cancelled (entry is scheduled for deletion): " + k);

            return;
        }

        GridTuple<byte[]> t = null;

        ReadWriteLock lock = lock(k.hash());

        lock.readLock().lock();

        try {
            Space space = space(spaceName, false);

            if (space != null)
                t = space.remove(k, ctx);

            else if (log.isDebugEnabled())
                log.debug("Remove cancelled (unknown space): " + spaceName);
        }
        finally {
            lock.readLock().unlock();
        }

        // Process value after lock is released.
        if (t != null) {
            byte[] val = t.get();

            if (val != null)
                c.apply(val);

            // Remove file.
            RemoveSwapEntryTask task = new RemoveSwapEntryTask(k, ctx, false);

            if (!taskQueue.addTask(task))
                task.body(false);
        }
    }

    /** {@inheritDoc} */
    @Override public void removeAll(@Nullable String spaceName, Collection<GridSwapKey> keys,
        @Nullable final GridInClosure2<GridSwapKey, byte[]> c, GridSwapContext ctx) throws GridSpiException {
        assert keys != null;
        assert ctx != null;

        for (final GridSwapKey key : keys) {
            CI1<byte[]> c1 = null;

            if (c != null) {
                c1 = new CI1<byte[]>() {
                    @Override public void apply(byte[] val) {
                        c.apply(key, val);
                    }
                };
            }

            remove(spaceName, key, c1, ctx);
        }
    }

    /** {@inheritDoc} */
    @Override public void clear(@Nullable String spaceName) throws GridSpiException {
        Space space = space(maskNull(spaceName), false);

        if (space == null) {
            if (log.isDebugEnabled())
                log.debug("Clear cancelled (unknown space): " + spaceName);

            return;
        }

        space.clear();
    }

    /**
     * This method is intended for test purposes only.
     *
     * @return Swap space folder.
     */
    File rootFolder() {
        return rootFolder;
    }

    /**
     * This method is intended for test purposes only.
     *
     * @return Spaces folder.
     */
    File spacesFolder() {
        return spacesFolder;
    }

    /**
     * @param hash Hash.
     * @return Lock.
     */
    private ReadWriteLock lock(int hash) {
        return locks[Math.abs(hash) % locks.length];
    }

    /**
     * @param f File to recursively delete.
     */
    private void delete(File f) {
        assert f != null;

        if (f.isDirectory()) {
            for (File c : f.listFiles())
                delete(c);
        }

        if (log.isDebugEnabled())
            log.debug("Deleting file or directory: " + f);

        f.delete();
    }

    /**
     * This method is called whenever task count in task queue is changed.
     *
     * @param taskCnt Updated task count
     */
    private void taskCountChanged(int taskCnt) {
        assert taskCnt >= 0;

        if (sleepingCnt > 0) {
            if (taskCnt >= taskQueueFlushThreshold) {
                wakeUpLock.lock();
                
                try {
                    if (sleepingCnt > 0)
                        wakeUpCond.signalAll();
                }
                finally {
                    wakeUpLock.unlock();
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridSwapSpaceSpi.class, this);
    }

    /**
     *
     */
    private class Space {
        /** */
        private final String name;

        /** */
        @GridToStringExclude
        private File spaceFolder;

        /** */
        @GridToStringExclude
        private File idxFolder;

        /** */
        private final AtomicInteger startIdxFile = new AtomicInteger();

        /** End position of the read region of the start IDX file (read from next time). */
        private volatile long startIdxFilePos = IDX_DATA_POS;

        /** */
        private final AtomicInteger activeIdxFile = new AtomicInteger();

        /** */
        private volatile long activeIdxFilePos = IDX_DATA_POS;

        /** */
        private final ConcurrentMap<Integer, AtomicInteger> idxReservs =
            new ConcurrentHashMap<Integer, AtomicInteger>();

        /** */
        private final AtomicLong size = new AtomicLong();

        /** */
        private final AtomicLong cnt = new AtomicLong();

        /** */
        @GridToStringInclude
        private final Set<Integer> parts = new HashSet<Integer>();

        /** */
        private final GridConcurrentLinkedDeque<IndexEntry> idxQueue = new GridConcurrentLinkedDeque<IndexEntry>();

        /** */
        @GridToStringExclude
        private final AtomicBoolean flushGuard = new AtomicBoolean();

        /** Flag showing that positions were read. */
        @GridToStringExclude
        private final AtomicBoolean posRead = new AtomicBoolean();

        /** */
        @GridToStringExclude
        private final AtomicBoolean initGuard = new AtomicBoolean();

        /** */
        @GridToStringExclude
        private final CountDownLatch initLatch = new CountDownLatch(1);

        /** */
        private boolean initFlag;

        /** */
        private final GridBusyLock busyLock = new GridBusyLock();

        /** */
        @GridToStringExclude
        private final AtomicBoolean clearGuard = new AtomicBoolean();

        /** */
        @GridToStringExclude
        private final CountDownLatch clearLatch = new CountDownLatch(1);

        /** */
        private final AtomicReference<EvictionSession> curEvictSes =
            new AtomicReference<EvictionSession>();

        /** */
        @GridToStringExclude
        private final AtomicBoolean evictGuard = new AtomicBoolean();

        /** */
        private volatile Collection<IndexEntry> readEntries;

        /** */
        private volatile int idxEntryLen;

        /** */
        private final AtomicLong totalIdxSize = new AtomicLong();

        /**
         * @param name Space name.
         */
        private Space(@Nullable String name) {
            this.name = name;
        }

        /**
         * @param spiStart {@code True} if init is invoked on SPI start (persistent swap).
         * @throws GridSpiException If failed.
         */
        void init(boolean spiStart) throws GridSpiException {
            if (initGuard.compareAndSet(false, true)) {
                try {
                    spaceFolder = new File(spacesFolder, maskNull(name));

                    if (!U.mkdirs(spaceFolder))
                        throw new GridSpiException("Failed to create folder for space [space=" + name +
                            ", folder=" + spaceFolder + ']');

                    idxFolder = new File(spaceFolder, IDX_FOLDER);

                    if (!U.mkdirs(idxFolder))
                        throw new GridSpiException("Failed to create index folder for space [space=" + name +
                            ", folder=" + idxFolder + ']');

                    initIndex();

                    if (spiStart) {
                        assert persistent;

                        if (log.isDebugEnabled())
                            log.debug("Started exploring space folder recursively to initialize persisted data.");

                        walk(spaceFolder, 0);
                    }
                    else if (nestedPathLen > 0)
                        createNestedFolders(spaceFolder, 0);

                    initFlag = true;

                    if (log.isDebugEnabled())
                        log.debug("Space has been initialized: " + this);
                }
                finally {
                    initLatch.countDown();
                }
            }
            else {
                if (!initFlag) {
                    try {
                        initLatch.await();
                    }
                    catch (InterruptedException ignored) {
                        throw new GridSpiException("Thread has been interrupted.");
                    }

                    if (!initFlag)
                        throw new GridSpiException("Space has not been properly initialized: " + this);
                }
            }
        }

        /**
         * @param f File to recursively walk through.
         * @param nestLevel Nesting level.
         * @throws GridSpiException If failed.
         */
        private void walk(File f, int nestLevel) throws GridSpiException {
            assert f != null;

            if (nestLevel < nestedPathLen) {
                // Only folders expected here.
                BitSet folders = new BitSet(subFoldersCnt);

                File[] children = f.listFiles();

                if (children == null)
                    throw new GridSpiException("Failed to initialize persisted space " +
                        "(sub-folders count is not as expected) [space=" + name +
                        "curFolder=" + f.getAbsolutePath() +
                        ", cnt=" + children.length + ", expected=" + subFoldersCnt + ']');

                int expCnt = nestLevel == 0 ? children.length - 1 : children.length;

                if (expCnt != subFoldersCnt) {
                    throw new GridSpiException("Failed to initialize persisted space " +
                        "(sub-folders count is not as expected) [space=" + name +
                        "curFolder=" + f.getAbsolutePath() +
                        ", cnt=" + children.length + ", subFoldersCnt=" + subFoldersCnt + ']');
                }

                for (File f0: children) {
                    if (IDX_FOLDER.equals(f0.getName()) && nestLevel == 0)
                        continue;

                    try {
                        int idx = Integer.parseInt(f0.getName());

                        if (0 <= idx && idx < subFoldersCnt)
                            folders.set(idx);
                        else {
                            throw new GridSpiException("Failed to initialize persisted space " +
                                "(sub-folder index is out of range) [space=" + name +
                                ", subFolder=" + f0.getAbsolutePath() +
                                ", maxSubFolderIdx=" + (subFoldersCnt - 1) + ']');
                        }
                    }
                    catch (NumberFormatException ignored) {
                        throw new GridSpiException("Failed to initialize persisted space " +
                            "(failed to parse space sub-folder name) [space=" + name +
                            ", subFolder=" + f0.getAbsolutePath() + ']');
                    }

                    walk(f0, nestLevel + 1);
                }

                int miss = folders.nextClearBit(0);

                if (0 < miss && miss < subFoldersCnt) {
                    throw new GridSpiException("Failed to initialize persisted space " +
                        "(missing sub-folder) [space=" + name + ", folder=" + f.getAbsolutePath() +
                        ", missSubFolder=" + miss + ']');
                }
            }
            else {
                // Only files expected here.
                for (File f0: f.listFiles()) {
                    if (!f0.isFile())
                        throw new GridSpiException("Failed to initialize persisted space " +
                            "(file expected) [space=" + name + ", f=" + f.getAbsolutePath() + ']');

                    String name = f0.getName();

                    int sepIdx = name.indexOf(PART_ID_SEPARATOR);

                    if (sepIdx > 0) {
                        try {
                            int part = Integer.parseInt(name.substring(0, sepIdx));

                            if (part < 0)
                                throw new GridSpiException("Failed to parse partition ID from persisted file " +
                                    "[space=" + name + ", parsed=" + part + ", f=" + f0.getAbsolutePath() + ']');

                            if (part < Integer.MAX_VALUE)
                                parts.add(part);
                        }
                        catch (NumberFormatException ignored) {
                            throw new GridSpiException("Failed to parse partition ID from persisted file " +
                                "[space=" + name + ", f=" + f0.getAbsolutePath() + ']');
                        }
                    }
                    else
                        throw new GridSpiException("Failed to parse partition ID from persisted file " +
                            "[space=" + name + ", f=" + f0.getAbsolutePath() + ']');

                    cnt.incrementAndGet();
                    totalCnt.incrementAndGet();

                    long len = f0.length();

                    size.addAndGet(len);
                    totalSize.addAndGet(len);
                }
            }
        }

        /**
         * @param f Folder.
         * @param nestLevel Current nest level.
         * @throws GridSpiException If failed.
         */
        private void createNestedFolders(File f, int nestLevel) throws GridSpiException {
            assert f != null;
            assert nestLevel < nestedPathLen;

            int nextLevel = nestLevel + 1;

            boolean goDown = nextLevel < nestedPathLen;

            for (int i = 0; i < subFoldersCnt; i++) {
                File nested = new File(f, String.valueOf(i));

                if (!U.mkdirs(nested))
                    throw new GridSpiException("Failed to create nested folder: " + nested);

                if (goDown)
                    createNestedFolders(nested, nextLevel);
            }
        }

        /**
         * @throws GridSpiException If failed.
         */
        private void initIndex() throws GridSpiException {
            Iterable<String> files = new TreeSet<String>(Arrays.asList(idxFolder.list()));

            if (log.isDebugEnabled())
                log.debug("Persisted index files [space=" + this + ", files=" + files + ']');

            long len = 0;

            for (String s : files) {
                int idx;

                try {
                    idx = Integer.parseInt(s);
                }
                catch (NumberFormatException ignored) {
                    U.warn(log, "Failed to parse index file name (are there any outer modifications?): " + s);

                    // Delete file.
                    delete(new File(idxFolder, s));

                    continue;
                }

                activeIdxFile.set(idx);

                len = new File(idxFolder, idxToFilename(activeIdxFile.get())).length();

                // If index file length is less, file is corrupted and will be deleted on evictions.
                if (len > IDX_DATA_POS)
                    totalIdxSize.addAndGet(len - IDX_DATA_POS);
            }

            // Always write into new index file, if last persisted is not empty.
            int idx = len > IDX_DATA_POS ? activeIdxFile.incrementAndGet() : activeIdxFile.get();

            createIndexFile(idx);

            activeIdxFilePos = IDX_DATA_POS;
        }

        /**
         * Initializes new index file.
         *
         * @param idx Index file name number.
         * @throws GridSpiException If failed.
         */
        private void createIndexFile(int idx) throws GridSpiException {
            createIndexFile(idxToFilename(idx));
        }

        /**
         * Initializes new index file.
         *
         * @param idx Index file name.
         * @throws GridSpiException If failed.
         */
        private void createIndexFile(String idx) throws GridSpiException {
            assert !F.isEmpty(idx);

            FileOutputStream out = null;

            try {
                out = new FileOutputStream(new File(idxFolder, idx));

                byte[] data = positionToString(IDX_DATA_POS).getBytes();

                out.write(data); // Pos1.
                out.write(data); // Pos2.
                out.write(data); // Backup.
            }
            catch (IOException e) {
                throw new GridSpiException("Failed to initialize index file: " + idx, e);
            }
            finally {
                U.closeQuiet(out);
            }

            if (log.isDebugEnabled())
                log.debug("Initialized index file: " + idx);
        }

        /**
         * @param idx Index file number.
         * @return String in the following format: {@code %010d}.
         */
        private String idxToFilename(int idx) {
            return String.format("%010d", idx);
        }

        /**
         * @param pos Position.
         * @return Formatted string.
         */
        private String positionToString(long pos) {
            return String.format("%020d%n", pos);
        }

        /**
         * @return Name.
         */
        String name() {
            return name;
        }

        /**
         * @return Index folder for this space.
         */
        File indexFolder() {
            return idxFolder;
        }

        /**
         * @return Partitions IDs persisted to disk prior to SPI start.
         * @throws GridSpiException If failed.
         */
        @Nullable Collection<Integer> partitions() throws GridSpiException {
            if (!busyLock.enterBusy())
                return null;

            try {
                init(false);

                return new ArrayList<Integer>(parts);
            }
            finally {
                busyLock.leaveBusy();
            }
        }

        /**
         * @param addSize Size in bytes.
         * @param addCnt Count.
         */
        private void updateCounters(int addCnt, long addSize) {
            size.addAndGet(addSize);
            cnt.addAndGet(addCnt);

            // Update global counters as well.
            totalSize.addAndGet(addSize);
            totalCnt.addAndGet(addCnt);

            if (addSize > 0)
                totalStoredSize.addAndGet(addSize);

            if (addCnt > 0)
                totalStoredCnt.addAndGet(addCnt);
        }

        /**
         * @return Size overflow value in bytes if any or {@code 0} if size is OK.
         */
        long sizeOverflow() {
            if (!busyLock.enterBusy())
                return 0;

            try {
                long size = size();

                Map<String, Long> maxSwapSizeMap0 = maxSwapSizeMap;

                // Use individual or common value.
                long maxSwapSize0 = (maxSwapSizeMap0 != null && maxSwapSizeMap0.containsKey(name)) ?
                    maxSwapSizeMap0.get(name) : maxSwapSize;

                long limit = (long)(maxSwapSize0 * sizeOverflowRatio);

                if (size > limit)
                    return size - maxSwapSize0;

                return 0;
            }
            finally {
                busyLock.leaveBusy();
            }
        }

        /**
         * @return Count overflow value if any or {@code 0} if count is OK.
         */
        long countOverflow() {
            if (!busyLock.enterBusy())
                return 0;

            try {
                long cnt = count();

                Map<String, Long> maxSwapCntMap0 = maxSwapCntMap;

                // Use individual or common value.
                long maxSwapCnt0 = (maxSwapCntMap0 != null && maxSwapCntMap0.containsKey(name)) ?
                    maxSwapCntMap0.get(name) : maxSwapCnt;

                long limit = (long)(maxSwapCnt0 * cntOverflowRatio);

                if (limit < 0)
                    limit = Long.MAX_VALUE;

                if (cnt > limit)
                    return cnt - maxSwapCnt0;

                return 0;
            }
            finally {
                busyLock.leaveBusy();
            }
        }

        /**
         * @return {@code True} if it is needed to compact index.
         */
        long indexOverflow() {
            if (!busyLock.enterBusy())
                return 0;

            try {
                int idxEntryLen = this.idxEntryLen;

                if (idxEntryLen == 0)
                    // Impossible to determine by now.
                    return 0;

                long totalIdxSize = this.totalIdxSize.get();

                long cnt = count();

                long calcIdxSize = cnt * idxEntryLen;

                if (calcIdxSize < 0)
                    calcIdxSize = Long.MAX_VALUE;

                long limit = (long)(calcIdxSize * idxOverflowRatio);

                if (limit < 0)
                    limit = Long.MAX_VALUE;

                return totalIdxSize > limit ? totalIdxSize - calcIdxSize : 0;
            }
            finally {
                busyLock.leaveBusy();
            }
        }

        /**
         * @return Size in bytes.
         */
        long size() {
            if (!busyLock.enterBusy())
                return 0;

            try {
                return size.get();
            }
            finally {
                busyLock.leaveBusy();
            }
        }

        /**
         * @return Entries count.
         */
        long count() {
            if (!busyLock.enterBusy())
                return 0;

            try {
                return cnt.get();
            }
            finally {
                busyLock.leaveBusy();
            }
        }

        /**
         * @param e Index entry.
         */
        private void addIndexEntry(IndexEntry e) {
            assert e != null;

            idxQueue.add(e);
        }

        /**
         * @param force {@code True} to force flush.
         * @throws GridSpiException If failed.
         */
        void flushIndex(boolean force) throws GridSpiException {
            if (!busyLock.enterBusy())
                return;

            try {
                init(false);

                flushIndex0(force);
            }
            finally {
                busyLock.leaveBusy();
            }
        }

        /**
         * @param force {@code True} to force flush.
         * @throws GridSpiException If failed.
         */
        private void flushIndex0(boolean force) throws GridSpiException {
            if (!force && idxQueue.sizex() < maxIdxBufSize)
                return;

            if (flushGuard.compareAndSet(false, true)) {
                int idx;

                byte[] data;

                long pos;

                try {
                    int delta = idxQueue.sizex();

                    if (delta == 0 || (!force && delta < maxIdxBufSize))
                        return;

                    if (log.isDebugEnabled())
                        log.debug("Index entries count to be flushed to disk: " + delta);

                    int avgLen = 0;

                    Formatter f = new Formatter();

                    for (int i = 0; i < delta; i++) {
                        IndexEntry e = idxQueue.poll();

                        if (e == null)
                            break; // Safety.

                        // Length of the result string.
                        int len = e.length();

                        f.format("%s|%011d|%020d|%04d%n", e.path(), e.hash(), e.timestamp(), len);

                        avgLen += len;
                    }

                    if (idxEntryLen == 0) {
                        idxEntryLen = avgLen / delta;

                        if (log.isDebugEnabled())
                            log.debug("Saved average index entry length: " + idxEntryLen);
                    }

                    // Remember value to avoid volatile-reads.
                    idx = activeIdxFile.get();

                    // Check if it is necessary to create new file.
                    pos = activeIdxFilePos;

                    boolean nextIdx = false;

                    if (pos != IDX_DATA_POS && pos > maxIdxSize) {
                        if (log.isDebugEnabled())
                            log.debug("Need to create new index file (active is full) [active=" + idx +
                                ", new=" + (idx + 1) + ']');

                        idx++;

                        createIndexFile(idx);

                        pos = IDX_DATA_POS;

                        nextIdx = true;
                    }

                    // Reserve current index file.
                    AtomicInteger cntr = F.addIfAbsent(idxReservs, idx, F.newAtomicInt());

                    assert cntr != null;

                    int res = cntr.incrementAndGet();

                    if (log.isDebugEnabled())
                        log.debug("Reservations for index file [idx=" + idx + ", res=" + res + ']');

                    data = f.toString().getBytes();

                    // Reserve chunk for this write.
                    activeIdxFilePos = pos + data.length;

                    if (nextIdx)
                        activeIdxFile.set(idx);
                }
                finally {
                    flushGuard.set(false);
                }

                // The rest of the method can be executed concurrently.
                try {
                    File file = new File(idxFolder, idxToFilename(idx));

                    RandomAccessFile randAccessFile = new RandomAccessFile(file, "rw");

                    try {
                        randAccessFile.seek(pos);

                        randAccessFile.write(data);
                    }
                    finally {
                        U.closeQuiet(randAccessFile);
                    }

                    totalIdxSize.addAndGet(data.length);

                    if (log.isDebugEnabled())
                        log.debug("Flushed index [file=" + idx + ", pos=" + pos + ", dataLen=" + data.length +
                            ", space=" + this + ']');
                }
                catch (IOException e) {
                    throw new GridSpiException("Failed to flush index for space: " + this, e);
                }
                finally {
                    // Clear reservations.
                    AtomicInteger cntr = idxReservs.get(idx);

                    assert cntr != null;

                    cntr.decrementAndGet();
                }
            }
        }

        /**
         * @return Average value calculated during first flush.
         */
        int averageIndexEntryLength() {
            return idxEntryLen;
        }

        /**
         * @throws GridSpiException If failed.
         */
        void evict() throws GridSpiException {
            if (!busyLock.enterBusy())
                return;

            try {
                init(false);

                createEvictionSession();
            }
            finally {
                busyLock.leaveBusy();
            }

            EvictionSession ses = curEvictSes.get();

            if (ses != null) {
                try {
                    ses.join(false);
                }
                catch (InterruptedException ignored) {
                    throw new GridSpiException("Thread has been interrupted.");
                }
            }
        }

        /**
         * @throws GridSpiException If failed.
         */
        private void createEvictionSession() throws GridSpiException {
            if (evictGuard.compareAndSet(false, true)) {
                try {
                    EvictionSession ses = null;

                    long delta;
                    long cntDelta = 0;

                    if ((delta = sizeOverflow()) > 0 || (cntDelta = countOverflow()) > 0) {
                        if (canCreateEvictionSession(SwapEntriesEvictionSession.class)) {
                            if (log.isDebugEnabled())
                                log.debug("Space needs entries evictions [space=" + this + ", delta=" + delta + ']');

                            finishCurrentEvictionSession(true);

                            ses = new SwapEntriesEvictionSession(sesIdGen.incrementAndGet(), this, delta, cntDelta);
                        }
                        else if (log.isDebugEnabled())
                            log.debug("Cannot create entries eviction session for space: " + this);
                    }
                    else if ((delta = indexOverflow()) > 0) {
                        if (canCreateEvictionSession(IndexEntriesEvictionSession.class)) {
                            if (log.isDebugEnabled())
                                log.debug("Space needs index compacting [space=" + this + ", delta=" + delta + ']');

                            ses = new IndexEntriesEvictionSession(sesIdGen.incrementAndGet(), this, delta);
                        }
                        else if (log.isDebugEnabled())
                            log.debug("Cannot create index eviction session for space: " + this);
                    }
                    else if (log.isDebugEnabled())
                        log.debug("Space does not need evictions: " + this);

                    // Set new session if current is null.
                    if (ses != null) {
                        if (log.isDebugEnabled())
                            log.debug("Created eviction session [ses=" + ses + ", space=" + this + ']');

                        boolean res = curEvictSes.compareAndSet(null, ses);

                        assert res : "Failed to set session [ses=" + ses + ", cur=" + curEvictSes + ']';

                        taskQueue.addKeylessTask(new JoinEvictionSessionTask(ses));
                    }
                }
                finally {
                    evictGuard.set(false);
                }
            }
        }

        /**
         * @param sesCls Session class to create instance of.
         * @return {@code True} if it is possible to create eviction session of the passed in type.
         */
        private boolean canCreateEvictionSession(Class<? extends EvictionSession> sesCls) {
            EvictionSession ses = curEvictSes.get();

            if (ses != null)
                return ses.replaceable(sesCls);

            Collection<IndexEntry> readEntries = this.readEntries;

            if (readEntries != null && !readEntries.isEmpty())
                return true;

            int startIdx = startIdxFile.get();

            // Don't mess with evictions if start and active indexes are the same.
            if (activeIdxFile.get() != startIdx) {
                AtomicInteger cntr = idxReservs.get(startIdx);

                return cntr == null || cntr.get() == 0;
            }

            return false;
        }

        /**
         * @param ses Session.
         * @param applyRes {@code True} if results should be applied.
         * @throws GridSpiException If failed.
         */
        void onEvictionSessionFinished(EvictionSession ses, boolean applyRes) throws GridSpiException {
            long sesTime = System.currentTimeMillis() - ses.timestamp();

            if (applyRes) {
                if (ses instanceof IndexEntriesEvictionSession) {
                    applyResults((IndexEntriesEvictionSession)ses);

                    maxIdxEvictSesTime.setIfGreater(sesTime);
                }
                else {
                    // That was swap entries eviction session.
                    Collection<IndexEntry> entries = new LinkedList<IndexEntry>(ses.unprocessed());

                    if (!entries.isEmpty())
                        readEntries = entries;

                    maxEntriesEvictSesTime.setIfGreater(sesTime);
                }

                if (log.isDebugEnabled())
                    log.debug("Applied session results [ses=" + ses + ", time=" + sesTime + ']');
            }
            else if (log.isDebugEnabled())
                log.debug("Ignored session results: " + ses);

            boolean res = curEvictSes.compareAndSet(ses, null);

            assert res : "Session has been concurrently replaced [ses=" + ses + ", cur=" + curEvictSes + ']';
        }

        /**
         * @param ses Index entries eviction session to apply results of.
         * @throws GridSpiException If failed.
         */
        private void applyResults(IndexEntriesEvictionSession ses) throws GridSpiException {
            int last = ses.lastFileIndex();

            if (last == 0)
                // Nothing was created.
                return;

            int start = startIdxFile.get();

            // 1. Rename compacted files and put them before current start index file.
            for (int i = 1; i <= last; i++) {
                File src = new File(idxFolder, ses.id() + "." + i);

                File dest = new File(idxFolder, idxToFilename(start - last + i - 1));

                if (log.isDebugEnabled())
                    log.debug("Renaming compacted index [src=" + src + ", dest=" + dest + ']');

                src.renameTo(dest);

                totalIdxSize.addAndGet(src.length());
            }

            // 2. Append unprocessed entries to the last compacted file.
            Collection<IndexEntry> unprocessed = ses.unprocessed();

            if (!unprocessed.isEmpty()) {
                Formatter f = new Formatter();

                for (IndexEntry e : unprocessed)
                    f.format("%s|%011d|%020d|%04d%n", e.path(), e.hash(), e.timestamp(), e.length());

                byte[] data = f.toString().getBytes();

                String fileName = idxToFilename(start - 1);

                try {
                    File file = new File(idxFolder, fileName);

                    RandomAccessFile randAccessFile = new RandomAccessFile(file, "rw");

                    try {
                        randAccessFile.seek(randAccessFile.length());

                        randAccessFile.write(data);
                    }
                    finally {
                        U.closeQuiet(randAccessFile);
                    }

                    if (log.isDebugEnabled())
                        log.debug("Appended unprocessed index entries [file=" + fileName +
                            ", dataLen=" + data.length + ']');

                    totalIdxSize.addAndGet(data.length);
                }
                catch (IOException e) {
                    throw new GridSpiException("Failed to apply session results: " + ses, e);
                }
            }

            // 3. If any entries was read from current start file, remove this portion.
            if (startIdxFilePos > IDX_DATA_POS) {
                String fileName = idxToFilename(start);

                try {
                    File file = new File(idxFolder, fileName);

                    RandomAccessFile randAccessFile = new RandomAccessFile(file, "rw");

                    try {
                        long readPos = startIdxFilePos;

                        long writePos = IDX_DATA_POS;

                        long len = randAccessFile.length() - readPos;

                        while (len > 0) {
                            randAccessFile.seek(readPos);

                            byte[] data = new byte[10 * 1024 * 1024]; // 10 MB buffer.

                            int readCnt = randAccessFile.read(data);

                            readPos += readCnt;

                            randAccessFile.seek(writePos);

                            randAccessFile.write(data, 0, readCnt);

                            writePos += readCnt;

                            len -= readCnt;
                        }

                        randAccessFile.getChannel().truncate(writePos);
                    }
                    finally {
                        U.closeQuiet(randAccessFile);
                    }

                    if (log.isDebugEnabled())
                        log.debug("Removed already processed index entries from file: " + fileName);
                }
                catch (IOException e) {
                    throw new GridSpiException("Failed to apply session results: " + ses, e);
                }
            }

            // Set pointers.
            startIdxFile.set(start - last);

            startIdxFilePos = IDX_DATA_POS;

            if (log.isDebugEnabled())
                log.debug("Finished applying session results [ses=" + ses + ", space=" + this + ']');
        }

        /**
         * @param idxEntry Index entry to evict swap entry for.
         * @return Length of the deleted file if any was deleted or {@code 0}.
         */
        long evictSwapEntry(IndexEntry idxEntry)  {
            if (!busyLock.enterBusy())
                return 0;

            try {
                File file = new File(spaceFolder, idxEntry.path());

                long l = file.lastModified();

                if (l != 0 && l < idxEntry.timestamp() + syncDelay) { // l != 0 instead of file.exists().
                    byte[] keyBytes = null;

                    try {
                        keyBytes = readKey(file);
                    }
                    catch (GridSpiException e) {
                        if (log.isDebugEnabled())
                            log.debug("Failed to read swap entry before eviction [path=" + idxEntry.path() +
                                ", err=" + e.getMessage() + ']');
                    }

                    long len = file.length();

                    delete(file);

                    if (log.isDebugEnabled())
                        log.debug("Evicted entry for task: " + idxEntry);

                    updateCounters(-1, -len);

                    handleCollisions(idxEntry.path());

                    // Notify listener.
                    if (keyBytes != null)
                        notifySwapManager(EVT_SWAP_SPACE_DATA_EVICTED, name, keyBytes);

                    return len;
                }
                else if (log.isDebugEnabled())
                    log.debug("Cannot complete eviction task since file does not exist or has been modified: " +
                        idxEntry.path());
            }
            finally {
                busyLock.leaveBusy();
            }

            return 0;
        }

        /**
         * @param f File.
         * @return Key bytes or {@code null} if file is corrupted.
         * @throws GridSpiException If failed.
         */
        @Nullable private byte[] readKey(File f) throws GridSpiException {
            RandomAccessFile randAccessFile = null;

            try {
                randAccessFile = new RandomAccessFile(f, "r");

                // Read file size + key length.
                byte[] len = new byte[12];

                int readCnt = randAccessFile.read(len);

                if (readCnt < 12)
                    return null; // File is corrupted.

                int keyLen = U.bytesToInt(len, 8);

                byte[] key = new byte[keyLen];

                readCnt = randAccessFile.read(key);

                if (readCnt < keyLen)
                    return null; // File is corrupted.

                // Verify trailing length.
                randAccessFile.read(len);

                if (U.bytesToInt(len, 0) != keyLen)
                    return null; // File is corrupted.

                return key;
            }
            catch (IOException e) {
                throw new GridSpiException("Failed to read entry from file: " + f.getAbsolutePath(), e);
            }
            finally {
                U.closeQuiet(randAccessFile);
            }
        }

        /**
         * @param idxEntry Index entry to check validity of.
         * @return {@code True} if entry is still valid
         *      (i.e. target file has not been replaced or updated).
         */
        boolean valid(IndexEntry idxEntry)  {
            if (!busyLock.enterBusy())
                return false;

            try {
                File file = new File(spaceFolder, idxEntry.path());

                long l = file.lastModified();

                boolean valid = l != 0 && l < idxEntry.timestamp() + syncDelay; // l != 0 instead of file.exists().

                if (log.isDebugEnabled())
                    log.debug("Checked validity of index entry [valid=" + valid + ", entry=" + idxEntry + ']');

                return valid;
            }
            finally {
                busyLock.leaveBusy();
            }
        }

        /**
         * @param task Remove entry task.
         * @throws GridSpiException If failed.
         */
        void processRemoveEntryTask(RemoveSwapEntryTask task) throws GridSpiException {
            if (!busyLock.enterBusy()) {
                if (log.isDebugEnabled())
                    log.debug("Remove entry task cannot be processed (space is being cleared): " + task);

                return;
            }

            try {
                String path = task.path();

                if (path == null) {
                    SpaceKey key = task.spaceKey();
                    GridSwapContext ctx = task.context();

                    assert key != null;
                    assert ctx != null;

                    EntryFile entryFile = null;

                    try {
                        entryFile = findFile(key, false, ctx);

                        if (!entryFile.exists()) {
                            if (log.isDebugEnabled())
                                log.debug("File to remove was not found [space=" + name + ", key=" + key + ']');
                        }
                        else
                            // File exists, get path to.
                            path = entryFile.path();
                    }
                    finally {
                        if (entryFile != null)
                            entryFile.dispose();
                    }
                }

                long len = -1;

                if (path != null) {
                    File file = new File(spaceFolder, path);

                    long l = file.lastModified();

                    if (l != 0 && l <= task.timestamp()) { // l != 0 instead of file.exists().
                        len = file.length();

                        delete(file);

                        if (log.isDebugEnabled())
                            log.debug("Removed file for task [task=" + task + ", file=" + file.getAbsolutePath() + ']');

                        updateCounters(-1, -len);

                        handleCollisions(path);
                    }
                    else if (log.isDebugEnabled())
                        log.debug("Cannot process delete task since file does not exist or has been modified: " +
                            path);
                }

                if (task.fireEvent() || len != -1)
                    // Entry was delete from buffer or from disk; need to fire event.
                    notifySwapManager(EVT_SWAP_SPACE_DATA_REMOVED, name, null);
            }
            finally {
                busyLock.leaveBusy();
            }
        }

        /**
         * @return Read entries.
         * @throws GridSpiException if failed.
         */
        @Nullable Collection<IndexEntry> readIndex() throws GridSpiException {
            if (!busyLock.enterBusy())
                return null;

            try {
                init(false);

                return readIndex0();
            }
            finally {
                busyLock.leaveBusy();
            }
        }

        /**
         * @return Read entries.
         * @throws GridSpiException if failed.
         */
        @Nullable private Collection<IndexEntry> readIndex0() throws GridSpiException {
            // 1. Check if there are index entries that have already been read
            // and have not been processed by previous session.
            Collection<IndexEntry> res = readEntries;

            if (res != null) {
                assert !res.isEmpty();

                readEntries = null;

                return res;
            }

            // 2. Read new entries.
            int idx = 0;

            RandomAccessFile randAccessFile = null;

            try {
                int readCnt = 0;

                while (readCnt < idxReadBatchSize) {
                    File file;

                    long pos;

                    // 2.1. Find start index file.
                    while (true) {
                        idx = startIdxFile.get();

                        if (idx == activeIdxFile.get()) {
                            if (log.isDebugEnabled())
                                log.debug("Active and start index files are the same: " + idx);

                            return res;
                        }

                        AtomicInteger cntr = idxReservs.get(idx);

                        if (cntr != null) {
                            int val = cntr.get();

                            if (val == 0) {
                                boolean b = idxReservs.remove(idx, cntr);

                                assert b : "Counter was concurrently replaced for index: " + idx;
                            }
                            else {
                                if (log.isDebugEnabled())
                                    log.debug("There are concurrent writes to index file: " + idx);

                                return res;
                            }
                        }

                        file = new File(idxFolder, idxToFilename(idx));

                        if (!file.exists()) {
                            if (log.isDebugEnabled())
                                log.debug("Index file does not exist (will move to next): " + idx);

                            startIdxFile.incrementAndGet();

                            continue;
                        }

                        long len = file.length();

                        if (len <= IDX_DATA_POS) {
                            if (log.isDebugEnabled())
                                log.debug("Index file is corrupted (will move to next) [idx=" + idx +
                                    ", len=" + len + ']');

                            delete(file);

                            startIdxFile.incrementAndGet();

                            continue;
                        }

                        long pos1 = IDX_DATA_POS;
                        long pos2 = IDX_DATA_POS;
                        long backup = IDX_DATA_POS;

                        if (persistent && !posRead.get()) {
                            // We need to read positions only once, if persistent flag is true and
                            // positions were never read before.
                            randAccessFile = new RandomAccessFile(file, "r");

                            try {
                                pos1 = Long.parseLong(randAccessFile.readLine());
                                pos2 = Long.parseLong(randAccessFile.readLine());
                                backup = Long.parseLong(randAccessFile.readLine());
                            }
                            catch (NumberFormatException ignored) {
                                U.warn(log, "Failed to parse index positions for file " +
                                    "(will start from the beginning): " + idx);
                            }
                            finally {
                                U.closeQuiet(randAccessFile);

                                posRead.set(true);
                            }
                        }

                        pos = pos1 == pos2 ? pos1 : backup;

                        if (log.isDebugEnabled())
                            log.debug("Read positions [pos=" + pos + ", pos1=" + pos1 + ", pos2=" + pos2 +
                                ", backup=" + backup + ']');

                        if (pos == len || startIdxFilePos == len) {
                            if (log.isDebugEnabled())
                                log.debug("Index file was fully processed: " + idx);

                            delete(file);

                            startIdxFile.incrementAndGet();

                            startIdxFilePos = IDX_DATA_POS;

                            continue;
                        }

                        long newPos = startIdxFilePos;

                        if (newPos > pos) {
                            saveIndexPosition(newPos, pos, idx);

                            pos = newPos;
                        }

                        break;
                    } // While loop (found next start file).

                    randAccessFile = new RandomAccessFile(file, "rw");

                    randAccessFile.seek(pos);

                    while (readCnt < idxReadBatchSize && randAccessFile.getFilePointer() < randAccessFile.length()) {
                        String s = randAccessFile.readLine();

                        if (log.isDebugEnabled())
                            log.debug("Read line from index [file=" + idx + ", s=" + s + ']');

                        IndexEntry e = parseIndexEntry(s);

                        if (e == null) {
                            if (log.isDebugEnabled())
                                log.debug("Failed to parse string: " + s);

                            continue;
                        }

                        if (log.isDebugEnabled())
                            log.debug("Parsed index entry: " + e);

                        if (res == null)
                            res = new LinkedList<IndexEntry>();

                        res.add(e);

                        readCnt++;
                    }

                    long ptr = randAccessFile.getFilePointer();

                    long readLen = ptr - pos;

                    if (readLen > 0) {
                        startIdxFilePos = ptr;

                        totalIdxSize.addAndGet(-readLen);
                    }
                }

                if (log.isDebugEnabled())
                    log.debug("Read index entries count: " + res.size());

                return res;
            }
            catch (IOException e) {
                throw new GridSpiException("Failed to read index file:" + idx, e);
            }
            finally {
                U.closeQuiet(randAccessFile);
            }
        }

        /**
         * @throws GridSpiException If failed.
         * @param applyRes {@code True} if session results should be applied.
         */
        private void finishCurrentEvictionSession(boolean applyRes) throws GridSpiException {
            EvictionSession ses = curEvictSes.get();

            if (ses != null)
                ses.finish(applyRes);
        }

        /**
         * @param s String.
         * @return Parsed entry.
         */
        @Nullable private IndexEntry parseIndexEntry(String s) {
            if (s.length() < 4)
                return null;

            int len;

            try {
                len = Integer.parseInt(s.substring(s.length() - 4));
            }
            catch (NumberFormatException ignored) {
                return null;
            }

            if (len != s.length())
                return null;

            s = s.substring(0, s.length() - 5);

            String path = s.substring(0, s.indexOf('|'));
            int hash = Integer.parseInt(s.substring(path.length() + 1, s.lastIndexOf('|')));
            long tstamp = Long.parseLong(s.substring(s.lastIndexOf('|') + 1));

            return new IndexEntry(path, hash, tstamp);
        }

        /**
         * @param pos Position (pos1, pos2).
         * @param backup Backup.
         * @param idx Index file.
         * @throws GridSpiException If failed.
         */
        private void saveIndexPosition(long pos, long backup, int idx) throws GridSpiException {
            RandomAccessFile randAccessFile = null;

            try {
                randAccessFile = new RandomAccessFile(new File(idxFolder, idxToFilename(idx)), "rw");

                // Save backup.
                randAccessFile.seek((IDX_DATA_POS / 3) * 2);

                randAccessFile.write(positionToString(backup).getBytes());

                randAccessFile.getFD().sync();

                // Save pos1 and pos2.
                randAccessFile.seek(0);

                byte[] b = positionToString(pos).getBytes();

                randAccessFile.write(b);
                randAccessFile.write(b);

                if (log.isDebugEnabled())
                    log.debug("Wrote new position to index [idx=" + idx + ", pos=" + pos + ", backup=" + backup + ']');
            }
            catch (IOException e) {
                throw new GridSpiException("Failed to write positions to index file [idx=" + idx + ", pos=" + pos +
                    ", backup=" + backup + ']', e);
            }
            finally {
                U.closeQuiet(randAccessFile);
            }
        }

        /**
         * @throws GridSpiException If failed.
         */
        void clear() throws GridSpiException {
            if (clearGuard.compareAndSet(false, true)) {
                busyLock.block();

                try {
                    finishCurrentEvictionSession(false);

                    delete(spaceFolder);

                    // Update global counters.
                    totalCnt.addAndGet(-cnt.get());
                    totalSize.addAndGet(-size.get());

                    notifySwapManager(EVT_SWAP_SPACE_CLEARED, name, null);
                }
                finally {
                    boolean res = spaces.remove(name(), this);

                    assert res;

                    clearLatch.countDown();
                }

                if (log.isDebugEnabled())
                    log.debug("Finished clear space: " + this);
            }
            else if (log.isDebugEnabled())
                log.debug("Space is being deleted concurrently: " + this);
        }

        /**
         * @throws InterruptedException If thread is interrupted.
         */
        void waitClearFinish() throws InterruptedException {
            assert !busyLock.enterBusy() : "Space is not being deleted: " + this;

            if (log.isDebugEnabled())
                log.debug("Waiting for clear finish: " + this);

            clearLatch.await();
        }

        /**
         * Find file for entry and store it.
         *
         * @param swapEntry SwapEntry.
         * @param ctx Swap context.
         * @return {@code False} if could not enter busy state, {@code true} on successful store.
         * @throws GridSpiException If failed.
         */
        boolean store(SwapEntry swapEntry, GridSwapContext ctx) throws GridSpiException {
            if (!busyLock.enterBusy())
                return false;

            EntryFile entryFile = null;

            try {
                init(false);

                entryFile = findFile(swapEntry.spaceKey(), true, ctx);

                int sizeDelta = entryFile.writeEntryToFile(swapEntry);

                updateCounters(entryFile.exists() ? 0 : 1, sizeDelta);

                addIndexEntry(new IndexEntry(entryFile.path(), swapEntry.spaceKey().hash(),
                        System.currentTimeMillis()));

                if (log.isDebugEnabled())
                    log.debug("Stored swap entry: " + swapEntry);
            }
            catch (GridException e) {
                throw new GridSpiException("Failed to store swap entry [space=" + name  +
                        ", key=" + swapEntry.spaceKey().swapKey() + ']', e);
            }
            catch (IOException e) {
                throw new GridSpiException("Failed to store swap entry [space=" + name  +
                        ", key=" + swapEntry.spaceKey().swapKey() + ']', e);
            }
            finally {
                busyLock.leaveBusy();

                if (entryFile != null)
                    entryFile.dispose();
            }

            return true;
        }

        /**
         * @param k SpaceKey.
         * @param write Open target file for write.
         * @param ctx Context.
         * @return Entry file.
         * @throws GridSpiException If failed.
         */
        private EntryFile findFile(SpaceKey k, boolean write, GridSwapContext ctx) throws GridSpiException {
            String filePath = k.canonicalPath();

            File file = new File(spaceFolder, filePath);

            int collisionIdx = 0;

            EntryFile entryFile;

            while (true) {
                EntryFile tmp = null;

                try {
                    RandomAccessFile randAccessFile = new RandomAccessFile(file, write ? "rw" : "r");

                    tmp = new EntryFile(k, filePath, randAccessFile);

                    byte[] keyBytes = tmp.keyBytes();

                    if (keyBytes == null) {
                        if (write) {
                            // Overwrite corrupted or never existed file.
                            entryFile = tmp;

                            tmp = null;

                            break;
                        }
                        else {
                            U.warn(log, "File is corrupted (failed to read key): " + file.getAbsolutePath());

                            if (delCorrupt) {
                                taskQueue.addKeylessTask(new RemoveSwapEntryTask(name, filePath, k.hash()));

                                collisionIdx++;
                            }
                        }
                    }
                    else {
                        // Read key is not null.
                        boolean equals = false;

                        byte[] passedKeyBytes = k.swapKey().keyBytes();

                        if (passedKeyBytes != null && Arrays.equals(passedKeyBytes, keyBytes))
                            equals = true;

                        if (!equals) {
                            try {
                                Object readKey = U.unmarshal(marsh, new ByteArrayInputStream(keyBytes),
                                        ctx.classLoader());

                                equals = readKey.equals(k.swapKey().key());
                            }
                            catch (GridException e) {
                                U.warn(log, "Failed to unmarshal read key (either file is corrupted " +
                                    "or class loader is not correct) [file=" + file.getAbsolutePath() +
                                    ", ctx=" + ctx + ", err=" +  e.getMessage() + ']');
                            }
                        }

                        if (equals) {
                            // Found target file.
                            entryFile = tmp;

                            tmp = null;

                            break;
                        }
                        else {
                            // Collision detected.
                            filePath = k.canonicalPath() + COLLISION_IDX_SEPARATOR + (++collisionIdx);

                            file = new File(spaceFolder, filePath);
                        }
                    }
                }
                catch (FileNotFoundException ignored) {
                    // File does not exist.
                    return new EntryFile(k, filePath);
                }
                catch (IOException e) {
                    throw new GridSpiException("Failed to find file [space=" + k.space() +
                        ", key=" + k.swapKey() + ']');
                }
                finally {
                    if (tmp != null)
                        tmp.dispose();
                }
            }

            if (log.isDebugEnabled())
                log.debug("Entry file: " + entryFile);

            return entryFile;
        }

        /**
         *
         * @param key Key to remove.
         * @param ctx Context.
         * @return Tuple where first element is value bytes or {@code null} if file does not exist.
         * @throws GridSpiException If failed.
         */
        @Nullable GridTuple<byte[]> remove(SpaceKey key, GridSwapContext ctx) throws GridSpiException {
            if (!busyLock.enterBusy())
                return null;

            EntryFile entryFile = null;

            try {
                init(false);

                entryFile = findFile(key, false, ctx);

                if (entryFile.exists())
                    return F.t(entryFile.valueBytes());

                return null;
            }
            catch (IOException e) {
                throw new GridSpiException("Failed to remove swap entry [space=" + name  +
                    ", key=" + key.swapKey() + ']');
            }
            finally {
                busyLock.leaveBusy();

                if (entryFile != null)
                    entryFile.dispose();
            }
        }

        /**
         * This method should be called only when thread owns write lock for hash.
         *
         * @param path Path of the deleted file.
         */
        private void handleCollisions(String path) {
            int idx;

            String path0;

            int sepPos = path.lastIndexOf(COLLISION_IDX_SEPARATOR);

            if (sepPos > 0) {
                try {
                    idx = Integer.parseInt(path.substring(sepPos + 1));
                }
                catch (NumberFormatException ignored) {
                    U.warn(log, "Failed to extract collision index from path (is path valid?): " + path);

                    return;
                }

                path0 = path.substring(0, sepPos);
            }
            else {
                idx = 0;
                path0 = path;
            }

            File lastFile = null;

            while (true) {
                File file = new File(spaceFolder, path0 + COLLISION_IDX_SEPARATOR + (++idx));

                if (file.exists())
                    lastFile = file;
                else
                    break;
            }

            if (lastFile != null) {
                File dest = new File(spaceFolder, path);

                boolean res = lastFile.renameTo(dest);

                if (log.isDebugEnabled()) {
                    if (res)
                        log.debug("Renaming to handle collisions [src=" + lastFile.getAbsolutePath() +
                            ", dest=" + dest.getAbsolutePath() + ']');
                    else
                        log.debug("Renaming failed [src=" + lastFile.getAbsolutePath() +
                            ", dest=" + dest.getAbsolutePath() + ']');
                }
            }
        }

        /**
         * @param key Key to read.
         * @param ctx Context.
         * @return Tuple where first element is value bytes or {@code null} if file does not exist.
         * @throws GridSpiException If failed.
         */
        @Nullable GridTuple<byte[]> read(SpaceKey key, GridSwapContext ctx) throws GridSpiException {
            if (!busyLock.enterBusy())
                return null;

            EntryFile entryFile = null;

            try {
                init(false);

                diskReadCnt.incrementAndGet();

                entryFile = findFile(key, false, ctx);

                if (entryFile.exists()) {
                    byte[] res = entryFile.valueBytes();

                    if (res == null && entryFile.corrupted()) {
                        U.warn(log, "File is corrupted (failed to read value): " +
                            new File(spaceFolder, entryFile.path()).getAbsolutePath());

                        if (delCorrupt)
                            taskQueue.addKeylessTask(new RemoveSwapEntryTask(name, entryFile.path(), key.hash()));
                    }

                    return F.t(res);
                }

                return null;
            }
            catch (IOException e) {
                throw new GridSpiException("Failed to store read entry [space=" + name  +
                        ", key=" + key.swapKey() + ']');
            }
            finally {
                busyLock.leaveBusy();

                if (entryFile != null)
                    entryFile.dispose();
            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(Space.class, this);
        }
    }

    /**
     *
     */
    private class TaskQueue {
        /** */
        private final AtomicInteger qSize = new AtomicInteger();

        /** */
        private final AtomicInteger availableTasks = new AtomicInteger();

        /** */
        private final AtomicInteger storeTasksCnt = new AtomicInteger();

        /** */
        private final AtomicInteger rmvTasksCnt = new AtomicInteger();

        /** */
        private final AtomicInteger evictTasksCnt = new AtomicInteger();

        /** */
        private ConcurrentMap<SpaceKey, StoreSwapEntryTask> storeTasks = GridConcurrentFactory.newMap(1024);

        /** */
        private ConcurrentMap<SpaceKey, RemoveSwapEntryTask> rmvTasks = GridConcurrentFactory.newMap(1024);

        /** */
        private final GridConcurrentLinkedDeque<Task> keylessTasks = new GridConcurrentLinkedDeque<Task>();

        /**
         * @param task Task.
         * @return {@code True} if task was added and will be processed asynchronously,
         *      {@code false} if queue is full and task should be processed by caller thread.
         */
        boolean addTask(StoreSwapEntryTask task) {
            assert task != null;

            SpaceKey key = task.spaceKey();

            assert key != null;

            int tasks = availableTasks.get();

            // Prohibit remove if any
            Task task0 = rmvTasks.remove(key);

            if (task0 != null && task0.reserve())
                // Prohibit async processing and update counter.
                tasks = availableTasks.decrementAndGet();

            // Add to store task.
            task0 = storeTasks.replace(key, task);

            if (task0 == null) {
                // Entry was not found in the queue.
                if (qSize.get() < taskQueueCap) {
                    task0 = storeTasks.put(key, task);

                    if (task0 == null) {
                        qSize.incrementAndGet();

                        storeTasksCnt.incrementAndGet();
                    }
                }
                else
                    return false;
            }

            // If we put new task or old task was reserved.
            if (task0 == null || !task0.reserve())
                // Prohibit async processing and update counter.
                tasks = availableTasks.incrementAndGet();

            // Check if workers should be waked up.
            taskCountChanged(tasks);

            return true;
        }

        /**
         * @param task Task.
         * @return {@code True} if task was added and will be processed asynchronously,
         *      {@code false} if queue is full and task should be processed by caller thread.
         */
        boolean addTask(RemoveSwapEntryTask task) {
            assert task != null;

            SpaceKey key = task.spaceKey();

            assert key != null;

            if (qSize.get() < taskQueueCap) {
                RemoveSwapEntryTask task0 = rmvTasks.putIfAbsent(key, task);

                if (task0 == null) {
                    // Task was put to map.
                    qSize.incrementAndGet();

                    rmvTasksCnt.incrementAndGet();

                    // Increment task count check if workers should be waked up.
                    taskCountChanged(availableTasks.incrementAndGet());
                }
            }
            else
                return rmvTasks.containsKey(key);

            return true;
        }

        /**
         * @param task Task without key. It should never be rejected.
         */
        void addKeylessTask(Task task) {
            assert task != null;
            assert task.spaceKey() == null;

            if (DEBUG)
                assert task instanceof RemoveSwapEntryTask || task instanceof JoinEvictionSessionTask;

            qSize.incrementAndGet();

            int tasks = availableTasks.incrementAndGet();

            keylessTasks.add(task);

            if (task instanceof RemoveSwapEntryTask)
                rmvTasksCnt.incrementAndGet();
            else
                evictTasksCnt.incrementAndGet();

            taskCountChanged(tasks);
        }

        /**
         * @param key Key.
         * @return Value from buffer or {@code null}.
         */
        @Nullable byte[] get(SpaceKey key) {
            assert key != null;

            StoreSwapEntryTask task = storeTasks.get(key);

            if (task != null) {
                bufHitCnt.incrementAndGet();

                return task.entry().value();
            }

            return null;
        }

        /**
         * @param key Space key.
         * @return Value removed from buffer or {@code null}.
         */
        @Nullable byte[] remove(SpaceKey key) {
            assert key != null;

            StoreSwapEntryTask task = storeTasks.remove(key);

            if (task != null) {
                if (task.reserve())
                    // Prohibit async execution and update counter.
                    onTaskReserved();

                bufHitCnt.incrementAndGet();

                qSize.decrementAndGet();

                return task.entry().value();
            }

            return null;
        }

        /**
         * @param key Space key.
         * @return {@code True} if queue contains remove task for this key.
         */
        boolean containsRemoveTask(SpaceKey key) {
            assert key != null;

            return rmvTasks.containsKey(key);
        }

        /**
         * @return Task or {@code null}.
         */
        @Nullable Task keylessTask() {
            Task task = keylessTasks.poll();

            if (task != null) {
                qSize.decrementAndGet();

                if (task instanceof RemoveSwapEntryTask)
                    rmvTasksCnt.incrementAndGet();
                else
                    evictTasksCnt.incrementAndGet();
            }

            return task;
        }

        /**
         * @return Iterator over store tasks.
         */
        Iterator<StoreSwapEntryTask> storeTasksIterator() {
            return storeTasks.values().iterator();
        }

        /**
         * @return Iterator over store tasks.
         */
        Iterator<RemoveSwapEntryTask> removeTasksIterator() {
            return rmvTasks.values().iterator();
        }

        /**
         * @param key Processed key.
         * @param task Processed task.
         */
        void onProcessed(SpaceKey key, StoreSwapEntryTask task) {
            assert key != null;
            assert task != null;

            if (storeTasks.remove(key, task)) {
                qSize.decrementAndGet();

                storeTasksCnt.decrementAndGet();
            }
        }

        /**
         * @param key Processed key.
         * @param task Processed task.
         */
        void onProcessed(SpaceKey key, RemoveSwapEntryTask task) {
            assert key != null;
            assert task != null;

            if (rmvTasks.remove(key, task)) {
                qSize.decrementAndGet();

                rmvTasksCnt.decrementAndGet();
            }
        }

        /**
         * @return Current write queue size.
         */
        int size() {
            return qSize.get();
        }

        /**
         * @return Current available tasks count.
         */
        int availableTasks() {
            return availableTasks.get();
        }

        /**
         * @return Specific tasks count.
         */
        int storeTasksCount() {
            return storeTasksCnt.get();
        }

        /**
         * @return Specific tasks count.
         */
        int removeTasksCount() {
            return rmvTasksCnt.get();
        }

        /**
         * @return Specific tasks count.
         */
        int evictTasksCount() {
            return evictTasksCnt.get();
        }

        /**
         *
         */
        void onTaskReserved() {
            taskCountChanged(availableTasks.decrementAndGet());
        }
    }

    /**
     *
     */
    private abstract class EvictionSession {
        /** */
        private final int id;

        /** */
        @GridToStringExclude
        private final Space space;

        /** */
        private final AtomicBoolean finished = new AtomicBoolean();

        /** */
        private final long tstamp = System.currentTimeMillis();

        /** */
        private final AtomicBoolean idxReadGuard = new AtomicBoolean();

        /** */
        private final BlockingQueue<IndexEntry> q = new LinkedBlockingQueue<IndexEntry>();

        /** */
        private final GridBusyLock busyLock = new GridBusyLock();

        /**
         * @param id Session ID.
         * @param space Space.
         */
        protected EvictionSession(int id, Space space) {
            assert id > 0;
            assert space != null;

            this.id = id;
            this.space = space;
        }

        /**
         * @param force {@code True} to force join.
         * @throws GridSpiException If failed.
         * @throws InterruptedException If thread is interrupted.
         */
        void join(boolean force) throws GridSpiException, InterruptedException {
            if (finished.get())
                return;

            if (!force && tstamp + sesTimeout > System.currentTimeMillis())
                // Session is not timed out, pool will handle it.
                return;

            if (log.isDebugEnabled())
                log.debug("Joined eviction session: " + this);

            while (!finished.get()) {
                if (!busyLock.enterBusy())
                    break;

                boolean forceFinish = false;

                try {
                    IndexEntry idxEntry = next();

                    if (idxEntry != null)
                        processIndexEntry(idxEntry);
                    else
                        forceFinish = true;
                }
                finally {
                    busyLock.leaveBusy();
                }

                if (forceFinish || checkFinished())
                    finish(true);
            }

            if (log.isDebugEnabled())
                log.debug("Exiting eviction session: " + this);
        }

        /**
         * @param idxEntry Entry to process.
         * @throws GridSpiException If failed.
         */
        abstract void processIndexEntry(IndexEntry idxEntry) throws GridSpiException;

        /**
         * @return {@code True} if {@link #finish(boolean)} should be called.
         */
        abstract boolean checkFinished();

        /**
         * @param cls Session class to replace with instance of.
         * @return {@code True} if current session can be replaced with new one
         *      of the provided class.
         */
        abstract boolean replaceable(Class<? extends EvictionSession> cls);

        /**
         * @return Next index entry or {@code null} if none can be read.
         * @throws GridSpiException If failed.
         * @throws InterruptedException If thread is interrupted.
         */
        @Nullable IndexEntry next() throws GridSpiException, InterruptedException {
            IndexEntry idxEntry = null;

            while (!finished.get() && idxEntry == null) {
                idxEntry = q.poll();

                if (idxEntry == null) {
                    if (idxReadGuard.compareAndSet(false, true)) {
                        try {
                            Collection<IndexEntry> entries = space.readIndex();

                            if (entries != null)
                                q.addAll(entries);
                            else
                                return null;
                        }
                        finally {
                            idxReadGuard.set(false);
                        }
                    }
                    else
                        idxEntry = q.poll(500, MILLISECONDS);
                }
            }

            return idxEntry;
        }

        /**
         * @param applyRes {@code True} if results should be applied.
         * @throws GridSpiException If failed.
         */
        void finish(boolean applyRes) throws GridSpiException {
            if (finished.compareAndSet(false, true)) {
                busyLock.block();

                onFinish();

                space.onEvictionSessionFinished(this, applyRes);
            }
        }

        /**
         * @throws GridSpiException If failed.
         */
        void onFinish() throws GridSpiException {
            // No-op.
        }

        /**
         * @return Unprocessed entries.
         */
        Collection<IndexEntry> unprocessed() {
            assert finished.get() : "Session has not been finished: " + this;

            return q;
        }

        /**
         * @return {@code True} if session was finished.
         */
        boolean finished() {
            return finished.get();
        }

        /**
         * @return ID.
         */
        int id() {
            return id;
        }

        /**
         * @return Space.
         */
        Space space() {
            return space;
        }

        /**
         * @return Start timestamp.
         */
        long timestamp() {
            return tstamp;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(EvictionSession.class, this, "space", space.name());
        }
    }

    /**
     *
     */
    private class SwapEntriesEvictionSession extends EvictionSession {
        /** */
        private final long sizeDelta;

        /** */
        private final long cntDelta;

        /** */
        private final AtomicLong evictedSize = new AtomicLong();

        /** */
        private final AtomicLong evictedCnt = new AtomicLong();

        /**
         * @param id Session ID.
         * @param space Space.
         * @param sizeDelta Size delta.
         * @param cntDelta Count delta.
         */
        private SwapEntriesEvictionSession(int id, Space space, long sizeDelta, long cntDelta) {
            super(id, space);

            this.sizeDelta = sizeDelta;
            this.cntDelta = cntDelta;
        }

        /** {@inheritDoc} */
        @Override boolean replaceable(Class<? extends EvictionSession> cls) {
            return false; // Swap entries eviction session cannot be replaced.
        }

        /** {@inheritDoc} */
        @Override void processIndexEntry(IndexEntry idxEntry) {
            ReadWriteLock lock = lock(idxEntry.hash());

            lock.writeLock().lock();

            try {
                long len = space().evictSwapEntry(idxEntry);

                if (len > 0) {
                    evictedSize.addAndGet(len);

                    evictedCnt.incrementAndGet();
                }
            }
            finally {
                lock.writeLock().unlock();
            }
        }

        /** {@inheritDoc} */
        @Override boolean checkFinished() {
            return (evictedSize.get() >= sizeDelta || space().sizeOverflow() == 0) &&
                (evictedCnt.get() >= cntDelta || space().countOverflow() == 0);
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(SwapEntriesEvictionSession.class, this, super.toString());
        }
    }

    /**
     *
     */
    private class IndexEntriesEvictionSession extends EvictionSession{
        /** */
        private final long delta;

        /** */
        private final AtomicLong survived = new AtomicLong();

        /** */
        private final AtomicLong evicted = new AtomicLong();

        /** */
        private volatile int idx;

        /** */
        private volatile long curPos = IDX_DATA_POS;

        /** */
        private final AtomicBoolean flushGuard = new AtomicBoolean();

        /** */
        private final GridConcurrentLinkedDeque<IndexEntry> survivedEntries =
            new GridConcurrentLinkedDeque<IndexEntry>();

        /**
         * @param id Session ID.
         * @param space Space.
         * @param delta Delta.
         */
        private IndexEntriesEvictionSession(int id, Space space, long delta) {
            super(id, space);

            this.delta = delta;
        }

        /** {@inheritDoc} */
        @Override void processIndexEntry(IndexEntry idxEntry) throws GridSpiException {
            ReadWriteLock lock = lock(idxEntry.hash());

            lock.readLock().lock();

            try {
                if (space().valid(idxEntry)) {
                    survivedEntries.add(idxEntry);

                    survived.addAndGet(idxEntry.length());

                    flush(false);
                }
                else
                    evicted.addAndGet(idxEntry.length());
            }
            finally {
                lock.readLock().unlock();
            }
        }

        /**
         * @param force {@code True} to force flush.
         * @throws GridSpiException If failed.
         */
        private void flush(boolean force) throws GridSpiException {
            if (flushGuard.compareAndSet(false, true)) {
                // File index and position for current flush.
                int idx0 = idx;

                long curPos0 = curPos;

                if (idx0 == 0) {
                    // This is first flush.
                    idx0 = 1;

                    idx = 1;
                }

                byte[] data;

                String fileName;

                try {
                    int cnt = survivedEntries.sizex();

                    if (cnt < maxIdxBufSize && !force)
                        return;

                    if (log.isDebugEnabled())
                        log.debug("Survived index entries count to be flushed to disk: " + cnt);

                    Formatter f = new Formatter();

                    for (int i = 0; i < cnt; i++) {
                        IndexEntry e = survivedEntries.poll();

                        f.format("%s|%011d|%020d|%04d%n", e.path(), e.hash(), e.timestamp(), e.length());
                    }

                    data = f.toString().getBytes();

                    if (curPos0 + data.length > maxIdxSize) {
                        // Create new index file for future writes.
                        idx = idx0 + 1; // Next file.

                        curPos = IDX_DATA_POS;
                    }
                    else
                        // Set position for next write in the same file.
                        curPos = curPos0 + data.length;

                    fileName = id() + "." + idx0;

                    space().createIndexFile(fileName);
                }
                finally {
                    flushGuard.set(false);
                }

                // The rest of the method can be processed concurrently.
                try {
                    File file = new File(space().indexFolder(), fileName);

                    RandomAccessFile randAccessFile = new RandomAccessFile(file, "rw");

                    try {
                        randAccessFile.seek(IDX_DATA_POS);

                        randAccessFile.write(data);
                    }
                    finally {
                        U.closeQuiet(randAccessFile);
                    }

                    if (log.isDebugEnabled())
                        log.debug("Flushed index [file=" + fileName + ", ses=" + this +
                            ", dataLen=" + data.length + ']');
                }
                catch (IOException e) {
                    throw new GridSpiException("Failed to flush index for session: " + this, e);
                }
            }
        }

        /** {@inheritDoc} */
        @Override boolean replaceable(Class<? extends EvictionSession> cls) {
            return SwapEntriesEvictionSession.class.isAssignableFrom(cls);
        }

        /**
         * @return Last file index (number after '.') created by this session.
         */
        int lastFileIndex() {
            return idx;
        }

        /** {@inheritDoc} */
        @Override boolean checkFinished() {
            return evicted.get() - survived.get() >= delta;
        }

        /** {@inheritDoc} */
        @Override void onFinish() throws GridSpiException {
            flush(true);
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(IndexEntriesEvictionSession.class, this, super.toString());
        }
    }

    /**
     * Holds information about SwapEntry file.
     */
    private class EntryFile {
        /** */
        private final SpaceKey spaceKey;

        /** Target path to store in index file. */
        private final String path;

        /** */
        @GridToStringExclude
        private final RandomAccessFile file;

        /** */
        private byte[] keyBytes;

        /** */
        private byte[] valBytes;

        /** */
        private long fileSize;

        /** */
        private boolean readKey;

        /** */
        private boolean readVal;

        /** */
        private boolean posDirty;

        /** */
        private boolean corrupt;

        /**
         * Constructor for non-existent files.
         *
         * @param spaceKey Space key.
         * @param path Path.
         */
        private EntryFile(SpaceKey spaceKey, String path) {
            assert spaceKey != null;
            assert path != null;

            this.spaceKey = spaceKey;
            this.path = path;

            file = null;
            keyBytes = null;
            valBytes = null;
            fileSize = 0;
        }

        /**
         * @param spaceKey Space key.
         * @param path Path.
         * @param file Random access file.
         */
        private EntryFile(SpaceKey spaceKey, String path, RandomAccessFile file) {
            assert spaceKey != null;
            assert path != null;
            assert file != null;

            this.spaceKey = spaceKey;
            this.path = path;
            this.file = file;

            valBytes = null;
        }

        /**
         * @return Space key.
         */
        SpaceKey spaceKey() {
            return spaceKey;
        }

        /**
         * @return path.
         */
        String path() {
            return path;
        }

        /**
         * @return Stored key bytes (from disk).
         * @throws IOException If failed.
         */
        @Nullable byte[] keyBytes() throws IOException {
            if (!readKey && file != null) {
                keyBytes = readKeyBytes();

                readKey = true;
            }

            return keyBytes;
        }

        /**
         * @return Read key bytes.
         * @throws IOException If failed.
         */
        @Nullable private byte[] readKeyBytes() throws IOException {
            // Read file size.
            byte[] len = new byte[8];

            int readCnt = file.read(len);

            if (readCnt > 0)
                posDirty = true;

            if (readCnt < 8) {
                corrupt = true;

                return null; // File is corrupted.
            }

            fileSize = U.bytesToLong(len, 0);

            // Read and verify key.
            len = new byte[4];

            readCnt = file.read(len);

            if (readCnt < 4) {
                corrupt = true;

                return null; // File is corrupted.
            }

            int keyLen = U.bytesToInt(len, 0);

            byte[] key = new byte[keyLen];

            readCnt = file.read(key);

            if (readCnt < keyLen) {
                corrupt = true;

                return null; // File is corrupted.
            }

            // Verify trailing length.
            file.read(len);

            if (U.bytesToInt(len, 0) != keyLen) {
                corrupt = true;

                return null; // File is corrupted.
            }

            return key;
        }

        /**
         * @return Stored value bytes.
         * @throws IOException If failed.
         */
        @Nullable byte[] valueBytes() throws IOException {
            if (!readVal) {
                valBytes = readValueBytes();

                readVal = true;
            }

            return valBytes;
        }

        /**
         * @return Read value bytes.
         * @throws IOException If failed.
         */
        @Nullable private byte[] readValueBytes() throws IOException {
            byte[] len = new byte[4];

            int readCnt = file.read(len);

            if (readCnt < 4) {
                corrupt = true;

                return null; // File is corrupted.
            }

            int valLen = U.bytesToInt(len, 0);

            byte[] val = null;

            if (valLen > 0) {
                val = new byte[valLen];

                readCnt = file.read(val);

                if (readCnt < valLen) {
                    corrupt = true;

                    return null; // File is corrupted.
                }
            }

            // Verify trailing length.
            file.read(len);

            if (U.bytesToInt(len, 0) != valLen) {
                corrupt = true;

                return null; // File is corrupted.
            }

            return val;
        }

        /**
         * @param swapEntry SwapEntry.
         * @throws GridException If key marshalling failed.
         * @throws IOException If failed.
         * @return Size delta.
         */
        private int writeEntryToFile(SwapEntry swapEntry) throws GridException, IOException {
            ByteArrayOutputStream bout = new ByteArrayOutputStream(256 * 1024);

            byte[] arr = swapEntry.spaceKey().swapKey().keyBytes();

            if (arr == null) {
                arr = U.marshal(marsh, swapEntry.spaceKey().swapKey().key()).getArray();

                swapEntry.spaceKey().swapKey().keyBytes(arr);
            }

            int keyLen = arr.length;
            byte[] keyLenBytes = U.intToBytes(keyLen);

            byte[] val = swapEntry.value();

            int valLen = val != null ? val.length : 0;

            // Write file size bytes.
            long fileSize0 = 8 + 4 + keyLen + 4 + 4 + valLen + 4;
            bout.write(U.longToBytes(fileSize0));

            // Write key.
            bout.write(keyLenBytes);
            bout.write(arr);
            bout.write(keyLenBytes);

            // Write value.
            byte[] valLenBytes = U.intToBytes(valLen);

            bout.write(valLenBytes);

            if (valLen > 0)
                bout.write(val);

            bout.write(valLenBytes);

            byte[] data = bout.toByteArray();

            if (posDirty)
                file.seek(0);

            file.write(data);

            int sizeDelta = (int) (fileSize0 - fileSize);

            if (sizeDelta < 0) // File has been shrunk and needs to be truncated.
                file.getChannel().truncate(data.length);

            return sizeDelta;
        }

        /**
         * @return File size or {@code 0} if file does not exist.
         */
        long fileSize() {
            return fileSize;
        }

        /**
         * @return {@code True} if file exists.
         */
        boolean exists() {
            return fileSize > 0;
        }

        /**
         * @return {@code True} if file is corrupted.
         */
        boolean corrupted() {
            return corrupt;
        }

        /**
         *
         */
        void dispose() {
            U.closeQuiet(file);
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(EntryFile.class, this);
        }
    }

    /**
     * SpaceKey is a unique identifier for SwapEntry.
     */
    private class SpaceKey {
        /** Space name. */
        private final String space;

        /** Entry key. */
        private final GridSwapKey key;

        /** Path. */
        @GridToStringExclude
        private String canonicalPath;

        /**
         * @param space Space name.
         * @param key Entry key.
         */
        private SpaceKey(String space, GridSwapKey key) {
            assert key != null;

            this.space = space;
            this.key = key;
        }

        /**
         * @return Space name.
         */
        @Nullable String space() {
            return space;
        }

        /**
         * @return entry key.
         */
        GridSwapKey swapKey() {
            return key;
        }

        /**
         * @return File path.
         */
        String canonicalPath() {
            if (canonicalPath == null) {
                SB sb = new SB();

                int idx = Math.abs(key.hashCode());

                // TODO: propose better distribution.
                for (int i = 0; i < nestedPathLen; i++) {
                    sb.a(idx % subFoldersCnt).a(File.separator);

                    idx /= subFoldersCnt;
                }

                sb.a(key.partition()).a(PART_ID_SEPARATOR).a(hash());

                canonicalPath = sb.toString();
            }

            return canonicalPath;
        }

        /**
         * @return Hash.
         */
        int hash() {
            return key.hashCode();
        }

        /** {@inheritDoc} */
        @Override public boolean equals(Object o) {
            if (this == o)
                return true;

            if (o instanceof SpaceKey) {
                SpaceKey k = (SpaceKey)o;

                return key.equals(k.key) && F.eq(space, k.space);
            }

            return false;
        }

        /** {@inheritDoc} */
        @Override public int hashCode() {
            return 31 * key.hashCode() + (space != null ? space.hashCode() : 1);
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(SpaceKey.class, this, "canonicalPath", canonicalPath(), "hash", hash());
        }
    }

    /**
     *
     */
    private static class SwapEntry {
        /** Unique identifier for swap entry. */
        private final SpaceKey spaceKey;

        /** Entry value. */
        private final byte[] val;

        /**
         * @param spaceKey Space key.
         * @param val Value.
         */
        private SwapEntry(SpaceKey spaceKey, byte[] val) {
            this.spaceKey = spaceKey;
            this.val = val;
        }

        /**
         * @return Value.
         */
        byte[] value() {
            return val;
        }

        /**
         * @return Space key.
         */
        SpaceKey spaceKey() {
            return spaceKey;
        }

        /** {@inheritDoc} */
        @Override public boolean equals(Object o) {
            if (this == o)
                return true;

            if (o instanceof SwapEntry) {
                SwapEntry e = (SwapEntry)o;

                return spaceKey.equals(e.spaceKey) && val.equals(e.val);
            }

            return false;
        }

        /** {@inheritDoc} */
        @Override public int hashCode() {
            return 31 * spaceKey.hashCode() + val.hashCode();
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(SwapEntry.class, this);
        }
    }

    /**
     *
     */
    private static class IndexEntry {
        /** Entry file path. */
        private final String path;

        /** Hash. */
        private final int hash;

        /** Timestamp.*/
        private final long tstamp;

        /**
         * @param path Path.
         * @param hash Hash.
         * @param tstamp Timestamp.
         */
        private IndexEntry(String path, int hash, long tstamp) {
            assert path != null;
            assert tstamp > 0;

            this.path = path;
            this.hash = hash;
            this.tstamp = tstamp;
        }

        /**
         * @return Path.
         */
        String path() {
            return path;
        }

        /**
         * @return Hash.
         */
        int hash() {
            return hash;
        }

        /**
         * @return Timestamp.
         */
        long timestamp() {
            return tstamp;
        }

        /**
         * @return Length of the entry string in index file
         *      (string format - {@code %s|%011d|%020d|%04d%n}).
         */
        int length() {
            return path().length() + 11 + 20 + 4 + 3;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(IndexEntry.class, this);
        }
    }

    /**
     *
     */
    private abstract class Task {
        /** */
        private final AtomicBoolean reserved = new AtomicBoolean();

        /**
         * @param async {@code True} if task is being executed asynchronously.
         */
        public abstract void body(boolean async);

        /**
         * @return {@code True} if task was reserved by this call.
         */
        boolean reserve() {
            return reserved.compareAndSet(false, true);
        }

        /**
         * @return {@code True} if task has been already reserved.
         */
        boolean reserved() {
            return reserved.get();
        }

        /**
         * @return Key of the task or {@code null} for specific tasks.
         */
        @Nullable abstract SpaceKey spaceKey();
    }

    /**
     *
     */
    private class StoreSwapEntryTask extends Task {
        /** */
        private final SwapEntry entry;

        /** */
        private final GridSwapContext ctx;

        /**
         * @param entry Entry.
         * @param ctx Context.
         */
        private StoreSwapEntryTask(SwapEntry entry, GridSwapContext ctx) {
            this.entry = entry;
            this.ctx = ctx;
        }

        /** {@inheritDoc} */
        @Override public void body(boolean async) {
            assert async;

            boolean cntDec = false;

            try {
                SpaceKey key = entry.spaceKey();

                if (!persistent && spiStopping.get()) {
                    if (log.isDebugEnabled())
                        log.debug("Ignoring task since SPI is stopping:" + this);

                    taskQueue.onProcessed(key, this);

                    return;
                }

                storeThreadsCnt.incrementAndGet();

                Space space = null;

                ReadWriteLock lock = lock(key.hash());

                lock.writeLock().lock();

                try {
                    while (true) {
                        space = space(key.space());

                        if (space.store(entry, ctx))
                            break;
                        else
                            space.waitClearFinish();
                    }
                }
                finally {
                    lock.writeLock().unlock();
                }

                taskQueue.onProcessed(key, this);

                if (log.isDebugEnabled())
                    log.debug("Successfully wrote entry [entry=" + entry + ", qSize=" + taskQueue.size() + ']');

                notifySwapManager(EVT_SWAP_SPACE_DATA_STORED, key.space(), null);

                // Flush index and help with evictions outside of the lock.
                space.flushIndex(false);

                // Decrement storing threads count after index is flushed.
                storeThreadsCnt.decrementAndGet();

                cntDec = true;

                if (!spiStopping.get()) {
                    try {
                        evictThreadsCnt.incrementAndGet();

                        space.evict();
                    }
                    finally {
                        evictThreadsCnt.decrementAndGet();
                    }
                }
            }
            catch (InterruptedException ignored) {
                assert false : "Worker thread has been interrupted."; // This should never happen.
            }
            catch (GridSpiException e) {
                U.error(log, "Failed to process task: " + this, e);
            }
            finally {
                // Decrement only if count was not already decremented.
                if (!cntDec)
                    storeThreadsCnt.decrementAndGet();
            }
        }

        /**
         * @return Swap entry.
         */
        public SwapEntry entry() {
            return entry;
        }

        /** {@inheritDoc} */
        @Override SpaceKey spaceKey() {
            return entry.spaceKey();
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(StoreSwapEntryTask.class, this);
        }
    }

    /**
     *
     */
    private class JoinEvictionSessionTask extends Task{
        /** Session. */
        private final EvictionSession ses;

        /**
         * @param ses Eviction session.
         */
        private JoinEvictionSessionTask(EvictionSession ses) {
            this.ses = ses;
        }

        /** {@inheritDoc} */
        @Override public void body(boolean async) {
            assert async;

            try {
                if (spiStopping.get()) {
                    if (log.isDebugEnabled())
                        log.debug("Ignoring task since SPI is stopping:" + this);

                    return;
                }

                evictThreadsCnt.incrementAndGet();

                ses.join(true);

                assert ses.finished() : "Worker exited prior to session finish.";

                // Check if more evictions are needed for space.
                if (!spiStopping.get())
                    ses.space().evict();
            }
            catch (InterruptedException e) {
                assert false : "Worker thread has been interrupted."; // This should never happen.
            }
            catch (GridSpiException e) {
                U.error(log, "Failed to join eviction session: " + ses, e);
            }
            finally {
                evictThreadsCnt.decrementAndGet();
            }
        }

        /** {@inheritDoc} */
        @Nullable @Override SpaceKey spaceKey() {
            return null;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(JoinEvictionSessionTask.class, this);
        }
    }

    /**
     *
     */
    private class RemoveSwapEntryTask extends Task {
        /** Space key. */
        private final SpaceKey spaceKey;

        /** Context. */
        private GridSwapContext ctx;

        /** Space name. */
        private final String spaceName;

        /** Entry file path. */
        private final String path;

        /** Hash. */
        private final int hash;

        /** Fire even flag. */
        private final boolean fireEvt;

        /** Timestamp.*/
        private final long tstamp = System.currentTimeMillis();

        /**
         * @param spaceName Space name.
         * @param path Path.
         * @param hash Hash.
         */
        private RemoveSwapEntryTask(String spaceName, String path, int hash) {
            assert spaceName != null;
            assert path != null;

            spaceKey = null;
            this.spaceName = spaceName;
            this.path = path;
            this.hash = hash;

            fireEvt = false; // This constructor is for corrupted files only.
        }

        /**
         * @param spaceKey Space key.
         * @param ctx Swap context.
         * @param fireEvt {@code True} to fire {@link GridEventType#EVT_SWAP_SPACE_DATA_REMOVED}
         *      even if no file was found.
         */
        private RemoveSwapEntryTask(SpaceKey spaceKey, GridSwapContext ctx, boolean fireEvt) {
            assert spaceKey != null;
            assert ctx != null;

            this.spaceKey = spaceKey;
            spaceName = spaceKey.space();
            path = null;
            hash = spaceKey.hash();
            this.ctx = ctx;
            this.fireEvt = fireEvt;
        }

        /** {@inheritDoc} */
        @Override public void body(boolean async) {
            assert async || spaceKey != null;

            try {
                if (async) {
                    if (!persistent && spiStopping.get()) {
                        if (log.isDebugEnabled())
                            log.debug("Ignoring task since SPI is stopping:" + this);

                        if (spaceKey != null)
                            taskQueue.onProcessed(spaceKey, this);

                        return;
                    }

                    rmvThreadsCnt.incrementAndGet();
                }
                else
                    rejectedTasksCnt.incrementAndGet();

                Space space = space(spaceName, false);

                if (space == null) {
                    if (log.isDebugEnabled())
                        log.debug("Remove entry task cannot be processed (unknown space): " + this);

                    return;
                }

                ReadWriteLock lock = lock(hash);

                lock.writeLock().lock();

                try {
                    space.processRemoveEntryTask(this);
                }
                finally {
                    lock.writeLock().unlock();
                }
            }
            catch (GridSpiException e) {
                U.error(log, "Failed to process remove entry task: " + this, e);
            }
            finally {
                if (async) {
                    rmvThreadsCnt.decrementAndGet();

                    if (spaceKey != null)
                        taskQueue.onProcessed(spaceKey, this);
                }
            }
        }

        /**
         * @return Space key.
         */
        @Nullable
        @Override SpaceKey spaceKey() {
            return spaceKey;
        }

        /**
         * @return Context.
         */
        @Nullable
        GridSwapContext context() {
            return ctx;
        }

        /**
         * @return Path.
         */
        @Nullable
        String path() {
            return path;
        }

        /**
         * @return Hash.
         */
        int hash() {
            return hash;
        }

        /**
         * @return {@code True} if event needs to be fired.
         */
        boolean fireEvent() {
            return fireEvt;
        }

        /**
         * @return Timestamp.
         */
        long timestamp() {
            return tstamp;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(RemoveSwapEntryTask.class, this);
        }
    }

    /**
     *
     */
    private class Worker extends GridSpiThread {
        /**
         *
         */
        private Worker() {
            super(gridName, "file-swap-space-worker", log);
        }

        /** {@inheritDoc} */
        @SuppressWarnings("BusyWait")
        @Override protected void body() throws InterruptedException {
            while (!spiStopping.get() || taskQueue.availableTasks() > 0) {
                while (!spiStopping.get() && taskQueue.availableTasks() <= 0)
                    waitForTasks();

                processTasks(taskQueue.storeTasksIterator());

                if (taskQueue.availableTasks() <= 0)
                    continue;

                processTasks(taskQueue.removeTasksIterator());

                if (taskQueue.availableTasks() <= 0)
                    continue;

                Task task;

                while ((task = taskQueue.keylessTask()) != null) {
                    if (log.isDebugEnabled())
                        log.debug("Processing task: " + this);

                    boolean res = task.reserve();

                    assert res;

                    taskQueue.onTaskReserved();

                    task.body(true);
                }
            }
        }

        /**
         * Waits until queue size reaches the wakeup capacity or a timeout expires.
         *
         * @throws InterruptedException If waiting thread was interrupted.
         */
        private void waitForTasks() throws InterruptedException {
            wakeUpLock.lock();

            try {
                sleepingCnt++;

                wakeUpCond.await(taskQueueFlushFreq, TimeUnit.MILLISECONDS);

                sleepingCnt--;
            }
            finally {
                wakeUpLock.unlock();
            }
        }

        /**
         * @param iter Iterator.
         */
        private <T extends Task> void processTasks(Iterator<T> iter) {
            while (iter.hasNext() && taskQueue.availableTasks() > 0) {
                T task = iter.next();

                if (task.reserve()) {
                    if (log.isDebugEnabled())
                        log.debug("Processing task: " + this);

                    taskQueue.onTaskReserved();

                    task.body(true);
                }
                else if (log.isDebugEnabled())
                    log.debug("Ignoring task since it was removed or replaced or " +
                        "is being processed by another thread: " + task);
            }
        }
    }
}
