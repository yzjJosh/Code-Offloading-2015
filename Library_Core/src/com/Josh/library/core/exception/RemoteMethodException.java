package com.Josh.library.core.exception;

/**
 * This runtime exception is a wrapper of a checked exception, it will be thrown by the
 * offloading system and will be caught and unwrapped by the system to get the original
 * exception and throw it.  
 * @author Josh
 *
 */
public class RemoteMethodException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	private Exception originalException;
	
	public RemoteMethodException(Exception e){
		super();
		this.originalException = e;
	}
	
	public Exception getOriginalException(){
		return this.originalException;
	}
}
