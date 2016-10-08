package com.Josh.library.core.component;

import java.io.Serializable;

/**
 * A RemoteObjectWrapper is a wrapped object that contains a object and its information.
 * Each object need to be wrapped as a RemoteObjectWrapper before sending to the server.
 * @author Josh
 *
 */
public class RemoteObjectWrapper implements Serializable  {
	private boolean needTransmit;
	private ObjectReferenceInfo InfoTree = null;
	private boolean isEmpty;
	private int mainId;
	private Object obj;
	private boolean isBasicType;
	private static final long serialVersionUID = 1L;
	
	
	public RemoteObjectWrapper(Object obj, int id,ObjectReferenceInfo infoTree){
		if(id == -1){
			setEmpty();
			return;
		}
		if(infoTree == null){
			setEmpty();
			return;
		}
				
		int referenceTime = infoTree.getReferenceNum();
		if(referenceTime>1){
			needTransmit = false;
			this.obj = null;
		}
		else{
			needTransmit = true;
			this.obj = obj;
		}
		
		if(needTransmit && obj == null){
			setEmpty();
			return;
		}
		
		this.mainId = id;
		this.isEmpty = false;
		this.InfoTree = infoTree;
		this.isBasicType = false;
	}
	
	public RemoteObjectWrapper(Object obj){
		if(obj == null){
			setEmpty();
			return;
		}
		this.obj = obj;
		needTransmit = true;
		InfoTree = null;
		mainId = -1;
		isEmpty = false;
		isBasicType = true;
	}
	
	/**
	 * Set this wrapper empty
	 */
	private void setEmpty(){
		needTransmit = false;
		InfoTree = null;
		isEmpty = true;
		mainId = -1;
		this.obj = null;
		isBasicType = false;
	}
	
	/**
	 * Set the object in this wrapper
	 * @param obj
	 * 		the object
	 */
	public void setObject(Object obj){
		synchronized(obj){
			this.obj = obj;
		}
	}
	
	/**
	 * Set if this object need transmission or not
	 * @param needTransmit
	 * 		if this object need transmission or not
	 */
	public void setNeedTransmit(boolean needTransmit){
		this.needTransmit = needTransmit;
	}
		
	/**
	 * Get the id of the object
	 * @return
	 * 		the id of the object
	 */
	public int getMainId(){
		return mainId;
	}
	
	/**
	 * Check if the type of the wrapped object is a basic type
	 * @return
	 * 		if the type is a basic type or not
	 */
	public boolean isBasicType(){
		return this.isBasicType;
	}
	
	/**
	 * Check if this wrapper is empty or not
	 * @return
	 * 		if this wrapper is empty or not
	 */
	public boolean isEmpty(){
		return isEmpty;
	}

	/**
	 * Check if this object need transmission
	 * @return
	 * 		if this object need transmission
	 */
	public boolean needTransmit(){
		return needTransmit;
	}
	
	/**
	 * Get the object in this wrapper
	 * @return
	 * 		the object
	 */
	public Object getObject(){
		return this.obj;
	}
	
	/**
	 * Get the Object reference information of the object
	 * @return
	 * 		the object reference information
	 */
	public ObjectReferenceInfo getInfoTree(){
		return this.InfoTree;
	}


}
