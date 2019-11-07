# Windows Installation Instructions

1. On windows command propmt (I usually run in administrative mode) cd into the nodejs directory (C:\Program Files\nodejs)
2. from that directory run npm install -g mqtt-bridge-smartthings
	This will automatically download all the dependencies and setup - other installed packages should not make a difference.
3. In windows explorer go to C:\Program Files\nodejs\node_modules\mqtt-bridge-smartthings and there create ‘config.yml’ and ‘devices.yml’ for your situation using the examples.
4. Go back to the command prompt and from the same directory C:\Program Files\nodejs run the command mqtt-bridge-smartthings

this should start the server and you should see the logging on the console.
Then follow the directions for smartthings IDE to set up the device handlers, the bridge and the smartapp.
Once you are comfortable everything is working fine you an set the process to run as a service (See the windows service folder for the readme) so it automatically stars and everything should be handled automatically.

