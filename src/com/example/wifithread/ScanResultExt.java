package com.example.wifithread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


import android.net.wifi.ScanResult;

class ScanResultExt {

	private static final Comparator<ScanResult> LEVEL_COMP = new Comparator<ScanResult>() {
		public int compare(ScanResult lhs, ScanResult rhs) {
			return rhs.level - lhs.level;
		}
	};

	private CopyOnWriteArrayList<ScanResult> results;
	/**
	 * �?始扫描时�?
	 */
	private long scanTime;
	/**
	 * 得到扫描结果时间
	 */
	private long updateTime;

	ScanResultExt(long scanTime) {
		super();
		this.results = new CopyOnWriteArrayList<ScanResult>();
		this.scanTime = scanTime;
	}

	ScanResultExt(List<ScanResult> result, long scanTime, long updateTime) {
		this.results = new CopyOnWriteArrayList<ScanResult>(result);
		sort();
		this.scanTime = scanTime;
		this.updateTime = updateTime;
	}

	public void setResults(List<ScanResult> results) {
		this.results.clear();
		this.results.addAll(results);
		sort();
		
	}

	//将其进行排序
	private synchronized void sort(){
		ArrayList<ScanResult> results2 = new ArrayList<ScanResult>(this.results);
		Collections.sort(results2, LEVEL_COMP);
		this.results = new CopyOnWriteArrayList<ScanResult>(results2);
	}
	public void setScanTime(long scanTime) {
		this.scanTime = scanTime;
	}

	public void setUpdateTime(long updateTime) {
		this.updateTime = updateTime;
	}

	/** 注意: 不要修改它的返回�? */
	public CopyOnWriteArrayList<ScanResult> getResults() {
		return results;
	}

	public void clear() {
		results.clear();
	}

	public static boolean isNullOrEmpty(ScanResultExt s) {
		return s == null || s.size() == 0;
	}

	public void setResults(List<ScanResult> results, long updateTime) {
		setResults(results);
		setUpdateTime(updateTime);
	}

	public long getScanTime() {
		return scanTime;
	}

	public long getUpdateTime() {
		return updateTime;
	}

	/**
	 * 是否包含该bssid表示的ap
	 * 
	 * @param bssid
	 * @return
	 */
	public final boolean contains(String bssid) {
		for (ScanResult r : results) {
			if (r.BSSID.equals(bssid)) {
				return true;
			}
		}
		return false;
	}

	public int size() {
		return results.size();
	}

	/**
	 * 合并另一个实�?
	 * 
	 * @param other
	 * @return �? other 为null �? �?, 则返回当前实�?; 否则返回合并得到的新实例
	 */
	public ScanResultExt merge( ScanResultExt other) {
		if (isNullOrEmpty(other)) {
			return new ScanResultExt(results, scanTime, updateTime);
		}
		CopyOnWriteArrayList<ScanResult> older = null;
		CopyOnWriteArrayList<ScanResult> younger = null;
		if (updateTime > other.updateTime) {
			// this �? result �?
			older = other.results;
			younger = this.results;
		} else {
			// this �? result �?
			older = this.results;
			younger = other.results;
		}

		final ScanResultExt merged = new ScanResultExt(0L);
		final CopyOnWriteArrayList<ScanResult> mergedResult = merged.results;

		merged.scanTime = Math.max(scanTime, other.scanTime);
		merged.updateTime = Math.max(updateTime, other.updateTime);

		mergedResult.addAll(younger);
		for (ScanResult r : older) {
			if (!merged.contains(r.BSSID)) {
				mergedResult.add(r);
			}
		}
		return merged;
	}
}
