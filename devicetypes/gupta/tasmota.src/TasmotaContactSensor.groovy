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
		command "close"
		command "processMQTT"
		
		attribute "update", "string"		
		attribute "device_details", "string"
        attribute "details", "string"
        attribute "rssi", "string"
	}

	simulator {
		status "open": "contact:open"
		status "closed": "contact:close"   
    }

    tiles {		
		multiAttributeTile(name:"main", type: "generic"){
			tileAttribute ("device.contact", key: "PRIMARY_CONTROL") {
				attributeState "open", label:'${name}', action: "close", icon:"st.contact.contact.open", backgroundColor:"#e86d13"
				attributeState "closed", label:'${name}', action: "open", icon:"st.contact.contact.closed", backgroundColor:"#00a0dc"
            }
			
			tileAttribute("device.device_details", key: "SECONDARY_CONTROL") {
				attributeState("device_details", action: "refresh", label: '${currentValue}', icon:"st.secondary.refresh-icon")				
                attributeState("refresh", label: 'Updating data from server...')
			}
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

		main "main"
		details(["main", "contactopen", "rssi", "contactclosed","details" ])
    }
}


def parse(String description) {
	log.debug "Parsing message  is ${description}"
	def pair = description.split(":")
	switch(pair[0].trim()){
		case 'contact':
				(pair[1].trim() == "open") ? open() : close();
				break;
		default:
				break;
	}
}

def processMQTT(attribute, value){
	//log.debug "Processing ${attribute} Event:  ${value} from MQTT for device: ${device}"	
	switch (attribute) {
		case 'update':
			updateTiles(value);
			break;
		default:
			break;
	}
}
	
def updateTiles(Object val ){
		//log.debug "Msg ${val}"
		state.pingState = (state?.pingState == null) ? 'ONLINE':  state.pingState;
		if ((val == 'Online') || (val == 'Offline')){
			log.debug "Received Health Check LWT event ${val}"
			if (val == 'Online') {
				state.pingState =  'ONLINE';		
				sendEvent(name: 'rssi', value: state.RSSI+ "%\n" + state.ssid1 + "\n["+ state.pingState + "]", isStateChange : 'true', displayed: 'false')			
			} else {
				state.pingState =  'OFFLINE';
				sendEvent(name: 'rssi', value: "0" + "%\n" + state.ssid1 + "\n["+ state.pingState + "]", isStateChange : 'true', displayed: 'false')	
			} 
			return;			
		}		
		state.updates = (state?.updates == null)  ? "" : state.updates +  val + "\n";
		def value = parseJson(val);	
		if (state?.update1 == null) state.update1 = false 
		if (state?.update2 == null) state.update2 = false 
		if (state?.update3 == null) state.update3 = false 
		if (state?.update4 == null) state.update4 = false  
		if (value?.Status != null) state.update1 = true else 
		if (value?.StatusFWR != null) state.update2 = true else
		if (value?.StatusNET != null) state.update3 = true else
		if (value?.StatusSTS != null) state.update4 = true;
		(value?.Status != null) 
		state.topic = (value?.Status?.Topic != null) ? value?.Status?.Topic : state.topic
		state.friendlyName = (value?.Status?.FriendlyName != null) ? value?.Status?.FriendlyName : state.friendlyName
		state.firmware = (value?.StatusFWR?.Version != null) ? value?.StatusFWR?.Version : state.firmware 
		state.macAddress = ( value?.StatusNET?.Mac != null) ? value?.StatusNET?.Mac : state.macAddress
		state.ipAddress = (value?.StatusNET?.IPAddress != null) ? value?.StatusNET?.IPAddress : state.ipAddress
		if (value?.StatusSTS?.Time != null) state.currentTimestamp = Date.parse("yyyy-MM-dd'T'HH:mm:ss",value?.StatusSTS?.Time).format("EEE MMM dd, yyyy 'at' hh:mm:ss a")
		state.ssid1 = (value?.StatusSTS?.Wifi?.SSId != null) ? value?.StatusSTS?.Wifi?.SSId : state.ssid1
		state.upTime = (value?.StatusSTS?.Uptime != null) ? value?.StatusSTS?.Uptime : state.upTime
		state.RSSI = (value?.StatusSTS?.Wifi?.RSSI	!= null) ? value?.StatusSTS?.Wifi?.RSSI : state.RSSI
		//log.debug "Are updates ready ${state.update1}, ${state.update2}, ${state.update3}, ${state.update4}"	
		//log.debug "Time is  ${state.currentTimestamp}"	
		if (state.update1 && state.update2 && state.update3 && state.update4){
			sendEvent(name: 'device_details', value: state.topic + ", running for: " + state.upTime + 
			"\nIP: " + state.ipAddress + " [ " + state.ssid1+": "+state.RSSI + "% ]", isStateChange : 'true', displayed: 'false')				
			sendEvent(name: 'details', value: state.topic + "\n" + state.friendlyName + "\n" + state.ipAddress + " [ " +state.macAddress + " ]\n" + state.firmware + 
			 " - Up Time: " + state.upTime + "\nLast updated: " + state.currentTimestamp , isStateChange : 'true',  displayed: 'false')
			sendEvent(name: 'rssi', value: state.RSSI+ "%\n" + state.ssid1 + "\n["+ state.pingState + "]", isStateChange : 'true', displayed: 'false')			
			log.debug "Refresh is [${state.refresh}]. Processed Status updates for device: [${device}]\n  ${state.updates}"
			state.updates = "";
			state.update1 = state.update2 = state.update3 = state.update4 = false;
			state.refresh = false
		}	
}

def refresh(){
	state.refresh = true;
	sendEvent(name : "update", value : 'refresh', isStateChange : 'true', descriptionText : 'Refreshing from Server...');	
	log.debug "Sent 'refresh' command for device: ${device}"
}

def open(){
	if (device.currentValue("contact") == "open") return;
	_open();
}

def close(){
	if (device.currentValue("contact") == "closed") return;
	_close();
}

def _open() {
    sendEvent(name: "contact", value: "open")
	log.debug "Sent 'open' command for device: ${device}"
}

def _close() {
    sendEvent(name: "contact", value: "closed")
	log.debug "Sent 'close' command for device: ${device}"
}

