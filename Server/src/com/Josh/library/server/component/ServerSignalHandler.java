package com.Josh.library.server.component;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

import com.Josh.library.core.component.Command;
import com.Josh.library.core.component.RemoteObjectWrapper;
import com.Josh.library.core.component.Command.COMMAND;
import com.Josh.library.core.component.StaticFieldVirtualParentObject;
import com.Josh.library.core.exception.RemoteExecutionFailedException;
import com.Josh.library.server.core.IdPool;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * This class is a handler that deals with the communication between client methods and
 * the server, that is to say, when a method is offloading, it can communicate with the 
 * server via this handler, e.g. ask for an object from client, use the server network 
 * and something else that may use server resources.
 * @author Josh
 *
 */
public class ServerSignalHandler extends Handler{
	private ServerSocketHandler socketHandler;
	private IdPool getFieldIdPool;
	static final private String Tag = "ServerSignalHandler";
	
	public ServerSignalHandler(ServerSocketHandler s){
		this.socketHandler = s;
		getFieldIdPool = new IdPool(10);
	}
		
		@Override
		public void handleMessage(final Message msg) {
			new Thread(){
				public void run(){
					handleMessage_core(msg);
				}
			}.start();
		}
		
		/**
		 * Handle the message recieved from offloading methods.
		 * @param msg
		 * 		the recieved method
		 */
		@SuppressWarnings({ "unchecked", "unused" })
		private void handleMessage_core(Message msg){
			//Get the bundle
			Bundle signal = msg.getData();
			
			//Get the blocking queue that blocks the method thread
			LinkedBlockingQueue<HashMap<String,Object>> queue = (LinkedBlockingQueue<HashMap<String,Object>>) signal.getSerializable("QUEUE");
			HashMap<String,Object> result = new HashMap<String,Object>();
			
			
			try {
				
				//Is not connected, send an exception
				if(!socketHandler.isConnected()){
					result.put("hasException", true);
					result.put("exception", new IOException("Connection is not established!"));
					queue.put(result);
					return;
				}
				
				//Recieve thread is off, send an exception
				if(!socketHandler.isRecieveServiceOn()){
					result.put("hasException", true);
					result.put("exception", new IOException("Recieve service is not started!"));
					queue.put(result);
					return;
				}
				
				
				//Get a request for client static field
				if(signal.getString("SIGNAL").equals("GET_FIELD")){
					String className = signal.getString("className");
					String fieldName = signal.getString("fieldName");
					long threadId = signal.getLong("threadId");
					
					ObjectInfo info = ServerEngine.getServerEngine().getObjectInfo();
					StaticFieldVirtualParentObject resultObj = 
							info.getStaticFieldVirtualParentObject(className,
							fieldName, socketHandler.getExecutor().getClassLoader());
					
					if(resultObj!=null){
						result.put("result",resultObj.getValue());
						result.put("hasException", false);
						queue.put(result);
						return;
					}
					
					int CommandId = getFieldIdPool.getPosition();
					Command cmd = new Command(COMMAND.OBJECT_REQUEST,CommandId);
					cmd.putExtra("className", className);
					cmd.putExtra("fieldName", fieldName);			
					cmd.putExtra("threadId", threadId);
					
					
					try {
						Log.i(Tag, "ask for static field: "+className+"."+fieldName+" from client!");
						socketHandler.transmit(cmd);
					} catch (IOException e) {
						e.printStackTrace();
						getFieldIdPool.returnPosition(CommandId);
						result.put("hasException", true);
						result.put("exception", e);
						queue.put(result);
						return;
					}
					Command retCommand;

					try {
						retCommand = socketHandler.WaitForCommand(COMMAND.OBJECT_REQUEST_RETURN,CommandId,5000);
					} catch (RemoteExecutionFailedException e) {
						e.printStackTrace();
						getFieldIdPool.returnPosition(CommandId);
						result.put("hasException", true);
						result.put("exception", e);
						queue.put(result);
						return;
					}

					getFieldIdPool.returnPosition(CommandId);
					
					if((Boolean)retCommand.getExtra("hasException") == true){				
						Log.e(Tag, "Error: there is a problem when asking for client static field: "+className+"."+fieldName);
						result.put("hasException", true);
						result.put("exception", retCommand.getExtra("exception"));
						queue.put(result);
						return;
					}										
					
					RemoteObjectWrapper objectWrapper = (RemoteObjectWrapper) retCommand.getExtra("objectWrapper");										
					
					Log.i(Tag, "Static field got: "+((StaticFieldVirtualParentObject)objectWrapper.getObject()).getValue());
					
					try {
						resultObj = (StaticFieldVirtualParentObject) info.unWrapObjectInAnotherThread(objectWrapper,threadId);
						if(resultObj == null)
							throw (new RemoteExecutionFailedException("Static field virtual parent is null!"));
						info.addStaticFieldVirtualParentObject(resultObj);
					} catch (RemoteExecutionFailedException e) {
						result.put("hasException", true);
						result.put("exception",	e);
						queue.put(result);
						if(resultObj == null) return;
						info.removeStaticFieldVirtualParentObject(resultObj.getClassName(), resultObj.getFieldName());
						info.removeObjectInfoInAnotherThread(objectWrapper,threadId);
						return;
					}
					
					resultObj.setValue(resultObj.getValue(), socketHandler.getExecutor().getClassLoader());
					result.put("result",resultObj.getValue());
					result.put("hasException", false);
					queue.put(result);
					return;
				}
				
				
				//Get a request for native library path
				if(signal.getString("SIGNAL").equals("GET_LIBRARY_PATH")){
					String libName = signal.getString("libName");
					String path = socketHandler.getExecutor().getLibraryPath(libName);
					Log.i(Tag, "Got native library path : "+path);
					result.put("result", path);
					result.put("hasException", false);
					queue.put(result);
					return;
				}
				
			} catch (InterruptedException e) {
				e.printStackTrace();
				Log.e(Tag, "Fatal error: blocking queue interrupted unnormally");
			}
		}
		
	}