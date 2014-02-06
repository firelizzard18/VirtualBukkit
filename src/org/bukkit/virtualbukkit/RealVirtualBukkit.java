/*
 * RealVirtualBukkit.java v. 1.2
 * 
 * Ethan Reesor			01/09/2014	v. 1.0
 * Nicholas Harrell		02/05/2014	v. 1.2
 * 
 * Parametric operations, including loading and
 * recall of configuration settings.
 * 
 * Default configuration location is %workingDirectory%/vhosts.config
 * 
 * For real-time (packet-handling) and connect/
 * disconnect operations, see VirtualBukkit.java
 *
 */
package org.bukkit.virtualbukkit;
 
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RealVirtualBukkit extends VirtualBukkit
{
	private InetSocketAddress laddr = null;	//The socket on which this service listens.
	private InetSocketAddress ospp = null;	//"Old-School Ping Pong", the rudimentary and
											//	unreliable way in which pre-1.7 clients
											//	begin the server handshake.
											//ALL pre-1.7 clients will be routed here,
											//	regardless of what domain or host they
											//	entered.
	
	private String vhostsFilePath = "";		//The vhosts file path is placed here upon
											//	attempting to load configuration.
											//This is for reporting, upon fatal error,
											//	precisely where the service sought the
											//	file.
	
	private boolean didReadConfig = true;	//Whether the config file was successfully
											//	loaded.
	
	//The list of domain/host => socket associations.
	private Map<String, InetSocketAddress> vhosts = new ConcurrentHashMap<String, InetSocketAddress>();
	
	{
		//Read from the file "vhosts.config" in the local directory...
		BufferedReader br = null;
		try {
			String jarPath = RealVirtualBukkit.class.getProtectionDomain().getCodeSource().getLocation().getPath();
			File vhostsFile = new File(new File(jarPath).getParentFile(), "vhosts.config");
			vhostsFilePath = vhostsFile.getPath();
			br = new BufferedReader(new InputStreamReader(new FileInputStream(vhostsFile)));
			
			String line, tokens[];
			while ((line = br.readLine()) != null)
			//For each line...
			{
				//Split the line by spaces.
				tokens = line.split("\\s");
				
				//Ignore blank lines...
				if (tokens.length < 1)
					continue;
				
				//
				//	LISTEN
				//
				//On finding a line begining with "Listen",
				//	begin specification of this service's
				//	listening port.
				//Syntax: "Listen [host] <port>"
				if (tokens[0].equalsIgnoreCase("Listen"))
				{
					//Set default socket options.
					String host = "0.0.0.0";
					Integer port = null;	//Will specify...
					
					//If only two tokens, read the port from the
					//	second token, index 1.
					//Use the default host, set above.
					if (tokens.length == 2)
						port = Integer.valueOf(tokens[1]);
					//If three tokens, read host and port.
					else if (tokens.length == 3)
					{
						host = tokens[1].toUpperCase();
						port = Integer.valueOf(tokens[2]);
					}
					//In case of a wrong number of tokens, throw an error.
					else
						throw new Exception("[ERROR]       Configuration error on line begining with \"Listen\"; should be \"Listen [host] <port>\".");
					
					//Create the listening socket address.
					this.laddr = new InetSocketAddress(host, port);
					
					
				}
				//
				//	DEFAULT
				//
				//Upon finding a line begining with "Default",
				//	begin specification of this service's
				//	default destination port.
				//Only one of these can be used. In case
				//	of multiple valid specifications, the first
				//	one will be used.
				//Syntax: "Default [host] <port>"
				else if (tokens[0].equalsIgnoreCase("Default"))
				{
					//Set default socket options.
					String host = "localhost";
					Integer port = null;	//Will specify...
					
					//If only two tokens, read the port from the
					//	second token, index 1.
					//Use the default host, set above.
					if (tokens.length == 2)
					{
						//If the default is specified to be none,
						//	skip specification of this service's
						//	default destination port.
						if (tokens[1].equalsIgnoreCase("none"))
							continue;
						
						port = Integer.valueOf(tokens[1]);
						
						//In case of an invalid port, ignore this
						//	line in the configuration.
						if (port < 0)
							continue;
					}
					//In case of three tokens, read host and port.
					else if (tokens.length == 3)
					{
						host = tokens[1];
						port = Integer.valueOf(tokens[2]);
					}
					//In case of a wrong number of tokens, throw an error.
					else
						throw new Exception("[ERROR]       Configuration error on line begining with \"Default\"; should be \"Default [host] <port>\".");
					
					//Add the (default) socket to the list of domain/host => socket
					//	associations without specifying a domain/host.
					vhosts.put("", new InetSocketAddress(host, port));
					
					
				}
				//
				//	PRE-1.7
				//
				//Upon finding a line begining with "PRE-1.7",
				//	begin specification of this service's
				//	sole pre-1.7 destination port.
				//Only one of these can be used. In case
				//	of multiple valid specifications, the first
				//	one will be used.
				//Syntax: "PRE-1.7 [host] <port>"
				else if (tokens[0].equalsIgnoreCase("PRE-1.7"))
				{
					//Set default socket options.
					String host = "localhost";
					Integer port = null;	//Will specify...
					
					//If only two tokens, read the port from the
					//	second token, index 1.
					//Use the default host, set above.
					if (tokens.length == 2)
					{
						port = Integer.valueOf(tokens[1]);
						if (port < 0)
							continue;
					}
					//In case of three tokens, read host and port.
					else if (tokens.length == 3)
					{
						host = tokens[1];
						port = Integer.valueOf(tokens[2]);
					}
					else
					//In case of a wrong number of tokens, throw an error.
						throw new Exception("[ERROR]       Configuration error on line begining with \"PRE-1.7\"; should be \"PRE-1.7 [host] <port>\".");
					
					//Add the socket to the list of domain/host => socket
					//	associations.
					this.ospp = new InetSocketAddress(host, port);
					
					
				}
				//
				//	VIRTUALHOST
				//
				//Upon finding a line begining with "VirtualHost",
				//	begin specification of this service's
				//	virtual host destination ports.
				//Any number of these can be specified.
				//Syntax: "VirtualHost <name> [host] <port>"
				else if (tokens[0].equalsIgnoreCase("VirtualHost"))
				{
					//Set default host options.
					String key = null;			//Will specify...
					String host = "localhost";
					Integer port = null;		//Will specify...
					
					//If only three tokens, read the key from the
					//	second token (index 1) and the port from
					//	token index 2.
					//Use the default host, set above.
					//Virtualhost names ("key") are set to uppercase,
					//	so that all incoming requests can be capitalized
					//	to facilitate a case-insensitve comparison.
					//For example, "here.there.com" will thusly match
					//	"HERE.THere.coM".
					if (tokens.length == 3)
					{
						key = tokens[1].toUpperCase();
						port = Integer.valueOf(tokens[2]);
					}
					//In case of four tokens, read key, host and port.
					else if (tokens.length == 4)
					{
						key = tokens[1].toUpperCase();
						host = tokens[2];
						port = Integer.valueOf(tokens[3]);
					}
					else
					//In case of a wrong number of tokens, throw an error.
						throw new Exception("[ERROR]       Configuration error on line begining with \"VirtualHost\"; should be \"VirtualHost <name> [host] <port>\".");
					
					//Add the socket to the list of domain/host => socket
					//	associations.
					vhosts.put(key, new InetSocketAddress(host, port));
				}
			}//Finish reading lines from configuration.
			
			//If no listening address was ever set, use the
			//	Minecraft default of 25565.
			if (this.laddr == null)
				this.laddr = new InetSocketAddress("localhost", 25565);
		}
		catch (Exception e)	//Any problems with reading the configuration file...
		{
			didReadConfig = false;
			System.err.println("[ERROR]       Error reading configuration file:\n" + e);
		}
		finally
		{
			try
			{
				//Close configuration file reading.
				if (br != null)
					br.close();
			}
			catch (IOException e)	//Any problems with closing the configuration file...
			{
				System.err.println("[ERROR]       Error closing file: " + e);
			}
		}
		
		//Check that the listening address has been set somehow.
		//If it has not been set, kill this service.
		if (listeningAddress() == null)
		{
			System.err.println("[ERROR-FATAL] Could not load config file at " + vhostsFilePath + " : file missing or obstructed.");
			System.exit(-1);
		}
		
		//Initial bootup output, reporting what's going on.
		System.out.println("[BOOT]        Startup successful." + ((didReadConfig) ? " Configuration loaded from " + vhostsFilePath : ""));
		System.out.println("[BOOT]        Listening on " + listeningAddress());
		
		//Check for a default configuration.
		//In case of a missing default, the service will continue anyway.
		if (vhosts.get("") != null)
			System.out.println("[BOOT]        Default destination socket is " + vhosts.get(""));
		else
		{
			System.out.println("\n[WARN]        No default destination socket set!");
			System.out.println("[WARN]        CAUTION: Advise adding a default config to vhosts.config.");
			System.out.println("[WARN]        Syntax: \"Default [host] <port>\"");
			System.out.println("[WARN]        This service will proceed regardless...\n");
		}
		
		//Report all loaded domain/host => socket associations.
		for (Map.Entry<String, InetSocketAddress> entry : vhosts.entrySet())
			if (!entry.getKey().equals(""))
				System.out.println("[BOOT]        Virtual host " + entry.getKey() + " => " + entry.getValue());
		
		//Notify that loading is complete.
		System.out.println("\n[BOOT]        ==========\t[BOOT]        Loading complete.\n");
	}
	
	//Return the listening socket.
	public InetSocketAddress listeningAddress()
	{
		return this.laddr;
	}
	
	//Return the Pre-1.7 destination socket.
	public InetSocketAddress oldSchoolPingPongAddress()
	{
		return this.ospp;
	}
	
	//Resolve a domain (virtual host) to a destination
	//	socket. If no explicit entry exists, use
	//	the default.
	//Hostnames sent to this address are capitalized;
	//	all virtual host keys are stored in all-caps.
	//For example, "here.there.com" will thusly match
	//	"HERE.THere.coM".
	public InetSocketAddress addressForVirtualHost(String hostname)
	{
		InetSocketAddress addr = vhosts.get(hostname.toUpperCase());
		
		if (addr == null)
			addr = vhosts.get("");
		
		return addr;
	}
	
	//This initiates the entire program.
	//Execution begins here.
	public static void main(String[] args)
	{
		new RealVirtualBukkit().run();
	}
}
