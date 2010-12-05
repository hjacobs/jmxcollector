package de.jacobs1.jmxcollector;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.rmi.ConnectException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import org.apache.log4j.Logger;
import org.rrd4j.ConsolFun;
import org.rrd4j.DsType;
import org.rrd4j.core.RrdDb;
import org.rrd4j.core.RrdDbPool;
import org.rrd4j.core.RrdDef;
import org.rrd4j.core.Sample;
import org.rrd4j.graph.RrdGraph;
import org.rrd4j.graph.RrdGraphDef;

public class Main {

    private static final Logger LOG = Logger.getLogger(Main.class);

    private static final Map<Connection, MBeanServerConnection> mBeanServerConnections = new HashMap<Connection, MBeanServerConnection>();

    public static void graph(String rrdPath, String dsName, String outPath) throws Exception {
        RrdGraphDef graphDef = new RrdGraphDef();
        graphDef.setTimeSpan(-3600, -1);
        graphDef.setVerticalLabel("req/s");
        graphDef.datasource("req", rrdPath, dsName, ConsolFun.AVERAGE);
        graphDef.line("req", new Color(0xFF, 0, 0), null, 2);
        graphDef.gprint("req", ConsolFun.MIN, "%10.2lf/s MIN");
        graphDef.gprint("req", ConsolFun.AVERAGE, "%10.2lf/s AVG");
        graphDef.gprint("req", ConsolFun.MAX, "%10.2lf/s MAX");
        graphDef.setFilename(outPath);
        //graphDef.setBase(1);
        RrdGraph graph = new RrdGraph(graphDef);
        BufferedImage bi = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        graph.render(bi.getGraphics());
    }

    /* For simplicity, we declare "throws Exception".
    Real programs will usually want finer-grained exception handling. */
    public static void main(final String[] args) throws Exception {

        if (args.length < 1) {
            System.out.println("Usage: jmxcollector <CONFIGFILE>");
            System.out.println("Usage: jmxcollector <JMXURL> <RRDFILE>");
            return;
        }

        if (args.length >= 4 && args[0].equals("graph")) {
            graph(args[1], args[2], args[3]);
            return;
        }

        run(args[0]);
    }

    public static void run(final String configFile) throws FileNotFoundException, IOException, MalformedObjectNameException, MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException {
        final Properties props = new Properties();
        final FileReader fr = new FileReader(configFile);
        props.load(fr);
        fr.close();

        final List<Connection> connections = new ArrayList<Connection>();
        final List<DataSource> datasources = new ArrayList<DataSource>();

        String val;
        int i;

        i = 1;
        while ((val = props.getProperty("connection." + i + ".host")) != null) {
            Connection conn = new Connection();
            conn.setHost(val);
            conn.setPort(props.getProperty("connection." + i + ".port"));
            conn.setUser(props.getProperty("connection." + i + ".user"));
            conn.setPassword(props.getProperty("connection." + i + ".password"));
            connections.add(conn);
            i++;
        }

        i = 1;
        while ((val = props.getProperty("datasource." + i + ".connection")) != null) {
            DataSource ds = new DataSource();
            ds.setConnection(connections.get(Integer.valueOf(val) - 1));
            ds.setBeanName(new ObjectName(props.getProperty("datasource." + i + ".bean")));
            ds.setAttributeName(props.getProperty("datasource." + i + ".attribute"));
            String[] parts = props.getProperty("datasource." + i + ".rrd").split(":", 2);
            ds.setRrdPath(parts[0]);
            ds.setRrdDSName(parts[1]);
            datasources.add(ds);
            createRrdFile(ds.getRrdPath(), ds.getRrdDSName());
            i++;
        }

        LOG.info("Loaded config from " + configFile + " with "
                + connections.size() + " connections and " + datasources.size() + " datasources");


        final RrdDbPool pool = RrdDbPool.getInstance();
        pool.setCapacity(datasources.size() * 2);

        for (DataSource dataSource : datasources) {
            dataSource.setRrdDb(pool.requestRrdDb(dataSource.getRrdPath()));
        }

        final long interval = 2000;
        long lastTime = System.currentTimeMillis();
        i = 0;
        int j;
        MBeanServerConnection mbsc;
        Sample sample;
        while (true) {

            if (i % 100 == 0) {
                LOG.info("Heartbeat " + i);
            }
            j = 1;
            for (DataSource dataSource : datasources) {
                try {
                    mbsc = getMBeanServerConnection(dataSource.getConnection());
                    if (mbsc == null) {
                        continue;
                    }
                    Object attr = null;
                    try {
                        attr = mbsc.getAttribute(dataSource.getBeanName(), dataSource.getAttributeName());
                    } catch (ConnectException ce) {
                        LOG.error("ConnectException while trying to get attribute "
                                + dataSource.getAttributeName() + " from " + dataSource.getBeanName().getCanonicalName(), ce);
                        // remove connection to force reconnect
                        mBeanServerConnections.remove(dataSource.getConnection());
                        continue;
                    } catch (InstanceNotFoundException infe) {
                        LOG.error("InstanceNotFoundException while trying to get attribute "
                                + dataSource.getAttributeName() + " from " + dataSource.getBeanName().getCanonicalName(), infe);
                        continue;
                    }

                    final RrdDb rrd = dataSource.getRrdDb();

                    sample = rrd.createSample();
                    sample.setValue(dataSource.getRrdDSName(), (Integer) attr);
                    try {
                        sample.update();
                    } catch (IllegalArgumentException iae) {
                        LOG.error("Dropping sample of datasource " + j, iae);
                    }
                } catch (Throwable t) {
                    LOG.error("Unknown eror while retrieving data for datasource " + j, t);
                }
                j++;
            }

            sleep(Math.max(1, interval - (System.currentTimeMillis() - lastTime)));
            lastTime = System.currentTimeMillis();
            i++;
        }

    }
    

    private static MBeanServerConnection getMBeanServerConnection(final Connection conn) throws IOException {
        MBeanServerConnection mbsc = mBeanServerConnections.get(conn);
        if (mbsc == null) {

            JMXServiceURL url =
                    new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + conn.getHost() + ":" + conn.getPort() + "/jmxrmi");
            Map<String, Object> env = new HashMap<String, Object>();

            if (conn.getUser() != null) {
                String[] credentials = new String[]{conn.getUser(), conn.getPassword()};
                env.put("jmx.remote.credentials", credentials);
            }


            try {
                LOG.info("Trying to connect to " + conn.getHost() + ":" + conn.getPort());
                JMXConnector jmxc = JMXConnectorFactory.connect(url, env);
                LOG.info("Trying to get an MBeanServerConnection");
                mbsc = jmxc.getMBeanServerConnection();
                mBeanServerConnections.put(conn, mbsc);
            } catch (IOException ioe) {
                LOG.error("Failed to connect to JMX service URL " + url, ioe);
                return null;
            }
        }

        return mbsc;
    }

    private static void createRrdFile(final String path, final String dsName) throws IOException {
        final RrdDbPool pool = RrdDbPool.getInstance();

        if (!(new File(path)).exists()) {
            LOG.info("Creating new RRD file " + path);
            RrdDef def = new RrdDef(path, 2);
            def.addDatasource(dsName, DsType.DERIVE, 90, 0, Double.NaN);
            // 2sec resolution for the last 4 hours
            def.addArchive(ConsolFun.AVERAGE, 0.5, 1, 30 * 60 * 4);
            // 10sec resolution for the last 24 hours
            def.addArchive(ConsolFun.AVERAGE, 0.5, 5, 600 * 24);
            // 1min resolution for the last week
            def.addArchive(ConsolFun.AVERAGE, 0.5, 30, 60 * 24 * 7);
            // 1 hour resolution for the last 365 days
            def.addArchive(ConsolFun.AVERAGE, 0.5, 30 * 60, 24 * 365);
            RrdDb rrd = pool.requestRrdDb(def);
            pool.release(rrd);
        }
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            LOG.error("Sleep was interrupted", e);
        }
    }
}
