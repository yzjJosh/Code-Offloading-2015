package com.Josh.library.core.component;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import com.Josh.library.core.interfaces.TreeScanner;

/**
 * An ObjectReferenceInfo object is an object that contains information about an object,
 * its sub-objects, and the reference relationship between parent object and its sub-objects.
 * ObjectReferenceInfo can be a node of a tree, its parent node contains information of its parent object,
 * and its son nodes contain information of its sub-objects.
 * @author Josh
 *
 */
public class ObjectReferenceInfo implements Serializable {
	private int id;
	private int referenceNum;
	private Object thisObject = null;
	private ObjectReferenceInfo parent = null;
	private Set<ObjectReferenceInfo> sonSet = new HashSet<ObjectReferenceInfo>();
	private String parentField;
	private static final long serialVersionUID =1L;
	
	public ObjectReferenceInfo(int id,ObjectReferenceInfo parent, String parentFieldName, int referenceNum){
		this.id = id;
		this.referenceNum = referenceNum;
		this.parent = parent;
		this.parentField = parentFieldName;
	}
	
	/**
	 * Get the name of the filed in which this object belongs to
	 * @return
	 * 		the field name, or null if this object does not has a parent object
	 */
	public String getParentFieldName(){
		return this.parentField;
	}
	
	/**
	 * Get the id of this object
	 * @return
	 * 		the id
	 */
	public int getId(){
		return this.id;
	}
	
	/**
	 * get the reference time of this object
	 * @return
	 * 		reference time
	 */
	public int getReferenceNum(){
		return this.referenceNum;
	}
	
	/**
	 * Get the parent node of this ObjectReferenceInfo
	 * @return
	 * 		the parent node, or null if this ObjectReferenceInfo does not has a parent
	 */
	public ObjectReferenceInfo getParent(){
		return this.parent;
	}
	
	/**
	 * Put son ObjectReferenceInfo to this ObjectReferenceInfo, a son ObjectReferenceInfo
	 * contains information of a sub-object of this object.
	 * @param son
	 * 		the son ObjectReferenceInfo of this  ObjectReferenceInfo
	 * @return
	 * 		true if succeed, false otherwise
	 */
	public boolean put(ObjectReferenceInfo son){
		synchronized(sonSet){
			return this.sonSet.add(son);
		}
	}
	
	/**
	 * Set the object which this ObjectReferenceInfo describes
	 * @param obj
	 * 		the object
	 */
	public void setObject(Object obj){
		this.thisObject = obj;
	}
	
	/**
	 * Get the object which this ObjectReferenceInfo describes
	 * @return
	 * 		the object
	 */
	public Object getObject(){
		return this.thisObject;
	}
	
	/**
	 * Scan all sub-ObjectReferenceInfo-nodes from this node
	 * @param tree
	 * 		a tree scanner that provides onScanning method
	 */
	public void ScanObjectTree(final TreeScanner tree){
		tree.onScanning(this);
		if(sonSet==null) return;
		synchronized(sonSet){
			for(ObjectReferenceInfo son:sonSet){
				son.ScanObjectTree(tree);
			}
		}
	}

}
