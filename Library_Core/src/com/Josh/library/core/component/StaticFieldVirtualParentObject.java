package com.Josh.library.core.component;

import java.io.Serializable;

/**
 * A static virtual parent object is a virtual parent object of a static field, a 
 * StaticFieldVirtualParentObject is binded with a unique static field, that means
 * if you have a StaticFieldVirtualParentObject, you can get the binded static field.
 * If a StaticFieldVirtualParentObject is marked as a remote object, the binded static
 * field will be seen as in the server.
 * @author Josh
 *
 */
public class StaticFieldVirtualParentObject implements Serializable {
	private String ClassName;
	private String FieldName;
	private Object obj;
	private static final long serialVersionUID = 1L;
	
	public StaticFieldVirtualParentObject(String className, String fieldName){
		this.ClassName = className;
		this.FieldName = fieldName;
	}
	
	/**
	 * Get the value of the binded field. Note that this value may not be the latest value,
	 * this value is the value of the field at the time when updateValue method is called.
	 * @return
	 * 		the value
	 */
	public Object getValue(){
		return obj;
	}
	
	/**
	 * Get the name of the class which the binded field locates
	 * @return
	 * 		the name of the class
	 */
	public String getClassName(){
		return this.ClassName;
	}
	
	/**
	 * Get the name of the static field
	 * @return
	 * 		the name of the field
	 */
	public String getFieldName(){
		return this.FieldName;
	}
	
	/**
	 * Set the value of the binded static field
	 * @param value
	 * 		the value
	 * @param loader
	 * 		the class loader which loads the class of the field, if this parameter is null, 
	 * 		this method will use defalt class loader to load the class.
	 */
	public void setValue(Object value, ClassLoader loader){
		Class<?> clazz = null;
		try {
			clazz = Class.forName(ClassName);
		} catch (ClassNotFoundException e1) {
			try {
				clazz = loader.loadClass(ClassName);		
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		try {
			CodeHandler.setFieldValue(clazz, FieldName, null, value);
			obj = value;
		} catch (Exception e) {
			e.printStackTrace();
		}
	
	}
	
	/**
	 * Update the value of the binded static field, that means set the field "obj" in this 
	 * StaticFieldVirtualParentObject as the value of the binded static field. The value 
	 * can be got via getValue method.
	 * @param cLoader
	 * 		the class loader which loads the class of the field, if this parameter is null, 
	 * 		this method will use defalt class loader to load the class.
	 */
	public void updateValue(ClassLoader cLoader){

		Class<?> clazz = null;
		try {
			clazz = Class.forName(ClassName);
		} catch (ClassNotFoundException e1) {
			try {
				clazz = cLoader.loadClass(ClassName);		
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		try {
			obj = CodeHandler.getFieldValue(clazz, FieldName, null);
		} catch (Exception e) {
			e.printStackTrace();
		} 

	}
}
