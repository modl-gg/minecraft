modl.gg Minecraft Plugin
-

Multi-platform (Velocity, BungeeCord, Spigot, Fabric) plugin to connect to modl backend with extensive in-game functionality and interface. All dependencies are downloaded, hash-checked, and relocated during runtime.
Plugin communicates with backend through web sockets and REST API requests, handled with proper concurrent design. The plugin can load into core or bridge mode, the latter intended to be ran on backend Spigot servers
for networks running behind a Velocity/BungeeCord proxy (core to be ran on proxy). For servers without a proxy, core mode can be ran directly on the Spigot/Fabric server. Bridge servers and the proxy plugin communicate
using a TCP query server on the proxy (configure your firewalls accordingly) to broadcast changes in state and enable many of the world-interacting features (freeze, staffmode, replays, etc) synced across your entire
network.  


### This plugin uses the following in-house libraries: 
For backend socket communication: https://github.com/modl-gg/proto \
For replays Fabric compat: https://github.com/modl-gg/minecraft-packetevents \
For replays web-viewable format: https://github.com/modl-gg/minecraft-replay-format \
For replays logic: https://github.com/modl-gg/minecraft-replay-recording \
For cross-platform GUIs (proxy-native): https://github.com/modl-gg/minecraft-cirrus 

---
We welcome any and all contributions and will work with contributors on most PRs within reason. \
*** **Note: please keep PRs specific and concise; large or sprawling PRs are difficult to review and will likely be rejected.**
