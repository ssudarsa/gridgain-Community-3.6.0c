package org.gridgain.grid.marshaller.optimized;

import org.gridgain.grid.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.marshaller.jdk.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Optimized implementation of {@link GridMarshaller}. Unlike {@link GridJdkMarshaller},
 * which is based on standard {@link ObjectOutputStream}, this marshaller does not
 * enforce that all serialized objects implement {@link Serializable} interface. It is also
 * generally much faster as it removes lots of serialization overhead that exists in
 * default JDK implementation.
 * <p>
 * {@code GridOptimizedMarshaller} is the default marshaler and will be used if no other
 * marshaller was explicitly configured.
 * <p>
 * <h1 class="header">Configuration</h1>
 * <h2 class="header">Mandatory</h2>
 * This marshaller has no mandatory configuration parameters.
 * <h2 class="header">Java Example</h2>
 * <pre name="code" class="java">
 * GridOptimizedMarshaller marshaller = new GridOptimizedMarshaller();
 *
 * // Enforce Serializable interface.
 * marshaller.setRequireSerializable(true);
 *
 * GridConfigurationAdapter cfg = new GridConfigurationAdapter();
 *
 * // Override marshaller.
 * cfg.setMarshaller(marshaller);
 *
 * // Starts grid.
 * G.start(cfg);
 * </pre>
 * <h2 class="header">Spring Example</h2>
 * GridOptimizedMarshaller can be configured from Spring XML configuration file:
 * <pre name="code" class="xml">
 * &lt;bean id="grid.custom.cfg" class="org.gridgain.grid.GridConfigurationAdapter" singleton="true"&gt;
 *     ...
 *     &lt;property name="marshaller"&gt;
 *         &lt;bean class="org.gridgain.grid.marshaller.optimized.GridOptimizedMarshaller"&gt;
 *             &lt;property name="requireSerializable"&gt;true&lt;/property&gt;
 *         &lt;/bean&gt;
 *     &lt;/property&gt;
 *     ...
 * &lt;/bean&gt;
 * </pre>
 * <p>
 * <img src="http://www.gridgain.com/images/spring-small.png">
 * <br>
 * For information about Spring framework visit <a href="http://www.springframework.org/">www.springframework.org</a>
 * <h2 class="header">Injection Example</h2>
 * GridOptimizedMarshaller can be injected in users task, job or SPI as following:
 * <pre name="code" class="java">
 * public class MyGridJob implements GridJob {
 *     ...
 *     &#64;GridMarshallerResource
 *     private GridMarshaller marshaller;
 *     ...
 * }
 * </pre>
 * or
 * <pre name="code" class="java">
 * public class MyGridJob implements GridJob {
 *     ...
 *     private GridMarshaller marshaller;
 *     ...
 *     &#64;GridMarshallerResource
 *     public void setMarshaller(GridMarshaller marshaller) {
 *         this.marshaller = marshaller;
 *     }
 *     ...
 * }
 * </pre>
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
public class GridOptimizedMarshaller implements GridMarshaller {
    /** Whether or not to require an object to be serializable in order to be marshalled. */
    private boolean requireSer;

    /** */
    private final Map<String, Integer> name2id = new HashMap<String, Integer>();

    /** */
    private final Map<Integer, String> id2name = new HashMap<Integer, String>();

    /** */
    private final ClassLoader dfltClsLdr = getClass().getClassLoader();

    /**
     * Initializes marshaller not to enforce {@link Serializable} interface.
     */
    public GridOptimizedMarshaller() {
        setRequireSerializable(false);
    }

    /**
     * Initializes marshaller with given serialization flag. If {@code true},
     * then objects will be required to implement {@link Serializable} in order
     * to be serialize.
     *
     * @param requireSer Flag to enforce {@link Serializable} interface or not. If {@code true},
     *      then objects will be required to implement {@link Serializable} in order to be
     *      marshalled, if {@code false}, then such requirement will be relaxed.
     * @param clsNames User preregistered class names.
     * @param clsNamesPath Path to a file with user preregistered class names.
     * @throws GridException If an I/O error occurs while writing stream header.
     */
    public GridOptimizedMarshaller(boolean requireSer, Collection<String> clsNames, String clsNamesPath)
        throws GridException {
        setRequireSerializable(requireSer);

        setClassNames(clsNames);

        setClassNamesPath(clsNamesPath);
    }

    /**
     * Adds provides class names.
     *
     * @param clsNames User preregistered class names to add.
     */
    public void setClassNames(Collection<String> clsNames) {
        if (clsNames != null && !clsNames.isEmpty()) {
            List<String> cp = new ArrayList<String>(clsNames);

            Collections.sort(cp);

            int i = name2id.size();

            for (String name : cp) {
                Integer id = i++;

                name2id.put(name, id);

                id2name.put(id, name);
            }
        }
    }

    /**
     * Specifies a name of the file which lists all class names to be optimized.
     * The file path can either be absolute path, relative to {@code GRIDGAIN_HOME},
     * or specify a resource file on the class path.
     * <p>
     * The format of the file is class name per line, like this:
     * <pre>
     * ...
     * com.example.Class1
     * com.example.Class2
     * ...
     * </pre>
     *
     * @param path Path to a file with user preregistered class names.
     * @throws GridException If an error occurs while writing stream header.
     */
    public void setClassNamesPath(String path) throws GridException {
        A.notNull(path, "path");

        URL url = GridUtils.resolveGridGainUrl(path, false);

        if (url == null)
            throw new GridException("Failed to find resource for name: " + path);

        List<String> clsNames;

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(),
                GridOptimizedUtils.UTF_8));

            clsNames = new LinkedList<String>();

            try {
                String clsName;

                while ((clsName = reader.readLine()) != null)
                    clsNames.add(clsName);
            }
            finally {
                reader.close();
            }
        }
        catch (IOException e) {
            throw new GridException("Failed to read class names from path: " + path, e);
        }

        setClassNames(clsNames);
    }

    /**
     * @return Whether to enforce {@link Serializable} interface.
     */
    public boolean isRequireSerializable() {
        return requireSer;
    }

    /**
     * Sets flag to enforce {@link Serializable} interface or not.
     *
     * @param requireSer Flag to enforce {@link Serializable} interface or not. If {@code true},
     *      then objects will be required to implement {@link Serializable} in order to be
     *      marshalled, if {@code false}, then such requirement will be relaxed.
     * @throws IllegalArgumentException If {@code requireSer} is {@code false} while marshalling of
     *      non-serializable classes is not available in the current JVM implementation.
     */
    public void setRequireSerializable(boolean requireSer) throws IllegalArgumentException {
        if (!requireSer && !GridOptimizedUtils.SERIALIZATION_CONSTRUCTOR_AVAILABLE)
            throw new IllegalArgumentException(
                "Marshalling of non-serializable classes is not available in the current JVM implementation");

        this.requireSer = requireSer;
    }

    /** {@inheritDoc} */
    @Override public void marshal(@Nullable Object obj, OutputStream out) throws GridException {
        assert out != null;

        try {
            GridOptimizedObjectOutput objOut = new GridOptimizedObjectOutput(out, requireSer, name2id);

            objOut.writeObject(obj);

            objOut.delayedWrite();

            objOut.flush();
        }
        catch (IOException e) {
            throw new GridException("Failed to serialize object: " + obj, e);
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"unchecked"})
    @Override public <T> T unmarshal(InputStream in, @Nullable ClassLoader clsLdr) throws GridException {
        assert in != null;

        if (clsLdr == null)
            clsLdr = dfltClsLdr;

        try {
            GridOptimizedObjectInput objIn = new GridOptimizedObjectInput(in, clsLdr, id2name);

            T obj = (T)objIn.readObject();

            objIn.delayedRead();

            return obj;
        }
        catch (IOException e) {
            throw new GridException("Failed to deserialize object with given class loader: " + clsLdr, e);
        }
        catch (ClassNotFoundException e) {
            throw new GridException("Failed to deserialize object with given class loader: " + clsLdr, e);
        }
    }
}
