package com.Josh.library.core.component;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * A MethodPackage object is an object that contains all the information of a method,
 * including its name, class, parameter types, parameters, and so on.
 * @author Josh
 *
 */
public class MethodPackage implements Serializable{

	private static final long serialVersionUID = 1L;
	private String MethodName;
	private String ClassName;
	private String TypeName[];
	private RemoteObjectWrapper objectWrapper;
	private RemoteObjectWrapper[] paramWrappers;
	private boolean packed = false;

	public MethodPackage(){
		
	}

	public MethodPackage(String ClassName, String MethodName, String TypeName[], RemoteObjectWrapper obj, RemoteObjectWrapper[] params){	
		pack(ClassName,MethodName,TypeName,obj,params);
	};
	
	/**
	 * Pack method information as a MethodPackage
	 * @param ClassName
	 * 		the name of the class in which this method locates
	 * @param MethodName
	 * 		the name of this method
	 * @param TypeName
	 * 		array of parameter type names
	 * @param obj
	 * 		the wrapped object which invokes this method
	 * @param params
	 * 		array of wrapped parameters
	 */
	public void pack(String ClassName, String MethodName, String TypeName[], RemoteObjectWrapper obj, RemoteObjectWrapper[] params){
		this.ClassName=ClassName;
		this.MethodName=MethodName;
		this.TypeName=TypeName;
		this.objectWrapper=obj;
		this.paramWrappers=params;
		packed = true;
	}
	
	/**
	 * Get the full name of the method, includes its class, its modifiers and its parameter types. 
	 * @param loader
	 * 		the class loader which loads the class in which the method is defined
	 * @return
	 * 		the full name of the method
	 */
	public String toString(ClassLoader loader){
		if(!packed) return "";
		try {
			Class<?> clazz = loader.loadClass(ClassName);
			Method method = CodeHandler.getMethod(clazz, MethodName, TypeName);			
			int modifier = method.getModifiers();
			String str = Modifier.toString(modifier) + " " + method.getReturnType().getName()+" "+ClassName +"." + MethodName +"(";
			for(int i=0;i<TypeName.length;i++){
				str += TypeName[i];
				if(i != TypeName.length-1)
					str+=",";
			}
			str+=")";
			return str;
		} catch (NoSuchMethodException e) {
		} catch(ClassNotFoundException e){
		}
		return this.MethodName;
	}
	
	/**
	 * Get the name of the class in which this method is defined
	 * @return
	 * 		the name of the class, or empty string if no method is packed
	 */
	public String getClassname(){
		if(!packed) return "";
		return this.ClassName;
	}
	
	/**
	 * Get the name of the method
	 * @return
	 * 		the method name, or empty string if no method is packed
	 */
	public String getMethodName(){
		if(!packed) return "";
		return this.MethodName;
	}
	
	/**
	 * Get the parameter names array
	 * @return
	 * 		the array of parameter names, or null if no method is packed
	 */
	public String[] getParamTypeName(){
		if(!packed) return null;
		return this.TypeName;
	}
	
	/**
	 * Get the wrapper of the object which invokes this method.
	 * @return
	 * 		the wrapper of the object, or null if no method is packed
	 */
	public RemoteObjectWrapper getObjectWrapper(){
		if(!packed) return null;
		return this.objectWrapper;
	}
	
	/**
	 * Get the array of wrappers of parameters
	 * @return
	 * 		array of wrapped parameters, or null if no method is packed
	 */
	public RemoteObjectWrapper[] getParamWrappers(){
		if(!packed) return null;
		return this.paramWrappers;
	}
	
	/**
	 * Check if any method has been packed to this package
	 * @return
	 * 		if any method is packed or not
	 */
	public boolean hasPacked(){
		return this.packed;
	}
}
