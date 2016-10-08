package com.Josh.library.client.component;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.Josh.library.client.core.IdPool;
import com.Josh.library.client.interfaces.Remoteable;
import com.Josh.library.core.component.BasicType;
import com.Josh.library.core.component.CodeHandler;
import com.Josh.library.core.component.ObjectReferenceInfo;
import com.Josh.library.core.component.RemoteObjectWrapper;
import com.Josh.library.core.component.StaticFieldVirtualParentObject;
import com.Josh.library.core.exception.RemoteExecutionFailedException;

import android.annotation.SuppressLint;
import android.util.Log;
import android.util.SparseArray;

/**
 * this class deals with remote object information
 * @author Josh
 *
 */
@SuppressLint("UseSparseArrays")
public class RemoteObjectInformationSystem {

	private  SparseArray<Integer[]> hashCode2idAndreferenceTimes;
	private SparseArray<RemoteObjectInfo> id2ObjectInfo;
	private IdPool pool;
	private Map<Long,List<Object>> methodRemoteObject;
	private Map<Long,Long> ClientThread2ServerThread;
	private Map<Long,Long> ServerThread2ClientThread;
	private Map<String,Map<String,StaticFieldVirtualParentObject>> staticFieldVirtualParentObjectMap;
	private Object threadIdLock = new Object();
	static private final String Tag = "RemoteClassInfo";	
	
	public RemoteObjectInformationSystem(){
		hashCode2idAndreferenceTimes = new SparseArray<Integer[]>();
		methodRemoteObject = new HashMap<Long,List<Object>>();
		id2ObjectInfo = new SparseArray<RemoteObjectInfo>();
		ServerThread2ClientThread = new HashMap<Long,Long>();
		ClientThread2ServerThread = new HashMap<Long,Long>();
		staticFieldVirtualParentObjectMap = new HashMap<String,Map<String,StaticFieldVirtualParentObject>>();
		pool = new IdPool(100);
	}
	
	/**
	 *  get the StaticFieldVirtualParentObject of a static field
	 * @param ClassName
	 * 		name of the class
	 * @param FieldName
	 * 		name of the field
	 * @return
	 * 		the StaticFieldVirtualParentObject, the vitual parent object has updated
	 */
	public StaticFieldVirtualParentObject getStaticFieldVirtualParentObject(String ClassName, String FieldName){
		StaticFieldVirtualParentObject result = null;
		Map<String,StaticFieldVirtualParentObject> fieldMap = staticFieldVirtualParentObjectMap.get(ClassName);
		if(fieldMap!=null){
			result = fieldMap.get(FieldName);
			if(result!=null){
				result.updateValue(null);
				return result;
			}
			
			else{
				result = new StaticFieldVirtualParentObject(ClassName,FieldName);
				result.updateValue(null);
				synchronized(fieldMap){
					fieldMap.put(FieldName, result);
				}
				return result;
			}
		}else{
			fieldMap = new HashMap<String,StaticFieldVirtualParentObject>();
			result = new StaticFieldVirtualParentObject(ClassName,FieldName);
			result.updateValue(null);
			synchronized(fieldMap){
				fieldMap.put(FieldName, result);
			}
			synchronized(staticFieldVirtualParentObjectMap){
				staticFieldVirtualParentObjectMap.put(ClassName, fieldMap);
			}
			return result;
		}
	}
	
	/**
	 * return an avaliable id from the id pool
	 * @return
	 *   id
	 */
	private int getAvaliableId(){
		return pool.getPosition();
	}
	 
	/**
	 * release an id to the id pool, so that this id can be used again
	 * @param id
	 * 		the id to release
	 */
	private void releaseId(int id){
		pool.returnPosition(id);
	}
	
	/**
	 * bind client thread id with server thread id, so that you can get one of them from another
	 * @param ClientThreadId
	 * @param ServerThreadId
	 */
	public void setThreadRelationship(long ClientThreadId, long ServerThreadId){
		synchronized(threadIdLock){
			synchronized(ServerThread2ClientThread){
				ServerThread2ClientThread.put(ServerThreadId, ClientThreadId);	
			}
			synchronized(ServerThread2ClientThread){
				ClientThread2ServerThread.put(ClientThreadId, ServerThreadId);
			}
		}
	}
	
	/**
	 * clear saved thread id information, this method will clear the saved client thread id and its
	 * binding server thread id
	 * @param ClientThreadId
	 */
	public void ClearThreadId(long ClientThreadId){
		synchronized(threadIdLock){
			Long ServerThreadId = ClientThread2ServerThread.get(ClientThreadId);
			if(ServerThreadId!=null){
				synchronized(ServerThread2ClientThread){
					ServerThread2ClientThread.remove(ServerThreadId);
				}
				synchronized(ClientThread2ServerThread){
					ClientThread2ServerThread.remove(ClientThreadId);
				}
			}
		}
	}
	
	/**
	 * get server thread id from client thread id
	 * @param ClientThreadId
	 * @return
	 * 		the server thread id
	 * @throws RemoteExecutionFailedException
	 * 		if cannot find the server thread id
	 */
	public long getServerThreadId(long ClientThreadId) throws RemoteExecutionFailedException{
		int i = 0;
		while(true){
			Long result = ClientThread2ServerThread.get(ClientThreadId);
			if(result != null)
				return result;
			i++;
			if(i>20) throw(new RemoteExecutionFailedException("Unable to get server thread id"));
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * get client thread id from server thread id
	 * @param ServerThreadId
	 * @return
	 * 		the client thread id
	 */
	public long getClientThreadId(long ServerThreadId){
		return ServerThread2ClientThread.get(ServerThreadId);	
	}
	
	/**
	 * get object information from object id
	 * @param id
	 *  		object id
	 * @return
	 * 		the object information
	 */
	public RemoteObjectInfo getObjectInfoFromId(int id){
		return id2ObjectInfo.get(id);
	}

	
	/**
	 * get the id of a remote object, if the object is not a remote object, this method will return -1
	 * @param obj
	 * 		the remote object
	 * @return
	 * 		the id of the object, or -1 if the object is not remote
	 */
	public int getIdFromObject(Object obj){
		if(obj == null) return -1;
		if(Remoteable.class.isInstance(obj)){
			if(RemoteExecute.getEnvironment(obj) == Environment.LOCAL)
				return -1;
			else
				return RemoteExecute.getId(obj);
		}
		else
			if(Serializable.class.isInstance(obj)){
				Integer[] result = hashCode2idAndreferenceTimes.get(System.identityHashCode(obj));
				if(result!=null)
					return result[0];
				else
					return -1;
			}
			else
				return -1;
	}
	
	/**
	 * get reference time of an remote object, if the object is not remote, this method will return 0
	 * @param obj
	 * 		the remote objcet
	 * @return
	 * 		the reference time of the object, or 0 if the object is not remote
	 */
	public int getReferenceTimesFromObject(Object obj){
		if(obj == null) return 0;
		if(Remoteable.class.isInstance(obj))
			return RemoteExecute.getReferenceNum(obj);
		else
			if(Serializable.class.isInstance(obj)){
				Integer[] result = hashCode2idAndreferenceTimes.get(System.identityHashCode(obj));
				if(result!=null)
					return result[1];
				else
					return 0;
			}
			else
				return 0;
	}
	
	
	/**
	 * check if an object is remote
	 * @param obj
	 * 		the object 
	 * @return
	 * 		if it is remote or not
	 */
	public boolean isRemote(Object obj){
		if(obj == null) return false;
		Class<?> clazz = obj.getClass();
		if(BasicType.isBasicType(clazz)) return false;
		if(clazz.isEnum()) return false;
		if(clazz.isPrimitive()) return false;
		if(Remoteable.class.isInstance(obj)){
			Environment envi = RemoteExecute.getEnvironment(obj);
			if(envi == Environment.SERVER)
				return true;
			else
				return false;
		}
		else
			if(Serializable.class.isInstance(obj)){
				Integer[] result = hashCode2idAndreferenceTimes.get(System.identityHashCode(obj));
				if(result!=null)
					return true;
				else
					return false;
			}
			else
				return false;
	}
	
	/**
	 * save the information of an object and all its sub-objects in another thread, this method will tell the system that this 
	 * object and its sub-objects will be sent to the server, that is to say, the object and its sub-objects will get id, their 
	 * reference time will increase, and they will be binded to a client thread
	 * 
	 * @param obj
	 * 		the object
	 * @param ThreadId
	 * 		id of the client thread which this object will be binded to
	 * @return
	 * 		wrapped object with additional information
	 * @throws RemoteExecutionFailedException
	 * 			if the object or any of its sub-objects is inserializable
	 */
	public RemoteObjectWrapper SaveObjectInfoInAnotherThread(Object obj, long ThreadId) throws RemoteExecutionFailedException{
		Set<Object> scannedObject = new HashSet<Object>();
		List<Object> objList = null;
		synchronized(methodRemoteObject){
			objList = methodRemoteObject.get(ThreadId);
			if(objList == null){
				objList = new ArrayList<Object>();
				methodRemoteObject.put(ThreadId, objList);
			}
		}
		ObjectReferenceInfo infoTree = setIdRecursive(obj,null,null,scannedObject,ThreadId,objList);		
		if(infoTree == null){
			if(obj == null)
				return new RemoteObjectWrapper(null,-1,null);
			else
				return new RemoteObjectWrapper(obj);
		}
		return new RemoteObjectWrapper(obj,infoTree.getId(),infoTree);
	}
	
	
	/**
	 * save the information of an object and all its sub-objects, this method will tell the system that this object and
	 *  its sub-objects will be sent to the server, that is to say, the object and its sub-objects will get id, their 
	 *  reference time will increase, and they will be binded to the current client thread
	 * 
	 * @param obj
	 * 		the object
	 * @return
	 * 		wrapped object with additional information
	 * @throws RemoteExecutionFailedException
	 * 			if the object or any of its sub-objects is inserializable
	 */
	public RemoteObjectWrapper SaveObjectInfo(Object obj) throws RemoteExecutionFailedException{	
		long ThreadId = Thread.currentThread().getId();
		return SaveObjectInfoInAnotherThread(obj, ThreadId);
	}
	
	/**
	 * save the information of an object, this method will tell the system that this object will be sent
	 * to the server, that is to say, the object will get id, its reference time will increase, and it
	 * will be binded to a client thread. This method will not save information of sub-objects of this object.
	 * 
	 * @param obj
	 * 		the object
	 * @param ThreadId
	 * 		id of the client thread which this object will be binded to
	 * @return
	 * 		wrapped object with additional information
	 * @throws RemoteExecutionFailedException
	 * 			if the object is inserializable
	 */
	public RemoteObjectWrapper SaveObjectInfo_Sole(Object obj, long ThreadId) throws RemoteExecutionFailedException{
		Set<Object> scannedObject = new HashSet<Object>();
		List<Object> objList = methodRemoteObject.get(ThreadId);
		if(objList == null){
			objList = new ArrayList<Object>();
			synchronized(methodRemoteObject){
				methodRemoteObject.put(ThreadId, objList);
			}
		}
		ObjectReferenceInfo infoTree = setId_Sole(obj,null,null,scannedObject,ThreadId,objList);
		if(infoTree == null){
			if(obj == null)
				return new RemoteObjectWrapper(null,-1,null);
			else
				return new RemoteObjectWrapper(obj);
		}
		return new RemoteObjectWrapper(obj,infoTree.getId(),infoTree);
	}
	
	/**
	 * The core component of saving information methods. This method will set an id to the object, increase its
	 * reference time, save some information in an ObjectReferenceInfo, and bind this object to a client thread.
	 * This method will not save information of sub-objects of the object.
	 * @param obj
	 * 		the object
	 * @param parent
	 * 		the parent ObjectReferenceInfo, this parameter is useful to save ObjectReferenceInfo as a tree
	 * @param fieldName
	 * 		the name of the field in which this object lies, this parameter is needed to build an ObjectReferenceInfo
	 * @param scannedObject
	 * 		a set of scanned objects, if an object is in this set, this method will skip this object and will not
	 * 		execute. Object will be added to this set after its information being saved by this function. This parameter
	 * 		is useful to prevent repeatly saving information.
	 * @param threadId
	 * 		id of the client thread which this object will be binded to
	 * @param objList
	 * 		if the object's information is saved successfully, the object will be added to this list.
	 * @return
	 * 		an ObjectReferenceInfo that contains saved information
	 * @throws RemoteExecutionFailedException
	 * 		if the object is inserializable
	 * 			
	 */
private ObjectReferenceInfo setId_Sole(Object obj, ObjectReferenceInfo parent, String fieldName, Set<Object> scannedObject, long threadId, List<Object> objList) throws RemoteExecutionFailedException{
	if(obj==null) return null;
	Class<?> clazz = obj.getClass();
	if(clazz.isEnum()) return null;
	if(clazz.isAnnotation()) return null;
	if(clazz.isPrimitive()) return null;
	if(BasicType.isBasicType(clazz)) return null;
	if(scannedObject.contains(obj)) return null;
	scannedObject.add(obj);
	ObjectReferenceInfo info ;
	int id = -1;
	if(Remoteable.class.isInstance(obj)){
		RemoteExecute.setEnvironment(obj, Environment.SERVER);
		int referenceTime = RemoteExecute.getReferenceNum(obj);
		if(referenceTime==0){
			id = getAvaliableId();
			RemoteExecute.setId(obj, id);
			RemoteObjectInfo rinfo = new RemoteObjectInfo();
			synchronized(rinfo){
				rinfo.id = id;
				rinfo.referenceNum = 1;
				rinfo.obj = obj;
			}
			synchronized(rinfo.ClientThreadId){
				rinfo.ClientThreadId.add(threadId);
			}
			synchronized(id2ObjectInfo){
				id2ObjectInfo.put(id, rinfo);
			}
			Log.i(Tag, "object "+clazz.getName()+" get id:"+id);
		}else{
			id = RemoteExecute.getId(obj);
			RemoteObjectInfo rinfo = id2ObjectInfo.get(id);
			if(rinfo!=null){
				synchronized(rinfo){
					rinfo.referenceNum+=1;
					rinfo.ClientThreadId.add(threadId);
				}
			}
		}
		referenceTime += 1;
		RemoteExecute.setReferenceNum(obj, referenceTime);
		synchronized(objList){
			objList.add(obj);
		}
		info = new ObjectReferenceInfo(id,parent,fieldName,referenceTime);
		if(parent!=null)
			parent.put(info);
		Log.i(Tag, "Local: id = "+id+" Reference time = "+referenceTime);
		
	}else
		if(Serializable.class.isInstance(obj)){
			int referenceTime;
			int hashCode = System.identityHashCode(obj);
			Integer[] result = hashCode2idAndreferenceTimes.get(hashCode);
			if(result == null){
				Integer[] data = new Integer[2];
				id = getAvaliableId();
				data[0] = id;
				data[1] = 1;
				referenceTime = data[1];
				synchronized(hashCode2idAndreferenceTimes){
					hashCode2idAndreferenceTimes.put(hashCode, data);
				}
				RemoteObjectInfo rinfo = new RemoteObjectInfo();
				synchronized(rinfo){
					rinfo.id = id;
					rinfo.referenceNum = 1;
					rinfo.obj = obj;
				}
				synchronized(rinfo.ClientThreadId){
					rinfo.ClientThreadId.add(threadId);
				}
				synchronized(id2ObjectInfo){
					id2ObjectInfo.put(id, rinfo);
				}
				Log.i(Tag, "object "+clazz.getName()+" get id:"+data[0]);
				Log.i(Tag, "Local: id = "+data[0]+ " Reference time = "+data[1]);
			}else{
				synchronized(result){
					result[1]+=1;
				}
				referenceTime = result[1];
				id = result[0];
				RemoteObjectInfo rinfo = id2ObjectInfo.get(id);
				if(rinfo!=null){
					synchronized(rinfo){
						rinfo.referenceNum += 1;
						rinfo.ClientThreadId.add(threadId);
					}
				}
//				Log.i(Tag, "Local: id = "+result[0]+ " Reference time = "+result[1]);
			}
			synchronized(objList){
				objList.add(obj);
			}
			info = new ObjectReferenceInfo(id,parent,fieldName,referenceTime);
			if(parent!=null)
				parent.put(info);
		}
		else
			throw(new RemoteExecutionFailedException("Inserializable object "+obj));
	return info;
}


/**
 * This method will set an id to the object and its sub-objects, increase their reference time, save their information
 * as a tree in an ObjectReferenceInfo, and bind these objects to a client thread. This method is the recursively calling 
 * of the method setId_Sole 
 * @param obj
 * 		the object
 * @param parent
 * 		the parent ObjectReferenceInfo, this parameter is useful to save ObjectReferenceInfo as a tree
 * @param fieldName
 * 		the name of the field in which this object lies, this parameter is needed to build an ObjectReferenceInfo
 * @param scannedObject
 * 		a set of scanned objects, if an object is in this set, this method will skip this object and will not
 * 		execute. Object will be added to this set after its information being saved by this function. This parameter
 * 		is useful to prevent repeatly saving information.
 * @param threadId
 * 		id of the client thread which this object and its sub-objects will be binded to
 * @param objList
 * 		if the object's and its sub-objects' information are saved successfully, these objects will be added to this list.
 * @return
 * 		an ObjectReferenceInfo that contains saved information, this ObjectReferenceInfo is a tree.
 * @throws RemoteExecutionFailedException
 * 		if the object or any of its sub-objects is inserializable
 */
private ObjectReferenceInfo setIdRecursive(Object obj, ObjectReferenceInfo parent, String fieldName, Set<Object> scannedObject, long threadId, List<Object> objList) throws RemoteExecutionFailedException{
	if(obj == null) return null;
	synchronized(obj){
		ObjectReferenceInfo info = setId_Sole(obj,parent,fieldName,scannedObject,threadId,objList);
		if(info == null) return null;
		Class<?> clazz = obj.getClass();
			Field[] fields = clazz.getDeclaredFields();
			for(Field field:fields){
				if(field.isEnumConstant()) continue;
				int modifier=field.getModifiers();
				if(Modifier.isStatic(modifier)) continue;
				if(Modifier.isTransient(modifier)) continue;
				try {
					Object value = CodeHandler.getFieldValue(field, obj);
					setIdRecursive(value, info,field.getName(),scannedObject,threadId,objList);
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

/**
 * this method will remove all saved information of objects that is binded to current thread, that is to say,
 * their reference time will be decreased, they will no longer be binded to current thread, and their id may
 * be returned to id pool and their saved information in the system may be cleared if needed
 */
public void removeAllRemoteObjectInCurrentThread(){
	long threadId = Thread.currentThread().getId();
	List<Object> objList = null;
	objList = methodRemoteObject.get(threadId);
	if(objList == null) return;
	synchronized(objList){
		for(Object obj: objList){
			synchronized(obj){
				Class<?> clazz = obj.getClass();
				if(Remoteable.class.isInstance(obj)){
					int referenceTime = RemoteExecute.getReferenceNum(obj);
					if(RemoteExecute.getEnvironment(obj) == Environment.LOCAL) continue;
					if(referenceTime>0){
						RemoteObjectInfo rinfo = id2ObjectInfo.get(RemoteExecute.getId(obj));
						if(rinfo!=null){
							synchronized(rinfo){
								rinfo.referenceNum--;
								rinfo.ClientThreadId.remove(threadId);
							}
						}
						referenceTime-=1;
					}
					Log.i(Tag, "Local: id = "+RemoteExecute.getId(obj)+ " Reference time = "+referenceTime);
					RemoteExecute.setReferenceNum(obj, referenceTime);
					if(referenceTime == 0){
						RemoteExecute.setEnvironment(obj, Environment.LOCAL);
						int id = RemoteExecute.getId(obj);
						RemoteExecute.setId(obj, 0);
						synchronized(id2ObjectInfo){
							id2ObjectInfo.remove(id);
						}
						releaseId(id);
						Log.i(Tag, "object "+clazz.getName()+" release id:"+id);
					}
					}else
						if(Serializable.class.isInstance(obj)){
							int hashCode = System.identityHashCode(obj);
							Integer[] result = hashCode2idAndreferenceTimes.get(hashCode);
							if(result == null) continue;
							if(result[1]>0){
								synchronized(result){
									result[1]-=1;
								}
								RemoteObjectInfo rinfo = id2ObjectInfo.get(result[0]);
								if(rinfo!=null){
									synchronized(rinfo){
										rinfo.referenceNum--;
										rinfo.ClientThreadId.remove(threadId);
									}
								}
							}
							Log.i(Tag, "Local: id = "+result[0]+ " Reference time = "+result[1]);
							if(result[1] == 0){
								synchronized(hashCode2idAndreferenceTimes){
									hashCode2idAndreferenceTimes.remove(hashCode);
								}
								synchronized(id2ObjectInfo){
									id2ObjectInfo.remove(result[0]);
								}
								releaseId(result[0]);
								Log.i(Tag, "object "+clazz.getName()+" release id:"+result[0]);
							}		
						}
						else
							continue;
			}
		}
		synchronized(methodRemoteObject){
			methodRemoteObject.remove(threadId);
		}
	}	
}

/**
 * this method will remove saved information of an object, that is to say, its reference time will
 * be decreased, it will no longer be binded to current thread, its id may be returned to the id pool
 * and its saved information may be deleted if needed. Note that this method will not remove information 
 * of sub-objects of this object, and this method Should be called in the thread which this object
 * is binded to.
 * @param obj
 * 		the object whose information need to be removed
 */
public void removeObjectInfo_Sole(Object obj){
	Set<Object> scannedObject = new HashSet<Object>();
	long threadId = Thread.currentThread().getId();
	List<Object> objList = methodRemoteObject.get(threadId);
	if(objList ==null) return;
	removeSign_Sole(obj,scannedObject,threadId,objList);
	if(objList.size() == 0)
		synchronized(methodRemoteObject){
			methodRemoteObject.remove(objList);
		}
}

/**
 * this method will remove saved information of an object and its sub-objects, that is to say, their reference
 *  time will be decreased, they will no longer be binded to current thread, their id may be returned to the id 
 *  pool and their saved information may be deleted if needed. This method should be called in the thread which
 *  this object is binded to.
 *  
 * @param obj
 * 		the object whose information need to be removed
 */
public void removeObjectInfo(Object obj){
	long threadId = Thread.currentThread().getId();
	removeObjectInfoInAnotherThread(obj, threadId);
}

/**
 * this method will remove saved information of an object and its sub-objects, that is to say, their reference
 *  time will be decreased, they will no longer be binded to a specified thread, their id may be returned to the id 
 *  pool and their saved information may be deleted if needed.
 *   
 * @param obj
 * 		the object whose information need to be removed
 * @param threadId
 * 		id of the client thread which this object is binded to
 * 		
 */
public void removeObjectInfoInAnotherThread(Object obj, long threadId){
	Set<Object> scannedObject = new HashSet<Object>();
	List<Object> objList = methodRemoteObject.get(threadId);
	if(objList ==null) return;
	removeSignRecursive(obj,scannedObject,threadId,objList);
	if(objList.size() == 0)
		synchronized(methodRemoteObject){
			methodRemoteObject.remove(objList);
		}
}

/**
 * The core component of all remove information methods. This method will remove saved information of an object,
 * that is to say, the object's reference time will decrease, it will no longer be binded to a specific thread,
 * its id may be returned to the id pool and its saved information in the system may be cleared if needed.
 * Note that this method will not remove information of sub-objects of this object
 * @param obj
 * 		the object whose information need to be removed
 * @param scannedObject
 * 		a set of scanned objects, if an object is in this set, this method will skip this object and will not
* 		execute. Object will be added to this set after its information being removed by this function. This parameter
* 		is useful to prevent repeatly removing information.
 * @param threadId
 * 		id of the client thread which this object is binded to
 * @param objList
 *		 if the object's information is removed successfully, the object will be removed from this list.
 * @return
 * 		if the object's information is removed successfully or not
 */
private boolean removeSign_Sole(Object obj,Set<Object> scannedObject, long threadId, List<Object> objList){
	if(obj==null) return false;
	Class<?> clazz = obj.getClass();
	if(clazz.isEnum()) return false;
	if(clazz.isAnnotation()) return false;
	if(clazz.isPrimitive()) return false;
	if(BasicType.isBasicType(clazz)) return false;
	if(scannedObject.contains(obj)) return false;
	scannedObject.add(obj);

	if(Remoteable.class.isInstance(obj)){
		int referenceTime = RemoteExecute.getReferenceNum(obj);
		if(RemoteExecute.getEnvironment(obj) == Environment.LOCAL) return false;
		if(referenceTime>0){
			RemoteObjectInfo rinfo = id2ObjectInfo.get(RemoteExecute.getId(obj));
			if(rinfo!=null){
				synchronized(rinfo){
					rinfo.referenceNum--;
					rinfo.ClientThreadId.remove(threadId);
				}
			}
			referenceTime-=1;
		}
		Log.i(Tag, "Local: id = "+RemoteExecute.getId(obj)+ " Reference time = "+referenceTime);
		RemoteExecute.setReferenceNum(obj, referenceTime);
		if(referenceTime == 0){
			RemoteExecute.setEnvironment(obj, Environment.LOCAL);
			int id = RemoteExecute.getId(obj);
			RemoteExecute.setId(obj, 0);
			synchronized(id2ObjectInfo){
				id2ObjectInfo.remove(id);
			}
			releaseId(id);
			Log.i(Tag, "object "+clazz.getName()+" release id:"+id);		
		}
		synchronized(objList){
			objList.remove(obj);
		}
		}else
			if(Serializable.class.isInstance(obj)){
				int hashCode = System.identityHashCode(obj);
				Integer[] result = hashCode2idAndreferenceTimes.get(hashCode);
				if(result == null) return false;
				if(result[1]>0){
					result[1]-=1;
					RemoteObjectInfo rinfo = id2ObjectInfo.get(result[0]);
					if(rinfo!=null){						
						synchronized(rinfo){
							rinfo.referenceNum--;
							rinfo.ClientThreadId.remove(threadId);
						}
					}
				}
				Log.i(Tag, "Local: id = "+result[0]+ " Reference time = "+result[1]);
				if(result[1] == 0){
					synchronized(hashCode2idAndreferenceTimes){
						hashCode2idAndreferenceTimes.remove(hashCode);
					}
					synchronized(id2ObjectInfo){
						id2ObjectInfo.remove(result[0]);
					}
					releaseId(result[0]);
					Log.i(Tag, "object "+clazz.getName()+" release id:"+result[0]);
					
				}	
				synchronized(objList){
					objList.remove(obj);
				}
			}
			else
				return false;
	return true;
}


/**
 * This method will remove saved information of an object and its sub-objects, that is to say, the object 
 * and its sub-objects' reference time will decrease, they will no longer be binded to a specific thread,
 * their id may be returned to the id pool and their saved information in the system may be cleared if needed.
 * This method is the recursively calling of the method removeSign_Sole
 * @param obj
 * 		the object whose information need to be removed
 * @param scannedObject
 * 		a set of scanned objects, if an object is in this set, this method will skip this object and will not
* 		execute. Object will be added to this set after its information being removed by this function. This parameter
* 		is useful to prevent repeatly removing information.
 * @param threadId
 * 		id of the client thread which this object and its sub-objects is binded to
 * @param objList
 *		 if the object and its sub-objects' information is removed successfully, these objects will be removed from this list.
 * @return
 * 		if removing succeed or not
 */
	private boolean removeSignRecursive(Object obj,Set<Object> scannedObject, long threadId, List<Object> objList){
		if(obj == null) return true;
		synchronized(obj){
			if(!removeSign_Sole(obj,scannedObject,threadId,objList))
				return false;
			Class<?> clazz = obj.getClass();
			Field[] fields = clazz.getDeclaredFields();
			for(Field field:fields){
				try {
					if(field.isEnumConstant()) continue;
					int modifier=field.getModifiers();
					if(Modifier.isStatic(modifier)) continue;
					if(Modifier.isTransient(modifier)) continue;
					Object value = CodeHandler.getFieldValue(field, obj);
					if(!removeSignRecursive(value,scannedObject,threadId,objList))
						return false;
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
				} catch(NoSuchFieldException e){
					e.printStackTrace();
				}
		}
	}
		return true;
	
}
	
	/**
	 * This class contains information of a specific remote object, including the object, its
	 * id, its reference time, and the list of id of threads that this object is binded to.
	 * @author Josh
	 *
	 */
	public class RemoteObjectInfo{
		public Object obj;
		public int id;
		public int referenceNum;
		public List<Long> ClientThreadId = new ArrayList<Long>();
	}

}
