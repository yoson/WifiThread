package com.example.wifithread;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		TxWifiProvider wp = new TxWifiProvider(this);
		HandlerThread ht = new HandlerThread("wifi");
		ht.start();
		Handler hd = new Handler(ht.getLooper());
		wp.startup(hd, 0);
		
		
	}
}
