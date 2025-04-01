# Crossfire Pre-Release

This is a pre-release version of CrossFire which is an extension of Elements
to enable WebRTC based Cross Play. This adds a Websocket endpoint which will
ensure that a WebRTC session can exchange the SDP messages to establish a 
data channel session.

Additionally, it runs the complete Elements API.

This is based on our example project, which is found here https://github.com/Elemental-Computing/element-example

## Quick Start

Running the Elements SDK Requires the following tools:

* Intellij IDEA - https://www.jetbrains.com/idea/
  * It is not tested, but should run in Eclipse as well https://eclipseide.org/
* MongoDB or Docker (running MongoDB)
  * Docker https://www.docker.com/ (Recommended)
  * MongoDB https://www.mongodb.com/

Run the following to quick-start MongoDB using Docker:

* Within IntelliJ: View -> Tool Windows -> Terminal (or hit Alt+F12)
* Enter the following command:
```aiignore
cd services-dev
docker-compose up -d
```
You should see:
```aiignore
[+] Running 2/2
 ✔ Container services-dev-rs-init-1  Started                                   
 ✔ Container services-dev-mongo-1    Started  
```

## Running the Server

* Expand src/test/java
* Right click "Main"
* Select "Debug"

**Note:** You will need to set up the application for the first run and restart
the server. To do this:

* Login with the default credentials:
  * Visit http://localhost:8080/admin
  * User: root
  * Password: example
* Click "Applications"
* Click "Add Application"
  * Set the name to "example" (this is important)
  * Set the description to "Example" (this can be anything)
* Hit "OK"
* Stop Main and restart in the IDE

