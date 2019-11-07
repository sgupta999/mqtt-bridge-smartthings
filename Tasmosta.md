# If you are using sonoff’s 
1. Enable MQTT
2. Configure MQTT to you broker.
3. Set the unique topic like “SNF-M1” etc, for each sonoff (This topic should also match with the topics in the devices.yml)
4. For full topic I just prepend smartthings as "smartthings/%prefix%/%topic%"
5. Also make sure to set the timezones etc otherwise your timestamps will be wrong. From the web console I run the following backlog commands
```
Backlog ntpServer1 0.us.pool.ntp.org; ntpServer2 1.us.pool.ntp.org; ntpServer3 2.us.pool.ntp.org; Sleep 250; TIMEDST 0,2,3,1,2,-300; TIMESTD 0,1,11,1,2,-360; Timezone 99; WifiConfig 2; Latitude xx.xxx; Longitude -xx.xxx; SetOption55 0
Backlog setoption53 1; powerretain on;SwitchRetain off; ButtonRetain on; ButtonRetain off
```
Timezone 99 is CST for me. Enter your specific longitude / latitude (that gets local sunrise /sunset)

# If you are using RF Bridge 
run this rule on the RFBridge to broadcast  'rfsensor' payload for any RFReceived event
```
rule1 on rfreceived#data do publish rfbridge/%value%  rfsensor endon
```