# logback-riemann-appender  [![Build Status](https://travis-ci.org/walmartlabs/logback-riemann-appender.svg?branch=master)](https://travis-ci.org/walmartlabs/logback-riemann-appender)

Forked from: https://github.com/walmartlabs/logback-riemann-appender

## Logback appender for [Riemann](http://riemann.io/)

- Sends logging events to Riemann via UDP/TCP
- Supports configurable level-based filtering of log events via the `riemannLogLevel` config property
- Maps attributes of the log event to a Riemann event as follows:

Log Event                       | Riemann Event
------------------------------- | -------------
rendered message                | `:log/message`
`level`                         | added as a custom attribute: `:log/level`
Marker name                     | prefixed with `log/` and added to `:tags`
each key-value pair in the MDC  | added as a custom attribute with key in the `:log` ns
throwableProxy, if it exists    | custom attribute: `:log/stacktrace`, custom attribute: `:log/cause`
name of the logger              | custom attribute: `:log/logger`

:host and :service are set via configuring `logback.xml` as below. You
can also configure `customAttributes` for the logger -- these will be
added to the Riemann event as custom attributes. Each of the keys of
the custom attributes will be in the `log` namespace.

## Usage

Artifacts are available through
[clojars](https://clojars.org/defunkt/logback-riemann-appender) which you can add to your maven repository like so:

```xml
<repository>
  <id>clojars.org</id>
  <url>http://clojars.org/repo</url>
</repository>
```

See `resources/logback.xml` for a full example configuration.

    <configuration scan="true">
      <appender name="R1" class="pl.defunkt.logback.RiemannAppender">
        <serviceName>Test Service</serviceName>
        <riemannHostName>127.0.0.1</riemannHostName>
        <riemannPort>5555</riemannPort>
        <hostName>graphene</hostName>
        <customAttributes>application:test-service,datacenter:us-sw</customAttributes>
		<riemannLogLevel>WARN</riemannLogLevel>
      </appender>
      <root level="DEBUG">
        <appender-ref ref="R1"/>
      </root>
    </configuration>

## Build

Project is under [boot](http://boot-clj.com/) control. To make a jar:

``` shell
boot javac build-jar
```

## License

Copyright © 2013 Kyle Burton

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
