/**
 *  Tasmota Switch (no extra tasmota info) Device Handler
 *
 *  Authors
 *	 - sandeep gupta
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
 
metadata {

    definition (name: "Tasmota Switch Lite", namespace: "gupta", author: "Sandeep Gupta") {
        capability "Switch"
		
		command "processMQTT"
		
		attribute "power", "string"
	}

	simulator {
		status "on": "switch:on"
		status "off": "switch:off"
    }

    tiles {	
		multiAttributeTile(name:"main", type: "device.switch", width: 6, height: 4, canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00A0DC"
				attributeState "off", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
			}
		}
		
		standardTile("switchon", "device.switch",  width: 2, height: 2) {
			state("on", label: 'ON', action: "switch.on", icon: "st.Home.home30")
			state("off", label: 'ON', action: "switch.on", icon: "st.Home.home30")
		}
		
		
		
		standardTile("switchoff", "device.switch",  width: 2, height: 2) {
			state("off", label: 'OFF', action: "switch.off", icon: "st.Home.home30")
			state("on", label: 'OFF', action: "switch.off", icon: "st.Home.home30")
		}

		valueTile("empty", "empty", width: 2, height: 2) {
			state ("empty", label: '${currentValue}')
		}

		main "main"
		details(["main", "switchon","empty", "switchoff"])
    }
}


def parse(String description) {
	log.debug "Parsing message  is ${description}"
	def pair = description.split(":")
	switch(pair[0].trim()){
		case 'switch':	
				(pair[1].trim() == "on") ? on() : off();
				break;
		default:
				break;
	}
}

def processMQTT(attribute, value){
	//log.debug "Processing ${attribute} Event:  ${value} from MQTT for device: ${device}"	
	switch (attribute) {
		case 'power':
			(device.currentValue('switch') == value) ? _empty(value) : (value == "on") ? _on() : _off();
			break;
		default:
			break;
	}
}

def on(){
	sendEvent(name : 'power',  value: "on", displayed: false);
	_on();
}

def off(){
	sendEvent(name : 'power', value: "off", displayed: false);
	_off();
}

def _on() {
    sendEvent(name: "switch", value: "on")
	log.debug "Sent 'on' command for device: ${device}"
}

def _off() {
    sendEvent(name: "switch", value: "off")
	log.debug "Sent 'off' command for device: ${device}"
}
def _empty(value){
	//log.debug "Ignoring ${value} event from MQTT for device: ${device}"
}

