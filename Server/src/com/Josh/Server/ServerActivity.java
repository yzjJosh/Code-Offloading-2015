package com.Josh.Server;

import com.Josh.Server.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Main activity of the server app
 * @author Josh
 *
 */
public class ServerActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		//Start the service
		startService(new Intent(this,MyService.class));
	}
}
