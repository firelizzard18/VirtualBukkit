package org.bukkit.virtualbukkit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public abstract class VirtualBukkit extends Thread {
	private volatile boolean running = false;
	
	public abstract InetSocketAddress listeningAddress();
	public abstract InetSocketAddress addressForVirtualHost(String hostname);
	
	public boolean isRunning() {
		return this.running;
	}
	
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
	
	protected void safeRun() throws Exception {
		synchronized (this) { this.running = true; }

		ServerSocket ss = new ServerSocket(this.listeningAddress().getPort(), 10, this.listeningAddress().getAddress());
		
		while (this.isRunning()) {
			Socket s = ss.accept();
			byte[] b = new byte[512];
			int r = s.getInputStream().read(b);
			
//			System.out.println(b[0] + " " + b[1] + " "  + b[2] + " "  + b[3]);
			
			if (r > 0)
				this.handle(s, b, r);
		}
		
		ss.close();
	}
	
	protected void handle(final Socket src, final byte[] init, final int len) {
		new Thread() {
			{
				this.setDaemon(true);
			}
			
			@Override
			public void run() {
				try {
					InetSocketAddress addr = VirtualBukkit.this.addressForVirtualHost(new String(init, 4, init[3]));
					if (addr == null) {
						src.close();
						return;
					}
					
					Socket dst = new Socket(addr.getAddress(), addr.getPort());
					
					dst.getOutputStream().write(init, 0, len);
					handle(src, dst);
				} catch (Exception e) {
					e.printStackTrace();
					try {
						src.close();
					} catch (IOException ioe) {
						ioe.printStackTrace();
					}
				}
			}
		}.start();
	}
	
	protected void handle(final Socket src, final Socket dst) {
		new Thread() {
			{
				this.setDaemon(true);
			}
			
			@Override
			public void run() {
				try {
					InputStream in = src.getInputStream();
					OutputStream out = dst.getOutputStream();
					
					int r = 0;
					byte[] b = new byte[512];
					while ((r = in.read(b)) > -1)
						out.write(b, 0, r);
				} catch (Exception e) {
					e.printStackTrace();
					try {
						src.close();
					} catch (IOException ioe) {
						ioe.printStackTrace();
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
					InputStream in = dst.getInputStream();
					OutputStream out = src.getOutputStream();
					
					int r = 0;
					byte[] b = new byte[512];
					while ((r = in.read(b)) > -1)
						out.write(b, 0, r);
				} catch (Exception e) {
					e.printStackTrace();
					try {
						dst.close();
					} catch (IOException ioe) {
						ioe.printStackTrace();
					}
				}
			}
		}.start();
	}
}
