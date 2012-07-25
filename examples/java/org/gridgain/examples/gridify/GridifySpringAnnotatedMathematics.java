// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.gridify;

import org.gridgain.grid.gridify.*;
import org.gridgain.grid.typedef.*;
import java.util.*;

/**
 * Bean implementation for Spring AOP-based annotations example.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridifySpringAnnotatedMathematics {
    /**
     * Find maximum value in collection.
     * Method grid-enabled with {@link org.gridgain.grid.gridify.GridifySetToValue} annotation.
     * Note that {@link org.gridgain.grid.gridify.GridifySetToValue} annotation
     * is attached to {@link GridifySpringMathematics#findMaximum(java.util.Collection)} method on the
     * interface.
     *
     * @param input Input collection.
     * @return Maximum value.
     */
    @GridifySetToValue(threshold = 2, splitSize = 2)
    public Long findMaximum(Collection<Long> input) {
        X.println(">>>");
        X.println("Find maximum in: " + input);
        X.println(">>>");

        return Collections.max(input);
    }

    /**
     * Find prime numbers in collection.
     * Method grid-enabled with {@link org.gridgain.grid.gridify.GridifySetToSet} annotation.
     * Note that {@link org.gridgain.grid.gridify.GridifySetToSet} annotation
     * is attached to {@link GridifySpringMathematics#findPrimes(java.util.Collection)} method on the
     * interface.
     *
     * @param input Input collection.
     * @return Prime numbers.
     */
    @GridifySetToSet(threshold = 2, splitSize = 2)
    public Collection<Long> findPrimes(Iterable<Long> input) {
        X.println(">>>");
        X.println("Find primes in: " + input);
        X.println(">>>");

        Collection<Long> res = new ArrayList<Long>();

        for (Long val : input) {
            Long divisor = GridSimplePrimeChecker.checkPrime(val, 2, val);

            if (divisor == null)
                res.add(val);
        }

        return res;
    }
}
