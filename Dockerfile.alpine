FROM node:alpine
MAINTAINER Sandeep Gupta <tech.gfamily@gmail.com>

# Create our application direcory
RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app

# Expose Configuration Volume
VOLUME /mbs/config

# Copy and install dependencies
COPY package.json /usr/src/app/
COPY *.yml /mbs/config/
RUN npm install --production

# Copy everything else
COPY . /usr/src/app

# Set config directory
ENV CONFIG_DIR=/mbs/config

# Expose the web service port
EXPOSE 8080

# Run the service
CMD [ "npm", "start" ]
