# MQTT Bridge to SmartThings [MBS-SmartApp]
# Configuring the capability mao
To start you do not have to change anything here. Just use a Virtual SmartThings switch and configure the devices.yml on server. Once you are comfortable you can come to this sections for more advanced customizations
```
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
	// These could be standardized Smartthings virtual switches or any other device that has MQTT functionality implemented
    "switches": [
        name: "Switch",
        capability: "capability.switch",
        attributes: [
            "switch"
        ],
        action: "actionOnOff"
    ]
]
```

On the settings sections of the MBS SmartApp you will select the devices that you want to associate with each capability map. Make sure devices you associate with a specific capability map have thse  associate 'attributes' and handlers defined.

If you associate an 'action event' with all attributes or attribute specific action events - make sure  the appropriate action events are already pre-defined in the SmartApp or make sure to define them. for e.g.
```
def actionProcessMQTT(device, attribute, value) {
		if ((device == null) || (attribute == null) || (value == null)) return;	
		device.processMQTT(attribute, value);
}

def actionOnOff(device, attribute, value) {
    if (value == "off") {
        device.off()
    } else if (value == "on") {
        device.on()
    }
}
```
Also make sure devices handlers for the devices have the functions being invoked implemented. For e.g. here my custom device handlers will have an implementation of processMQTT(attribute, value). The off() on() commands are typically implementation by any device handler with capability.switch.