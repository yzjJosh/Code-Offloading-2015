package com.Josh.library.core.interfaces;

/**
 * This interface provides a method that is used for scanning a tree. The onScanning method 
 * will be invoked when scanning a tree node, this method should be implemented by user.
 * @author Josh
 *
 */
public interface TreeScanner {
	
	/**
	 * This method will be called when scanning a tree node.
	 * @param obj
	 * 		the current scanning tree node
	 */
	public abstract void onScanning(Object obj);
}
