package com.Josh.library.core.exception;

/**
 * This exception will be throwed when a method is not invoked successfully due 
 * to null class name or method name, or the parameter type name array and parameter 
 * type array does not have the same length, or the ClassLoader is null.
 * @author Josh
 */
public class InvokeMethodFailureException extends Exception {
	private static final long serialVersionUID = 1L;
	public InvokeMethodFailureException(String str){
		super(str);
	}
	public InvokeMethodFailureException(){
		super();
	}
}
