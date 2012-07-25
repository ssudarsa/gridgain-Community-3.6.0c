// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.functional;

import org.gridgain.grid.typedef.*;
import java.text.*;
import java.util.*;

/**
 * Demonstrates various functional APIs from {@link org.gridgain.grid.lang.GridFunc} class.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridFunctionTransformExample {
    /**
     * Ensures singleton.
     */
    private GridFunctionTransformExample() {
        /* No-op. */
    }

    /**
     * Executes example.
     *
     * @param args Command line arguments, none required.
     */
    public static void main(String[] args) {
        // Typedefs:
        // ---------
        // G -> GridFactory
        // CI1 -> GridInClosure
        // CO -> GridOutClosure
        // CA -> GridAbsClosure
        // F -> GridFunc
        
        // Initialisation array of random formatted dates.
        Collection<String> dates = Arrays.asList("20091013153610", "20091109030023", "20091203115347",
            "20110101134929", "2011/02/02 12:59:05", "20110316153632", "20110408201519");

        // With GridFunc.transform get new collection of dates in another format.
        // Transform only those days which correspond to the specified format.
        Collection<String> res = F.transform(dates,
            new C1<String, String>() {
                private DateFormat fmt1 = new SimpleDateFormat("yyyyMMddHHmmss");
                private DateFormat fmt2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                @Override public String apply(String dateStr) {
                    try {
                        return fmt2.format(fmt1.parse(dateStr));
                    }
                    catch (ParseException e) {
                        throw new IllegalArgumentException(e);
                    }
                }
            },
            new P1<String>() {
                @Override public boolean apply(String dateStr) {
                    return dateStr.matches("\\d{14}");
                }
            }
        );

        // Print result.
        F.forEach(res, F.<String>println());
    }
}
