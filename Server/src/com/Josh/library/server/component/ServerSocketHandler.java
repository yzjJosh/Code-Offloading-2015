package com.Josh.library.server.component;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.Josh.library.core.component.Command;
import com.Josh.library.core.component.MethodPackage;
import com.Josh.library.core.component.ObjectSynchronizationInfo;
import com.Josh.library.core.component.RemoteObjectWrapper;
import com.Josh.library.core.component.Command.COMMAND;
import com.Josh.library.core.exception.RemoteExecutionFailedException;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 *  this class provides methods dealing with network communication
 * @author Josh
 *
 */
@SuppressLint("UseSparseArrays")
public class ServerSocketHandler {
	
	private RecieveThread thread;
	private Set<Thread> waitingThreads;
	private Set<Command> recievedCommands;
	private ServerSocket serverSocket;
	private Socket socket;
	private ObjectInputStream is;
	private ObjectOutputStream os;
	private boolean isRecieveThreadOn = false;
	private RemoteCommandExecutor remoteCmdExe;
	private boolean isConnected = false;
	private TransmitThread transmitThread;
	private boolean isTransmitServiceOn = false;
	private Handler waitHandler = new Handler();
	static private final String Tag = "ServerSocketHandler";
	
	public ServerSocketHandler(Context context){
		waitingThreads = new HashSet<Thread>();
		recievedCommands = new HashSet<Command>();
		remoteCmdExe = new RemoteCommandExecutor(context, this);
	}
	
	/**
	 * Get the remote command executor
	 * @return
	 * 		the remote command executor
	 */
	public RemoteCommandExecutor getExecutor(){
		return remoteCmdExe;
	}
	
	
	/**
	 * Wait for client connection at a port, this method will block the thread until
	 * connection is established
	 * @param port
	 * 		the port
	 * @throws IOException
	 */
	public void waitForConnect(int port) throws IOException{
		if(isServerSocketOn())
			throw(new IOException("Server socket is already on, unable to open again!"));
			
		serverSocket = new ServerSocket(port);
		Log.i(Tag, "Server started, waiting for client at port : "+port);	
		socket = serverSocket.accept();
		os = new ObjectOutputStream(socket.getOutputStream());
		is = remoteCmdExe.getObjectInputStream(socket.getInputStream());
		isConnected = true;
		Log.i(Tag, "Connect success! Client address: "+socket.getInetAddress()+":"+socket.getPort());
	}
	
	/**
	 * Close the server socket.
	 * @throws IOException
	 */
	public void closeServerSocket() throws IOException{
		if(isConnected())
			disConnect();
		if(serverSocket == null) return;
		serverSocket.close();
		Log.i(Tag, "Server socket closed!");
	}
	
	/**
	 * Disconnect with client
	 * @throws IOException
	 */
	public void disConnect() throws IOException{	
		if(socket == null) return;
		this.isConnected = false;
		InetAddress  ip = socket.getInetAddress();
		int port = socket.getPort();
		if(os!=null)
			os.close();
		if(is!=null)
			is.close();
		socket.close();
		if(this.transmitThread!=null)
			this.transmitThread.stopTransmission();
		synchronized(this.waitingThreads){
			for(Thread thread: waitingThreads)
				thread.interrupt();
			waitingThreads.clear();
		}
		
		synchronized(this.recievedCommands){
			recievedCommands.clear();
		}
		Log.i(Tag, "Disconnected from client: "+ip+":"+port);
	}
	
	
	/**
	 * start the transmitting thread
	 * @return
	 * 		succeed or not
	 */
	public boolean startTransmit(){
		if(isConnected() && !isTransmitServiceOn){
			transmitThread = new TransmitThread();
			transmitThread.start();
			isTransmitServiceOn = true;
			return true;
		}else{
			Log.e(Tag, "Unable to start transfer service!");
			return false;
		}
	}
	
	/**
	 * start the recieving thread
	 * @return
	 * 		succeed or not
	 */
	public boolean startRecieve(){
		if(isConnected() && !isRecieveThreadOn)
		{
			thread = new RecieveThread();
			thread.start();
			isRecieveThreadOn = true;
			Log.i(Tag, "Recieve service started!");
			return true;
		}else{
			Log.e(Tag, "Unable to start recieve service!");
			return false;
		}
	}
	
	/**
	 * check if network is connected
	 * @return
	 * 		connected or not
	 */
	public boolean isConnected(){
		if(!isServerSocketOn())
			return false;
		if(socket == null)
			return false;
		return isConnected;
	}
	
	/**
	 * Check if the server socket is on
	 * @return
	 * 		server socket is on or not
	 */
	private boolean isServerSocketOn(){
		if(serverSocket == null)
			return false;
		return !serverSocket.isClosed();
	}
	
	/**
	 * Check if the recieve thread is on
	 * @return
	 * 		if recieve thread is on
	 */
	public boolean isRecieveServiceOn(){
		return this.isRecieveThreadOn;
	}
	
	
	/**
	 * Block the thread until recieving specific command or time out.
	 * @param cmd 
	 * 			Command for waiting
	 * @param commandId
	 * 			The command id which is used to filter commands
	 * @param MaxWaitingTime
	 * 			Maximum waiting microseconds, if no command recieved when time out,
	 * 			this method will stop waiting and throws an RemoteExecutionFailedException.
	 * 			if this paremeter is 0, the maximum waiting time is ignored.
	 * @return recieved command
	 * @throws RemoteExecutionFailedException when time out or connection losing
	 */

	public Command WaitForCommand(final COMMAND cmd, final int commandId, final long MaxWaitingTime) throws RemoteExecutionFailedException{
		if(!isRecieveThreadOn)
			throw(new RemoteExecutionFailedException("Recieve service unreachable!"));
		
		final Thread thisThread = Thread.currentThread();
		class Stop{
			public boolean stop = false;
		}
		
		final Stop stop = new Stop();
		
		Runnable r = new Runnable(){
			@Override
			public void run() {
				stop.stop = true;
				thisThread.interrupt();
				Log.e(Tag, "Error: waiting for command "+cmd+" out of time, maximum time: "+ MaxWaitingTime+" ms");
			}
			
		};
		
		if(MaxWaitingTime != 0)
			waitHandler.postDelayed(r , MaxWaitingTime);
		
		synchronized(waitingThreads){
			waitingThreads.add(thisThread);
		}
		
		Command result = null;
		
		while(true){
			synchronized(recievedCommands){
				for(Command command : recievedCommands){
					if(command.getCOMMAND() == cmd && command.getCommandId() == commandId){
						result = command;
						break;
					}
				}
				if(result!=null){					
					recievedCommands.remove(result);
				}
			}
				
			if(result!=null){
				waitHandler.removeCallbacks(r);
				synchronized(waitingThreads){
					waitingThreads.remove(thisThread);
				}			
				return result;
			}
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				if(stop.stop){
					synchronized(waitingThreads){
						waitingThreads.remove(thisThread);
					}
					throw(new RemoteExecutionFailedException("Waiting for command "+cmd+"("+commandId+") time out!"));
				}
				if(!isConnected()){
					throw(new RemoteExecutionFailedException("Waiting for command "+cmd+"("+commandId+") canceled, due to lost connection!"));
				}
			}
		}

	}
	  
	
	/**
	 * recieve command, this method will wait until data is revieved
	 * @return
	 * 		the recieved command
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	private Command recieveCommand() throws IOException, ClassNotFoundException{
		 if(!isConnected())
			 throw(new IOException("Socket is not connected!"));
		 if(is == null)
				throw(new IOException("InputStream is null!"));
		 Command cmd = (Command) is.readObject();
		 return cmd;
	};
	
	
	/**
	 * Transfer a command
	 * @param cmd 
	 * 		command needs transmission
	 * @throws IOException
	 */
	public void transmit(Command cmd) throws IOException {		
		if(isConnected() && isTransmitServiceOn && os!=null && transmitThread!=null){
			transmitThread.AddTransmitQueue(cmd);
		}else{
			throw(new IOException("Unable to transmit data!"));
		}
	};

	
	/**
	 * The recieve thread
	 * @author Josh
	 *
	 */
	private class RecieveThread extends Thread{
		public void run(){
			Looper.prepare();
			while(true){
				isRecieveThreadOn = true;
				try {
					final Command cmd = recieveCommand();
					new Thread(){
						public void run(){
							try {
								onRecieve(cmd);
							} catch (RemoteExecutionFailedException e) {
								e.printStackTrace();
							}
						}
					}.start();
					
					if(Command.isReturnCommand(cmd)){
						synchronized(recievedCommands){
							recievedCommands.add(cmd);							
						}
						synchronized(waitingThreads){
							for(Thread thread : waitingThreads)
								thread.interrupt();
						}
					}
				} catch (Exception e) {
					Log.e(Tag, "An error occur when recieving command, recieve service quited!");
					isRecieveThreadOn = false;
					isConnected = false;
					return;
				} 
				
			}
		}
	}
	
	
	/**
	 * the transmit thread
	 * @author Josh
	 *
	 */
	private class TransmitThread extends Thread{
		private ConcurrentLinkedQueue<Command> transmitQueue = new ConcurrentLinkedQueue<Command>();
		private boolean exit = false;
		
		/**
		 * stop the transmission
		 */
		public void stopTransmission(){
			exit = true;
			this.interrupt();
			Log.i(Tag, "Transmit Thread exists!");
			isTransmitServiceOn = false;
		}
		
		/**
		 * add command to the waiting queue
		 * @param cmd
		 * 		command to transmit
		 */
		public void AddTransmitQueue(Command cmd){
			if(cmd==null) return;
			transmitQueue.add(cmd);
			this.interrupt();
		}
		
		public void run(){
			isTransmitServiceOn = true;
			Log.i(Tag, "Socket transmitting service started!");
			while(true){
				if(!isConnected || os==null){
					Log.e(Tag, "An error occured when transmitting data, transmit service quited!");
					isTransmitServiceOn = false;
					return;
				}
				try {
					Command cmd = null;
					while(true){
						cmd = transmitQueue.poll();
						if(cmd!=null)
							break;
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							if(exit)
								return;
						}
					}
					os.writeObject(cmd);
					os.reset();
				} catch (IOException e) {
					Log.e(Tag, "An error occured when rensfering data, transfer service quited!");
					isTransmitServiceOn = false;
					return;
				}
			
			
			}
			
		}
		
	}

	
	/**
	 * this method will be called when a command is recieved
	 * @param cmd
	 * 		recieved command
	 */
	@SuppressWarnings("unchecked")
	private void onRecieve(Command cmd) throws RemoteExecutionFailedException{
		COMMAND CMD = cmd.getCOMMAND();
		
//		Log.i(Tag, "recieved command: "+CMD);
		
		//do when recieve a code transmit command
		if(CMD == COMMAND.CODE_TRANSMIT){
			boolean ask = (Boolean) cmd.getExtra("ask");
			String apkName = (String) cmd.getExtra("apkName");
			Command reply = new Command(COMMAND.CODE_TRANSMIT_RETURN, cmd.getCommandId());
			if(ask){
				//if this is a asking
				if(remoteCmdExe.hasAPK(apkName)){
					
					remoteCmdExe.LoadAPK(apkName);
					reply.putExtra("needTransmit", false);
					reply.putExtra("hasException", false);
				
					
					try {
						transmit(reply);
					} catch (IOException e) {
						e.printStackTrace();
						Log.e(Tag, "Unable to transmit reply "+CMD+" to client!");
					}				
					
				}else{
					reply.putExtra("needTransmit", true);
					reply.putExtra("hasException", false);
					try {
						transmit(reply);
					} catch (IOException e) {
						e.printStackTrace();
						Log.e(Tag, "Unable to transmit reply "+CMD+" to client!");
					}
					
				}
			}else{
				//if this is a code sending
				Log.i(Tag, "start recieving apk!");
				byte[] apk = (byte[]) cmd.getExtra("apk");
				try {
					remoteCmdExe.saveAPK(apkName, apk);
					remoteCmdExe.LoadAPK(apkName);
					reply.putExtra("needTransmit", false);
					reply.putExtra("hasException", false);
				} catch (Exception e) {
					e.printStackTrace();
					reply.putExtra("hasException", true);
					reply.putExtra("exception", new RemoteExecutionFailedException("error occurs when loading apk...!"));
					reply.putExtra("exceptionType", "RemoteExecutionFailedException");
					Log.e(Tag, "An error occur when recieving apk...");
				}
				
				try {
					transmit(reply);
				} catch (IOException e) {
					e.printStackTrace();
					Log.e(Tag, "Unable to transmit reply "+CMD+" to client!");
				}
				
				Log.i(Tag, "APK recieved!");
			}
			
			return;
		}
		
		
		//do when recieve a execute method command
		if(CMD == COMMAND.EXECUTE_METHOD){
			Command resultReply = new Command(COMMAND.EXECUTE_METHOD_RESULT_RETURN, cmd.getCommandId());
			final Command threadIdReply = new Command(COMMAND.EXECUTE_METHOD_THREAD_ID_RETURN, cmd.getCommandId());		
			final MethodPackage Package = (MethodPackage) cmd.getExtra("MethodPackage");
			long ClientThreadId = (Long) cmd.getExtra("threadId");
					
			//set thread id information
			final long threadId = Thread.currentThread().getId();
			ServerEngine.getServerEngine().getObjectInfo().setThreadRelationship(ClientThreadId, threadId);
			threadIdReply.putExtra("threadId", threadId);
			threadIdReply.putExtra("hasException", false);
			
			Log.i(Tag, "Client thread id: "+ClientThreadId+" Server thread id: "+threadId);

			
			try {
				transmit(threadIdReply);
			} catch (IOException e) {
				e.printStackTrace();
				Log.e(Tag, "Unable to transmit thread id to client, method :"
						+ Package.toString(remoteCmdExe.getClassLoader())+
						", client thread id: "+threadId);
			}
			
			try {
				WaitForCommand(COMMAND.EXECUTE_METHOD_THREAD_ID_RETURN,cmd.getCommandId(),5000);
			} catch (RemoteExecutionFailedException e1) {
				Log.e(Tag, "an error occur when trying to get reply for transmitting thread id" + threadId);
				throw(e1);
			}
			
			try {
				Map<Integer,ObjectSynchronizationInfo> remoteObjecSynctMap = new HashMap<Integer,ObjectSynchronizationInfo>();
				ObjectSynchronizationInfo resultSync = remoteCmdExe.executeMethod(Package,remoteObjecSynctMap);		
				resultReply.putExtra("resultSync", resultSync);
				resultReply.putExtra("remoteObjecSynctMap", remoteObjecSynctMap);
				resultReply.putExtra("hasException", false);
			} catch (InvocationTargetException e) {
				resultReply.putExtra("hasException", true);
				resultReply.putExtra("exception", e);
				resultReply.putExtra("exceptionType", "InvocationTargetException");
			} catch (RemoteExecutionFailedException e) {
				resultReply.putExtra("hasException", true);
				resultReply.putExtra("exception", e);
				resultReply.putExtra("exceptionType", "RemoteExecutionFailedException");
			}
			
			try {
				transmit(resultReply);
			} catch (IOException e) {
				e.printStackTrace();
				Log.e(Tag, "Unable to transmit reply "+CMD+" to client!");
			}
			
			ServerEngine.getServerEngine().getObjectInfo().ClearThreadId(threadId);
			
			return;
			
		}
		
		//do when recieve an object request command
		if(CMD == COMMAND.OBJECT_REQUEST){
			Command reply = new Command(COMMAND.OBJECT_REQUEST_RETURN,cmd.getCommandId());
			int id = (Integer) cmd.getExtra("id");
			String field = (String) cmd.getExtra("field");
			ObjectSynchronizationInfo objInfo = null;
			Object[] newObjArray = null;
			ObjectInfo info = ServerEngine.getServerEngine().getObjectInfo();
			try {
				Log.i(Tag, "Got a field request, id ="+id+" field name = "+field);
				Object obj = remoteCmdExe.getObjectField(id, field);				
				objInfo = info.getObjectSynchronizationInfo(obj);
				newObjArray = remoteCmdExe.getNewObjectSet(objInfo).toArray();
				reply.putExtra("objInfo", objInfo);
				reply.putExtra("newObjArray", newObjArray);
				reply.putExtra("hasException", false);
				Log.i(Tag, "field got: "+objInfo.getObject()+", send it to the client...");
			} catch (RemoteExecutionFailedException e) {
				reply.putExtra("hasException", true);
				reply.putExtra("exceptionType", "RemoteExecutionFailedException");
				reply.putExtra("exception", e);
			} catch(NullPointerException e){
				reply.putExtra("hasException", true);
				reply.putExtra("exceptionType", "NullPointerException");
				reply.putExtra("exception", e);
			}
			
			try {			
				transmit(reply);
			} catch (IOException e) {
				e.printStackTrace();
				Log.e(Tag, "Unable to transmit reply "+CMD+" to client!");
			}
			
			if((Boolean)reply.getExtra("hasException"))
				return;
			
			Command ret;
			
			try {
				ret = WaitForCommand(COMMAND.OBJECT_REQUEST_RETURN,cmd.getCommandId(),5000);
			} catch (RemoteExecutionFailedException e) {
				return;
			}
			
						
			RemoteObjectWrapper[] wrappers = (RemoteObjectWrapper[]) ret.getExtra("wrappers");
			List<Long> threadList = (List<Long>) ret.getExtra("threadList");
			for(int i=0;i<newObjArray.length;i++ ){
				RemoteObjectWrapper wrapper = wrappers[i];
				wrapper.setObject(newObjArray[i]);
				boolean isFirst = true;
				for(Long threadId:threadList){
					info.unWrapObjectInAnotherThread(wrapper, threadId);
					if(isFirst){
						isFirst = false;
						wrapper.setNeedTransmit(false);
					}
					
				}
			}
			
			return;
			
		}
		
		
		//do when recieve a field set command
		if(CMD == COMMAND.FIELD_SET){
			
			Command reply = new Command(COMMAND.FIELD_SET_RETURN,cmd.getCommandId());
			int id = (Integer) cmd.getExtra("id");
			String field = (String) cmd.getExtra("field");
			Object value = null;
			ObjectInfo info = ServerEngine.getServerEngine().getObjectInfo();
			RemoteObjectWrapper wrapper = null;
			try {
				if((Boolean)cmd.getExtra("isAlreadyRemote")){
					value = info.getObject((Integer)cmd.getExtra("valueId"));
				}else{					
					wrapper = (RemoteObjectWrapper) cmd.getExtra("valueWrapper");
					List<Long> threadIdList = (List<Long>) cmd.getExtra("ThreadIdList");
					boolean isFirst = true;
					for(Long threadId : threadIdList){
						value = info.unWrapObjectInAnotherThread(wrapper, threadId);
						if(isFirst){
							wrapper.setNeedTransmit(false);
							isFirst = false;
						}
					}
				}			
					Log.i(Tag, "Got a setting field command, id = "+id+", field name = "+field
							+" value = "+value);
					remoteCmdExe.setField(value, id, field);
					reply.putExtra("hasException", false);
					Log.i(Tag, "Field setting succeed!");
			} catch (RemoteExecutionFailedException e) {
				if(wrapper!=null){
					List<Long> threadIdList = (List<Long>) cmd.getExtra("ThreadIdList");
					for(Long threadId : threadIdList){
						info.removeObjectInfoInAnotherThread(wrapper, threadId);
					}
				}
				reply.putExtra("hasException", true);
				reply.putExtra("exceptionType", "RemoteExecutionFailedException");
				reply.putExtra("exception", e);
				e.printStackTrace();
			}catch(NullPointerException e){
				if(wrapper!=null){
					List<Long> threadIdList = (List<Long>) cmd.getExtra("ThreadIdList");
					for(Long threadId : threadIdList){
						info.removeObjectInfoInAnotherThread(wrapper, threadId);
					}
				}
				reply.putExtra("hasException", true);
				reply.putExtra("exceptionType", "NullPointerException");
				reply.putExtra("exception", e);
			}
											
			try {
				transmit(reply);
			} catch (IOException e) {
				e.printStackTrace();
				Log.e(Tag, "Unable to transmit reply "+CMD+" to client!");
				if(wrapper!=null){
					List<Long> threadIdList = (List<Long>) cmd.getExtra("ThreadIdList");
					for(Long threadId : threadIdList){
						info.removeObjectInfoInAnotherThread(wrapper, threadId);
					}
				}
			}
			
			return;
		}
		
		//do when recieve a ping command
		if(CMD == COMMAND.PING){
			Command reply = new Command(COMMAND.PING_RETURN,cmd.getCommandId());
			try {
				transmit(reply);
			} catch (IOException e) {
			}
		}
		
	}
	
	
	
}
