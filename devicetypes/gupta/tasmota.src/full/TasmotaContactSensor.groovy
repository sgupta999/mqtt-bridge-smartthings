/**
 *  Tasmota Contact Sensor Device Handler
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

    definition (name: "Tasmota Contact Sensor", namespace: "gupta", author: "Sandeep Gupta") {
        capability "Contact Sensor"	
		capability "Refresh"
		
		command "open"
		command "closed"
		command "processMQTT"
		
		attribute "update", "string"		
		attribute "sensor", "string"
        attribute "device_details", "string"
        attribute "details", "string"
        attribute "rssi", "number"
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
		
		standardTile("refresh", "device.", width: 1, height: 1) {
			state("refresh", label:'${name}',action: "refresh",  icon:"st.secondary.refresh-icon")
		}		
		
		valueTile("device_details", "device.device_details", width: 5, height: 1) {
			state("device_details", label: '${currentValue}')		
		}
		
		standardTile("contactclosed", "device.contact", width: 2, height: 2) {
			state("closed", label:'CLOSE', icon:"st.contact.contact.closed", backgroundColor:"#79b821", action: "closed")
			state("open", label:'CLOSE', icon:"st.contact.contact.closed", backgroundColor:"#79b821", action: "closed")
		}

		
		standardTile("contactopen", "device.contact", width: 2, height: 2) {
			state("closed", label:'OPEN', icon:"st.contact.contact.open", backgroundColor:"#ffa81e", action: "open")
			state("open", label:'OPEN', icon:"st.contact.contact.open", backgroundColor:"#ffa81e", action: "open")
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
		
		details(["empty", "contact", "empty", "refresh", "device_details", "contactopen", "rssi", "contactclosed","details" ])
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
	sendEvent(name : "device_details", value: 'Updating data from server...', isStateChange : 'true', displayed: 'false');
	sendEvent(name : "update", value : 'refresh', isStateChange : 'true', descriptionText : 'Refreshing from Server...');
	log.debug "Sent 'refresh' command for device: ${device}"
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

