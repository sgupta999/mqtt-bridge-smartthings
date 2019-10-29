/*global describe, it */
var server;

describe('MQTT Bridge Test Case', function () {
    describe('mbs-server', function () {
        it('start the service', function (done) {
            server = require('../mbs-server');
            done();
        });
    });
});
