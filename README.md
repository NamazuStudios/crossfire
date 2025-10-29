# Namazu Crossfire

This is an extension of Namazu Elements to enable CrossPlay games. Crossfire consists of two major components:

* Server - A Websocket-based signaling server which queries the Elements database for other eligible players. It also enables signaling between players to establish peer to peer netplay.
* Client - A Java-based client which communicates with the matchmaking and signaling services in order to establish connections between players and facilitate network play.

The Java-based implementation is suitable for Java-based applications. However, as few 
game engines support Java natively we provied a C# client for Unity and .NET applications
additionally, we are actively developing support for [Defold](https://defold.com/) and [GameMaker](https://gamemaker.io/).

More importantly, the Java implementation is used to run all integration tests to ensure proper connectivity. The implementation here serves as a reference implementation which others should follow. If there is any doubt or confusion over the correct behavior, we recommend checking the Java implementation.

For a detailed overview of Crossfire's protocol, refer to the protocol document [Protocol Specification](PROTOCOL.md).

We chose to release Crossfire under the MIT License to encourage studios to hack, extend, and modify Crossfire to suit their needs. We believe that Crossfire provides a solid foundation for building multiplayer games, and we hope that by releasing it under a permissive license, we can foster a community of developers who can contribute to its growth and improvement. Namazu Studios will provide paid support for Namazu Crossfire and Namazu Elements. Please check out our website for more information.

## Communication Modes

Crossfire supports two modes of communication between players, at the time of writing. The Java client interface provides implementations which should be easily swapped out in production code.

### WebRTC Peer to Peer

WebRTC is a high performance peer to peer protocol which is supported natively in web browsers. WebRTC is the preferred method of communication for games as it provides low latency connections between players without the need for a dedicated server. Originally developed for video conferencing, it is a standards based protocol for real-time communication and was developed by Google. WebRTC established a secure and encrypted connection between players, ensuring that data transmitted between them is protected from eavesdropping and tampering. It provides built-in NAT traversal and can support voice and video on top of raw data. Note, voice and video is not supported by this version of the Crossfire Client, but could be extended to do so using standard WebRTC libraries.

In order for WebRTC to function properly, it requires a process called signaling to establish the initial connection between players. Signaling involves the exchange of metadata and network information between players, which is used to set up the WebRTC connection. This process is typically handled by a signaling server, which acts as an intermediary between players. Crossfire's Websocket protocol provides this process and uses signaling to inform all connected peers of disconnect/connect events, as well as host reassignment.

[More Information on WebRTC](https://webrtc.org/)

### Signaling / WebSockets

WebSockets is a protocol that enables real-time, bidirectional communication between a client and a server over a single, long-lived connection. It is designed to be lightweight and efficient, making it ideal for applications that require low latency and high throughput, such as multiplayer games. Websockets are TCP based and therefore introduce overhead which may not be suitable for some games. However, Websockets are widely supported and can be used in a variety of applications. The advantage to Websockets is that it is a simple protocol to implement and can be used in conjunction with other protocols, such as WebRTC, to provide a complete solution for real-time communication. The disadvantage to Websockets is that it requires a dedicated server to handle the connections, which can introduce latency and increase costs.

Crossfire's protocol provides Websocket based messages to facilitate data exchanges between players. The server simply forwards client information from one player to another or can broadcast a message to all players in a match. This is useful for games which do not require low latency connections or for players who are unable to establish a WebRTC connection due to network restrictions.

[More Information on WebSockets](https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API)

## Planned Future Features

We have many features on the roadmap for Crossfire to enables more robust multiplayer arechitectures.

### Unity Multiplay

Multiplay is a game server hosting platform that provides scalable, reliable, and flexible solutions for multiplayer games. It enables developers to host and manage game servers globally, ensuring low latency and high availability for players. We are actively working on integrating support for authoritative matches using Multiplay. This involves leveraging Multiplay's infrastructure to host dedicated game servers that enforce game rules, manage player connections, and maintain the integrity of matches. By utilizing Multiplay's scalability, we aim to provide a robust solution for hosting authoritative matches, ensuring fair gameplay and reducing the reliance on peer-to-peer connections.

[More Information Multiplay](https://www.unity.com/products/multiplay)

### EdgeGap

EdgeGap is a platform that provides edge computing solutions for hosting multiplayer game servers. It leverages a distributed network of edge locations to minimize latency, improve performance, and enhance the gaming experience for players. We are actively working on integrating support for authoritative matches using EdgeGap. This involves utilizing EdgeGap's edge computing infrastructure to deploy dedicated game servers closer to players, ensuring low latency and high availability. By leveraging EdgeGap's dynamic orchestration capabilities, we aim to provide a scalable and efficient solution for hosting authoritative matches, ensuring fair gameplay and reducing the reliance on centralized server locations.

[More Information on EdgeGap](https://edgegap.com/)

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

