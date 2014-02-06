package org.bukkit.virtualbukkit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public abstract class VirtualBukkit extends Thread {
	private volatile boolean running = false; // set true on start, will stop if set false
	
	public abstract InetSocketAddress listeningAddress(); // returns listening address
	public abstract InetSocketAddress oldSchoolPingPongAddress(); // returns pre-1.7 ping address
	public abstract InetSocketAddress addressForVirtualHost(String hostname); // returns address for host name
	
	// returns true if the service is running
	public boolean isRunning() {
		return this.running;
	}
	
	// terminates the service
	public void terminate() {
		synchronized (this) { this.running = false; }
	}
	
	@Override
	public final void run() {
		try {
			this.safeRun();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// called by run(), can throw exceptions
	protected void safeRun() throws Exception {
		// start running
		synchronized (this) { this.running = true; }
		
		// acquire the listening socket
		ServerSocket ss = new ServerSocket(this.listeningAddress().getPort(), 10, this.listeningAddress().getAddress());
		
		// until the service is terminated...
		while (this.isRunning())
			// accept and process connections
			this.handle(ss.accept());
		
		// close the listening socket
		ss.close();
	}
	
	protected void handle(final Socket src) {
		new Thread() {
			{
				this.setDaemon(true);
			}
			
			@Override
			public void run() {
				try {
					// read the first packet
					byte[] init = new byte[512];
					int len = s.getInputStream().read(init);
					
					// if no data was read, we're done
					if (len <= 0)
						return;
					
//					String user = null; // user name
					String host = null; // host name
					
					// gotta figure out what this first packet is
					
					// is it a pre-1.7 server ping?
					if (init[0] == (byte)0xFE)
					{
						// get the designated recipient
						InetSocketAddress ospp = VirtualBukkit.this.oldSchoolPingPongAddress();
						if (ospp == null)
							return; // give up if there isn't one
						
						// open a connection
						Socket dst = new Socket(ospp.getAddress(), ospp.getPort());
						
						// write the first packet, hand off
						dst.getOutputStream().write(init, 0, len);
						handle(src, dst);
						
						// we're done here
						return;
					}
					// is it a pre-1.7 server connection
					else if (init[0] == 2)
					{
						// user name
						int offset = 2; // string length offset
						int strlen = init[offset++] << 8 | init[offset++]; // string length
//						user = new String(init, offset, strlen * 2, "UTF-16");
						
						// host name
						offset += 2 * strlen; // string length offset
						strlen = init[offset++] << 8 | init[offset++]; // string length
						host = new String(init, offset, strlen * 2, "UTF-16");
					}
					// is it a 1.7+ connection (ping or otherwise)?
					else if (len == init[0] && init[1] == 0)
					{
						// host name
						int offset = 3; // string length offset
						int strlen = init[offset++]; // string length
						host = new String(init, offset, strlen, "UTF-8");
					}
					// no idea what it might be, print it out so someone can submit an issue
					else {
						System.err.println("Unknown inital packet, closing connection:");
						
						// print out the packet as characters (non-printing chars become '.')
						System.out.print('\t');
						for (int i = 0; i < len; i++)
							if (20 <= init[i] && init[i] < 127)
								System.out.print(String.format("%c", init[i]));
							else
								System.out.print('.');
						System.out.println();
						
						// print out the packet as hex
						System.out.print('\t');
						for (int i = 0; i < len; i++) {
							System.out.print(String.format("%02x", init[i]));
							if (i % 4 == 3)
								System.out.print(' ');
						}
						System.out.println();
						
						// we're done
						src.close();
						return;
					}
					
					// get the address for the host
					InetSocketAddress addr = VirtualBukkit.this.addressForVirtualHost(host);
					if (addr == null) {
						// if there isn't one, we're done
						System.err.println("No server for host: " + host);
						src.close();
						return;
					}
					
					// open a connection to the server
					System.out.println("Forwarding client to " + addr + " (" + host + ")");
					Socket dst = new Socket(addr.getAddress(), addr.getPort());
					
					// write the first packet, hand off
					dst.getOutputStream().write(init, 0, len);
					handle(src, dst);
				} catch (Exception e) {
					// something bad happened
					System.err.println("Exception ocurred: " + e);
					
					// close the socket
					try {
						if (!src.isClosed())
							src.close();
					} catch (IOException ioe) {
						System.err.println("Error closing socket: " + ioe);
					}
				}
			}
		}.start(); // start in a new thread
	}
	
	// handle the forwarding
	protected void handle(final Socket src, final Socket dst) {
		new Thread() {
			{
				this.setDaemon(true);
			}
			
			@Override
			public void run() {
				try {
					// src => dst
					InputStream in = src.getInputStream();
					OutputStream out = dst.getOutputStream();
					
					int r = 0;
					byte[] b = new byte[512];
					while ((r = in.read(b)) > -1)
						out.write(b, 0, r);
				} catch (Exception e) {
					System.err.println("Socket closed: " + e);
					try {
						if (!src.isClosed())
							src.close();
					} catch (IOException ioe) {
						System.err.println("Error closing socket: " + ioe);
					}
				}
			}
		}.start();
		
		new Thread() {
			{
				this.setDaemon(true);
			}
			
			@Override
			public void run() {
				try {
					// dst => src
					InputStream in = dst.getInputStream();
					OutputStream out = src.getOutputStream();
					
					int r = 0;
					byte[] b = new byte[512];
					while ((r = in.read(b)) > -1)
						out.write(b, 0, r);
				} catch (Exception e) {
					System.err.println("Socket closed: " + e);
					try {
						if (!src.isClosed())
							src.close();
					} catch (IOException ioe) {
						System.err.println("Error closing socket: " + ioe);
					}
				}
			}
		}.start();
	}
}
