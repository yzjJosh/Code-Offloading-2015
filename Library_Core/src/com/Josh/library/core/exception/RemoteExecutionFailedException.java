package com.Josh.library.core.exception;

/**
 * This exception is thrown when there is a problem for offloading a method, this problem
 * happens in the offloading system, not thrown by the user code.
 * @author Josh
 *
 */
public class RemoteExecutionFailedException extends Exception {
	private static final long serialVersionUID = 1L;
	public RemoteExecutionFailedException(String str){
			super(str);
	}
	public RemoteExecutionFailedException(){
		super();
	}
}
