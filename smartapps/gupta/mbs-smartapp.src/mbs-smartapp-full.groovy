/**
 *  An MQTT bridge to SmartThings [MBS-SmartApp-Full] - SmartThings SmartApp (Full Version)
 *
 *  Authors
 *	 - sandeep gupta
 *  Derived from work of previous authors
 *   - st.john.johnson@gmail.com
 *   - jeremiah.wuenschel@gmail.com
 *
 *	A lot of initial work was done by the previous two authors
 *	There is  significant refactoring and added functionality since Oct 2019.
 *
 *  Copyright 2019
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.transform.Field

// FULL lookup tree
// Every device in mbs-server device config file should have one of these  defined or else
// They will not interact with SmartThings
@Field CAPABILITY_MAP = [
	// My custom device type
    "tasmotaSwitches": [
		// filter name used on input screen
        name: "Tasmota Switch",
		// only one capability per device type to filter devices on input screen
        capability: "capability.switch",
        attributes: [
			// any number of actual attributes used by devices filtered by capability above.
			// if attribute for device does not exist, command/update structure for that attribute for that device 
			// will not work.
			"switch",
			"update"
        ],
		// When an event is received from the server, control will be passed to device if an action is defined here
		// If action is just single string only, that single action method will be invoked  for all events received 
		// from the server for all attributes
		// If action is defined as a Map like here, specific action method will be called for events received from server 
		// for the specified attribute. If an attribute is not mapped to an action command in this map no action will be 
		// taken on event received from server.
        action: [
			switch: "actionOnOff",
			// in my custom handlers I am using 'update' as a catch-all attribute, and actionProcessMQTT as a catch-all action
			// command. All logic about how these specific commands are generated from SmartThings or events are handled from
			// server are handle by the Device Handler 
			update: "actionProcessMQTT"
		]
    ],
    "tasmotaSensor": [
        name: "Tasmota Contact Sensor",
        capability: "capability.contactSensor",
        attributes: [
			"contact",
			"update"
        ],
        action: "actionProcessMQTT"
    ],
	// My custom MQTT device - non-tasmota, should not apply to any use case but given as an example here
    "customPowerMeters": [
        name: "Custom Power Meter",
        capability: "capability.powerMeter",
        attributes: [
            "demand",
			"mqttmsg"
        ],
        action: "actionProcessMQTT"
    ],
    "accelerationSensors": [
        name: "Acceleration Sensor",
        capability: "capability.accelerationSensor",
        attributes: [
            "acceleration"
        ]
    ],
    "alarm": [
        name: "Alarm",
        capability: "capability.alarm",
        attributes: [
            "alarm"
        ],
        action: "actionAlarm"
    ],
    "battery": [
        name: "Battery",
        capability: "capability.battery",
        attributes: [
            "battery"
        ]
    ],
    "beacon": [
        name: "Beacon",
        capability: "capability.beacon",
        attributes: [
            "presence"
        ]
    ],
    "button": [
        name: "Button",
        capability: "capability.button",
        attributes: [
            "button"
        ]
    ],
    "carbonDioxideMeasurement": [
        name: "Carbon Dioxide Measurement",
        capability: "capability.carbonDioxideMeasurement",
        attributes: [
            "carbonDioxide"
        ]
    ],
    "carbonMonoxideDetector": [
        name: "Carbon Monoxide Detector",
        capability: "capability.carbonMonoxideDetector",
        attributes: [
            "carbonMonoxide"
        ]
    ],
    "colorControl": [
        name: "Color Control",
        capability: "capability.colorControl",
        attributes: [
            "hue",
            "saturation",
            "color"
        ],
        action: "actionColor"
    ],
    "colorTemperature": [
        name: "Color Temperature",
        capability: "capability.colorTemperature",
        attributes: [
            "colorTemperature"
        ],
        action: "actionColorTemperature"
    ],
    "consumable": [
        name: "Consumable",
        capability: "capability.consumable",
        attributes: [
            "consumable"
        ],
        action: "actionConsumable"
    ],
    "contactSensors": [
        name: "Contact Sensor",
        capability: "capability.contactSensor",
        attributes: [
            "contact"
        ]
    ],
    "doorControl": [
        name: "Door Control",
        capability: "capability.doorControl",
        attributes: [
            "door"
        ],
        action: "actionOpenClosed"
    ],
    "energyMeter": [
        name: "Energy Meter",
        capability: "capability.energyMeter",
        attributes: [
            "energy"
        ]
    ],
    "garageDoors": [
        name: "Garage Door Control",
        capability: "capability.garageDoorControl",
        attributes: [
            "door"
        ],
        action: "actionOpenClosed"
    ],
    "illuminanceMeasurement": [
        name: "Illuminance Measurement",
        capability: "capability.illuminanceMeasurement",
        attributes: [
            "illuminance"
        ]
    ],
    "imageCapture": [
        name: "Image Capture",
        capability: "capability.imageCapture",
        attributes: [
            "image"
        ]
    ],
    "levels": [
        name: "Switch Level",
        capability: "capability.switchLevel",
        attributes: [
            "level"
        ],
        action: "actionLevel"
    ],
    "lock": [
        name: "Lock",
        capability: "capability.lock",
        attributes: [
            "lock"
        ],
        action: "actionLock"
    ],
    "mediaController": [
        name: "Media Controller",
        capability: "capability.mediaController",
        attributes: [
            "activities",
            "currentActivity"
        ]
    ],
    "motionSensors": [
        name: "Motion Sensor",
        capability: "capability.motionSensor",
        attributes: [
            "motion"
        ],
        action: "actionActiveInactive"
    ],
    "musicPlayer": [
        name: "Music Player",
        capability: "capability.musicPlayer",
        attributes: [
            "status",
            "level",
            "trackDescription",
            "trackData",
            "mute"
        ],
        action: "actionMusicPlayer"
    ],
    "pHMeasurement": [
        name: "pH Measurement",
        capability: "capability.pHMeasurement",
        attributes: [
            "pH"
        ]
    ],
    "powerMeters": [
        name: "Power Meter",
        capability: "capability.powerMeter",
        attributes: [
            "power"
        ]
    ],
    "presenceSensors": [
        name: "Presence Sensor",
        capability: "capability.presenceSensor",
        attributes: [
            "presence"
        ],
        action: "actionPresence"
    ],
    "humiditySensors": [
        name: "Relative Humidity Measurement",
        capability: "capability.relativeHumidityMeasurement",
        attributes: [
            "humidity"
        ]
    ],
    "relaySwitch": [
        name: "Relay Switch",
        capability: "capability.relaySwitch",
        attributes: [
            "switch"
        ],
        action: "actionOnOff"
    ],
    "shockSensor": [
        name: "Shock Sensor",
        capability: "capability.shockSensor",
        attributes: [
            "shock"
        ]
    ],
    "signalStrength": [
        name: "Signal Strength",
        capability: "capability.signalStrength",
        attributes: [
            "lqi",
            "rssi"
        ]
    ],
    "sleepSensor": [
        name: "Sleep Sensor",
        capability: "capability.sleepSensor",
        attributes: [
            "sleeping"
        ]
    ],
    "smokeDetector": [
        name: "Smoke Detector",
        capability: "capability.smokeDetector",
        attributes: [
            "smoke"
        ]
    ],
    "soundSensor": [
        name: "Sound Sensor",
        capability: "capability.soundSensor",
        attributes: [
            "sound"
        ]
    ],
    "stepSensor": [
        name: "Step Sensor",
        capability: "capability.stepSensor",
        attributes: [
            "steps",
            "goal"
        ]
    ],
	// These could be standardized Smartthings virtual switches or any other device that has MQTT functionality implemented
    "switches": [
        name: "Switch",
        capability: "capability.switch",
        attributes: [
            "switch"
        ],
        action: "actionOnOff"
    ],
    "soundPressureLevel": [
        name: "Sound Pressure Level",
        capability: "capability.soundPressureLevel",
        attributes: [
            "soundPressureLevel"
        ]
    ],
    "tamperAlert": [
        name: "Tamper Alert",
        capability: "capability.tamperAlert",
        attributes: [
            "tamper"
        ]
    ],
    "temperatureSensors": [
        name: "Temperature Measurement",
        capability: "capability.temperatureMeasurement",
        attributes: [
            "temperature"
        ]
    ],
    "thermostat": [
        name: "Thermostat",
        capability: "capability.thermostat",
        attributes: [
            "temperature",
            "heatingSetpoint",
            "coolingSetpoint",
            "thermostatSetpoint",
            "thermostatMode",
            "thermostatFanMode",
            "thermostatOperatingState"
        ],
        action: "actionThermostat"
    ],
    "thermostatCoolingSetpoint": [
        name: "Thermostat Cooling Setpoint",
        capability: "capability.thermostatCoolingSetpoint",
        attributes: [
            "coolingSetpoint"
        ],
        action: "actionCoolingThermostat"
    ],
    "thermostatFanMode": [
        name: "Thermostat Fan Mode",
        capability: "capability.thermostatFanMode",
        attributes: [
            "thermostatFanMode"
        ],
        action: "actionThermostatFan"
    ],
    "thermostatHeatingSetpoint": [
        name: "Thermostat Heating Setpoint",
        capability: "capability.thermostatHeatingSetpoint",
        attributes: [
            "heatingSetpoint"
        ],
        action: "actionHeatingThermostat"
    ],
    "thermostatMode": [
        name: "Thermostat Mode",
        capability: "capability.thermostatMode",
        attributes: [
            "thermostatMode"
        ],
        action: "actionThermostatMode"
    ],
    "thermostatOperatingState": [
        name: "Thermostat Operating State",
        capability: "capability.thermostatOperatingState",
        attributes: [
            "thermostatOperatingState"
        ]
    ],
    "thermostatSetpoint": [
        name: "Thermostat Setpoint",
        capability: "capability.thermostatSetpoint",
        attributes: [
            "thermostatSetpoint"
        ]
    ],
    "threeAxis": [
        name: "Three Axis",
        capability: "capability.threeAxis",
        attributes: [
            "threeAxis"
        ]
    ],
    "timedSession": [
        name: "Timed Session",
        capability: "capability.timedSession",
        attributes: [
            "timeRemaining",
            "sessionStatus"
        ],
        action: "actionTimedSession"
    ],
    "touchSensor": [
        name: "Touch Sensor",
        capability: "capability.touchSensor",
        attributes: [
            "touch"
        ]
    ],
    "valve": [
        name: "Valve",
        capability: "capability.valve",
        attributes: [
            "contact"
        ],
        action: "actionOpenClosed"
    ],
    "voltageMeasurement": [
        name: "Voltage Measurement",
        capability: "capability.voltageMeasurement",
        attributes: [
            "voltage"
        ]
    ],
    "waterSensors": [
        name: "Water Sensor",
        capability: "capability.waterSensor",
        attributes: [
            "water"
        ]
    ],
    "windowShades": [
        name: "Window Shade",
        capability: "capability.windowShade",
        attributes: [
            "windowShade"
        ],
        action: "actionOpenClosed"
    ]
]

definition(
    name: "MBS SmartApp Full",
    namespace: "gupta",
    author: "Sandeep Gupta",
    description: "An MQTT bridge to SmartThings [MBS-SmartApp-Full]",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Connections/Cat-Connections.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Connections/Cat-Connections@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Connections/Cat-Connections@3x.png"
)

preferences {
    section("Send Notifications?") {
        input("recipients", "contact", title: "Send notifications to", multiple: true, required: false)
    }

    section ("Input") {
        CAPABILITY_MAP.each { key, capability ->
            input key, capability["capability"], title: capability["name"], multiple: true, required: false
        }
    }

    section ("Bridge") {
        input "bridge", "capability.notification", title: "Notify this Bridge", required: true, multiple: false
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    runEvery30Minutes(initialize)
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    // Unsubscribe from all events
    unsubscribe()
    // Subscribe to stuff
    initialize()
}

def initialize() {
	state.events = [:];
    // Subscribe to new events from devices	
    CAPABILITY_MAP.each { key, capability ->
        capability["attributes"].each { attribute ->
			if (settings[key] != null){		
				subscribe(settings[key], attribute, inputHandler)		
				log.debug "Subscribed to event ${attribute} on device ${settings[key]}"
				// Create a last event hashmap for each device and attribute so duplicate events and looping can be eliminated
				settings[key].each {device ->
					state.events[device.displayName] = [:];
					state.events[device.displayName][attribute]=null;		
					//log.debug "creating json ${attribute} on device ${device.displayName}"
				}
			}
        }
    }
    // Subscribe to events from the bridge
    subscribe(bridge, "message", bridgeHandler)

    // Update the bridge
    updateSubscription()
}

// Update the bridge"s subscription
def updateSubscription() {
    def attributes = [
        notify: ["Contacts", "System"]
    ]
    CAPABILITY_MAP.each { key, capability ->
        capability["attributes"].each { attribute ->
            if (!attributes.containsKey(attribute)) {
                attributes[attribute] = []
            }
            settings[key].each {device ->
                attributes[attribute].push(device.displayName)
            }
        }
    }
    def json = new groovy.json.JsonOutput().toJson([
        path: "/subscribe",
        body: [
            devices: attributes
        ]
    ])

    log.debug "Updating subscription: ${json}"

    bridge.deviceNotification(json)
}

// Receive an event from the bridge
def bridgeHandler(evt) {
    def json = new JsonSlurper().parseText(evt.value)
    log.debug "Received device event from bridge: ${json}"

    if (json.type == "notify") {
        if (json.name == "Contacts") {
            sendNotificationToContacts("${json.value}", recipients)
        } else {
            sendNotificationEvent("${json.value}")
        }
        return
    }
	
    // @NOTE this is stored AWFUL, we need a faster lookup table
    // @NOTE this also has no fast fail, I need to look into how to do that
    CAPABILITY_MAP.each { key, capability ->
        if (capability["attributes"].contains(json.type)) {
            settings[key].each {device ->
                if (device.displayName == json.name) {
                    if (json.command == false) {
                        if (device.getSupportedCommands().any {it.name == "setStatus"}) {
                            log.debug "Setting state ${json.type} = ${json.value}"
                            device.setStatus(json.type, json.value)
                            state.ignoreEvent = json;
							return;
                        }
                    }
                    else {
                        if (capability.containsKey("action")) { 					
							if (!eventCheck(device.displayName,json.type, json.value)) {
								log.debug "Duplicate of last event, ignoring 'mbs-server' event '${json.value}' on attribute '${json.type}' for device '${device.displayName}'"	
								return;
							} 
                            def action = capability["action"]		
							if (action instanceof String){
								log.debug "Calling action method ${action}, for attribute ${json.type}, for device ${device} with payload ${json.value}"
								"$action"(device, json.type, json.value);								
							} else if (action.containsKey(json.type)){
								action = action[json.type];
								log.debug "Calling action method ${action}, for attribute ${json.type}, for device ${device} with payload ${json.value}"
								"$action"(device, json.type, json.value);
							}
							return;
                        }
                    }
                }
            }
        } else {
			// If server sends an event even if attribute is not defined in capability map, we will look for a COMMAND with same type and invoke that device command
			settings[key].each {device ->
				if ((device.displayName == json.name) && (json.type != null) && (json.value != null)) {						
					if (!eventCheck(device.displayName,json.type, json.value)) {
						log.debug "Duplicate of last event, ignoring 'mbs-server' event '${json.value}' on attribute '${json.type}' for device '${device.displayName}'"	
						return;	
					} 
					def command = json.type;
					if (device.getSupportedCommands().any {it.name == command}) {
						log.debug "Setting state for device ${json.name} ${command} = ${json.value}"
						device."$command"(json.value);
						return;
					}
				}
			}		
		}
    }	
}

// Receive an event from a device
def inputHandler(evt) {
	log.debug "Received event ${evt.value} on attribute ${evt.name} for device  ${evt.displayName} for BRIDGE "
	// This is legacy ignoring duplicate event
    if (
        state.ignoreEvent
        && state.ignoreEvent.name == evt.displayName
        && state.ignoreEvent.type == evt.name
        && state.ignoreEvent.value == evt.value
    ) {
        log.debug "Ignoring event ${state.ignoreEvent}"
        state.ignoreEvent = false;
    }else if (!eventCheck(evt.displayName,evt.name, evt.value)) {
		// Here we will ignore event from device if the last payload for the same event is the same as this one.
        log.debug "Duplicate of last event from device '${evt.displayName}', ignoring event '${evt.value}' on attribute '${evt.name}' for device '${evt.displayName}'"		
		return;
	} else {
        def json = new JsonOutput().toJson([
            path: "/push",
            body: [
                name: evt.displayName,
                value: evt.value,
                type: evt.name
            ]
        ])

        log.debug "Forwarding device event to bridge: ${json}"
        bridge.deviceNotification(json)
    }
}

def eventCheck(device, attribute, value){
	// If last event was same return false, else store event and return true
	if ((state?.events[device][attribute] == null) ||  (state?.events[device][attribute]  != value)){
		state.events[device][attribute] = value;
		return true;
	}else return false;
}

// +---------------------------------+
// | WARNING, BEYOND HERE BE DRAGONS |
// +---------------------------------+
// These are the functions that handle incoming messages from MQTT.
// I tried to put them in closures but apparently SmartThings Groovy sandbox
// restricts you from running clsures from an object (it's not safe).


// my catch-all action command, processMQTT implementation within device handler handles all he logic
def actionProcessMQTT(device, attribute, value) {
		if ((device == null) || (attribute == null) || (value == null)) return;	
		device.processMQTT(attribute, value);
}


def actionAlarm(device, attribute, value) {
    switch (value) {
        case "strobe":
            device.strobe()
        break
        case "siren":
            device.siren()
        break
        case "off":
            device.off()
        break
        case "both":
            device.both()
        break
    }
}

def actionColor(device, attribute, value) {
    switch (attribute) {
        case "hue":
            device.setHue(value as float)
        break
        case "saturation":
            device.setSaturation(value as float)
        break
        case "color":
            def values = value.split(',')
            def colormap = ["hue": values[0] as float, "saturation": values[1] as float]
            device.setColor(colormap)
        break
    }
}

def actionOpenClosed(device, attribute, value) {
    if (value == "open") {
        device.open()
    } else if (value == "closed") {
        device.close()
    }
}

def actionOnOff(device, attribute, value) {
    if (value == "off") {
        device.off()
    } else if (value == "on") {
        device.on()
    }
}

def actionActiveInactive(device, attribute, value) {
    if (value == "active") {
        device.active()
    } else if (value == "inactive") {
        device.inactive()
    }
}

def actionThermostat(device, attribute, value) {
    switch(attribute) {
        case "heatingSetpoint":
            device.setHeatingSetpoint(value)
        break
        case "coolingSetpoint":
            device.setCoolingSetpoint(value)
        break
        case "thermostatMode":
            device.setThermostatMode(value)
        break
        case "thermostatFanMode":
            device.setThermostatFanMode(value)
        break
    }
}

def actionMusicPlayer(device, attribute, value) {
    switch(attribute) {
        case "level":
            device.setLevel(value)
        break
        case "mute":
            if (value == "muted") {
                device.mute()
            } else if (value == "unmuted") {
                device.unmute()
            }
        break
        case "status":
            if (device.getSupportedCommands().any {it.name == "setStatus"}) {
                device.setStatus(value)
            }
        break
    }
}

def actionColorTemperature(device, attribute, value) {
    device.setColorTemperature(value as int)
}

def actionLevel(device, attribute, value) {
    device.setLevel(value as int)
}

def actionPresence(device, attribute, value) {
    if (value == "present") {
    	device.arrived();
    }
    else if (value == "not present") {
    	device.departed();
    }
}

def actionConsumable(device, attribute, value) {
    device.setConsumableStatus(value)
}

def actionLock(device, attribute, value) {
    if (value == "locked") {
        device.lock()
    } else if (value == "unlocked") {
        device.unlock()
    }
}

def actionCoolingThermostat(device, attribute, value) {
    device.setCoolingSetpoint(value)
}

def actionThermostatFan(device, attribute, value) {
    device.setThermostatFanMode(value)
}

def actionHeatingThermostat(device, attribute, value) {
    device.setHeatingSetpoint(value)
}

def actionThermostatMode(device, attribute, value) {
    device.setThermostatMode(value)
}

def actionTimedSession(device, attribute, value) {
    if (attribute == "timeRemaining") {
        device.setTimeRemaining(value)
    }
}
