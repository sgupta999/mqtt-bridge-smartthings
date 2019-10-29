/**
 *  ST-MQTT Bridge Server
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

/*jslint node: true */
'use strict';

var logger = require('winston'),
	daily = require('winston-daily-rotate-file'),
    express = require('express'),
    expressJoi = require('express-joi-validator'),
    expressWinston = require('express-winston'),
    bodyparser = require('body-parser'),
    mqtt = require('mqtt'),
    async = require('async'),
    url = require('url'),
    joi = require('joi'),
    yaml = require('js-yaml'),
    jsonfile = require('jsonfile'),
    fs = require('fs-extra'),
    semver = require('semver'),
	stripcomments = require("strip-json-comments"),
	mqttWildcard = require('mqtt-wildcard'),
    request = require('request'),
	path = require('path'),
	CONFIG_DIR = __dirname,
    CONFIG_FILE = path.join(CONFIG_DIR, 'config.yml')	;
	
	function loadConfiguration () {
    if (!fs.existsSync(CONFIG_FILE)) {
        console.log('No previous configuration found, creating one');
        fs.writeFileSync(CONFIG_FILE, fs.readFileSync(SAMPLE_FILE));
    }

    return yaml.safeLoad(fs.readFileSync(CONFIG_FILE));
}

var config = loadConfiguration(),
	DEVICE_CONFIG_FILE = path.join(CONFIG_DIR, 'devices.yml'),
    SAMPLE_FILE = path.join(__dirname, '_config.yml'),
    STATE_FILE = path.join(CONFIG_DIR, 'data', 'state.json'),
    STATE_SUMMARY_FILE = path.join(CONFIG_DIR, 'data', 'state.summary.json'),
    ACCESS_LOG = path.join(CONFIG_DIR, 'log','access.log'),
    ERROR_LOG = path.join(CONFIG_DIR,'log', 'error.log'),
    CURRENT_VERSION = require('./package').version,
    // The topic type to get state changes from smartthings
    TOPIC_READ_STATE = 'state',
    SUFFIX_READ_STATE = 'state_read_suffix',
    // The topic type to send commands to smartthings
    TOPIC_COMMAND = 'command',
    SUFFIX_COMMAND = 'command_suffix',
    // The topic type to send state changes to smartthings
    TOPIC_WRITE_STATE = 'set_state',
    SUFFIX_WRITE_STATE = 'state_write_suffix',
    RETAIN = 'retain',
	LOGGING_LEVEL = config.loglevel;

var app = express(),
    client,
    subscriptions = [],
	publications = {},
	subscribe = {},
    callback = '',
	devices = {},
    history = {};
  
 logger.loggers.add('ST-MQTT', {
	  console: {
		level: LOGGING_LEVEL,
//		label:'server.js:',
		timestamp: function () {
			var date = new Date();
			return date.toLocaleString(); 
		}
	  }, 
	  transports : [
			new (logger.transports.DailyRotateFile)({
				filename: path.join(CONFIG_DIR, 'log', 'events-%DATE%.log'),
				level: LOGGING_LEVEL,
				timestamp: function () {
					var date = new Date();
					return date.toLocaleString(); 
				},
				datePattern: 'YYYY-MM-DD',
				zippedArchive: true,
				maxSize: '2m',
				maxFiles: '10d',
//				label:'server.js:',
				json: false
			})
		]
 });    

 var winston = logger.loggers.get('ST-MQTT');

/**
 * Load device configuration if it exists
 * @method loadDeviceConfiguration
 * @return {Object} Configuration
 */
function loadDeviceConfiguration () {	
	subscribe = {};
	if (!config.deviceconfig) return null;
    var output;
    try {
        output = yaml.safeLoad(fs.readFileSync(DEVICE_CONFIG_FILE));
    } catch (ex) {
		winston.error(ex);
        winston.info('No external device configurations found, continuing');
		return;
    }
	Object.keys(output).forEach(function (device) {		
		winston.debug("Loading config for Device " , device);
		Object.keys(output[device]["subscribe"]).forEach (function (sub){
			var data = {};			
			data['device']=device;
			if (!!output[device]["subscribe"][sub]['attribute']) data['attribute'] = output[device]["subscribe"][sub]['attribute'] ;
			if (!!output[device]["subscribe"][sub]['command']) data['command']= output[device]["subscribe"][sub]['command'];	
			if (!subscribe[sub]) subscribe[sub] = {};
			subscribe[sub][device] = data;
			winston.debug("Subscription %s\t\[%s],[%s],[%s]",sub, subscribe[sub][device]['device'],
			subscribe[sub][device]['attribute'],subscribe[sub][device]['command']);
			winston.debug("Subscription: %s - Device %s" , sub, subscribe[sub]);
		});
	});
	winston.info('============================ALL POSSIBLE SUBSCRIPTIONS FROM ALL EXTERNAL DEVICES ===========================================');
	Object.keys(subscribe).forEach(function (subs){
		Object.keys(subscribe[subs]).forEach(function (dev){
			winston.info("Subscription %s\t\[%s],[%s],[%s]",subs, subscribe[subs][dev]['device'],
			subscribe[subs][dev]['attribute'], JSON.stringify((!!subscribe[subs][dev]['command']) ? subscribe[subs][dev]['command'] : ''));
		});
	});
    winston.info('============================================================================================================================');
	
    return output;
}

/**
 * Set defaults for missing definitions in config file
 * @method configureDefaults
 * @param  {String}     version Version the state was written in before
 */
function configureDefaults() {
    // Make sure the object exists
    if (!config.mqtt) {  
        config.mqtt = {};
    }

	if (!config.mqtt.preface) {
        config.mqtt.preface = '/smartthings';
    }

    // Default Suffixes
    if (!config.mqtt[SUFFIX_READ_STATE]) {
        config.mqtt[SUFFIX_READ_STATE] = '';
    }
    if (!config.mqtt[SUFFIX_COMMAND]) {
        config.mqtt[SUFFIX_COMMAND] = '';
    }
    if (!config.mqtt[SUFFIX_WRITE_STATE]) {
        config.mqtt[SUFFIX_WRITE_STATE] = '';
    }

    // Default retain
    if (!config.mqtt[RETAIN]) {
        config.mqtt[RETAIN] = false;
    }

    // Default port
    if (!config.port) {
        config.port = 8080;
    }

    // Default protocol
    if (!url.parse(config.mqtt.host).protocol) {
        config.mqtt.host = 'mqtt://' + config.mqtt.host;
    }
	
	// Default protocol
    if (!config.deviceconfig) {
        config.deviceconfig = false;
    }
}

/**
 * Load the saved previous state from disk
 * @method loadSavedState
 * @return {Object} Configuration
 */
function loadSavedState () {
    var output;
    try {
        output = jsonfile.readFileSync(STATE_FILE);
    } catch (ex) {
        winston.info('No previous state found, continuing');
        output = {
            version: '0.0.0',
            callback: '',
            subscriptions: {},
			subscribe: {},
			publications: {},
            history: {},
			devices: {}
        };
    }
    return output;
}

/**
 * Resubscribe on a periodic basis
 * @method saveState
 */
function saveState () {
    winston.info('Saving current state');
	fs.ensureDir(path.join(CONFIG_DIR,'data'));
    jsonfile.writeFileSync(STATE_FILE, {
        version: CURRENT_VERSION,
        callback: callback,
        subscriptions: subscriptions,
		subscribe: subscribe,
		publications: publications,
        history: history,
		devices: devices
    }, {
        spaces: 4
    });
    jsonfile.writeFileSync(STATE_SUMMARY_FILE, {
        version: CURRENT_VERSION,
        callback: callback,
        subscriptions: subscriptions,
		subscribe: Object.keys(subscribe),
		publications: Object.keys(publications),
        history: history,
		devices: Object.keys(devices)
    }, {
        spaces: 4
    });
}


/**
 * Handle Device Change/Push event from SmartThings
 *
 * @method handlePushEvent
 * @param  {Request} req
 * @param  {Object}  req.body
 * @param  {String}  req.body.name  Device Name (e.g. "Bedroom Light")
 * @param  {String}  req.body.type  Device Property (e.g. "state")
 * @param  {String}  req.body.value Value of device (e.g. "on")
 * @param  {Result}  res            Result Object
 */
function handlePushEvent (req, res) {	
    var value = req.body.value,
		attribute = req.body.type,
		retain = config.mqtt[RETAIN],
		topic = "",
		device = req.body.name;
	winston.debug('From ST: %s - %s - %s', topic, req.body.type, value);
	// for devices from config file
	if ((!!devices[device]) && (!!devices[device].publish) && (!!devices[device].publish[attribute])){
		retain  = (!!devices[device].retain) ? devices[device].retain : retain;
		value = ((!!devices[device].publish[attribute].command) && (!!devices[device].publish[attribute].command[value]))
				? devices[device].publish[attribute].command[value] : value;
		topic = devices[device].publish[attribute].topic;	
		winston.info('ST** --> MQTT: [%s][%s][%s]\t[%s][%s]', req.body.name, req.body.type, req.body.value, topic, value);
	}else {
	// for devices with standard read, write, command suffixes
		topic = getTopicFor(device, attribute, TOPIC_READ_STATE);
		winston.debug('Device from SmartThings: %s = %s', topic, value);	
		winston.info('ST --> MQTT: [%s][%s][%s]\t[%s][%s]', device, attribute, req.body.value, topic, value);
	}
    history[topic] = value;
	if ((!!publications) && (!publications[topic])){
		var data = {};
		data['device'] =  device;
		data['attribute'] =  attribute;
		data['command'] = value;
		publications[topic] = {};
		publications[topic][device] = data;
	}
	var sub = isSubscribed(topic);
	if ((!!subscribe[sub]) && (!!subscribe[sub][device]) && (!!subscribe[sub][device][attribute])) {
		winston.warn('POSSIBLE LOOP. Device[Attribute] %s[%s] is publishing to Topic %s while subscribed to Topic %s', device, attribute, topic, sub);
	} else if ((!!subscribe[sub]) && (!!subscribe[sub][device])) {
		winston.warn('POSSIBLE LOOP. Device %s is publishing to Topic %s while subscribed to Topic %s', device, topic, sub);
	}
    client.publish(topic, value, retain, function () {
        res.send({
            status: 'OK'
        });
    });
}



/**
 * Handle Subscribe event from SmartThings
 *
 * @method handleSubscribeEvent
 * @param  {Request} req
 * @param  {Object}  req.body
 * @param  {Object}  req.body.devices  List of properties => device names
 * @param  {String}  req.body.callback Host and port for SmartThings Hub
 * @param  {Result}  res               Result Object
 */
function handleSubscribeEvent (req, res) {
    // Subscribe to all events
	devices = loadDeviceConfiguration();
	subscriptions = [];
    Object.keys(req.body.devices).forEach(function (property) {
		winston.debug('Property - %s ', property);
        req.body.devices[property].forEach(function (device) {
			winston.debug(' %s - %s ', property, device);			
			// CRITICAL - if device in DEVICE_CONFIG_FILE, file sub/pub info will supercedes
			if ((!!devices[device]) && (!!devices[device]["subscribe"]) ){
				Object.keys(devices[device]["subscribe"]).forEach (function (sub){						
					subscriptions.push(sub);
					winston.debug('Subscribing[CUSTOM] ', sub);
				});
			}else {
				var data = {};			
				data['device']=device;
				data['attribute'] = property;
				var sub = getTopicFor(device, property, TOPIC_COMMAND);
				if (!subscriptions.includes(sub)) subscriptions.push(sub);
				if (!subscribe[sub]) subscribe[sub] = {};
				subscribe[sub][device] = data;
				sub = getTopicFor(device, property, TOPIC_WRITE_STATE);
				if (!subscriptions.includes(sub)) subscriptions.push(sub);
				if (!subscribe[sub]) subscribe[sub] = {};
				subscribe[sub][device] = data;
				winston.debug('Subscribing[R] ', sub);
			}
        });
    });

    // Store callback
    callback = req.body.callback;

	// Subscribe to events
	
    winston.info('===================================ACTUAL SUBSCRIPTIONS REQUESTED FROM SMARTAPP ============================================');
    winston.info('Currently subscribed to ' + subscriptions.join(', '));
    winston.info('============================================================================================================================');
    
	// Store current state on disk    
   saveState();
	client.subscribe(subscriptions, function () {
        res.send({
            status: 'OK'
        });
    });
}


/**
 * Get the topic name for a standard device  that is not in device  config file
 * @method getTopicFor
 * @param  {String}    device   Device Name
 * @param  {String}    property Property
 * @param  {String}    type     Type of topic (command or state)
 * @return {String}             MQTT Topic name
 */
function getTopicFor (device, property, type) {
    var tree = [config.mqtt.preface, device, property],
        suffix;

    if (type === TOPIC_COMMAND) {
        suffix = config.mqtt[SUFFIX_COMMAND];
    } else if (type === TOPIC_READ_STATE) {
        suffix = config.mqtt[SUFFIX_READ_STATE];
    } else if (type === TOPIC_WRITE_STATE) {
        suffix = config.mqtt[SUFFIX_WRITE_STATE];
    }

    if (suffix) {
        tree.push(suffix);
    }
    return tree.join('/');
}

/**
 * Check if the topic is subscribed to in external device config file
 * Can match subscriptions that MQTT wildcards
 * @method isSubscribed
 * @param  {String}    topic   topic received from MQTT broker
 * @return {String}    topic   topic from config file (may include wildcards)
 */
function isSubscribed(topic){
	if (!subscribe) return null;
	var keys = Object.keys(subscribe);
	var i;
	for (i=0; i< keys.length; i++){
		if (keys[i] == topic) {		
			return keys[i];		}
		if (mqttWildcard(topic, keys[i]) != null) return key;		
	}
	return null;
}


/**
 * Parse incoming message from MQTT
 * @method parseMQTTMessage
 * @param  {String} topic   Topic channel the event came from
 * @param  {String} message Contents of the event
 */
function parseMQTTMessage (topic, message) {
    var contents = message.toString();
    winston.debug('From MQTT: %s = %s', topic, contents);
	var topic = isSubscribed(topic);
	var device, property, cmd, value;
	if (!topic)  {
		winston.warn('%s-%s not subscribed. State error. Ignoring. ', topic, contents);
		return;
	}	
	// If we subscribe to topic we are publishing we get into a loop
	if ((!!publications) && (!!publications[topic])){
		winston.error('This is published topic, not subscribed, ignoring: %s = %s', topic, contents);
		return;		
	}
	
	// Topic is subscribe to
	if ((!!topic) && (!!subscribe)){
		Object.keys(subscribe[topic]).forEach(function(name) {	
			// Checking if  external device for this topic
			if (!!devices[name]){
				device = subscribe[topic][name]['device'];
				property = subscribe[topic][name]['attribute'];
				value = contents;
				if ((!!subscribe[topic][name]['command']) && (!!subscribe[topic][name]['command'][contents])) 
					value = subscribe[topic][name]['command'][contents];
				cmd = true;		
			} else { 	
				// Remove the preface from the topic before splitting it
				var pieces = topic.substr(config.mqtt.preface.length + 1).split('/');				
				device = pieces[0];
				property = pieces[1];
				value = contents;
				var topicReadState = getTopicFor(device, property, TOPIC_READ_STATE),
					topicWriteState = getTopicFor(device, property, TOPIC_WRITE_STATE),
					topicSwitchState = getTopicFor(device, 'switch', TOPIC_READ_STATE),
					topicLevelCommand = getTopicFor(device, 'level', TOPIC_COMMAND),		
					cmd = (!pieces[2] || pieces[2] && pieces[2] === config.mqtt[SUFFIX_COMMAND]);
				// Deduplicate only if the incoming message topic is the same as the read state topic
				if (topic === topicReadState) {
					if (history[topic] === contents) {
						winston.info('Skipping duplicate message from: %s = %s', topic, contents);
						return;
					}
				}
				// If sending level data and the switch is off, don't send anything
				// SmartThings will turn the device on (which is confusing)
				if (property === 'level' && history[topicSwitchState] === 'off') {
					winston.info('Skipping level set due to device being off');
					return;
				}

				// If sending switch data and there is already a nonzero level value, send level instead
				// SmartThings will turn the device on
				if (property === 'switch' && contents === 'on' &&
					history[topicLevelCommand] > 0) {
					winston.info('Passing level instead of switch on');
					property = 'level';
					contents = history[topicLevelCommand];
				}
				winston.info('MQTT --> ST - Topic: [%s][\t[%s][%s][%s]', topic, device, property, value);
			}
			history[topic] = contents;
			winston.info('MQTT --> ST - Topic: [%s][%s]\t[%s][%s][%s]', topic, (contents.length > 25) ?
						contents.substr(0,25) + "..." : contents , device, property, value);
			request.post({
						url: 'http://' + callback,
						json: {
							name: device,
							type: property,
							value: value,
							command: cmd
						}
					}, function (error, resp) {
						if (error) {
							// @TODO handle the response from SmartThings
							winston.error('Error from SmartThings Hub: %s', error.toString());
							winston.error(JSON.stringify(error, null, 4));
							winston.error(JSON.stringify(resp, null, 4));
						}
					});
		});
	}	
}

// Main flow
async.series([
    function loadFromDisk (next) {
        var state;

        winston.info('Starting SmartThings MQTT Bridge - v%s', CURRENT_VERSION); 
		winston.info('Configuration Directory - %s', CONFIG_DIR);
        winston.info('Loading configuration');
		configureDefaults();
		if (config.deviceconfig) {
			winston.info('Loading Device configuration');
			devices = loadDeviceConfiguration();
		}	

        winston.info('Loading previous state');
        state = loadSavedState();
        callback = state.callback;
        subscriptions = state.subscriptions;
		publications = state.publications;
        history = state.history;

        //winston.info('Performing configuration migration');
        //migrateState(state.version);
		saveState();
        process.nextTick(next);
    },
    function connectToMQTT (next) {
		// Default protocol
		if (!url.parse(config.mqtt.host).protocol) {
			config.mqtt.host = 'mqtt://' + config.mqtt.host;
		}		
        winston.info('Connecting to MQTT at %s', config.mqtt.host);
        client = mqtt.connect(config.mqtt.host, config.mqtt);
        client.on('message', parseMQTTMessage);
        client.on('connect', function () {
            if (subscriptions.length > 0) {
                client.subscribe(subscriptions);
            }
            next();
            // @TODO Not call this twice if we get disconnected
            next = function () {};
        });
    },
    function configureCron (next) {
        winston.info('Configuring autosave');

        // Save current state every 15 minutes
        setInterval(saveState, 15 * 60 * 1000);

        process.nextTick(next);
    },
    function setupApp (next) {
        winston.info('Configuring API');

        // Accept JSON
        app.use(bodyparser.json());

        // Log all requests to disk
        app.use(expressWinston.logger({
            transports: [
                new logger.transports.File({
                    filename: ACCESS_LOG,
					timestamp: function () {
						var date = new Date();
						return date.toLocaleString(); 
					},
                    json: false
                })
            ]
        }));

        // Push event from SmartThings
        app.post('/push',
            expressJoi({
                body: {
                    //   "name": "Energy Meter",
                    name: joi.string().required(),
                    //   "value": "873",
                    value: joi.string().required(),
                    //   "type": "power",
                    type: joi.string().required()
                }
            }), handlePushEvent);

        // Subscribe event from SmartThings
        app.post('/subscribe',
            expressJoi({
                body: {
                    devices: joi.object().required(),
                    callback: joi.string().required()
                }
            }), handleSubscribeEvent);

        // Log all errors to disk
        app.use(expressWinston.errorLogger({
            transports: [
                new logger.transports.File({
                    filename: ERROR_LOG,
					timestamp: function () {
						var date = new Date();
						return date.toLocaleString(); 
					},
                    json: false
                })
            ]
        }));

        // Proper error messages with Joi
        app.use(function (err, req, res, next) {
            if (err.isBoom) {
                return res.status(err.output.statusCode).json(err.output.payload);
            }
        });

        app.listen(config.port, next);
    }
], function (error) {
    if (error) {
        return winston.error(error);
    }
    winston.info('Listening at http://localhost:%s', config.port);
});
