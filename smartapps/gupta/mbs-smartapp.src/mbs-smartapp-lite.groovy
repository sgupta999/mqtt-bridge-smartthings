/**
 *  MQTT Bridge To SmartThings - SmartThings SmartApp (Lite Version)
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

// Lite lookup tree
@Field CAPABILITY_MAP = [
    "customPowerMeters": [
        name: "Custom Power Meter",
        capability: "capability.powerMeter",
        attributes: [
            "demand",
			"mqttmsg"
        ],
        action: "actionProcessMQTT"
    ],
    "tasmotaSwitches": [
        name: "Tasmota Switch",
        capability: "capability.switch",
        attributes: [
			"power",
			"update"
        ],
        action: "actionProcessMQTT"
    ],
    "tasmotaSensor": [
        name: "Tasmota Contact Sensor",
        capability: "capability.contactSensor",
        attributes: [
			"sensor",
			"update"
        ],
        action: "actionProcessMQTT"
    ]
]

definition(
    name: "MBS SmartApp Lite",
    namespace: "gupta",
    author: "Sandeep Gupta",
    description: "A bridge between SmartThings and MQTT",
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
    runEvery15Minutes(initialize)
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
    // Subscribe to new events from devices	
    CAPABILITY_MAP.each { key, capability ->
        capability["attributes"].each { attribute ->
			if (settings[key] != null){
				subscribe(settings[key], attribute, inputHandler)
				log.debug "Subscribed to event ${attribute} on device ${settings[key]}"
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
							//if ((json.value != null) && (device.currentValue(json.type) != null) && (device.currentValue(json.type).equalsIgnoreCase(json.value))) return; 
                            def action = capability["action"]
                            // Yes, this is calling the method dynamically 
                            "$action"(device, json.type, json.value);
							return;
                        }
                    }
                }
            }
        } else {
			// If message received and even if attribute not defined, it will look for command with same type and invoke that device command
			settings[key].each {device ->
				if ((device.displayName == json.name) && (json.type != null) && (json.value != null)) {	
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
	log.debug "Received event ${evt.name} for device  ${evt.displayName} for bridge: ${evt.value}"
    if (
        state.ignoreEvent
        && state.ignoreEvent.name == evt.displayName
        && state.ignoreEvent.type == evt.name
        && state.ignoreEvent.value == evt.value
    ) {
        log.debug "Ignoring event ${state.ignoreEvent}"
        state.ignoreEvent = false;
    }
    else {
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

// +---------------------------------+
// | WARNING, BEYOND HERE BE DRAGONS |
// +---------------------------------+
// These are the functions that handle incoming messages from MQTT.
// I tried to put them in closures but apparently SmartThings Groovy sandbox
// restricts you from running clsures from an object (it's not safe).

def actionProcessMQTT(device, attribute, value) {
		if ((device == null) || (attribute == null) || (value == null)) return;		                           							
		log.debug "Calling action method ${attribute} for device ${device} with state ${value}"
		device.processMQTT(attribute, value);
}