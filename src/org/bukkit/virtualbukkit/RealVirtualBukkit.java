package org.bukkit.virtualbukkit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RealVirtualBukkit extends VirtualBukkit {
	private InetSocketAddress laddr = null, ospp = null;
	private Map<String, InetSocketAddress> vhosts = new ConcurrentHashMap<String, InetSocketAddress>();
	
	{
		BufferedReader br = null;
		try {
			String jarPath = RealVirtualBukkit.class.getProtectionDomain().getCodeSource().getLocation().getPath();
			File vhostsFile = new File(new File(jarPath).getParentFile(), "vhosts.properties");
			br = new BufferedReader(new InputStreamReader(new FileInputStream(vhostsFile)));
			
			String line, bits[];
			while ((line = br.readLine()) != null) {
				bits = line.split("\\s");
				
				if (bits.length < 1)
					continue;
				
				if ("Listen".equalsIgnoreCase(bits[0])) {
					String host = "0.0.0.0";
					Integer port = null;
					
					if (bits.length == 2) {
						port = Integer.valueOf(bits[1]);
					} else if (bits.length == 3) {
						host = bits[1];
						port = Integer.valueOf(bits[2]);
					} else
						throw new Exception("Listen [host] <port>");
					
					this.laddr = new InetSocketAddress(host, port);
					
					
				} else if ("Default".equalsIgnoreCase(bits[0])) {
					String host = "localhost";
					Integer port = null;
					
					if (bits.length == 2) {
						if ("none".equalsIgnoreCase(bits[1]))
							continue;
						port = Integer.valueOf(bits[1]);
						if (port < 0)
							continue;
					} else if (bits.length == 3) {
						host = bits[1];
						port = Integer.valueOf(bits[2]);
					} else
						throw new Exception("Default [host] <port>");
					
					vhosts.put("", new InetSocketAddress(host, port));
					
					
				} else if ("OldschoolPingPong".equalsIgnoreCase(bits[0])) {
					String host = "localhost";
					Integer port = null;
					
					if (bits.length == 2) {
						port = Integer.valueOf(bits[1]);
						if (port < 0)
							continue;
					} else if (bits.length == 3) {
						host = bits[1];
						port = Integer.valueOf(bits[2]);
					} else
						throw new Exception("OldschoolPingPong [host] <port>");
					
					this.ospp = new InetSocketAddress(host, port);
					
					
				} else  if ("VirtualHost".equalsIgnoreCase(bits[0])) {
					String key = null;
					String host = "localhost";
					Integer port = null;
					
					if (bits.length == 3) {
						key = bits[1];
						port = Integer.valueOf(bits[2]);
					} else if (bits.length == 4) {
						key = bits[1];
						host = bits[2];
						port = Integer.valueOf(bits[3]);
					} else
						throw new Exception("VirtualHost <name> [host] <port>");
					
					vhosts.put(key, new InetSocketAddress(host, port));
				}
			}
			
			if (this.laddr == null)
				this.laddr = new InetSocketAddress("localhost", 25565);
		} catch (Exception e) {
			System.err.println("Error reading file: " + e);
		} finally {
			try {
				if (br != null)
					br.close();
			} catch (IOException e) {
				System.err.println("Error closing file: " + e);
			}
		}
		
		if (listeningAddress() == null) {
			System.err.println("Could not load config file, exiting");
			System.exit(-1);
		}
		
		System.out.println("Listening on " + listeningAddress());
		if (vhosts.get("") != null)
			System.out.println("Default host is " + vhosts.get(""));
		for (Map.Entry<String, InetSocketAddress> entry : vhosts.entrySet())
			if (!entry.getKey().equals(""))
				System.out.println("Virtual host " + entry.getKey() + " => " + entry.getValue());
	}
	
	public InetSocketAddress listeningAddress() {
		return this.laddr;
	}
	
	public InetSocketAddress oldSchoolPingPongAddress() {
		return this.ospp;
	}
	
	public InetSocketAddress addressForVirtualHost(String hostname) {
		InetSocketAddress addr = vhosts.get(hostname);
		
		if (addr == null)
			addr = vhosts.get("");
		
		return addr;
	}
	
	public static void main(String[] args) {
		new RealVirtualBukkit().run();
	}
}
