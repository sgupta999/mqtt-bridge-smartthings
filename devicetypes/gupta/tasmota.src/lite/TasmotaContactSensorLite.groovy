/**
 *  Tasmota Contact Sensor (no extra tasmota info) Device Handler
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

    definition (name: "Tasmota Contact Sensor Lite", namespace: "gupta", author: "Sandeep Gupta") {
        capability "Contact Sensor"	
		capability "Refresh"
		
		command "open"
		command "closed"
		command "processMQTT"
		
		attribute "sensor", "string"
	}

	simulator {
		status "open": "contact:open"
		status "closed": "contact:closed"  
    }

    tiles (scale:2) {		

		valueTile("empty", "device.contact", width: 1, height: 4) {
			state ("closed", label:'', backgroundColor:"#ffffff")
			state ("open", label:'', backgroundColor:"#ffffff")
		}
		
		standardTile("contact", "device.contact", width: 4, height: 4) {
			state("closed", label:'${name}', icon:"st.contact.contact.closed", backgroundColor:"#79b821", action: "open")
			state("open", label:'${name}', icon:"st.contact.contact.open", backgroundColor:"#ffa81e", action: "closed")
		}
		
		standardTile("contactclosed", "device.contact", width: 2, height: 2) {
			state("closed", label:'CLOSE', icon:"st.contact.contact.closed", backgroundColor:"#79b821", action: "closed")
			state("open", label:'CLOSE', icon:"st.contact.contact.closed", backgroundColor:"#79b821", action: "closed")
		}

		
		standardTile("contactopen", "device.contact", width: 2, height: 2) {
			state("closed", label:'OPEN', icon:"st.contact.contact.open", backgroundColor:"#ffa81e", action: "open")
			state("open", label:'OPEN', icon:"st.contact.contact.open", backgroundColor:"#ffa81e", action: "open")
		}

		valueTile("empty1", "", width: 2, height: 2) {
			state ("none", label:'', backgroundColor:"#ffffff")
		}

		details(["empty", "contact", "empty", "refresh", "device_details", "contactopen", "empty1", "contactclosed","details" ])
    }
}


def parse(String description) {
	log.debug "Parsing message  is ${description}"
	def pair = description.split(":")
	switch(pair[0].trim()){
		case 'contact':
				(pair[1].trim() == "open") ? open() : closed();
				break;
		default:
				break;
	}
}

def processMQTT(attribute, value){
	//log.debug "Processing ${attribute} Event:  ${value} from MQTT for device: ${device}"	
	switch (attribute) {
		case 'contact':
			(device.currentValue('contact') == value) ? _empty() : (contact == "open") ? _open : closed()
			break;
		default:
			break;
	}
}
	


def open(){
	sendEvent(name : 'sensor', value: "open", displayed: false);
	_open();
}

def closed(){
	sendEvent(name : 'sensor', value: "closed", displayed: false);
	_closed();
}

def _open() {
    sendEvent(name: "contact", value: "open")
	log.debug "Sent 'open' command for device: ${device}"
}

def _closed() {
    sendEvent(name: "contact", value: "closed")
	log.debug "Sent 'closed' command for device: ${device}"
}

def _empty(value){
	//log.debug "Ignoring ${value} event from MQTT for device: ${device}"
}

