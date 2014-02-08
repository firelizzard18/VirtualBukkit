/*
 * VirtualBukkit.java v. 1.2
 * 
 * Ethan Reesor			01/09/2014	v. 1.0
 * Nicholas Harrell		02/05/2014	v. 1.2
 * 
 * Real-time operations, including packet-sniffing,
 * routing, and reporting.
 * 
 * Connect/disconnect operations occur here.
 * 
 * For configuration and parametric operations,
 * see RealVirtualBukkit.java
 *
 */
 package org.bukkit.virtualbukkit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public abstract class VirtualBukkit extends Thread
{
	//Whether continuous handling (listening and routing)
	//	is currently happening.
	private volatile boolean running = false;
	
	/* See RealVirtualBukkit.java */
	/**/ public abstract InetSocketAddress listeningAddress();
	/**/ public abstract InetSocketAddress oldSchoolPingPongAddress();
	/**/ public abstract InetSocketAddress addressForVirtualHost(String hostname);
	/**/ public abstract Connection SQLconnection();
	
	//Check whether this thread is currently handling
	//	packet operations.
	public boolean isRunning()
	{
		return this.running;
	}
	
	//End this this thread and its operations.
	//this.running is the sole condition of safeRun().
	//Calling this method terminates this thread's
	//	permission to run, listen, and route.
	public void terminate()
	{
		synchronized (this) { this.running = false; }
	}
	
	//Begin execution.
	@Override
	public final void run()
	{
		try
		{
			//Loop:
			//Begin listening on the listening port and
			//	handling incoming packets.
			//run() catches Exceptions from safeRun(),
			//	allowing the program to continue in
			//	case of error.
			this.safeRun();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	//Execute this service's listening loop.
	//The loop herein executes under the condition
	//	while(this.isRunning()).
	//Termination of this thread should occur via
	//	void terminate().
	protected void safeRun() throws Exception
	{
		//Note that continuous handling has begun.
		synchronized (this) { this.running = true; }
		
		//Acquire listening socket.
		ServerSocket ss = new ServerSocket(this.listeningAddress().getPort(), 10, this.listeningAddress().getAddress());
		
		//While this thread has permission to run...
		//(this.running)
		//Call terminate() to revoke permission and halt.
		while (this.isRunning())
		{
			//Listen on the socket...
			Socket s = ss.accept();
			
			//Prepare an array of bytes into which
			//	to load the incoming packet.
			byte[] b = new byte[512];
			
			//Read the incoming packet into byte[] b.
			//Store b.length as int r.
			int r = s.getInputStream().read(b);
			
			// [scaffold]
			//System.out.println(b[0] + " " + b[1] + " "  + b[2] + " "  + b[3]);
			
			//If the packet was actually read (it has
			//	length), handle it.
			if (r > 0)
				this.handle(s, b, r);
		}
		
		//Upon revocation of listening permission (signified
		//	by boolean this.running = false) close the listening
		//	port and conclude execution.
		ss.close();
	}
	
	//Handle an incoming packet. The source socket (this service's
	//	listening socket), the packet itself, and the packet's
	//	length are all passed to this method.
	protected void handle(final Socket src, final byte[] init, final int len)
	{
		//Fork packet handling to a new (concurrent) process.
		new Thread()
		{
			{
				this.setDaemon(true);
			}
			
			@Override
			public void run()
			{							
				try
				{
					// [scaffold]
					String user = null;
					String host = null;
					
					//On detection of Old-School Ping-Pong packet
					//	(pre-1.7-ping packet)...
					if (init[0] == (byte)0xFE)
					{
						//Refer to the Pre-1.7-ping specification. 
						InetSocketAddress ospp = VirtualBukkit.this.oldSchoolPingPongAddress();
						
						//If no Pre-1.7-ping specification exists, fail to route the packet.
						if (ospp == null)
						{
							System.out.println("[NOTICE]      Failed to route ping packet from " + src.getInetAddress() + ":" + src.getPort() + "; no Pre-1.7 destination socket set.");
							return;
						}
						
						//Create the destination socket...
						Socket dst = new Socket(ospp.getAddress(), ospp.getPort());
						
						//Route this packet.
						dst.getOutputStream().write(init, 0, len);
						handle(src, dst);
						return;
					}
					
					//On detection of a pre-1.7 connection packet,
					//	distill from it the target hostname.
					else if (init[0] == 2)
					{
						int offset = 2;
						int strlen = init[offset++] << 8 | init[offset++];

						//Sure
						user = new String(init, offset, strlen * 2, "UTF-16");
						
						offset += 2 * strlen;
						strlen = init[offset++] << 8 | init[offset++];
						host = new String(init, offset, strlen * 2, "UTF-16");
					}
					
					//On detection of a 1.7+ connection packet,
					//	distill from it the target hostname.
					else if (len - 1 == init[0] && init[1] == 0)
					{
						int offset = 3;
						int strlen = init[offset++];
						
						//Not sure...
						user = new String(init, offset, strlen * 2, "UTF-8");
						
						host = new String(init, offset, strlen, "UTF-8");
					}
					
					//On detection of an unrecognizable connection
					//	packet, fail to route it and notify the
					//	output stream.
					else
					{
						System.err.print("[FAIL]        Unknown inital packet recieved from " + src.getInetAddress() + ":" + src.getPort() + "; closing connection: ");
						System.out.print("\n                     ");
						for (int i = 0; i < len; i++)
							if (20 <= init[i] && init[i] < 127)
								System.out.print(String.format("%c", init[i]));
							else
								System.out.print('.');
						System.out.println();
						System.out.print("\n                      ");
						for (int i = 0; i < len; i++) {
							System.out.print(String.format("%02x", init[i]));
							if (i % 4 == 3)
								System.out.print(' ');
						}
						System.out.println();
						System.out.println("                      init[0] should = " + len + " but was = " + init[0]);
						src.close();
						return;
					}
					
					System.out.println("[CONNECT]     Connection attempt from user \"" + user + "\" at " + src.getInetAddress() + ":" + src.getPort() + " to \"" + host + "\"...");
					
					//Resolve the solicited hostname to a socket from the
					//	domain/host => socket association table.
					InetSocketAddress addr = VirtualBukkit.this.addressForVirtualHost(host);
					
					//If no match was found and the program does not know where
					//	to send the packet, fail to route it and notify the
					//	output stream.
					if (addr == null)
					{
						System.err.println("[DROP]        No server was found for the solicited host \"" + host + "\"; the connection will be dropped.");
						src.close();
						return;
					}

					System.out.println("[CONNECT]     Forwarding client " + user + " (" + src.getInetAddress() + ":" + src.getPort() + ") to " + addr + " (" + host + ")");
					
					//Execute database entry.
					Statement stmt = null;
					String query = "INSERT INTO bc_logins VALUES('" + user + "','" + ((host.toUpperCase().contains("FTB")) ? "FTB" : "VLA") + "','" + src.getInetAddress().toString().substring(1) + "',null)";
					try
					{
						stmt = VirtualBukkit.this.SQLconnection().createStatement();
						stmt.executeUpdate(query);
					}
					catch (SQLException e)
					{
						System.err.println("[ERROR]       Error querying database to log connection attempt:");
						e.printStackTrace();
					}
					finally
					{
						if(stmt != null)
							stmt.close();
					}
					
					Socket dst = new Socket(addr.getAddress(), addr.getPort());
					
					dst.getOutputStream().write(init, 0, len);
					handle(src, dst);
				} catch (Exception e) {
					System.err.println("[ERROR]       Exception ocurred; connection from " + src.getInetAddress() + ":" + src.getPort() + " will be dropped:");
					System.out.println("              " + e);
					
					try {
						if (!src.isClosed())
							src.close();
					} catch (IOException ioe) {
						System.err.println("[ERROR]       Error closing socket from " + src.getInetAddress() + ":" + src.getPort() + ";");
						System.out.println("              " + ioe);
					}
				}
			}
		}.start();
	}
	
	//Handle packet routing...
	protected void handle(final Socket src, final Socket dst)
	{
		//CLIENT => SERVER
		new Thread()
		{
			{
				this.setDaemon(true);
			}
			@Override
			public void run()
			{
				try
				{
					InputStream in = src.getInputStream();
					OutputStream out = dst.getOutputStream();
					
					int r = 0;
					byte[] b = new byte[512];
					while ((r = in.read(b)) > -1)
						out.write(b, 0, r);
				}
				catch (Exception e)
				{
					System.err.println("[DISCONNECT]  Socket " + src.getInetAddress() + ":" + src.getPort() + " => " + dst.getInetAddress() + ":" + dst.getPort() + " closed: " + e);
					//SQL action on disconnect not implemented;
					//	Disconnect detection does not seem to be working reliably.
					try
					{
						if (!src.isClosed())
							src.close();
					}
					catch (IOException ioe)
					{
						System.err.println("[ERROR]       Error closing socket " + src.getInetAddress() + ":" + src.getPort() + " => " + dst.getInetAddress() + ":" + dst.getPort() + ": " + ioe);
					}
				}
			}
		}.start();
		
		//SERVER => CLIENT
		new Thread()
		{
			{
				this.setDaemon(true);
			}
			
			@Override
			public void run()
			{
				try
				{
					InputStream in = dst.getInputStream();
					OutputStream out = src.getOutputStream();
					
					int r = 0;
					byte[] b = new byte[512];
					while ((r = in.read(b)) > -1)
						out.write(b, 0, r);
				}
				catch (Exception e)
				{
					System.err.println("[DISCONNECT]  Socket " + src.getInetAddress() + ":" + src.getPort() + " <= " + dst.getInetAddress() + ":" + dst.getPort() + " closed: " + e);
					//SQL action on disconnect not implemented;
					//	Disconnect detection does not seem to be working reliably.
					try
					{
						if (!src.isClosed())
							src.close();
					}
					catch (IOException ioe)
					{
						System.err.println("[ERROR]       Error closing socket " + src.getInetAddress() + ":" + src.getPort() + " <= " + dst.getInetAddress() + ":" + dst.getPort() + ": " + ioe);
					}
				}
			}
		}.start();
	}
}
