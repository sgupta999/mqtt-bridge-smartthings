/**
 *  Tasmota Combo Switch (Primary) - Contact Sensor (Secondary) Device Handler
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

    definition (name: "Tasmota SwitchSensor", namespace: "gupta", author: "Sandeep Gupta") {
        capability "Switch"
		capability "Momentary"
        capability "Contact Sensor"	
		capability "Refresh"
		
		command "open"
		command "closed"
		command "processMQTT"
		
		attribute "power", "string"
		attribute "update", "string"		
		attribute "sensor", "string"
        attribute "device_details", "string"
        attribute "details", "string"
        attribute "rssi", "number"
	}

	simulator {
		status "open": "contact:open"
		status "closed": "contact:closed"   
		status "on": "switch:on"
		status "off": "switch:off"
		status "toggle": "momentary:push"
    }

    tiles {	
		multiAttributeTile(name:"main", type: "device.switch", width: 6, height: 4, canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00A0DC"
				attributeState "off", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
			}		
				
			tileAttribute("device.device_details", key: "SECONDARY_CONTROL", canChangeIcon: true) {
				attributeState("device_details", action: "refresh", label: '${currentValue}', icon:"st.secondary.refresh-icon")				
                attributeState("refresh", label: 'Updating data from server...')
			}
		}
		
		standardTile("contact", "device.contact", width: 2, height: 2) {
			state("closed", label:'${name}', icon:"st.contact.contact.closed", backgroundColor:"#79b821", action: "open")
			state("open", label:'${name}', icon:"st.contact.contact.open", backgroundColor:"#ffa81e", action: "closed")
		}
		
		
		
		standardTile("switch", "device.switch",  width: 2, height: 2) {
			state("on", label: 'Toggle', action: "push", icon: "st.switches.switch.on", backgroundColor: "#00A0DC")
			state("off", label: 'Toggle', action: "push", icon: "st.switches.switch.off", backgroundColor: "#ffffff")
		}

		valueTile("rssi", "device.rssi", width: 2, height: 2) {
			state ("rssi", label: '${currentValue}', backgroundColors:[
							[value: 100, color: "#44b621"],
							[value: 85, color: "#90d2a7"],
							[value: 68, color: "#1e9cbb"],
							[value: 51, color: "#153591"],
							[value: 34, color: "#f1d801"],
							[value: 17, color: "#d04e00"],
							[value: 0, color: "#bc2323"]
					])
		}
		
		 valueTile("details", "device.details", width: 6, height: 2) {
			state "details", label: '${currentValue}', backgroundColor: "#ffffff"
		}

		main "main"
		details(["main", "contact","rssi", "switch","details" ])
    }

	preferences {
		section("Main") {
			input(name: "linked", type: "bool", title: "Link Switch and Contact Sensor", description: "", required: false)
		}
	}
}


def parse(String description) {
	log.debug "Parsing message  is ${description}"
	def pair = description.split(":")
	switch(pair[0].trim()){
		case 'switch':	
				(pair[1].trim() == "on") ? on() : off();
				break;
		case 'contact':
				(pair[1].trim() == "open") ? open() : closed();
				break;
		case 'momentary':
				if (pair[1].trim() == "push") push();
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
		case 'contact':
			(device.currentValue('contact') == value) ? _empty() : (contact == "open") ? _open : closed()
			break;
		case 'update':
			updateTiles(value);
			break;
		default:
			break;
	}
}
	
def updateTiles(Object val ){
		state.updates = (state?.updates == null)  ? "" : state.updates +  val + "\n";
		def value = parseJson(val);	
		if (state?.updatesReady == null) {
			state.updatesReady = 0b00000000;
		} else {	
			state.updatesReady = (value?.Status != null) ? (state.updatesReady | 0b00000001) : 
								 (value?.StatusFWR != null) ? (state.updatesReady | 0b00000010) :
								 (value?.StatusNET != null) ? (state.updatesReady | 0b00000100) : state.udpatesReady; 
		}
		state.topic = (value?.Status?.Topic != null) ? value?.Status?.Topic : state.topic
		state.friendlyName = (value?.Status?.FriendlyName != null) ? value?.Status?.FriendlyName : state.friendlyName
		state.firmware = (value?.StatusFWR?.Version != null) ? value?.StatusFWR?.Version : state.firmware 
		state.macAddress = ( value?.StatusNET?.Mac != null) ? value?.StatusNET?.Mac : state.macAddress
		state.ipAddress = (value?.StatusNET?.IPAddress != null) ? value?.StatusNET?.IPAddress : state.ipAddress
		state.currentTimestamp = (value?.Time == null) ? new Date() : Date.parse("yyyy-MM-dd'T'HH:mm:ss",value?.Time)
		state.ssid1 = (value?.Wifi?.SSId != null) ? value?.Wifi?.SSId : state.ssid1
		state.upTime = (value?.Uptime != null) ? value?.Uptime : state.upTime
		state.RSSI = (value?.Wifi?.RSSI	!= null) ? value?.Wifi?.RSSI : state.RSSI		
		if ((state.updatesReady == 0b00000111) || (value?.Heap != null) ){
			sendEvent(name: 'device_details', value: state.topic + ", running for: " + state.upTime + 
			"\nIP: " + state.ipAddress + " [ " + state.ssid1+": "+state.RSSI + "% ]", isStateChange : state.refresh, displayed: false)	
			sendEvent(name: 'rssi', value: state.RSSI+ "%\n" + state.ssid1 + "\nRSSI", isStateChange : state.refresh, displayed: false)			
			sendEvent(name: 'details', value: state.topic + "\n" + state.friendlyName + "\n" + state.ipAddress + " [ " +state.macAddress + " ]\n" + state.firmware + 
			 " - Up Time: " + state.upTime + "\nLast updated: " + state.currentTimestamp.format("EEE MMM dd, yyyy 'at' hh:mm a", location.timeZone), isStateChange : state.refresh,  displayed: false)			
			state.lastTimestamp = state.currentTimestamp
			log.debug "Refresh is [${state.refresh}]. Processed Status updates for device: [${device}]\n  ${state.updates}"
			state.updates = "";
			state.updatesReady = 0b00000000;
			state.refresh = false
		}	
}

def refresh(){
	state.refresh = true;
	sendEvent(name : "update", value : 'refresh', isStateChange : 'true', descriptionText : 'Refreshing from Server');
	log.debug "Sent 'refresh' command for device: ${device}"
}

def on(){
	sendEvent(name : 'power',  value: "on", displayed: false);
	_on();
}

def off(){
	sendEvent(name : 'power', value: "off", displayed: false);
	_off();
}

def open(){
	sendEvent(name : 'sensor', value: "open", displayed: false);
	_open();
}

def closed(){
	sendEvent(name : 'sensor', value: "closed", displayed: false);
	_closed();
}

def push() {
	(device.currentValue("switch") == "on") ? off() : on()
	log.debug "Sent 'TOGGLE' command for device: ${device}"
}

def _on() {
    sendEvent(name: "switch", value: "on")
	if (linked) sendEvent(name: "contact", value: "open")
	log.debug "Sent 'on' command for device: ${device}"
}

def _off() {
    sendEvent(name: "switch", value: "off")
	if (linked) sendEvent(name: "contact", value: "closed")
	log.debug "Sent 'of' command for device: ${device}"
}

def _open() {
    sendEvent(name: "contact", value: "open")
	if (linked) sendEvent(name: "switch", value: "on")
	log.debug "Sent 'open' command for device: ${device}"
}

def _closed() {
    sendEvent(name: "contact", value: "closed")
	if (linked) sendEvent(name: "switch", value: "off")
	log.debug "Sent 'closed' command for device: ${device}"
}

def _empty(value){
	//log.debug "Ignoring ${value} event from MQTT for device: ${device}"
}

