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
    * maxMsgSize.
    * linger.
    * peerPublicKey, the Base64 encoded public key of the end point.
    * privateKeyFile, the path to the private key file, encoded using PKCS#8. Can be overridden by the system property `fr.loghub.logging.zmq.curve.privateKeyFile`.
    * publicKey, the public key, that can be found in the `.pub` file after creation of the key.
    * autoCreate: auto create of the 0MQ curve settings. Can be overridden by the system property `fr.loghub.logging.zmq.curve.autoCreate`.

## GCAppender

Every GC event is logged using the configured level, and the logger used is the concatenation of parent and GC name.

  * Parameters
    * level: the log4j2 log level to be usedn.
    * parent: The prefix name of the GC logger to be used.

## MsgPackLayout

  * Parameters
    * locationInfo: rue of false, it will send or not the log event location (file, line, method), default to false.
    * additionalFields: a list of additional field to unconditionaly add to message.


## Configuring the ZMQ curve key.

For enhanced security, it’s possible to encrypt ZMQ connexion using Curve.

The ZMQ appender can automatically handle creation of the key. The best way is to start once the application with the
following system property set:

- `fr.loghub.logging.zmq.curve.privateKeyFile` indicating the ZMQ file path, the extension is optional.
- `fr.loghub.logging.zmq.curve.autoCreate` set to true to allow the creation of the file.

Once the application is started, 3 files are created.

The first one ends with `.p8` if no extension is given. It contains the private part of the curve key, encoded as a p8
file. The default OID for the key type is 1.3.6.4.1.2, but can be overridden by using the system property fr.loghub.nacl.oid.

The second one ends with `.pub`. It encodes the public part of the curve key in Base64, prefixed by the string 'Curve '.

The third ends with `.zpl`. It encodes the public part of the curve key using the [ZPL format](https://rfc.zeromq.org/spec/4/)
using the hierarchical name `curve.public-key`.

Once it’s done, the path of private key should be defined in the property `privateKeyFile` and the base64-encoded public
key defined in the property `publicKey`.

## Complete Example

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

A example of GC log sent:

```
   {
    "threadId": 9,
    "type": "demort",
    "port": "51629",
    "@timestamp": "2019-06-10T17:10:55.898+0200",
    "level": "FATAL",
    "values": {
      "gcAction": "end of major GC",
      "gcCause": "System.gc()",
      "gcInfo": {
        "duration": 18,
        "memoryUsageBeforeGc": [
          {
            "value": {
              "init": 0,
              "committed": 2228224,
              "used": 2109344,
              "max": 1073741824
            },
            "key": "Compressed Class Space"
          },
          {
            "value": {
              "init": 11010048,
              "committed": 11010048,
              "used": 9656512,
              "max": 11010048
            },
            "key": "PS Survivor Space"
          },
          {
            "value": {
              "init": 179306496,
              "committed": 179306496,
              "used": 81936,
              "max": 2863661056
            },
            "key": "PS Old Gen"
          },
          {
            "value": {
              "init": 0,
              "committed": 17694720,
              "used": 17159016,
              "max": -1
            },
            "key": "Metaspace"
          },
          {
            "value": {
              "init": 67108864,
              "committed": 67108864,
              "used": 0,
              "max": 1409286144
            },
            "key": "PS Eden Space"
          },
          {
            "value": {
              "init": 2555904,
              "committed": 3735552,
              "used": 3697920,
              "max": 251658240
            },
            "key": "Code Cache"
          }
        ],
        "GcThreadCount": 8,
        "startTime": 1094,
        "endTime": 1112,
        "id": 1,
        "memoryUsageAfterGc": [
          {
            "value": {
              "init": 0,
              "committed": 2228224,
              "used": 2109344,
              "max": 1073741824
            },
            "key": "Compressed Class Space"
          },
          {
            "value": {
              "init": 11010048,
              "committed": 11010048,
              "used": 0,
              "max": 11010048
            },
            "key": "PS Survivor Space"
          },
          {
            "value": {
              "init": 179306496,
              "committed": 179306496,
              "used": 9155648,
              "max": 2863661056
            },
            "key": "PS Old Gen"
          },
          {
            "value": {
              "init": 0,
              "committed": 17694720,
              "used": 17159016,
              "max": -1
            },
            "key": "Metaspace"
          },
          {
            "value": {
              "init": 67108864,
              "committed": 67108864,
              "used": 0,
              "max": 1409286144
            },
            "key": "PS Eden Space"
          },
          {
            "value": {
              "init": 2555904,
              "committed": 3735552,
              "used": 3697920,
              "max": 251658240
            },
            "key": "Code Cache"
          }
        ]
      },
      "gcName": "PS MarkSweep"
    },
    "thread": "Service Thread",
    "loggerName": "gc.PS MarkSweep",
    "threadPriority": 9,
    "instant": {
      "nano": 887000000,
      "epochSecond": 1560179455
    }
  }
```
