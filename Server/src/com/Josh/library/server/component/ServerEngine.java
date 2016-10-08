package com.Josh.library.server.component;

import android.content.Context;
import android.util.Log;

/**
 * this class provides methods that can control the server part of the offloading system
 * @author Josh
 *
 */
public class ServerEngine {
	private static final String Tag="ServerEngine";
	private static ServerEngine engine = new ServerEngine();	
	private boolean isStarted;
	private ObjectInfo info;
	private ServiceThread thread;
	
	
	private ServerEngine(){
		info=new ObjectInfo();		
	}
	
	/**
	 * Return the server engine. A server engine can not be instantiated manually, 
	 * and this is the only method to get it.
	 * @return
	 * 		the server engine
	 */
	static public ServerEngine getServerEngine(){
		return engine;
	}
	
	/**
	 * Get the object information
	 * @return
	 * 		the object information
	 */
	public ObjectInfo getObjectInfo(){
		return info;
	}
	
	/**
	 * Start the server engine
	 * @param context
	 * 		context of this application
	 * @param port
	 * 		the port of the network
	 * @return
	 * 		this server engine
	 */
	public ServerEngine Start(Context context, int port){
		if(isStarted) return this;
		thread = new ServiceThread(context ,port);
		thread.start();
		isStarted=true;
		return this;
	}
	
	/**
	 * Stop the server engine
	 */
	public void Stop(){
		thread.Stop();
		isStarted=false;
		Log.i(Tag, "Server engine stopped!");
	}

	/**
	 * Check if the server engine is started
	 * @return
	 * 		if it is started
	 */
	public boolean isStarted(){
		
		return isStarted;
	}
	
		
}
