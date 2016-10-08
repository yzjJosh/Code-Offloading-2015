package com.Josh.library.server.component;

import java.io.IOException;

import android.content.Context;

/**
 * this is the thread that controls the network
 * @author Josh
 *
 */
public class ServiceThread extends Thread {
	private ServerSocketHandler sockethandler;
	private int port;
	private boolean stop = false;

	public ServiceThread(Context context, int port){
		this.sockethandler = new ServerSocketHandler(context);
		this.port = port;
	}
	
	/**
	 * thread main process
	 */
	public void run(){
		stop = false;
		while(true){
			if(stop) return;
			
			//Wait for client
			try {
				sockethandler.waitForConnect(port);
			} catch (IOException e) {
				e.printStackTrace();
				stopService();
				continue;
			}
			
			if(stop) return;
			
			//Strat transmission thread
			if(!sockethandler.startTransmit()){
				stopService();
				continue;
			}
			
			//Start recieve thread
			if(!sockethandler.startRecieve()){
				stopService();
				continue;
			}
			
			
			//Check if the connection is on for every 2 seconds
			while(sockethandler.isConnected() && sockethandler.isRecieveServiceOn()){
				if(stop) return;
				SLEEP(2000);
			}
			
			//The connection is lost, stop connect and run the loop again 
			stopService();
		}
	}
	
	/**
	 * sleep thread for specific microseconds
	 * @param time
	 * 		microseconds to sleep
	 */
	private void SLEEP(long time){
		try {
			Thread.sleep(time);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * stop this thread
	 */
	public void Stop(){
		stopService();
		this.stop = true;
	}
	
	/**
	 * stop the network connection
	 */
	private void stopService(){
		try {
			sockethandler.disConnect();
			sockethandler.closeServerSocket();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
