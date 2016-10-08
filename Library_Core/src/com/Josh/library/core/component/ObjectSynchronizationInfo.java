package com.Josh.library.core.component;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.Josh.library.core.interfaces.TreeScanner;

/**
 * An ObjectSynchronizationInfo object is an object that contains the synchronizarion information
 * of an object, the information includes the object itself, its parent object, sub-objects, if they are
 * new objects or not, their id, and some other information that is needed in object synchronization
 * process. An ObjectSynchronizationInfo can be a node of a tree, its parent node presents the 
 * synchronization information of the object's parent object, and its son nodes presents synchronization
 * information of the object's sub-objects.
 * @author Josh
 *
 */
public class ObjectSynchronizationInfo implements Serializable {
	private String fieldName;
	private boolean isNewObject = false;
	private Object obj;
	private Object needSychronizarionObj;
	private int objId = -1;
	private ObjectSynchronizationInfo parent;
	private Map<String,ObjectSynchronizationInfo> sonSychronizationInfo;
	private boolean isSynced = false;
	private static final long serialVersionUID =1L;
	
	public ObjectSynchronizationInfo(String fieldName, Object obj,ObjectSynchronizationInfo parent){
		sonSychronizationInfo = new HashMap<String,ObjectSynchronizationInfo>();
		this.fieldName = fieldName;
		this.obj = obj;
		this.isNewObject = true;
		this.parent = parent;
	}
	
	
	public ObjectSynchronizationInfo(String fieldName, Object obj, int id, ObjectSynchronizationInfo parent){
		sonSychronizationInfo = new HashMap<String,ObjectSynchronizationInfo>();
		this.fieldName = fieldName;
		this.objId = id;
		this.isNewObject = false;
		this.parent = parent;
		this.obj = obj;
	}
	/**
	 * Check if the object that need to be synchronized has been synchronized
	 * @return
	 * 		if the object has been synchronized or not
	 */
	public boolean isSynchronized(){
		return this.isSynced;
	}
	
	/**
	 * Get the amount of son nodes of this ObjectSynchronizationInfo
	 * @return
	 * 		amount of son nodes
	 */
	public int sonNum(){
		return sonSychronizationInfo.size();
	}
	
	/**
	 * Check if this object is a new object, that is to say, check if this object has been
	 * saved as remote object in client remote object information system, if the object is
	 * instantiate in the server, it probably is a new object.
	 * @return
	 * 		if this object is a new object or not
	 */
	public boolean isNewObject(){
		return this.isNewObject;
	}
	
	/**
	 * Get the name of the filed in which this object belongs to
	 * @return
	 * 		the field name
	 */
	public String getFieldName(){
		return this.fieldName;
	}
	
	/**
	 * Get the object which this ObjectSynchronizationInfo describes
	 * @return
	 * 		the object
	 */
	public Object getObject(){
		return this.obj;
	}
	
	/**
	 * Get the id of this object, and if the objct is new, this method will return -1.
	 * @return
	 * 		the id of this object, or -1 if the objcet is new
	 */
	public int getObjectId(){
		return this.objId;
	}
	
	/**
	 * Save the object which needs to be synchronized 
	 * @param obj
	 * 		the object that needs to be synchronized
	 */
	public void setNeedSynchronizationObj(Object obj){
		this.needSychronizarionObj = obj;
	}
	
	/**
	 * Get the object which needs to be synchronized
	 * @return
	 * 		the object that needs to be synchronized
	 */
	public Object getNeedSychronizarionObj(){
		return this.needSychronizarionObj;
	}
	
	/**
	 * Get the parent node of this ObjectSynchronizationInfo
	 * @return
	 * 		the parent ObjectSynchronizationInfo
	 */
	public ObjectSynchronizationInfo getParent(){
		return parent;
	}
	
	/**
	 * Add son ObjectSynchronizationInfo to this ObjectSynchronizationInfo, a son ObjectSynchronizationInfo
	 * contains synchronization information of a sub-object of this object.
	 * @param sync
	 * 		the son ObjectSynchronizationInfo of this  ObjectSynchronizationInfo
	 */
	public void addSonSynchronizationInfo(ObjectSynchronizationInfo sync){
		synchronized(sonSychronizationInfo){
			sonSychronizationInfo.put(sync.getFieldName(), sync);
		}
	}
	
	
	/**
	 * Scan all sub-ObjectSynchronizationInfo-nodes from this node
	 * @param tree
	 * 		a tree scanner that provides onScanning method
	 */
	public void ScanObjectTree(final TreeScanner tree){
		tree.onScanning(this);
		if(sonSychronizationInfo==null) return;
		synchronized(sonSychronizationInfo){
			Set<Entry<String,ObjectSynchronizationInfo>> entrySet = sonSychronizationInfo.entrySet();
			int i =0;
			int sonNum = sonSychronizationInfo.size();
			for(Entry<String,ObjectSynchronizationInfo> son:entrySet){
				if(i==sonNum-1)
					this.isSynced = true;
				son.getValue().ScanObjectTree(tree);
				i++;
			}		
		}
	}
}
