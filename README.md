JMX Collector
=============

Collects JMX information from various JMX datasources into RRD files.

Requirements
------------

JMX Collector saves collected data into RRD files written by the excellent rrd4j Java library.
You need to download rrd4j from <http://code.google.com/p/rrd4j/> and install it into your local Maven repository.


Configuration
-------------

The easiest way to configure it is using a custom `config.cfg` file, see example below:

    jmxusr:myjmxpwd@web01:48080
        /data/rrd/%h/p%4p/requests.rrd:requests = "Catalina:type=GlobalRequestProcessor,name=ajp-2%4p".requestCount
        /data/rrd/%h/p%4p/logins.rrd:logins = "MyJMXService:name=Customer Service".LoginCount

The above sample configuration would collect data from server `web01` (with JMX port at 48080) 
using JMX user `jmxusr` and password `myjmxpwd`:

* Tomcat AJP requests per second into `/data/rrd/web01/p8080/requests.rrd`
* Number of logins per second (custom MBean) into `/data/rrd/web01/p8080/logins.rrd`

Another example configuration:

    # Collecting requests/s from 3 different tomcat instances all running on host soap02
    # the tomcat instances listen on HTTP port 38080, 38081 and 38082
    jmxusr:myjmxpwd@soap02:48080
        /data/rrd/%h/p%4p/requests.rrd:requests = "Catalina:type=GlobalRequestProcessor,name=http-3%4p".requestCount
    jmxusr:myjmxpwd@soap02:48081
        /data/rrd/%h/p%4p/requests.rrd:requests = "Catalina:type=GlobalRequestProcessor,name=http-3%4p".requestCount
    jmxusr:myjmxpwd@soap02:48082
        /data/rrd/%h/p%4p/requests.rrd:requests = "Catalina:type=GlobalRequestProcessor,name=http-3%4p".requestCount



Running
-------

Building with maven and starting JMX Collector using configuration file `config.cfg`:

    mvn package && java -jar target/jmxcollector-1.0-SNAPSHOT-jar-with-dependencies.jar config.cfg


