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
const { createLogger, format } = require('winston');
const logform = require('logform');
const { combine, timestamp, label, printf } = logform.format;

var winston = require('winston'),
	daily = require('winston-daily-rotate-file'),
    express = require('express'),
    expressJoi = require('express-joi-validation').createValidator({}),
    expressWinston = require('express-winston'),
    bodyparser = require('body-parser'),
    mqtt = require('mqtt'),
    async = require('async'),
    url = require('url'),
    joi = require('@hapi/joi'),
    yaml = require('js-yaml'),
    jsonfile = require('jsonfile'),
    fs = require('fs-extra'),
	mqttWildcard = require('mqtt-wildcard'),
    request = require('request'),
	path = require('path'),
	
	CONFIG_DIR =  process.env.CONFIG_DIR || path.join(__dirname,'config'),
    SAMPLE_FILE = path.join(CONFIG_DIR, '_config.yml'),
    CONFIG_FILE = path.join(CONFIG_DIR, 'config.yml'),	
	DEVICE_CONFIG_FILE = path.join(CONFIG_DIR, 'devices.yml'),
    STATE_FILE = path.join(CONFIG_DIR, 'data', 'state.json'),
    STATE_SUMMARY_FILE = path.join(CONFIG_DIR, 'data', 'state.summary.json'),
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
	config = loadConfiguration(),	
	LOGGING_LEVEL = config.loglevel,

	app = express(),
    client,
    subscriptions = [],
	publications = {},
	subscribe = {},
    callback = '',
	devices = {},
	st_request = {},
    history = {},
  
	// winston transports
	consoleLog = new  winston.transports.Console(),
	eventsLog = new (winston.transports.DailyRotateFile)({
					filename: path.join(CONFIG_DIR, 'log', 'events-%DATE%.log'),
					datePattern: 'YYYY-MM-DD',
					maxSize: '5m',
					maxFiles: '10',
					json: false
				}),
	accessLog = new (winston.transports.DailyRotateFile)({
					filename: path.join(CONFIG_DIR, 'log', 'access-%DATE%.log'),
					datePattern: 'YYYY-MM',
					maxSize: '5m',
					maxFiles: '5',
					json: false
				}),
	errorLog = new (winston.transports.DailyRotateFile)({
					filename: path.join(CONFIG_DIR, 'log', 'error-%DATE%.log'),
					datePattern: 'YYYY-MM',
					maxSize: '5m',
					maxFiles: '5',
					json: false
				}),
	logFormat = combine(format.splat(), 
						timestamp({format:(new Date()).toLocaleString('en-US'),  format: 'YYYY-MM-DD HH:mm:ss A'}),
						printf(nfo => {return `${nfo.timestamp} ${nfo.level}: ${nfo.message}`;})
				);
				
	
  
winston = createLogger({
	level: LOGGING_LEVEL,
	format:  logFormat,
	transports : [eventsLog, consoleLog]
});   


function loadConfiguration () {
	if (!fs.existsSync(CONFIG_FILE)) {
		console.log('No previous configuration found, creating one');
		fs.writeFileSync(CONFIG_FILE, fs.readFileSync(SAMPLE_FILE));
	}
	return yaml.safeLoad(fs.readFileSync(CONFIG_FILE));
}

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
        winston.info('ERROR loading external device configurations, continuing');
		return;
    }
	Object.keys(output).forEach(function (device) {		
		winston.debug("Loading config for Device " , device);
		Object.keys(output[device]["subscribe"]).forEach (function (attribute){			
			Object.keys(output[device]["subscribe"][attribute]).forEach (function (sub){
				let data = {};			
				data['device']= device;
				data['attribute'] = attribute;
				if ((!!output[device]["subscribe"][attribute][sub]) && (!!output[device]["subscribe"][attribute][sub]['command']))
					data['command']= output[device]["subscribe"][attribute][sub]['command'];	
				if (!subscribe[sub]) subscribe[sub] = {};	
				if (!subscribe[sub][device]) subscribe[sub][device] = {};
				subscribe[sub][device][attribute] = data;
				winston.debug("Subscription %s\t\[%s],[%s],[%s]",sub, subscribe[sub][device][attribute]['device'],
				subscribe[sub][device][attribute]['attribute'],subscribe[sub][device][attribute]['command']);
				winston.debug("Subscription: %s - Device %s" , sub, subscribe[sub]);
			});
		});
	});
	winston.info('============================ALL POSSIBLE SUBSCRIPTIONS FROM ALL EXTERNAL DEVICES ===========================================');
	Object.keys(subscribe).forEach(function (subs){
		Object.keys(subscribe[subs]).forEach(function (dev){
			Object.keys(subscribe[subs][dev]).forEach(function (attribute){
				winston.info("Subscription %s\t\[%s],[%s],[%s]",subs, subscribe[subs][dev][attribute]['device'],
				subscribe[subs][dev][attribute]['attribute'], JSON.stringify((!!subscribe[subs][dev][attribute]['command']) ? subscribe[subs][dev][attribute]['command'] : ''));
			});
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
			st_request:{},
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
		st_request: st_request,
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
		devices: (devices) ? Object.keys(devices) : devices
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
	if ((!!devices[device]) && (!!devices[device]["publish"]) && (!!devices[device]["publish"][attribute])){
		retain  = (!!devices[device].retain) ? devices[device].retain : retain;			
			winston.debug('ST** --> MQTT: [%s][%s][%s]\t[%s][%s][Retain:%s]', req.body.name, req.body.type, req.body.value, pub, value, retain);
		Object.keys(devices[device]["publish"][attribute]).forEach (function (pub){
			value = ((!!devices[device]["publish"][attribute][pub].command) && (!!devices[device]["publish"][attribute][pub].command[value]))
					? devices[device]["publish"][attribute][pub].command[value] : value;
			topic = pub;	
			winston.info('ST** --> MQTT: [%s][%s][%s]\t[%s][%s][Retain:%s]', req.body.name, req.body.type, req.body.value, topic, value, retain);
		});
	}else {
	// for devices with standard read, write, command suffixes
		topic = getTopicFor(device, attribute, TOPIC_READ_STATE);
		winston.debug('Device from SmartThings: %s = %s', topic, value);	
		winston.info('ST --> MQTT: [%s][%s][%s]\t[%s][%s][Retain:%s]', device, attribute, req.body.value, topic, value, retain);
	}
	mqttPublish(device, attribute, topic, value, retain, res);
}

function mqttPublish(device, attribute, topic, value, retainflag, res){	
    history[topic] = value;
	if ((!!publications) && (!publications[topic])){
		let data = {};
		data['device'] =  device;
		data['attribute'] =  attribute;
		data['command'] = value;
		publications[topic] = {};
		publications[topic][device] = data;
	} else if (!!publications[topic][device]) publications[topic][device]['command'] = value;
	var sub = isSubscribed(topic);
	if ((!!subscribe) && (!!subscribe[sub]) && (!!subscribe[sub][device]) && (!!subscribe[sub][device][attribute])) {
		winston.warn('POSSIBLE LOOP. Device[Attribute] %s[%s] is publishing to Topic %s while subscribed to Topic %s', device, attribute, topic, sub);
	} else if ((!!subscribe[sub]) && (!!subscribe[sub][device])) {
		winston.warn('POSSIBLE LOOP. Device %s is publishing to Topic %s while subscribed to Topic %s', device, topic, sub);
	}
    client.publish(topic, value, {retain: retainflag}, function () {
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
	let oldsubscriptions = subscriptions;
	st_request = req.body.devices;
	processSubscriptions(st_request);
    // Store callback
    callback = req.body.callback;
	// Store current state on disk    
	saveState();
	let unsubs 	= comparearrays(subscriptions, oldsubscriptions),
		subs 	= comparearrays(oldsubscriptions, subscriptions);
	if ((!!unsubs) && (unsubs.length > 0)) client.unsubscribe(unsubs);
	if ((!!subs) && (subs.length > 0)) {
		winston.info('We are mqtt subscribing');
			client.subscribe( subs , function () {
			res.send({
				status: 'OK'
			});
		});
	}
}

function processSubscriptions(req){	
	if (config.deviceconfig) {
		winston.info('Loading Device configuration');
		devices = loadDeviceConfiguration();
	}
	subscriptions = [];
    Object.keys(req).forEach(function (property) {
		winston.debug('Property - %s ', property);
        req[property].forEach(function (device) {
			winston.debug(' %s - %s ', property, device);			
			// CRITICAL - if device in DEVICE_CONFIG_FILE, file sub/pub info will supercedes
			if (!!devices && (!!devices[device])) {
				if ((!!devices[device]["subscribe"]) && (!!devices[device]["subscribe"][property])){	
					Object.keys(devices[device]["subscribe"][property]).forEach (function (sub){	
						if (!subscriptions.includes(sub)) subscriptions.push(sub);	
						winston.debug('Subscribing[CUSTOM] ', sub);
					});
				}
			}else {
				let data = {};			
				data['device']=device;
				data['attribute'] = property;
				let sub = getTopicFor(device, property, TOPIC_COMMAND);
				if (!subscriptions.includes(sub)) subscriptions.push(sub);
				if (!subscribe[sub]) subscribe[sub] = {};
				if (!subscribe[sub][device]) subscribe[sub][device] = {};
				subscribe[sub][device][property] = data;
				sub = getTopicFor(device, property, TOPIC_WRITE_STATE);
				if (!subscriptions.includes(sub)) subscriptions.push(sub);
				if (!subscribe[sub]) subscribe[sub] = {};
				if (!subscribe[sub][device]) subscribe[sub][device] = {};
				subscribe[sub][device][property] = data;
				winston.debug('Subscribing[R] ', sub);
			}
        });
    });
	// Subscribe to events	
    winston.info('===================================ACTUAL SUBSCRIPTIONS REQUESTED FROM SMARTAPP ============================================');
    winston.info('Currently subscribed to ' + subscriptions.join(', '));
    winston.info('============================================================================================================================');
}

function comparearrays(arr1, arr2){
	if (!arr2) return null;
	if (!arr1) return arr2;
	let newarray = [];
	arr2.forEach (function (sub){
		if (!arr1.includes(sub)) newarray.push(sub);
	});
	return newarray;
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
	if (!subscriptions) return null;
	var topics = [];
	for (let i=0; i< subscriptions.length; i++){
		if (subscriptions[i] == topic) {
			topics.push(subscriptions[i]);
		}else if (mqttWildcard(topic, subscriptions[i]) != null) topics.push(subscriptions[i]);		
	}
	return topics ;
}


/**
 * Parse incoming message from MQTT
 * @method parseMQTTMessage
 * @param  {String} topic   Topic channel the event came from
 * @param  {String} message Contents of the event
 */
function parseMQTTMessage (incoming, message) {
    var contents = message.toString();
    winston.debug('From MQTT: %s = %s', incoming, contents);
	var topics = isSubscribed(incoming);
	var device, property, cmd, value;
	if (!topics)  {
		winston.warn('%s-%s not subscribed. State error. Ignoring. ', incoming, contents);
		return;
	}
	if (topics.length > 1) winston.info('Incoming topic maps to multiple subscriptions: %s = %s', incoming, topics);
	// Topic is subscribe to
	if ((!!topics) && (!!subscribe)){
		topics.forEach(function(topic) {	
			Object.keys(subscribe[topic]).forEach(function(name) {	
				// Checking if  external device for this topic
				if (!!devices[name]){
					Object.keys(subscribe[topic][name]).forEach(function(attribute) {	
						device = subscribe[topic][name][attribute]['device'];
						property = subscribe[topic][name][attribute]['attribute'];
						value = contents;
						if ((!!subscribe[topic][name][attribute]['command']) && (!!subscribe[topic][name][attribute]['command'][contents])) 
							value = subscribe[topic][name][attribute]['command'][contents];
						cmd = true;	
						postRequest(topic, contents, device, property, value, cmd, incoming);
					});
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
					postRequest(topic, contents, device, property, value, cmd, incoming);
				}
			});
		});
	}	
}


function postRequest(topic, contents, device, property, value, cmd, incoming){	
		
	// If we subscribe to topic we are publishing we get into a loop
	if ((!!publications) && (!!publications[incoming]) && (!!publications[incoming][device]) && (!!publications[incoming][device][property])){
		winston.error('Incoming %s for attribute %s for device %s is also being published to: [%s][%s][%s].\nIgnoring: %s = %s', incoming, property, device, device, property, incoming, incoming, contents);
		return;		
	}
	history[incoming] = contents;
	var msg = (contents.length > 25) ?	contents.substr(0,25) + "..." : contents
	if (!topic.match(/[*+]/)) {
		winston.info('MQTT --> ST - Topic: [%s][%s]\t[%s][%s][%s]', topic, msg , device, property, value)
	} else { 
		winston.info('MQTT  --> MQTT[WildCard] - Topic: [%s][%s]\t[%s][%s]', incoming, msg, topic, msg);
		winston.info('MQTT[WildCard] --> ST - Topic: [%s][%s]\t[%s][%s][%s]', topic, msg , device, property, value)
	}
	retryRequest(device, property, value, cmd);
}

function retryRequest(device, property, value, cmd){
	var retries = 0;
	var success = false;
	while (!success && (retries < 5)){
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
						retries++;
					}else {
						success = true;
					}
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
        winston.info('Loading previous state');
        state = loadSavedState();
        callback = state.callback;
        subscriptions = state.subscriptions;
		publications = state.publications;
		st_request = state.st_request;
        history = state.history;
		if (!!st_request) {
			try {
				winston.info ('Last Request from ST - %s', JSON.stringify(st_request));
				processSubscriptions(st_request);
			} catch (ex) {
				winston.error(ex);
				winston.info('Could not restore subscriptions. Please rebuscribe in IDE, continuing');
				return;
			}
		}
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
				winston.info('Subscribing to - %s', subscriptions);
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
										format: logFormat,
										msg: "HTTP /{{req.method}} {{req.url}} host={{req.hostname}} --> Status Code={{res.statusCode}} ResponseTime={{res.responseTime}}ms",
										transports : [accessLog]
			}));
			
        // Push event from SmartThings
        app.post('/push',
            expressJoi.body(joi.object({
                    //   "name": "Energy Meter",
                    name: joi.string().required(),
                    //   "value": "873",
                    value: joi.string().required(),
                    //   "type": "power",
                    type: joi.string().required()
            })), handlePushEvent);

        // Subscribe event from SmartThings
        app.post('/subscribe',
            expressJoi.body(joi.object({
                    devices: joi.object().required(),
                    callback: joi.string().required()
            })), handleSubscribeEvent);

        // Log all errors to disk
        app.use(expressWinston.errorLogger({
										format: logFormat,
										msg: " [{{res.statusCode}} {{req.method}}] {{err.message}}\n{{err.stack}}",
										transports : [errorLog,consoleLog]
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