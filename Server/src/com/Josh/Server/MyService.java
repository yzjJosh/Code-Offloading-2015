package com.Josh.Server;

import com.Josh.library.server.component.ServerEngine;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Main service of the server offloading system
 * @author Josh
 *
 */
public class MyService extends Service {

	@Override
	public void onCreate(){
		super.onCreate();
		
		//Start the server engine, network port is 6666
		ServerEngine.getServerEngine().Start(this, 6666);
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

}
