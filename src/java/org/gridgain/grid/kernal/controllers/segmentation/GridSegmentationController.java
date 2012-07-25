// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.controllers.segmentation;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.controllers.*;

/**
 * Kernal controller responsible for checking network segmentation issues.
 * <p>
 * Segment checks are performed by segmentation resolvers
 * Each segmentation resolver checks segment for validity, using its inner logic.
 * Typically, resolver should run light-weight single check (i.e. one IP address or
 * one shared folder). Compound segment checks may be performed using several
 * resolvers.
 * <p>
 * The following implementations are provided (Enterprise edition only):
 * <ul>
 *     <li>{@code GridReachabilitySegmentationResolver}</li>
 *     <li>{@code GridSharedFsSegmentationResolver}</li>
 *     <li>{@code GridTcpSegmentationResolver}</li>
 * </ul>
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 * @see GridConfiguration#getSegmentationResolvers()
 * @see GridConfiguration#getSegmentationPolicy()
 * @see GridConfiguration#getSegmentCheckFrequency()
 * @see GridConfiguration#isAllSegmentationResolversPassRequired()
 * @see GridConfiguration#isWaitForSegmentOnStart()
 */
public interface GridSegmentationController extends GridController {
    /**
     * Performs network segment check.
     * <p>
     * This method is called by discovery manager in the following cases:
     * <ol>
     *     <li>Before discovery SPI start.</li>
     *     <li>When other node leaves topology.</li>
     *     <li>When other node in topology fails.</li>
     *     <li>Periodically (see {@link GridConfiguration#getSegmentCheckFrequency()}).</li>
     * </ol>
     *
     * @return {@code True} if segment is correct.
     */
    public boolean isValidSegment();
}
