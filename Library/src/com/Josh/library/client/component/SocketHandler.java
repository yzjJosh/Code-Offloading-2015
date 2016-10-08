package com.Josh.library.client.component;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.Josh.library.core.component.Command;
import com.Josh.library.core.component.RemoteObjectWrapper;
import com.Josh.library.core.component.Command.COMMAND;
import com.Josh.library.core.component.StaticFieldVirtualParentObject;
import com.Josh.library.core.exception.RemoteExecutionFailedException;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * this class provides methods dealing with network communication
 * @author Josh
 *
 */
@SuppressLint("UseSparseArrays")
public class SocketHandler {
	
	private RecieveThread thread;
	private String IPAddress;
	private int  port;
	private Socket socket;
	private Set<Thread> waitingThreads;
	private Set<Command> recievedCommands;
	private ObjectInputStream is;
	private ObjectOutputStream os;
	private boolean isRecieveThreadOn = false;
	private TransmitThread transmitThread;
	private boolean isTransmitServiceOn;
	private boolean isConnected = false;
	private Handler waitHandler = new Handler();
	static private final String Tag = "SocketHandler";
	
	public SocketHandler(){
		waitingThreads = new HashSet<Thread>();
		recievedCommands = new HashSet<Command>();
	}
	
	
	/**
	 * connect to the server, throws exception if connection failed
	 * @param ip
	 * 		ip address of server
	 * @param port
	 *  	port of the server
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	public void connect(String ip, int port) throws UnknownHostException, IOException{
		if(isConnected()) return;
		this.IPAddress = ip;
		this.port = port;
		socket = new Socket(ip,port);
		os = new ObjectOutputStream(socket.getOutputStream());
		is = new ObjectInputStream(socket.getInputStream());
		isConnected = true;
		Log.i(Tag, "Connect success! Server address: "+this.IPAddress+":"+this.port);
	}
	
	/**
	 * disconnect with the server
	 * @throws IOException
	 */
	public void disConnect() throws IOException{	
		if(os!=null)
			os.close();
		if(is!=null)
			is.close();
		if(socket!=null)
			socket.close();
		if(this.transmitThread!=null)
			transmitThread.stopTransmit();
		synchronized(this.waitingThreads){
			for(Thread thread: waitingThreads)
				thread.interrupt();
			waitingThreads.clear();
		}
		
		synchronized(this.recievedCommands){
			recievedCommands.clear();
		}
		isConnected = false;
		Log.i(Tag, "Disconnected from server: "+this.IPAddress+":"+this.port);
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
			Log.i(Tag, "Unable to start recieve service!");
			return false;
		}
	}
	
	/**
	 * check if the recieve thread is on
	 * @return
	 * 		on or not
	 */
	public boolean isRecieveServiceOn(){
		return this.isRecieveThreadOn;
	}
	
	/**
	 * check if network is connected
	 * @return
	 * 		connected or not
	 */
	public boolean isConnected(){
		if(socket == null) return false;
		return isConnected;
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
				if(cmd!=COMMAND.PING_RETURN)
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
	}

	/**
	 * transmit command
	 * @param cmd
	 * 		command
	 * @throws IOException
	 */
	public void transmit(Command cmd) throws IOException {		
		if(isConnected() && isTransmitServiceOn && os!=null && transmitThread!=null){	
			transmitThread.AddTransmitQueue(cmd);
		}else{
			throw(new IOException("Unable to transmit data!"));
		}
	}
	
		
	/**
	 * the recieve thread
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
							onRecieve(cmd);
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
		public void stopTransmit(){
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
					Log.e(Tag, "An error occured when transmitting data, transmit service quited!");
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
	private void onRecieve(Command cmd){
		COMMAND CMD = cmd.getCOMMAND();
		
		if(CMD == COMMAND.OBJECT_REQUEST){
			//if the command is an object request
			RemoteObjectInformationSystem info = ClientEngine.getClientEngine().getRemoteObjectInfoSystem();
			Object result = null;
			String className = (String) cmd.getExtra("className");
			String fieldName = (String) cmd.getExtra("fieldName");
			long threadId = (Long) cmd.getExtra("threadId");
			Command reply = new Command(COMMAND.OBJECT_REQUEST_RETURN,cmd.getCommandId());	
			long ClientThreadId = -1;
			try {
				Log.i(Tag, "recieve object request from server: "+className+"."+fieldName+" to server");
				result = info.getStaticFieldVirtualParentObject(className, fieldName);
				if(result == null)
					throw(new RemoteExecutionFailedException("Unable to get virtual parent object of static field:"+
							className+"."+fieldName));
				
				ClientThreadId = info.getClientThreadId(threadId);
				RemoteObjectWrapper wrapper = info.SaveObjectInfoInAnotherThread(result, ClientThreadId);
				Log.i(Tag, "Getting object successful! Send it to the server: "+
						((StaticFieldVirtualParentObject )result).getValue());
				reply.putExtra("objectWrapper", wrapper);
				reply.putExtra("hasException", false);
			} catch (RemoteExecutionFailedException e) {
				reply.putExtra("hasException", true);
				reply.putExtra("exception", e);
				reply.putExtra("exceptionType", "RemoteExecutionFailedException");
				Log.e(Tag, "An error occur when get field: "+className+"."+fieldName
						+", error message: "+ e.getMessage());
				info.removeObjectInfoInAnotherThread(result, ClientThreadId);
			}catch(NullPointerException e) {
				reply.putExtra("hasException", true);
				reply.putExtra("exception", e);
				reply.putExtra("exceptionType", "NullPointerException");
				Log.e(Tag, "An error occur when get field: "+className+"."+fieldName);
				info.removeObjectInfoInAnotherThread(result, ClientThreadId);
			}
	
			try {
				transmit(reply);			
			} catch (IOException e) {
				e.printStackTrace();
				Log.e(Tag, "Unable to transmit reply "+CMD+" to server!");
				info.removeObjectInfoInAnotherThread(result, ClientThreadId);
			}
			
			return;
		}
	}
	
	
}
