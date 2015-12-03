package com.example.wifithread;

import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

/* 1. 创建实例前已确保  WifiManager 不为 null 
 * 2. 公开的方法非线程安全的, 调用者应处理同步问题
 * */
final class TxWifiProvider extends BroadcastReceiver {
	private static final String TAG = "TxWifiProvider";

	/** ap 数量下限, 少于这个数量时需要进行第二轮扫描 */
	private static final int MIN_AP_NUM = 7;

	volatile boolean mStarted;
	private final Context mContext;
	private final WifiManager mWifiManager;

	/**
	 * 上次更新wifi scan result的时间
	 */
	private long mLastUpdateTime;

	/**
	 * 上次发起wifi scan的时间.
	 * <p>
	 * 注意: scan result 更新跟发起 wifi scan 并不存在完全对应的关系, 比如其他应用发起 wifi scan 也会导致 scan
	 * result 更新, 所以非常有必要增加如下成员
	 */
	private long mLastScanTime;

	private int mUpdateCount;
	
	/**
	 * 记录是否已发起过第一次扫描
	 */
	private boolean mScaned;

	// FIXME
	/*
	 * 这里的 handler 来自 TxLocationManagerImpl, 产生了不必要的耦合. 但如果自己新建一个的话, 可能遇到
	 * handler thread dead 的问题
	 */
	private static Handler mHandler;
	
	/**
	 * 本次扫描, 第一轮(必须)
	 */
	private final ScanResultExt mFirstNewScan;
	/**
	 * 本次扫描, 第二轮(可选, 第一轮 ap 数目 MIN_AP_NUM 时才进行第二轮)
	 */
	private final ScanResultExt mSecondNewScan;

	private final Runnable mScanWifiTask;

	public TxWifiProvider(Context appContext) {
		mContext = appContext;
		mWifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
		
		mFirstNewScan = new ScanResultExt(0);
		mSecondNewScan = new ScanResultExt(0);
		
		//mHandler = new Handler(Looper.getMainLooper());
		mScanWifiTask = new Runnable() {

			@Override
			public void run() {
//				long lastUpdate = mLastUpdateTime;
//				long now = System.currentTimeMillis();
//				long offset = now - lastUpdate;
//				// TODO debug
//				
//				LogUtil.i(TAG, "mScanWifiTask: offset=" + offset + ",scan now=" + (offset > 3000));
//				
//				// trick 根据上次 update 时间避免 wifi 扫描均匀且不至于过快
//				if (offset > 3000) {
					tryScanNow();
//				} else {
//					long delay = mContext.getAppStatus().getWifiScanInterval() - offset;
//					scheduleWifiScan(delay < 0 ? 0L : delay);
//			}
				scheduleWifiScan(5000L);
			}
		};
	}

	public void startup(Handler handler, long millis) {
		if (mStarted) {
			return;
		}
		mStarted = true;
		mScaned = false;

		mHandler = handler;
		listenWifiState(handler);

		// 发起 wifi scan
		scheduleWifiScan(0L);

		Log.e(TAG, "startup: state=[start]");
	}
	
	public void shutdown() {
		if (!mStarted) {
			return;
		}
		mStarted = false;

		mHandler.removeCallbacks(mScanWifiTask);

		try {
			mContext.unregisterReceiver(this);
		} catch (Exception e) {
		}

		// clean obj
		mFirstNewScan.clear();
		mSecondNewScan.clear();

		mUpdateCount = 0;
		mLastScanTime = 0;
		mLastUpdateTime = 0;

		Log.i(TAG, "shutdown: state=[shutdown]");
	}

	/**
	 * 
	 * @return 1-wifi关闭导致无法扫描, 0-其他, 需要扫描且已成功扫描
	 */
	public int scanNowIfNeed() {
		// FIXME 首次定位时, cellprovider 先于 wifiprovider 启动
		// 而 cellprovider 会在当前线程中主动读取基站, 并使用这个基站通知上层
		// 上层要求 wifiprovider 开始扫描wifi, (但这时 wifiprovider尚未启动)
		// 所以如果这里检查 mStarted 会导致无法扫描
		
//		if (!mStarted) {
//			return 1;
//		}
		return tryScanNow() ? 0 : 1;
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent == null) {
			return;
		}
		Log.e(TAG, Thread.currentThread().getName()+":receiver");
		String action = intent.getAction();
		Log.i(TAG, "onReceive " + action);

		if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
			notifyStatus();
		}

		if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)
				|| WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
			List<ScanResult> result = Wifis.getScanResultsQuietly(mWifiManager);
			//result = WifiBlackList.filter(result, null);
			// TODO wifi 过滤
			/* Be Careful! */ 
			/* 无论如何都应向上层发送通知, 否则在热点过少的环境中可能出现长时间不定位的问题  */
			// if (result.size() == 0)
			//	return;

			// 认为直到此时才成功更新 wifi scan result
			mLastUpdateTime = System.currentTimeMillis();
			// TODO 外部 app 可能影响 mLastScanTime
			
			if (mUpdateCount == 0) { // 第一轮扫描
				// 第一轮扫描时必须完全清空
				mSecondNewScan.clear();
				mFirstNewScan.clear();

				ScanResultExt o = mFirstNewScan;
				
				o.clear();
				o.setScanTime(mLastScanTime);
				o.setUpdateTime(mLastUpdateTime);
				o.setResults(result);

				boolean needScanAgain = mScaned && issueSecondScan(o.size() < MIN_AP_NUM);
				if (needScanAgain) {
					mUpdateCount = 1;
				} else {
					handleWifiUpdate();
				}
			} else { // 第二轮扫描
				mUpdateCount = 0;
				ScanResultExt o = mSecondNewScan;
				
				o.clear();
				o.setScanTime(mLastScanTime);
				o.setUpdateTime(mLastUpdateTime);
				o.setResults(result);
				
				handleWifiUpdate();
			}
			mScaned = true;
			scheduleWifiScan(5000L);
		}

		// 不要在这里调用 scheduleWifiScan, 因为可能受其他应用干扰
	}

	private void listenWifiState(Handler handler) {
		IntentFilter filter = new IntentFilter();
		filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
		try {
			mContext.registerReceiver(this, filter, null, handler);
		} catch (Exception e) {
			Log.e(TAG, "listenWifiState: failed", e);
		}
	}

	/**
	 * 延时扫描 wifi
	 * 
	 */
	private void scheduleWifiScan(long scanInterval) {
		Handler handler = mHandler;
		Runnable scanTask = mScanWifiTask;

		handler.removeCallbacks(scanTask);
		handler.postDelayed(scanTask, scanInterval);
	}

	/**
	 * 
	 * @return true 成功发起 wifi 扫描
	 */
	private boolean tryScanNow() {
		Log.e(TAG, Thread.currentThread().getName()+":tryscan");
		boolean issued = Wifis.startScanQuietly(mWifiManager);
		if (issued) {
			mLastScanTime = System.currentTimeMillis();
		}
		return issued;
	}

	/**
	 * 发起第2次扫描
	 * @param needScanAgain 是否应发起第2次扫描
	 * @return true 成功发起第2次扫描
	 */
	private boolean issueSecondScan(boolean need) {
		return need && tryScanNow();
	}

	private void handleWifiUpdate() {
		ScanResultExt newScan = mFirstNewScan.merge(mSecondNewScan);
		notifyListeners(newScan.getResults());
	}

	private void notifyListeners(List<ScanResult> result) {
		for(ScanResult sr:result){
			Log.e(TAG, sr.toString());
		}
	}

	private void notifyStatus() {
		
	}

	// test method
	ScanResultExt[] checkScanResultExt() {
		return new ScanResultExt[] { mFirstNewScan, mSecondNewScan };
	}
}
