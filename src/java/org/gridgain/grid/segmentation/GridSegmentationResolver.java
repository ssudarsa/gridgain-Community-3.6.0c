// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.segmentation;

import org.gridgain.grid.*;
import org.gridgain.grid.editions.*;
import org.gridgain.grid.lang.*;

/**
 * This is the base class for segmentation (a.k.a "split-brain" problem) resolvers.
 * <p>
 * Each segmentation resolver checks segment for validity, using its inner logic.
 * Typically, resolver should run light-weight single check (i.e. one IP address or
 * one shared folder). Compound segment checks may be performed using several
 * resolvers.
 * <p>
 * Note that GridGain support a logical segmentation and not limited to network
 * related segmentation only. For example, a particular segmentation resolver
 * can check for specific application or service present on the network and
 * mark the topology as segmented in case it is not available. In other words
 * you can equate the service outage with network outage via segmentation resolution
 * and employ the unified approach in dealing with these types of problems.
 * <p>
 * The following implementations are built-in (Enterprise edition only):
 * <ul>
 *     <li>{@link org.gridgain.grid.segmentation.reachability.GridReachabilitySegmentationResolver}</li>
 *     <li>{@link org.gridgain.grid.segmentation.sharedfs.GridSharedFsSegmentationResolver}</li>
 *     <li>{@link org.gridgain.grid.segmentation.tcp.GridTcpSegmentationResolver}</li>
 * </ul>
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 * @see GridConfiguration#getSegmentationResolvers()
 * @see GridConfiguration#getSegmentationPolicy()
 * @see GridConfiguration#getSegmentCheckFrequency()
 * @see GridConfiguration#isAllSegmentationResolversPassRequired()
 * @see GridConfiguration#isWaitForSegmentOnStart()
 * @see GridSegmentationPolicy
 */
public abstract class GridSegmentationResolver extends GridAbsPredicate {
    /**
     * Checks whether segment is valid.
     * <p>
     * When segmentation happens every node ends up in either one of two segments:
     * <ul>
     *     <li>Correct segment</li>
     *     <li>Invalid segment</li>
     * </ul>
     * Nodes in correct segment will continue operate as if nodes in the invalid segment
     * simply left the topology (i.e. the topology just got "smaller"). Nodes in the
     * invalid segment will realized that were "left out or disconnected" from the correct segment
     * and will try to reconnect via {@link GridSegmentationPolicy segmentation policy} set
     * in configuration.
     *
     * @return {@code True} if segment is correct, {@code false} otherwise.
     * @throws GridException If an error occurred.
     */
    @GridEnterpriseFeature
    public abstract boolean isValidSegment() throws GridException;

    /**
     * Calls {@link #isValidSegment()}.
     *
     * @return Result of {@link #isValidSegment()} method call.
     * @throws GridRuntimeException If an error occurred.
     */
    @Override public boolean apply() throws GridRuntimeException {
        try {
            return isValidSegment();
        }
        catch (GridException e) {
            throw new GridRuntimeException("Failed to check segment validity.", e);
        }
    }
}
