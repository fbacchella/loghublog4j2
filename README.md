Log4J2 Tools for loghub
===========

This module provides different plugins for log4J2 that can improve logging.

First there is a [msgpack](https://msgpack.org) layout, a format much more efficient that JSON, XML or YAML. It don't use jackson to be lightweight.

There is a [ØMQ](http://zeromq.org) appender that should be better than the provided JeroMQ appender of log4j2, as it allows more choice for endpoints.

There is a pseudo appender, that put a listener on GC JMX events and log them.

## ZMQAppender

  * Parameters
    * endoint: the endpoint URL, like `tcp://localhost:2120`, mandatory.
    * type: the socket type, either `PUB` or `PUSH`, default to `PUB`.
    * method: the socket connection method, either `connect` or `bind`, default to `connect`.
    * hwm: the HWM for the socket, default to 1000.
    * maxMsgSize
    * linger

## GCAppender

Every GC event is logged using the configured level, and the the logger used is the concatenation of parent and GC name.

  * Parameters
    * level: the log4j2 log level to be usedn.
    * parent: The prefix name of the GC logger to be used.

## MsgPackLayout

  * Parameters
    * locationInfo: rue of false, it will send or not the log event location (file, line, method), default to false.
    * additionalFields: a list of additional field to unconditionaly add to message.

## Complete Exemple

In this exemple, the listening port of a ZMQ handler is defined using the java property `fr.loghub.log4j2.zmq.port`

```
<?xml version="1.0" encoding="UTF-8"?>
<Configuration packages="fr.loghub.log4j2">
    <Appenders>
        <ZMQ name="ZMQAppender">
            <endpoint>tcp://localhost:${sys:fr.loghub.log4j2.zmq.portt}</endpoint>
            <type>push</type>
            <method>connect</method>
            <hwm>1000</hwm>
            <properties>true</properties>
            <MsgPackLayout>
                <AdditionalField name="type">demo</AdditionalField>
                <AdditionalField name="port">${sys:fr.loghub.log4j2.test.port}</AdditionalField>
            </MsgPackLayout>
        </ZMQ>
        <GCAppender name="GCAppender">
            <level>FATAL</level>
        </GCAppender>
    </Appenders>
    <Loggers>
        <Root level="DEBUG">
            <AppenderRef ref="ZMQAppender" />
        </Root>
    </Loggers>
</Configuration>
```
