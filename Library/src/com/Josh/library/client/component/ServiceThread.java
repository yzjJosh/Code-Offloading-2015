package com.Josh.library.client.component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import com.Josh.library.core.component.Command;
import com.Josh.library.core.component.Command.COMMAND;
import com.Josh.library.core.exception.RemoteExecutionFailedException;

import android.content.Context;
import android.util.Log;

/**
 * this is the thread that controls the network
 * @author Josh
 *
 */
public class ServiceThread extends Thread {
	private SocketHandler socketHandler;
	private boolean stop = false;
	private String ip;
	private int port;
	private Context context;
	private final static String Tag = "ServiceThread";

	public ServiceThread(Context context, SocketHandler handler, String ip, int port){
		this.socketHandler = handler;
		this.ip = ip;
		this.port = port;
		this.context = context;
	}
	
	/**
	 * stop this thread
	 */
	public void Stop(){
		this.stop = true;
		stopService();
	}
	
	
	/**
	 * thread main process
	 */
	public void run(){
		this.stop = false;
		while(true){
			if(stop) return;
			
			// connect server
			tryToConnectServer();
			if(stop) return;
			
			//start thransmit 
			if(!socketHandler.startTransmit()){
				stopService();
				continue;
			}
			if(stop) return;
			
			//start recieve
			if(!socketHandler.startRecieve()){
				stopService();
				continue;
			}
			if(stop) return;
			
			//send apk file to server
			try {
				SendAPKfile();
			} catch (Exception e) {
				e.printStackTrace();
				Log.i(Tag, "Failed when try to send apk file!");
				stopService();
				continue;
			}
			if(stop) return;		
			
			//ping server
			while(socketHandler.isConnected() && socketHandler.isRecieveServiceOn()){
				if(stop) return;
				try {
					long time = pingTime();
		//			Log.d(Tag, "Ping time: "+time+"ms");
					if(stop) return;
					if(time<1000)
						//connection is stable, ready for offloading
						ClientEngine.getClientEngine().setCanExecuteRemotely(true);
					else
						// connection is unstable, it is unsuitable for offloading
						ClientEngine.getClientEngine().setCanExecuteRemotely(false);
				} catch (RemoteExecutionFailedException e) {
					if(stop) return;
					// failed to ping server, it is unsuitable for offloading
					Log.i(Tag, "Failed to ping server...");
					ClientEngine.getClientEngine().setCanExecuteRemotely(false);
				}
				SLEEP(2000);
			}
			
			//connection lost, set unable to execute remotely
			ClientEngine.getClientEngine().setCanExecuteRemotely(false);
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
	 * stop the network connection
	 */
	private void stopService(){
		try {
			socketHandler.disConnect();
		} catch (IOException e) {
			e.printStackTrace();
		}
		ClientEngine.getClientEngine().setCanExecuteRemotely(false);
	}
	
	/**
	 * try to connect the server in a loop
	 */
	private void tryToConnectServer(){
		while(true){
			if(stop) return;
			try {
				socketHandler.connect(ip, port);
				break;
			} catch (Exception e) {
				Log.i(Tag, "Try to connect server at "+ip+":"+port+", failed! Try again in 5 seconds.");
			}
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * ping the server, this method will throw a RemoteExecutionFailedException if time out
	 * @return
	 * 	ping time
	 * @throws RemoteExecutionFailedException
	 * 		if cannot get the ping reply in 5 seconds
	 */
	private long pingTime() throws RemoteExecutionFailedException{
		Command cmd = new Command(COMMAND.PING,0);		
		long time = 0;
		for(int i=0;i<4;i++){
			long temp = System.currentTimeMillis();
			try {
				socketHandler.transmit(cmd);
				socketHandler.WaitForCommand(COMMAND.PING_RETURN, 0, 5000);
			} catch (Exception e) {
				throw(new RemoteExecutionFailedException("failed to ping server"));
			}
			temp = System.currentTimeMillis() - temp;
			time+=temp;
		}
		time/=4;
		return time;
	}
	
	/**
	 * ask the server if it has the apk file of this application, if it does not have it,
	 * send the apk file to server
	 * @throws RemoteExecutionFailedException
	 * 			there is a problem
	 * @throws IOException
	 */
	private void SendAPKfile() throws RemoteExecutionFailedException, IOException{
		Command cmd = new Command(COMMAND.CODE_TRANSMIT,0);
		String apkPath = context.getPackageCodePath();
		String apkName = new File(apkPath).getName();
		cmd.putExtra("ask", true);
		cmd.putExtra("apkName", apkName);
		socketHandler.transmit(cmd);
		Command reply = socketHandler.WaitForCommand(COMMAND.CODE_TRANSMIT_RETURN,0,5000);
		if((Boolean)reply.getExtra("hasException") == true)
			throw((RemoteExecutionFailedException) reply.getExtra("exception"));
		if((Boolean)reply.getExtra("needTransmit") == true){			
			Log.i(Tag, "start transmitting apk file...");
			File apk = new File(apkPath);
			FileInputStream fis = new FileInputStream(apk);
			byte[] apkbytes = new byte[fis.available()];
			fis.read(apkbytes);
			fis.close();
			Command filecmd = new Command(COMMAND.CODE_TRANSMIT,0);
			filecmd.putExtra("ask", false);
			filecmd.putExtra("apk", apkbytes);
			filecmd.putExtra("apkName", apkName);
			socketHandler.transmit(filecmd);
			reply = socketHandler.WaitForCommand(COMMAND.CODE_TRANSMIT_RETURN,0,10000);
			if((Boolean)reply.getExtra("needTransmit") == false){
				Log.i(Tag, "Transmit apk file successfully!");
				return;
			}
			else
				throw(new RemoteExecutionFailedException("Server does not get apk file!"));
			
		}else
			Log.i(Tag, "Server already has apk file!");
		
	}
	
}



