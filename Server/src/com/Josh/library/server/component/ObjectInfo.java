package com.Josh.library.server.component;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.Josh.library.core.component.BasicType;
import com.Josh.library.core.component.CodeHandler;
import com.Josh.library.core.component.ObjectReferenceInfo;
import com.Josh.library.core.component.ObjectSynchronizationInfo;
import com.Josh.library.core.component.RemoteObjectWrapper;
import com.Josh.library.core.component.StaticFieldVirtualParentObject;
import com.Josh.library.core.exception.RemoteExecutionFailedException;
import com.Josh.library.core.interfaces.TreeScanner;

import android.annotation.SuppressLint;
import android.util.Log;
import android.util.SparseArray;

/**
 * This class deals with remote object information
 * @author Josh
 *
 */
@SuppressLint("UseSparseArrays")
public class ObjectInfo {
	private SparseArray<Object[]> id2objAndrN;
	private Map<Object,Integer> obj2id;
	private Map<Long,Long> ClientThread2ServerThread;
	private Map<Long,Long> ServerThread2ClientThread;
	private Map<String,Map<String,StaticFieldVirtualParentObject>> staticFieldVirtualParentObjectMap;
	private Map<Long,List<Integer>> methodRemoteObject;
	private Object threadIdLock = new Object();
	private SparseArray<Set<Thread>> id2SleepingThread;
	private final static String Tag = "ObjectInfo";

	
	public ObjectInfo(){
		id2objAndrN = new SparseArray<Object[]>();
		obj2id = new HashMap<Object,Integer>();
		ServerThread2ClientThread = new HashMap<Long,Long>();
		ClientThread2ServerThread = new HashMap<Long,Long>();
		staticFieldVirtualParentObjectMap = new HashMap<String,Map<String,StaticFieldVirtualParentObject>>();
		methodRemoteObject = new HashMap<Long,List<Integer>>();
		id2SleepingThread = new SparseArray<Set<Thread>>();
	}
	
	/**
	 * Get synchronization information of all remote objects in current server thread. Note that these
	 * objects are objects that has an id.
	 * @param objSyncMap
	 * 		a map to save synchronization information of objects 
	 * @param loader
	 * 		the class loader that loads client classes
	 * @return
	 * 		a map that saves synchronization information of objects, the return value is the same as 
	 * 		the parameter objSyncMap 
	 * @throws RemoteExecutionFailedException
	 * 		if any of the objects or their sub-objects is inserializable
	 */
	public Map<Integer,ObjectSynchronizationInfo> getRemoteObjectsSychronizationInfoInCurrentThread(Map<Integer,ObjectSynchronizationInfo> objSyncMap, ClassLoader loader) throws RemoteExecutionFailedException{
		if(objSyncMap == null) return null;
		long threadId = Thread.currentThread().getId();
		List<Integer> objIdList = this.methodRemoteObject.get(threadId);
		if(objIdList == null) return objSyncMap;
		synchronized(objIdList){
			for(Integer id : objIdList){
				Object obj = getObject(id);
				if(obj == null) continue;
				if(StaticFieldVirtualParentObject.class.isInstance(obj))
					((StaticFieldVirtualParentObject)obj).updateValue(loader);
				ObjectSynchronizationInfo syncInfo = getObjectSynchronizationInfo(obj);
				synchronized(objSyncMap){
					objSyncMap.put(id, syncInfo);
				}
			}
		}
		return objSyncMap;
	}
	
	/**
	 *  get the StaticFieldVirtualParentObject of a static field
	 * @param ClassName
	 * 		name of the class
	 * @param FieldName
	 * 		name of the field
	 * @param loader
	 * 		the class loader that loads client classes
	 * @return
	 * 		the StaticFieldVirtualParentObject, the vitual parent object has updated
	 */
	public StaticFieldVirtualParentObject getStaticFieldVirtualParentObject(String ClassName, String FieldName, ClassLoader loader){
		StaticFieldVirtualParentObject result = null;
		Map<String,StaticFieldVirtualParentObject> fieldMap = staticFieldVirtualParentObjectMap.get(ClassName);
		if(fieldMap!=null){
			result = fieldMap.get(FieldName);
			if(result!=null){
				result.updateValue(loader);
				return result;
			}
			
			else{
				return null;
			}
		}else{
			return null;
		}
	}
	
	/**
	 * Save a StaticFieldVirtualParentObject to the system
	 * @param obj
	 * 		the StaticFieldVirtualParentObject that needs to be saved
	 */
	public void addStaticFieldVirtualParentObject(StaticFieldVirtualParentObject obj){
		String className = obj.getClassName();
		String fieldName = obj.getFieldName();
		Map<String,StaticFieldVirtualParentObject> map = staticFieldVirtualParentObjectMap.get(className);
		if(map == null){
			map = new HashMap<String,StaticFieldVirtualParentObject>();
			synchronized(staticFieldVirtualParentObjectMap){
				staticFieldVirtualParentObjectMap.put(className, map);
			}
		}
		
		synchronized(map){
			map.put(fieldName, obj);
		}
	}
	
	/**
	 * Delete a saved StaticFieldVirtualParentObject
	 * @param className
	 * 		the name of the class where the binded field of the StaticFieldVirtualParentObject is defined
	 * @param fieldName
	 * 		the name of the field that is binded by the StaticFieldVirtualParentObject
	 */
	public void removeStaticFieldVirtualParentObject(String className, String fieldName){
		Map<String,StaticFieldVirtualParentObject> map = staticFieldVirtualParentObjectMap.get(className);
		if(map == null) return;
		synchronized(map){
			map.remove(fieldName);
		}
	}
	
	/**
	 * Bind a client thread with a server thread, that is to say, if you know one of them,
	 * you can get another.
	 * @param ClientThreadId
	 * 		id of the client thread
	 * @param ServerThreadId
	 * 		id of the server thread
	 */
	public void setThreadRelationship(long ClientThreadId, long ServerThreadId){
		synchronized(threadIdLock){
			synchronized(ServerThread2ClientThread){
				ServerThread2ClientThread.put(ServerThreadId, ClientThreadId);
			}
			synchronized(ClientThread2ServerThread){
				ClientThread2ServerThread.put(ClientThreadId, ServerThreadId);
			}
		}
	}
	
	/**
	 * clear saved thread id information, this method will clear the saved client thread id and its
	 * binding server thread id
	 * @param ServerThreadId
	 * 		the server thread id
	 */
	public void ClearThreadId(long ServerThreadId){
		synchronized(threadIdLock){
			Long ClientThreadId = ServerThread2ClientThread.get(ServerThreadId);
			if(ClientThreadId!=null){
				synchronized(ClientThread2ServerThread){
					ClientThread2ServerThread.remove(ClientThreadId);
				}
				synchronized(ServerThread2ClientThread){
					ServerThread2ClientThread.remove(ServerThreadId);
				}
			}
		}
	}
	
	/**
	 * Get server thread id from a client thread id
	 * @param ClientThreadId
	 * 		the client thread id
	 * @return
	 * 		the server thread id
	 */
	public long getServerThreadId(long ClientThreadId){
		return ClientThread2ServerThread.get(ClientThreadId);		
	}
	
	/**
	 * Get client thread id from server thread id
	 * @param ServerThreadId
	 * 		the server thread id
	 * @return
	 * 		the client thread id
	 */
	public long getClientThreadId(long ServerThreadId){
		return ServerThread2ClientThread.get(ServerThreadId);
	}
	
	/**
	 * Get a remote object from its id
	 * @param id
	 * 		the id
	 * @return
	 * 		the remote object
	 */
	public Object getObject(int id){
		Object[] temp = id2objAndrN.get(id);
		if(temp == null) return null;
		else return temp[0];
	}
	
	/**
	 * Get the value of a field of an remote object
	 * @param id
	 * 		the id of the remote object
	 * @param fieldName
	 * 		the name of the field
	 * @return
	 * 		the value
	 */
	public Object getObjectFieldValue(int id, String fieldName){
		Object obj = getObject(id);
		if(obj == null) return null;
		Field field = null;
		try {
			field = CodeHandler.getField(Class.forName(obj.getClass().getName()), fieldName);
			if(field == null) return null;
			else{
				return field.get(obj);
			}
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(Tag, "Unable to get field :" + fieldName + " object id:"+id);
			return null;
		}
		
	}
	
	/**
	 * Set reference time of a remote object
	 * @param id
	 * 		id of a remote object
	 * @param rNum
	 * 		reference time of the remote object
	 */
	public void setReferenceNum(int id, int rNum){
		if(id<0) return;
		Object[] objs = id2objAndrN.get(id);
		if(objs == null) return;
		synchronized(objs){
			objs[1] = rNum;
		}
	}
	
	
	/**
	 * Get a remote object and its reference time from an id
	 * @param id
	 * 		id of a remote object
	 * @return
	 * 		an array that contains the object and its reference time, the first value
	 * 		of the array is the object, and the second value of the array is its integer
	 * 		reference time
	 */
	public Object[] getObjectAndReferenceNum(int id){
		return id2objAndrN.get(id);
	}
	
	/**
	 * Get reference time of an remote object from its id
	 * @param id
	 * 		id of the remote object
	 * @return
	 * 		the reference time
	 */
	public int getReferenceNum(int id){
		if(id<0) return 0;
		Object[] temp = id2objAndrN.get(id);
		if(temp == null) return 0;
		else return (Integer) temp[1];
	}
	
	/**
	 * Save a new remote object, the object will be saved in the offloading system, its id will
	 * be remembered and its reference time will be set to 1
	 * @param id
	 * 		the id of the remote object
	 * @param obj
	 * 		the remote object
	 */
	public void addObject(int id, Object obj){
		if(obj == null) return;
		Object[] item = new Object[2];
		item[0] = obj;
		item[1] = 1;
		synchronized(id2objAndrN){
			id2objAndrN.put(id, item);
		}
		synchronized(obj2id){
			obj2id.put(obj, id);
		}
		interrupThreads(id);
		Log.i(Tag, obj.getClass().getName()+ " got id:"+id);
	}
	
	/**
	 * Get id from an remote object
	 * @param obj
	 * 		the remote object
	 * @return
	 * 		the id of the remote object, or -1 if the object is not saved 
	 */
	public int getIdFromObject(Object obj){
		Integer id = obj2id.get(obj);
		if(id == null) return -1;
		else
			return id;
	}
	
	/**
	 * Remove an remote object from its id, all the object's data that is saved in the system will be deleted
	 * @param id
	 * 		the id of the object
	 */
	public void removeObject(int id){
		Object[] objs = id2objAndrN.get(id);
		if(StaticFieldVirtualParentObject.class.isInstance(objs[0])){
			StaticFieldVirtualParentObject parentObj = (StaticFieldVirtualParentObject) objs[0];
			removeStaticFieldVirtualParentObject(parentObj.getClassName(),parentObj.getFieldName());
		}
		synchronized(obj2id){
			obj2id.remove(objs[0]);
		}
		synchronized(id2objAndrN){
			id2objAndrN.remove(id);
		}
		Log.i(Tag, objs[0].getClass().getName()+" release id:"+id);
	}
	
	
	/**
	 * Remove all saved remote object that is binded to current thread, that is to say, their
	 * reference time will decrease, they will no longer bind to this thread, and their saved data
	 * may be deleted if needed.
	 */
	public void removeAllRemoteObjectInCurrentThread(){
		long threadId = Thread.currentThread().getId();
		List<Integer> objList = methodRemoteObject.get(threadId);
		if(objList == null) return;
		synchronized(objList){
			for(Integer id: objList){
				Object[] objs = id2objAndrN.get(id);
				synchronized(objs){
					int referenceNum = (Integer) objs[1];
					if(referenceNum>1){
						synchronized(objs){
							objs[1] = referenceNum-1;
						}
						Log.i(Tag, "Server: id = "+id+" Referebce time = "+(referenceNum-1));
					}else{
						removeObject(id);
						Log.i(Tag, "Server: id = "+id+" Referebce time = 0");
					}
				}
			}
		}
		synchronized(methodRemoteObject){
			methodRemoteObject.remove(threadId);
		}
	}
	
	/**
	 * Remove saved information of an object and its sub-objects, that is to say, their 
	 * reference time will be decrease, they will no longer bind to current thread, and
	 * their saved data may be deleted if needed. 
	 * @param wrapper
	 * 		the wrapper that contains information of an object and its sub-objects
	 */
	public void removeObjectInfo(RemoteObjectWrapper wrapper){
		long threadId = Thread.currentThread().getId();
		removeObjectInfoInAnotherThread(wrapper, threadId);
	}
	
	/**
	 * Remove saved information of an object and its sub-objects, that is to say, their
	 * reference time will decrease, they will no longer bind to a specific thread, and 
	 * their saved data may be deleted if needed.
	 * @param wrapper
	 * 		the wrapper that contains information of an object and its sub-objects
	 * @param threadId
	 * 		id of the server thread that this object is binded to
	 */
	public void removeObjectInfoInAnotherThread(RemoteObjectWrapper wrapper, long threadId){
		List<Integer> objList = this.methodRemoteObject.get(threadId);
		if(objList == null) return;
		reMoveObjectInfo_core(wrapper,objList);
		if(objList.size()==0)
			synchronized(methodRemoteObject){
				methodRemoteObject.remove(threadId);
			}
	}
	
	/**
	 * The core component of all remove information methods. This method will remove saved 
	 * information of an object and its sub-objects, that is to say, their reference time 
	 * will decrease, and their saved data may be deleted if needed. 
	 * @param wrapper
	 * 		the wrapper that contains information of an object and its sub-objects
	 * @param objList
	 * 		if the object or its sub-object's information is removed successfully, the object 
	 * 		or its sub-object will be removed from this list.
	 */
	private void reMoveObjectInfo_core(RemoteObjectWrapper wrapper,final List<Integer> objList){
		synchronized(wrapper){
			if(wrapper.isEmpty()) return;
			if(wrapper.isBasicType()) return;
			ObjectReferenceInfo infoTree = wrapper.getInfoTree();
			infoTree.ScanObjectTree(new TreeScanner(){

				@Override
				public void onScanning(Object obj) {
					ObjectReferenceInfo info = (ObjectReferenceInfo) obj;
					int id = info.getId();
					Object[] objs = getObjectAndReferenceNum(id);
					if(objs == null) return;
					synchronized(objs){
						int referenceNum = (Integer) objs[1];
						if(referenceNum>1){
							objs[1] = referenceNum-1;
							synchronized(objList){
								objList.remove(id);
							}
							Log.i(Tag, "Server: id = "+id+" Referebce time = "+(referenceNum-1));
						}else{
							synchronized(objList){
								objList.remove(id);
							}
							removeObject(id);
							Log.i(Tag, "Server: id = "+id+" Referebce time = 0");
						}
					}
					
				}
				
			});
		}
	}
	
	
	/**
	 * This method will unwrap an object wrapper, get the object information and save it, that 
	 * is to say, the object and its sub-objects will be recorded, their id will be saved in the system, 
	 * their reference time will increase, and these objects will bind to the current server thread.
	 * @param wrapper
	 * 		the wrapper that contains information of an object and its sub-objects
	 * @return
	 * 		the unwrapped object
	 * @throws RemoteExecutionFailedException
	 * 		if there is a problem
	 */
	public Object unWrapObject(RemoteObjectWrapper wrapper) throws RemoteExecutionFailedException{
		long threadId = Thread.currentThread().getId();
		return unWrapObjectInAnotherThread(wrapper, threadId);
	}
	
	
	/**
	 * This method will unwrap an object wrapper, get the object information and save it, that 
	 * is to say, the object and its sub-objects will be recorded, their id will be saved in the system, 
	 * their reference time will increase, and these objects will bind to a specific server thread.
	 * @param wrapper
	 * 		the wrapper that contains information of an object and its sub-objects
	 * @param threadId
	 * 		id of the server thread that this object is binded to
	 * @return
	 * 		the unwrapped object
	 * @throws RemoteExecutionFailedException
	 * 		if there is a problem
	 */
	public Object unWrapObjectInAnotherThread(RemoteObjectWrapper wrapper, long threadId) throws RemoteExecutionFailedException{
		List<Integer> objList = this.methodRemoteObject.get(threadId);
		if(objList==null){
			objList = new ArrayList<Integer>();
			synchronized(methodRemoteObject){
				methodRemoteObject.put(threadId, objList);
			}
		}
		return unWrapObject_core(wrapper,objList);
	}
	
	/**
	 * Interrupt all threads that are waiting for an object
	 * @param id
	 * 		id of the object which threads are waiting for
	 */
	private void interrupThreads(int id){
		Set<Thread> threadSet = this.id2SleepingThread.get(id);
		if(threadSet == null) return;
		synchronized(threadSet){
			for(Thread thread : threadSet){
				thread.interrupt();
			}
		}
	}
	
	
	/**
	 *The core component of all unwrap object methods. This method will unwrap an object wrapper, get
	 * the object information and save it, that is to say, the object and its sub-objects will be recorded, 
	 * their id will be saved in the system, their reference time will increase.
	 * @param wrapper
	 * 		the wrapper that contains information of an object and its sub-objects
	 * @param objList
	 * 		if the object or its sub-object's information is saved successfully, the object or its sub-object
	 * 		 will be added to this list.
	 * @return
	 * 		the unwrapped object
	 * @throws RemoteExecutionFailedException
	 * 		if there is a problem
	 */
	private Object unWrapObject_core(RemoteObjectWrapper wrapper, final List<Integer> objList) throws RemoteExecutionFailedException {
		synchronized(wrapper){
			if(wrapper.isEmpty())
				return null;
			if(wrapper.isBasicType())
				return wrapper.getObject();
			Object obj = null;
			final int id = wrapper.getMainId();
			ObjectReferenceInfo infoTree = wrapper.getInfoTree();
			if(!wrapper.needTransmit()){
				Object[] objs =null;
				while(true){
					objs = getObjectAndReferenceNum(id);
					if(objs == null){						
						synchronized(id2SleepingThread){
							Set<Thread> sleepingThreads = id2SleepingThread.get(id);
							if(sleepingThreads == null){
								sleepingThreads = new HashSet<Thread>();
								id2SleepingThread.put(id, sleepingThreads);
							}
							synchronized(sleepingThreads){
								sleepingThreads.add(Thread.currentThread());
							}
						}					
						try {
							int i=0;
							for(;i<5;i++){
								Thread.sleep(1000);
								objs = getObjectAndReferenceNum(id);
								if(objs!=null) break;
							}
							if(i<5) break;
							throw(new RemoteExecutionFailedException("Server Error: Object is not transmiteed,"
									+ " but unable to find it locally!"));
						} catch (InterruptedException e) {
							synchronized(id2SleepingThread){
								id2SleepingThread.remove(id);
							}
							continue;
						}
					}
					break;
				}
				
				synchronized(objs){
					obj = objs[0];
					int r = (Integer)objs[1]+1;
					objs[1]=r;
					Log.i(Tag, "Server: id = "+id+" Referebce time = "+r);
				}
				synchronized(objList){
					objList.add(id);
				}				
			}
			else{
				obj = wrapper.getObject();
				if(obj == null) return null;
				addObject(id,obj);
				synchronized(objList){
					objList.add(id);
				}
				Log.i(Tag, "Server: id = "+id+" Referebce time = 1");
			}
			final Object topObj = obj;
			infoTree.ScanObjectTree(new TreeScanner(){
				@Override
				public void onScanning(Object object) {
					ObjectReferenceInfo info = (ObjectReferenceInfo) object;
					int ID = info.getId();
					int ReferenceNum = info.getReferenceNum();
					ObjectReferenceInfo parent = info.getParent();
					if(parent == null){
						info.setObject(topObj);
						return; 
					}
					Object parentObj = parent.getObject();
					String fieldName = info.getParentFieldName();
					if(parentObj == null) return;
					if(fieldName == null) return;
					if(ReferenceNum>1){
						Object[] objs = null;
						while(true){
							objs = getObjectAndReferenceNum(ID);
							if(objs == null){						
								synchronized(id2SleepingThread){
									Set<Thread> sleepingThreads = id2SleepingThread.get(ID);
									if(sleepingThreads == null){
										sleepingThreads = new HashSet<Thread>();
										id2SleepingThread.put(ID, sleepingThreads);
									}
									synchronized(sleepingThreads){
										sleepingThreads.add(Thread.currentThread());
									}
								}					
								try {
									int i=0;
									for(;i<5;i++){
										Thread.sleep(1000);
										objs = getObjectAndReferenceNum(ID);
										if(objs!=null) break;
									}
									if(i<5) break;
									Log.e(Tag,"Cannot find Object with id :"+ID+
											"that may be caused by unmatched reference number!");
										return;
								} catch (InterruptedException e) {
									synchronized(id2SleepingThread){
										id2SleepingThread.remove(ID);
									}
									continue;
								}
							}
							break;
						}
						
						synchronized(objs){
							int r = (Integer)objs[1]+1;
							objs[1]=r;
							Log.i(Tag, "Server: id = "+ID+" Referebce time = "+r);
						}
						synchronized(objList){
							objList.add(ID);
						}
						
						info.setObject(objs[0]);
					
						if(parent.getReferenceNum() == 1)
							try {
								CodeHandler.setFieldValue(parentObj.getClass(), fieldName, parentObj, objs[0]);
							} catch (IllegalAccessException e) {
								e.printStackTrace();
							} catch (IllegalArgumentException e) {
								e.printStackTrace();
							}
	
					}else{
						try {
							Object temp = CodeHandler.getFieldValue(parentObj.getClass(), fieldName, parentObj);
							addObject(ID, temp);
							objList.add(ID);
							info.setObject(temp);
							Log.i(Tag, "Server: id = "+ID+" Referebce time = 1");
						} catch (IllegalAccessException e) {
							e.printStackTrace();
						} catch (IllegalArgumentException e) {
							e.printStackTrace();
						} catch (NoSuchFieldException e) {
							e.printStackTrace();
						}
	
					}
				}
				
			});
			
			return topObj;
		}
	}
	
	/**
	 * Get synchronization information of an object
	 * @param obj
	 * 		the object
	 * @return
	 * 		its synchronization information
	 * @throws RemoteExecutionFailedException
	 * 		if the object or any of its sub-objects is inserializable
	 */
	public ObjectSynchronizationInfo getObjectSynchronizationInfo(Object obj) throws RemoteExecutionFailedException{
		Set<Object> scannedObject = new HashSet<Object>();
		return setObjectInfoRecursive(obj,null,null,scannedObject);
	}
	
	
	/**
	 * The core component of getting synchronization of an object
	 * @param obj
	 * 		the object
	 * @param parent
	 * 		the parent node of the ObjectSynchronizationInfo, this parameter can be null if the object
	 * 		has no parent object.
	 * @param fieldName
	 * 		the name of the field which the object belongs to, this parameter can be null if the object
	 * 		has no parent object.
	 * @param scannedObject
	 * 		a set of scanned object, if the object is in this set, this method will not execute and just
	 * 		skip it. If an object has been analyzed by this method successfully, it will be added to this
	 * 		set. It is useful to prevent repeatly analyzing.
	 * @return
	 * 		an ObjectSynchronizationInfo that contains synchronization information of this object, the
	 * 		ObjectSynchronizationInfo can be a node of a tree.
	 * @throws RemoteExecutionFailedException
	 * 		if the object or any of its sub-objects is inserializable
	 */
	private ObjectSynchronizationInfo setObjectInfoRecursive(Object obj, ObjectSynchronizationInfo parent, String fieldName, Set<Object> scannedObject) throws RemoteExecutionFailedException{
		ObjectSynchronizationInfo info ;
		if(obj==null){
			info = new ObjectSynchronizationInfo(fieldName,obj,parent);
			if(parent!=null)
				parent.addSonSynchronizationInfo(info);
			return info;
		}
			
			Class<?> clazz = obj.getClass();
			if(clazz.isEnum()){
				info = new ObjectSynchronizationInfo(fieldName,obj,parent);
				if(parent!=null)
					parent.addSonSynchronizationInfo(info);
				return info;
			}
			if(clazz.isAnnotation()){
				info = new ObjectSynchronizationInfo(fieldName,obj,parent);
				if(parent!=null)
					parent.addSonSynchronizationInfo(info);
				return info;
			}
			if(clazz.isPrimitive()){
				info = new ObjectSynchronizationInfo(fieldName,obj,parent);
				if(parent!=null)
					parent.addSonSynchronizationInfo(info);
				return info;
			}
			if(BasicType.isBasicType(clazz)){
				info = new ObjectSynchronizationInfo(fieldName,obj,parent);
				if(parent!=null)
					parent.addSonSynchronizationInfo(info);
				return info;
			}
			if(!Serializable.class.isInstance(obj)){
				throw(new RemoteExecutionFailedException("Inserializable object "+obj));
			}
			if(scannedObject.contains(obj)) return null;
			scannedObject.add(obj);
			int id = getIdFromObject(obj);		
			if(id != -1)
				info = new ObjectSynchronizationInfo(fieldName,obj,id,parent);
			else
				info = new ObjectSynchronizationInfo(fieldName,obj,parent);
			if(parent!=null)
				parent.addSonSynchronizationInfo(info);
			Field[] fields = clazz.getDeclaredFields();
			for(Field field:fields){
				if(field.isEnumConstant()) continue;
				int modifier=field.getModifiers();
				if(Modifier.isStatic(modifier)) continue;
				if(Modifier.isTransient(modifier)) continue;
				try {
					Object value = CodeHandler.getFieldValue(field, obj);
					setObjectInfoRecursive(value, info,field.getName(),scannedObject);
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
				} catch (NoSuchFieldException e) {
					e.printStackTrace();
				}
				
			}
				return info;
		}

}
