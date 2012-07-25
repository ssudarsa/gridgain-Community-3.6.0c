// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util;

import org.gridgain.grid.typedef.internal.*;

import java.io.*;
import java.util.*;

/**
 * Finds configuration files located in {@code GRIDGAIN_HOME} folder
 * and its subfolders.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public final class GridConfigurationFinder {
    /** Path to default configuration file. */
    private static final String DFLT_CFG = "config" + File.separator + "default-spring.xml";

    /**
     * Ensure singleton.
     */
    private GridConfigurationFinder() {
        // no-op
    }

    /**
     * Lists paths to all GridGain configuration files
     * located in {@code GRIDGAIN_HOME} folder and its subfolders.
     * Default configuration file will be skipped.
     *
     * @return List of configuration files.
     * @throws IOException If error occurs.
     */
    public static List<String> getConfigurationFiles() throws IOException {
        LinkedList<String> files = getConfigurationFiles(new File(U.getGridGainHome()));

        Collections.sort(files, new Comparator<String>() {
            @Override public int compare(String s1, String s2) {
                String tmp1 = s1.startsWith("(?) ") ? s1.substring(4) : s1;
                String tmp2 = s2.startsWith("(?) ") ? s2.substring(4) : s2;

                return tmp1.compareTo(tmp2);
            }
        });

        files.addFirst(DFLT_CFG);

        return files;
    }

    /**
     * Lists paths to all GridGain configuration files
     * located in specified folder and its subfolders.
     * Default configuration file will be skipped.
     *
     * @param dir Directory.
     * @return List of configuration files in the directory.
     * @throws IOException If error occurs.
     */
    private static LinkedList<String> getConfigurationFiles(File dir) throws IOException {
        LinkedList<String> files = new LinkedList<String>();

        for (String name : dir.list()) {
            File file = new File(dir, name);

            if (file.isDirectory())
                files.addAll(getConfigurationFiles(file));
            else if (file.getName().endsWith(".xml")) {
                boolean springCfg = false;
                boolean ggCfg = false;

                BufferedReader reader = new BufferedReader(new FileReader(file));

                String line;

                while ((line = reader.readLine()) != null) {
                    if (line.contains("http://www.springframework.org/schema/beans"))
                        springCfg = true;

                    if (line.contains("class=\"org.gridgain.grid.GridConfigurationAdapter\""))
                        ggCfg = true;

                    if (springCfg && ggCfg)
                        break;
                }

                if (springCfg) {
                    String path = file.getAbsolutePath().substring(
                        U.getGridGainHome().length() + File.separator.length());

                    if (!path.equals(DFLT_CFG)) {
                        if (!ggCfg)
                            path = "(?) " + path;

                        files.add(path);
                    }
                }
            }
        }

        return files;
    }
}
