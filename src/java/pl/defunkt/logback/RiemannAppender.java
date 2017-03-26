package pl.defunkt.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import com.aphyr.riemann.Proto;
import com.aphyr.riemann.client.EventDSL;
import com.aphyr.riemann.client.RiemannClient;
import com.aphyr.riemann.client.SimpleUdpTransport;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public class RiemannAppender<E> extends AppenderBase<E> {
    private final String className = getClass().getSimpleName();
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5555;

    private String serviceName = "*no-service-name*";
    private String hostName = determineHostname();
    private String riemannHostName = determineRiemannHostname();
    private Level riemannLogLevel = Level.ERROR;
    private int riemannPort = DEFAULT_PORT;

    private Map<String, String> customAttributes = new HashMap<String, String>();
    private RiemannClient riemannClient = null;

    private boolean tcp = false;

    private String determineRiemannHostname() {
        return System.getProperty("riemann.hostname", DEFAULT_HOST);
    }

    private String determineHostname() {
        String hostname = System.getProperty("hostname");
        if (hostname != null) {
            return hostname;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public void start() {
        try {
            riemannClient = tcp
                ? RiemannClient.tcp(riemannHostName, riemannPort)
                : new RiemannClient(new SimpleUdpTransport(riemannHostName, riemannPort));

            riemannClient.connect();
            printError("%s.start: connected to %s, using hostname of %s", className, riemannHostName, hostName);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        super.start();
    }

    public void stop() {
        if (riemannClient != null) {
            riemannClient.close();
        }
        super.stop();
    }

    public String toString() {
        return String.format(
            "RiemannAppender{hashCode=%s;serviceName=%s;transport=%s;riemannHostname=%s;riemannPort=%d;hostname=%s}",
            hashCode(),
            serviceName,
            tcp ? "tcp" : "udp",
            riemannHostName,
            riemannPort,
            hostName);
    }

    private void getStackTraceFromEvent(ILoggingEvent logEvent, EventDSL event) {
        IThrowableProxy throwable = logEvent.getThrowableProxy();

        if (throwable != null) {
            StringBuilder trace = new StringBuilder(throwable.getMessage()).append("\n");

            if (throwable.getStackTraceElementProxyArray() != null) {
                for (StackTraceElementProxy elt : throwable.getStackTraceElementProxyArray()) {
                    trace
                        .append("\t")
                        .append(elt.toString())
                        .append("\n");
                }
            }
            event.attribute("log/cause", throwable.getCause().getMessage());
            event.attribute("log/stacktrace", trace.toString());

            Level lvl = logEvent.getLevel();

            if (lvl == Level.ERROR) {
                event.attribute("state", "error");
            } else
            if (lvl == Level.WARN) {
                event.attribute("state", "warning");
            }
        }
    }

    boolean isMinimumLevel(ILoggingEvent logEvent) {
        return logEvent.getLevel().isGreaterOrEqual(riemannLogLevel);
    }

    /**
     * Sends the event and waits for the ack. If not ok, throws an IOException with the
     * server-reported error.
     */
    private final String send(EventDSL event) {
        try {
            Proto.Msg msg = event.send().deref();
            return msg.getOk() ? null : msg.getError();
        } catch(IOException ex) {
            return ex.getMessage();
        }
    }

    private String asString(ILoggingEvent logEvent) {
        Map<String, String> mdc = logEvent.getMDCPropertyMap();
        StringBuilder mdcContents = new StringBuilder();
        for (String key : mdc.keySet()) {
            mdcContents.append(String.format(", %s:%s", key, mdc.get(key)));
        }
        return String.format("{level:%s, message:%s, logger:%s, thread:%s%s}",
            logEvent.getLevel().toString(),
            logEvent.getMessage(),
            logEvent.getLoggerName(),
            logEvent.getThreadName(),
            mdcContents.toString());
    }

    protected void append(E event) {
        ILoggingEvent logEvent = (ILoggingEvent) event;

        if (isMinimumLevel(logEvent)) {
            EventDSL rEvent = createRiemannEvent(logEvent);
            String result = null;

            if (send(rEvent) != null) {
                try {
                    riemannClient.reconnect();
                    result = rEvent.send().deref().getError();
                } catch(IOException ex) {
                    result = ex.getMessage();
                }
            }
            if (result != null) {
                printError("%s.append: Error during append(): %s", className, result);
            }
        }
    }

    private EventDSL createRiemannEvent(ILoggingEvent logEvent) {
        EventDSL event = riemannClient.event()
            .host(hostName)
            // timestamp is expressed in millis,
            // `time` is expressed in seconds
            .time(logEvent.getTimeStamp() / 1000)
            .description(logEvent.getMessage())
            .attribute("log/level", logEvent.getLevel().levelStr)
            .attribute("log/logger", logEvent.getLoggerName())
            .attribute("log/thread", logEvent.getThreadName())
            .attribute("log/message", logEvent.getMessage())
            .attribute("service", serviceName);

        if (logEvent.getThrowableProxy() != null) {
            getStackTraceFromEvent(logEvent, event);
        }
        if (logEvent.getMarker() != null) {
            event.tag("log/" + logEvent.getMarker().getName());
        }

        copyAttributes(event, logEvent.getMDCPropertyMap());
        copyAttributes(event, customAttributes);

        return event;
    }

    /**
     * Copy attributes out of the source and add them to `target`,
     * making sure to prefix the keys with `log/` -- this puts the
     * keywords in that namespace, preventing any collisions with the
     * Riemann schema.
     */
    private void copyAttributes(EventDSL target, Map<String, String> source) {
        for (String key : source.keySet()) {
            target.attribute("log/" + key, source.get(key));
        }
    }

    private void printError(String format, Object... params) {
        System.err.println(String.format(format, params));
    }

    public void setServiceName(String s) {
        serviceName = s;
    }

    public void setRiemannHostName(String s) {
        riemannHostName = s;
    }

    public void setRiemannPort(int i) {
        riemannPort = i;
    }

    public void setHostName(String s) {
        hostName = s;
    }

    public void setRiemannLogLevel(String s) {
        riemannLogLevel = Level.toLevel(s);
    }

    public void setCustomAttributes(String s) {
        customAttributes.putAll(parseCustomAttributes(s));
    }

    Map<String, String> parseCustomAttributes(String attributesString) {
        HashMap<String, String> result = new HashMap<String, String>();
        try {
            for (String kvPair : attributesString.split(",")) {
                String[] splitKvPair = kvPair.split(":");
                result.put(splitKvPair[0], splitKvPair[1]);
            }
        } catch (Throwable t) {
            printError("Encountered error while parsing attribute string: %s", attributesString);
        }
        return result;
    }

    /**
     * Set to true to enable TCP requests to the Riemann server.  The default, false, is to
     * use UDP.
     */
    public void setTcp(boolean b) {
        tcp = b;
    }
}
