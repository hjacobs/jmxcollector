package de.jacobs1.jmxcollector;

import javax.management.ObjectName;

import org.rrd4j.DsType;

import org.rrd4j.core.RrdDb;

/**
 * @author  henning
 */
public class DataSource {

    private Connection connection;
    private ObjectName beanName;
    private String attributeName;
    private String rrdPath;
    private String rrdDSName;
    private DsType rrdDSType;

    private RrdDb rrdDb;

    public String getAttributeName() {
        return attributeName;
    }

    public void setAttributeName(final String attributeName) {
        this.attributeName = attributeName;
    }

    public ObjectName getBeanName() {
        return beanName;
    }

    public void setBeanName(final ObjectName beanName) {
        this.beanName = beanName;
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(final Connection connection) {
        this.connection = connection;
    }

    public String getRrdPath() {
        return rrdPath;
    }

    public void setRrdPath(final String rrdPath) {
        this.rrdPath = rrdPath;
    }

    public String getRrdDSName() {
        return rrdDSName;
    }

    public void setRrdDSName(final String rrdDSName) {
        this.rrdDSName = rrdDSName;
    }

    public RrdDb getRrdDb() {
        return rrdDb;
    }

    public void setRrdDb(final RrdDb rrdDb) {
        this.rrdDb = rrdDb;
    }

    public DsType getRrdDSType() {
        return rrdDSType;
    }

    public void setRrdDSType(final DsType rrdDSType) {
        this.rrdDSType = rrdDSType;
    }

}
