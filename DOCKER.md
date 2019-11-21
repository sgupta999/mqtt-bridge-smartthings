# Docker Setup
# On Windows - using Docker Desktop

Windows Defender Firewall with Advanced Security, the following rule needs to be created:

    Type: Inbound
    Program: C:\Program Files\Docker\Docker\resources\com.docker.backend.exe
    Allow all connections
	
Volume mapping
```-
make sure the directory that you are going to map to /mbs/config in docker has the appropriate config.yml and devices.yml
For e.g. for  "d:/data/docker/volumes/mbs:/mbs/config" the files need to be in d:/data/docker/volumes/mbs
```	
for alpine distro
```
docker pull mbs-alpine sgupta99/mqtt-bridge-smartthings:1.0.3-alpine
docker run -p:8080:8080 -v d:/data/docker/volumes/mbs:/mbs/config -e TZ=America/Chicago --name mbs-alpine sgupta99/mqtt-bridge-smartthings:1.0.3-alpine 
```
for raspberry pi distro
```
docker pull mbs-alpine sgupta99/mqtt-bridge-smartthings:1.0.3-rpi
docker run -p:8080:8080 -v d:/data/docker/volumes/mbs:/mbs/config -e TZ=America/Chicago --name mbs-rpi sgupta99/mqtt-bridge-smartthings:1.0.3-rpi
```
The RPI distro is about 768MB and Alpine is about 136MB - so if running on windows I would go for the alpine distro
# Some helpful commands for widows users not familiar with docker
```
docker image ls
docker image rm -f <image-id>
docker container ls -a
docker container stop <container-name>
docker container start -i <container-name> (starts in interactive mode
docker container rm <container-name>
Examples
docker stop mbs-alpine
docker start -i mbs-alpine
```
Container names in the above examples are mbs-alpine and mbs-rpi for example


# On linux platforms - (I have not tested then but should work)

make sure linux firewall rules are set appropriately

Volume mapping -
```
make sure the directory that you are going to map to /mbs/config in docker has the appropriate config.yml and devices.yml
For e.g. for  "/home/pi/docker/volumes/mbs:/mbs/config" the files need to be in /home/pi/docker/volumes/mbs
```	
for alpine distro
```
docker pull mbs-alpine sgupta99/mqtt-bridge-smartthings:1.0.3-alpine
docker run -p:8080:8080 \
		   -v /home/pi/docker/volumes/mbs:/mbs/config \
		   -e TZ=America/Chicago \
		   --name mbs-alpine \
		   sgupta99/mqtt-bridge-smartthings:1.0.3-alpine
```
for raspberry pi distro
```
docker pull mbs-alpine sgupta99/mqtt-bridge-smartthings:1.0.3-rpi
docker run -p:8080:8080 \
		   -v /home/pi/docker/volumes/mbs:/mbs/config \
		   -e TZ=America/Chicago \
		   --name mbs-rpi 
		   sgupta99/mqtt-bridge-smartthings:1.0.3-rpi
```	   
The RPI distro is about 768MB and Alpine is about 136MB - so I would still go for the alpine distro