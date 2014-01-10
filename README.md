VirtualBukkit
=============

'Virtual host' server for Minecraft servers

Build
-----

Use eclipse to export a JAR


Configuration
-------------

RealVirtualBukkit looks for vhosts.properties in the current directory. This file should have entries of the form:

  * Listen <port>
  * Listen <host> <port>
  * Default none
  * Default <port>
  * Default <host> <port>
  * VirtualHost <name> <port>
  * VirtualHost <name> <host> <port>

If no `Listen` entry is found, the server will listen on 0.0.0.0:25565. If a default host is specified, all connections with no assigned virtual host will be forwarded, otherwise they will be ignored.
