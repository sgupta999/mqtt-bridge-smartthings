# MQTT Bridge to SmartThings [MBS]
***Broker between Smartthings and MQTT Broker.***

[![GitHub Tag](https://img.shields.io/github/tag/sgupta999/mqtt-bridge-smartthings.svg)](https://github.com/sgupta999/mqtt-bridge-smartthings/releases)

This is an upate from the [smartthings-mqtt-broker](https://github.com/stjohnjohnson/smartthings-mqtt-bridge) orginally developed by St. John Johnson.  (https://github.com/stjohnjohnson/smartthings-mqtt-bridge). I have borrowed with abandon from his work, both in compiling this readme; and the server/client codeset, so a big thank you to him.

The primary motivation was having a pure MQTT solution for Tasmota devices in Smartthings. Unfortunately, the old solution was more targeted towards HASS-IO integration. Most of the architecture and basic concepts remain the same but significant refactoring and changes have been made.

# Architecture

![Architecture](https://www.websequencediagrams.com/cgi-bin/cdraw?lz=dGl0bGUgU21hcnRUaGluZ3MgPC0-IE1RVFQgCgpwYXJ0aWNpcGFudCBaV2F2ZSBMaWdodAoKAAcGTW90aW9uIERldGVjdG9yLT5TVCBIdWI6ABEIRXZlbnQgKFotV2F2ZSkKABgGACEFTVFUVEJyaWRnZSBBcHA6IERldmljZSBDaGFuZ2UAMAhHcm9vdnkAMwUAIg4AMxAAOAY6IE1lc3NhADYKSlNPTgAuEABjBi0-AHYLU2VyADkGAHAVUkVTVCkKAB0SAD0GIEJyb2tlcgCBaQk9IHRydWUgKE1RVFQpCgAyBQAcBwBdFgCCSgUgPSAib24iAC4IAFgUAIFaFgCBFhsAgWAWAIJnEwCCESMAgmoIAINWBVR1cm4AgTAHT24AgxcNAINXBQCEGwsAgVYIT24Ag3oJ&s=default)

# Compatibility
The new server should be fully backward compatible. If you have been using the old library you should still be able to use and avail of all the new functionality.

# Updates
1. An external devices YAML config file has been introduced. It allows to define any custom mapping between and smartthings [device][attribute][command] and MQTT [topic][payload] and vice-versa.
2. A device can subscribe and publish to any number of topics
3. MQTT wildcards can be used in subscribe topics
4. Within smartthings. smartapp and device handlers have a generic processMQTT method to process subscription messages. Smartthings attribute 'events' are use to pubish messages to MQTT broker
5. The logging and configurations have beem significantly streamlined. 'Log' and 'Data' directories store logs and state information
6. All dependencies have been updated to the latest versions.
7. The use case tested was for primarily Tasmota devices. I have a lite and full version of the SmartApp and sample tasmota Device Type Handlers. Use the 'lite' version if you are primary interested in configuring devices using the external device config file and primarily using Tasmota device handlers provided with this package. 
8. The Tasmota Device Type Handlers periodically update other information from device like SSID, RSSI, LWT etc. If you use the SmartThings virtual switches or contact sensors they will just process the commands


# MQTT Events Flow
While the original flow is preserved as is - a new flow has been introduced to make the bridge more flexible. Please read the original readme on the previous project for the original flow. The new flow is as follows:

1. Smartthings events are generated from attributes - both standard and custom. You may choose to define custom attriutes for handling functionality beyond standard attributes - e.g combo devices or more functional tiles
2. Within the 'MBS - SmartApp' you describe a capability map and specify which attribute(s) (and corresponding event(s)), the SmartApp will subscribe to.
3. For every event Smartthings generates a [device][attribute][command] event package that the SmartApp is subscribed to and sends it to the mbs-server.
4. The devices.yml config file maintains a mapping of [device][attribute][command] to the MQTT [topic][payload] to be published.
5. Once the server receives the event from Smartthings, if the device is in the config file it uses the mapping to publish to MQTT broker. If the device is not in the device config file it assumes the standard old flow and publishes a message {PREFACE}/{DEVICE_NAME}/${ATTRIBUTE}/SUFFIX corresponding to the old flow.
6. On the flip side, when the server receives an MQTT message from the broker, it checks the device config file for mapping of [topic][payload] to [device][attribute][command] and sends the command back to Smartthings. If device is not found it follows the old flow.
7. Smartthings MBS-SmartApp maintains  mappings for each capability of what attribute[event] to subscribe (for publishing to MQTT Broker) and what action method to call for an event received form the MQTT broker via the MBS-Server. See [_smartapp.example.md](https://github.com/sgupta999/mqtt-bridge-smartthings/blob/master/_smartapp.example)

# [SmartApp example](https://github.com/sgupta999/mqtt-bridge-smartthings/blob/master/_smartapp.example). 
Please read this to ensure appropriate configuration.
The MBS-SmartApp controls the mappings between the Devices and the Server config. A lot of flexibility for advanced configuration has been built in, but it can also be used without any configuration for the basic switches and sensors.

# Configuration

The bridge has two yaml files for configuration:

[config.yml](https://github.com/sgupta999/mqtt-bridge-smartthings/blob/master/config.yml)
==========
```
---
# Port number to listen on
port: 8080

#Default (info) - error, warn, info, verbose, debug, silly
loglevel: "info"
        
#is there an external device config file: true, false
deviceconfig: true

mqtt:
    # Specify your MQTT Broker URL here
    host: mqtt://localhost
    # Example from CloudMQTT
    # host: mqtt:///m10.cloudmqtt.com:19427

    # Preface for the topics $PREFACE/$DEVICE_NAME/$PROPERTY
    preface: smartthings

    # The write and read suffixes need to be different to be able to differentiate when state comes from SmartThings or when its coming from the physical device/application

    # Suffix for the topics that receive state from SmartThings $PREFACE/$DEVICE_NAME/$PROPERTY/$STATE_READ_SUFFIX
    # Your physical device or application should subscribe to this topic to get updated status from SmartThings
    # state_read_suffix: state

    # Suffix for the topics to send state back to SmartThings $PREFACE/$DEVICE_NAME/$PROPERTY/$STATE_WRITE_SUFFIX
    # your physical device or application should write to this topic to update the state of SmartThings devices that support setStatus
    # state_write_suffix: set_state

    # Suffix for the command topics $PREFACE/$DEVICE_NAME/$PROPERTY/$COMMAND_SUFFIX
    # command_suffix: cmd

    # Other optional settings from https://www.npmjs.com/package/mqtt#mqttclientstreambuilder-options
    # username: AzureDiamond
    # password: hunter2

    # MQTT retains state changes be default, retain mode can be disabled:
    # retain: false


```
[devices.yml](https://github.com/sgupta999/mqtt-bridge-smartthings/blob/master/_devices.example.yml)
===========
```
---
# Look for actual scenarios at the end without comments
# Complete example of complex device setup with multiple subscriptions and commands
Living Room Light:
# device name - make sure it is exactly the same as in smartthings
  attribute: switch
  # REQUIRED: mapped to an actual attribute of device [e.g. switch, contact or any custom attribute
  # this attribute is specified in the capability map section of the mbs-smartapp
  # an attribute is required for each topic subbscription
      subscribe:
      # topic details to which smartthings will be subscribed
      # (topic, payload) from MQTT will be transformed to (device, attribute, payload*) to smartthings
        smartthings/stat/sonoff-1/POWER:
        # OPTIONAL: subscribe to this topic, for tasmota you really need it to get status updates for third party on/off
          command:
          # OPTIONAL: Translate payload coming from MQTT to this new payload* send to smartthings
          # For e.g. here OFF command published from MQTT will be sent as off (lowercase) to smartthings
          # if not set payload from MQTT is sent as is
            'OFF': 'off'
            'ON': 'on'
        smartthings/stat/sonoff-1/STATUS:
        # You can subscribe to as many topics 
        smartthings/stat/sonoff-1/STATUS2:
        smartthings/stat/sonoff-1/STATUS5:
        smartthings/stat/sonoff-1/STATUS11:
  publish:
  # OPTIONAL: commands (device, attribute, payload) from smartthings is send to MQTT as (topic, payload*)
    switch:
    #REQUIRED: attribute specified in the capability map section of the mbs-smartapp
      topic: smartthings/cmnd/sonoff-1/POWER
      # REQUIRED: topic to be published to MQTT
      command:
      # REQUIRED: transforming payload from smartthings to the one sent to MQTT and physical device
        'off': 'OFF'
        'on': 'ON'
    update:
      topic: smartthings/cmnd/sonoff-1/Backlog
      command:
      # tasmota specific example of using Backlog to send multiple simultaneous commands to physical device
        refresh: Status; Status 2; Status 5; Status 11
  retain: 'false'
  # false set as default and here
```

# Installation

## NPM

To install the module, just use `npm`:
```
$ npm install -g mqtt-bridge-smartthings
```

If you want to run it, you can simply call the binary:
```
$ smartthings-mqtt-bridge
Starting SmartThings MQTT Bridge - v1.0.1
Loading configuration
No previous configuration found, creating one
```

If you are interested in running it on windows as a server the windows service directory has instructions and sample .ini file and .bat file with commands.

## Usage
1. Customize the MQTT host and devices
    ```
    $ vi config.yml
     $ vi devices.yml
    # Restart the service to get the latest changes
    ```

2. Install the [MBS-Bridge Device Handler](https://github.com/sgupta999/mqtt-bridge-smartthings/blob/master/devicetypes/gupta/mbs-bridge.src/mbs-bridge.groovy) in the [Device Handler IDE][ide-dt] using "Create via code"
3. Add the "MQTT Bridge" device in the [My Devices IDE][ide-mydev]. Enter MQTT Bridge (or whatever) for the name. Select "MBS Bridge" for the type. 
4. Configure the "MQTT Bridge" in the [My Devices IDE][ide-mydev] with the IP Address, Port, and MAC Address of the machine running the mbs-server container
4. Install the [MBS SmartApp Lite](https://github.com/sgupta999/mqtt-bridge-smartthings/blob/master/smartapps/gupta/mbs-smartapp.src/mbs-smartapp-lite.groovy) or [MBS SmartApp Full](https://github.com/sgupta999/mqtt-bridge-smartthings/blob/master/smartapps/gupta/mbs-smartapp.src/mbs-smartapp-full.groovy)on the [Smart App IDE][ide-app] using "Create via code"
5. If using a Tasmota device install the [Tasmota SwitchSensor] or any other Tamota device from the [Tasmota Device Type] folder.
5. Configure the Smart App (via the Native App) with the devices you want to subscribe to and the bridge that you just installed
6. Via the Native App, select your MQTT device and watch as device is populated with events from your devices



