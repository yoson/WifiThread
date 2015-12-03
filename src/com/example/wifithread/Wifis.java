package com.example.wifithread;

import java.util.Collections;
import java.util.List;

import android.R.string;
import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;


public class Wifis {

	public static boolean Permission_Denied = false;


	/**
	 * startScan, 忽略 CHANGE_WIFI_STATE权限问题引起的exception
	 */
	public static boolean startScanQuietly(WifiManager wifiManager) {
		if (wifiManager != null) {
			try {
				return wifiManager.startScan();
			} catch (Exception e) {
				//Log.e("Wifis", "cannot start wifi scan");
				Permission_Denied = true;
			}
		}
		return false;
	}

	/**
	 * getScanResults, 忽略 CHANGE_WIFI_STATE权限问题引起的exception
	 */
	public static List<ScanResult> getScanResultsQuietly(WifiManager wifiManager) {
		List<ScanResult> list = null;
		if (wifiManager != null) {
			try {
				list = wifiManager.getScanResults();
			} catch (Exception e) {
				//Log.e("Wifis", "cannot getScanResults");
				Permission_Denied = true;
			}
		}
		if (list == null) {
			list = Collections.emptyList();
		}
		
		return list;
	}
}
