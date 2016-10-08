package com.Josh.library.client.component;

import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * this class provides static methods and variables that are used in the server
 * @author Josh
 *
 */
public class RemoteExecutionEngine {

	private static Environment envi = Environment.LOCAL;
	private static Handler handler;
	private static final String Tag = "RemoteExecutionEngine";
	
	/**
	 * set the environment to server, this method should NOT be invoked manually
	 */
	@SuppressWarnings("unused")
	private static void setEnvironment_Server(){
		envi = Environment.SERVER;
	}
	
	/**
	 * get the environment information, that is to say, check if this method is called locally or remotely
	 * @return
	 * 		the environment information
	 */
	public static Environment getEnvironment(){
		return envi;
	}
	
	/**
	 *  get the static field from clinet, the thread will be blocked until field is got or 
	 *  exception is caught. This method should be called in the server
	 * @param className
	 * 			The name of the calss where the static field lies
	 * @param fieldName
	 * 			the name of the field
	 * @return value of the field
	 * @throws Exception
	 * 			if there is a problem
	 */
	static Object getStaticField(String className, String fieldName) throws Exception{
		Handler signalHandler = handler;
		if(signalHandler == null)
			throw(new Exception("Unable to get signal handler!"));
		Message msg = new Message();
		Bundle signal = new Bundle();
		long threadId = Thread.currentThread().getId();
		LinkedBlockingQueue<HashMap<String,Object>> queue = new LinkedBlockingQueue<HashMap<String,Object>>();
		signal.putString("SIGNAL", "GET_FIELD");
		signal.putString("className", className);
		signal.putString("fieldName", fieldName);
		signal.putLong("threadId", threadId);
		signal.putSerializable("QUEUE", queue);
		msg.setData(signal);
		signalHandler.handleMessage(msg);
		HashMap<String,Object> result = null;
		try { 
			result = queue.take();			
		} catch (InterruptedException e) {
			Log.e(Tag, "Fatal error: blocking queue is interrupted unnormally!");
			throw(new Exception("Unable to get static field from client"));
		}
		if((Boolean)result.get("hasException")){
			throw((Exception) result.get("exception"));
		}else{
			return result.get("result");
		}
	}
	
	/**
	 * ask for absolute native library path, this method should be called in the server
	 * @param lib
	 * 			the name of the native library
	 * @return
	 * 			the absolute path of the native library
	 * @throws Exception
	 */
	static String getNativeLibPath(String lib) throws Exception{
		Handler signalHandler = handler;
		if(signalHandler == null)
			throw(new Exception("Unable to get signal handler!"));
		Message msg = new Message();
		Bundle signal = new Bundle();
		LinkedBlockingQueue<HashMap<String,Object>> queue = new LinkedBlockingQueue<HashMap<String,Object>>();
		signal.putString("SIGNAL", "GET_LIBRARY_PATH");
		signal.putSerializable("QUEUE", queue);
		signal.putString("libName", lib);
		HashMap<String,Object> result = null;
		msg.setData(signal);
		handler.handleMessage(msg);
		try { 
			result = queue.take();			
		} catch (InterruptedException e) {
			Log.e(Tag, "Fatal error: blocking queue is interrupted unnormally!");
			throw(new Exception("Unable to load native library "+lib));
		}
		
		
		if((Boolean)result.get("hasException")){
			throw((Exception) result.get("exception"));
		}else{
			return (String) result.get("result");
		}
	}
}
