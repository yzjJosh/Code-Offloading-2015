package com.Josh.library.client.component;


import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.aspectj.lang.reflect.MethodSignature;

import android.content.Context;
import android.util.Log;

import com.Josh.library.client.component.RemoteObjectInformationSystem.RemoteObjectInfo;
import com.Josh.library.client.core.IdPool;
import com.Josh.library.core.component.BasicType;
import com.Josh.library.core.component.CodeHandler;
import com.Josh.library.core.component.Command;
import com.Josh.library.core.component.MethodPackage;
import com.Josh.library.core.component.ObjectSynchronizationInfo;
import com.Josh.library.core.component.RemoteObjectWrapper;
import com.Josh.library.core.component.StaticFieldVirtualParentObject;
import com.Josh.library.core.component.Command.COMMAND;
import com.Josh.library.core.exception.RemoteExecutionFailedException;
import com.Josh.library.core.interfaces.TreeScanner;


/**
 * this class provides methods that can control the whole offloading system, and some methods that is used
 * for offloading
 * @author Josh
 *
 */
public class ClientEngine {	
	static private final String Tag="ClientEngine";
	private static ClientEngine engine=new ClientEngine();
	private boolean isStarted=false;
	private RemoteObjectInformationSystem remoteObjInfoSys;
	private SocketHandler socketHandler;
	private IdPool MethodIdPool;
	private IdPool ServerFieldSetIdPool;
	private IdPool ServerFieldGetIdPool;
	private ServiceThread thread;
	private Database database;
	private Object lock = new Object();
	private boolean isDebugOn = false;
	private boolean CanExecuteRemotely = false;

	private ClientEngine(){
		remoteObjInfoSys=new RemoteObjectInformationSystem();
		MethodIdPool = new IdPool(10);
		ServerFieldSetIdPool = new IdPool(10);
		ServerFieldGetIdPool = new IdPool(10);
		socketHandler = new SocketHandler();		
	}
	
	/**
	 * Return the client engine. A client engine can not be instantiated manually, 
	 * and this is the only method to get it.
	 * @return
	 * 		the client engine
	 */
	public static ClientEngine getClientEngine(){
		return engine;
	}
	
	/**
	 * start the offloading system
	 * @param context
	 *         the context of the application
	 * @param IP
	 * 			ip address of server
	 * @param port
	 *          port of the server
	 * @return
	 *        this client engine
	 */
	public ClientEngine Start(Context context, String IP, int port){
		if(isStarted) return this;
		thread = new ServiceThread(context, socketHandler,IP,port);
		thread.start();
		isStarted=true;
		database = new Database(context);
		return this;
	}
	
	/**
	 * stop the offloading system
	 * @return
	 * 		this client engine
	 */
	public ClientEngine Stop(){
		if(thread!=null)
			thread.Stop();
		isStarted=false;
		return this;
		
	}
	
	/**
	 * check if the offloading system is started
	 * @return 
	 */
	public boolean isStarted(){
		
		return isStarted;
	}
	
	/**
	 * set if it is allowed to offload methods
	 * @param bool
	 * 		allow or not
	 */
	void setCanExecuteRemotely(boolean bool){
		synchronized(lock){
			CanExecuteRemotely = bool;
		}	
	}
	
	/**
	 * return the remote object information system
	 * @return
	 * 		the remote object information system
	 */
	public RemoteObjectInformationSystem getRemoteObjectInfoSystem(){
		return this.remoteObjInfoSys;
	}
	
	/**
	 * check if a method can be executed remotely, this decision is made by
	 * considering the network latency and the local and remote execution time
	 * @param name
	 * 			the full method name
	 * @return
	 *        can execute remotely or not
	 */
	boolean canExecuteRemotely(String name){
		boolean result;
		result = database.shouldExecuteRemotely(name);
		synchronized(lock){
			return (CanExecuteRemotely && result);
		}
	}
	
	Database getDatabase(){
		return this.database;
	}
	
	/**
	 * get the method full name from a method signature
	 * @param signature
	 *         method signature
	 * @return
	 * 			the full name of the method
	 */
	String getMethodName(MethodSignature signature){
		int modifier = signature.getModifiers();
		String name = Modifier.toString(modifier)+" ";
		name+= signature.getReturnType().getName()+" ";
		name+= signature.getDeclaringType().getName()+".";
		name+= signature.getName()+"(";
		Class<?>[] paramTypes = signature.getParameterTypes();
		for(int i=0;i<paramTypes.length;i++){
			name+=paramTypes[i].getName();
			if(i!=paramTypes.length-1)
				name+=",";
		}
		name+=")";
		return name;
	}
	
	
	/**
	 * turn on or turn off calling of method Log.i and Log.d in offloading system code, this method
	 * is useful for developer to debug in the offloading system. It is better to set it false
	 * in release version application
	 * @param debug
	 *        if is true, log output is on; if is false, log output is false
	 * @return
	 * 		 this client engine
	 */
	public ClientEngine setDebug(boolean debug){
		isDebugOn = debug;
		return this;
	}
	
	/**
	 * check if log output is on or off
	 * @return
	 *  	status of log output
	 */
	public boolean isDebugOn(){
		return isDebugOn;
	}

	
	/**
	 * offload a method to server, this method will block the thread until execution is finished or exception is caught.
	 * If the offloaded method throws an exception, this method will throw an  InvocationTargetException, and if error
	 *  occurs in the offloading process, this method will throw a RemoteExecutionFailedException
	 * @param Package
	 * 			a package containing information of the method
	 * @return
	 * 			the return value of the method
	 * @throws RemoteExecutionFailedException
	 * 			if error occurs in the offloading process
	 * @throws InvocationTargetException
	 * 			if the offloaded method throws an exception
	 */
	@SuppressWarnings("unchecked")
	Object executeMethodRemotely(final MethodPackage Package) throws RemoteExecutionFailedException, InvocationTargetException{
		if(Package == null)
			throw(new RemoteExecutionFailedException("Method package is null!"));
		if(!socketHandler.isConnected())
			throw(new RemoteExecutionFailedException("Can not connect to server!"));
		final int id = MethodIdPool.getPosition();
		final long threadId = Thread.currentThread().getId();
		Command cmd = new Command(COMMAND.EXECUTE_METHOD,id);
		cmd.putExtra("MethodPackage", Package);
		cmd.putExtra("threadId", threadId);
		try {
			Log.i(Tag, "Asking for server thread id...");
			socketHandler.transmit(cmd);			
		} catch (IOException e) {
			MethodIdPool.returnPosition(id);
			throw(new RemoteExecutionFailedException("error to transmit command when asking for thread id"));
		}
		Command retCmd ;
		


		Command reply = null;
		try {
			reply = socketHandler.WaitForCommand(COMMAND.EXECUTE_METHOD_THREAD_ID_RETURN,id, 5000);
		} catch (RemoteExecutionFailedException e) {
			Log.e(Tag, "Unable to get remote method thread id, method :"
					+ Package.toString(getClass().getClassLoader())+
					", client thread id: "+threadId);
			MethodIdPool.returnPosition(id);
			throw(e);
		}
		
		long ServerThreadId = (Long) reply.getExtra("threadId");
		remoteObjInfoSys.setThreadRelationship(threadId, ServerThreadId);	
		Log.i(Tag, "Got server thread id:"+ServerThreadId);

		
		cmd = new Command(COMMAND.EXECUTE_METHOD_THREAD_ID_RETURN,id);
		String methodName = Package.toString(getClass().getClassLoader());
		try {
			Log.i(Tag, "Start executing method " +methodName+" remotely!"+
					" Client thread id: "+threadId+", Server thread id: "+ServerThreadId);
			socketHandler.transmit(cmd);
		} catch (IOException e) {
			MethodIdPool.returnPosition(id);
			throw(new RemoteExecutionFailedException("error to transfer command when asking for executing method"));
		}
			
		try {
			retCmd = socketHandler.WaitForCommand(COMMAND.EXECUTE_METHOD_RESULT_RETURN,id,0);
		} catch (RemoteExecutionFailedException e) {
			Log.e(Tag, "Unable to get remote method result, method :"
					+ Package.toString(getClass().getClassLoader())+
					", client thread id: "+threadId);
			MethodIdPool.returnPosition(id);
			throw(e);
		}
		
		MethodIdPool.returnPosition(id);
		remoteObjInfoSys.ClearThreadId(threadId);
		
		if((Boolean)retCmd.getExtra("hasException")){
			if(((String)retCmd.getExtra("exceptionType")).equals("InvocationTargetException"))
				throw((InvocationTargetException) retCmd.getExtra("exception"));
			else
				if(((String)retCmd.getExtra("exceptionType")).equals("RemoteExecutionFailedException"))
					throw((RemoteExecutionFailedException) retCmd.getExtra("exception"));
		}
		
		Map<Integer,ObjectSynchronizationInfo> remoteObjecSynctMap = (Map<Integer,ObjectSynchronizationInfo>) retCmd.getExtra("remoteObjecSynctMap");
		Set<Object> skipObjects = new HashSet<Object>();
		if(remoteObjecSynctMap!=null){
			Set<Entry<Integer, ObjectSynchronizationInfo>> entrySet = remoteObjecSynctMap.entrySet();
			for(Entry<Integer,ObjectSynchronizationInfo> entry: entrySet){
				int ID = entry.getKey();
				ObjectSynchronizationInfo syncInfo = entry.getValue();
				Object localObject = remoteObjInfoSys.getObjectInfoFromId(ID).obj;
				SynchronizeObject(localObject,syncInfo,skipObjects);
				if(StaticFieldVirtualParentObject.class.isInstance(localObject)){
					StaticFieldVirtualParentObject vpo = (StaticFieldVirtualParentObject) localObject;
					vpo.setValue(vpo.getValue(), null);
				} 
			}
		}
		
		ObjectSynchronizationInfo resultSync = (ObjectSynchronizationInfo) retCmd.getExtra("resultSync");
		SynchronizeObject(resultSync.getObject(),resultSync,skipObjects);
		
		Log.i(Tag, "Method "+methodName+" execution finished!");
		
		
		return resultSync.getObject();
		
	}

	
	/**
	 * set a field of an object that is in the server, this method will block the thread until remote field is set or exception is caught.
	 * If the remote object is not setted, this method will
	 * throw a RemoteExecutionFailedException
	 * @param value
	 * 		value to set
	 * @param id
	 * 		id of the object
	 * @param field
	 * 		field name 
	 * @throws RemoteExecutionFailedException
	 * 			if the remote object is not setted
	 */
	void setFieldToServer(Object value, int id, String field) throws RemoteExecutionFailedException{
		if(!socketHandler.isConnected())
			throw(new RemoteExecutionFailedException("Can not connect to server!"));
		int cmdId = ServerFieldSetIdPool.getPosition();
		Command cmd = new Command(COMMAND.FIELD_SET,cmdId);
		cmd.putExtra("id", id);
		cmd.putExtra("field", field);
		if(remoteObjInfoSys.isRemote(value)){
			cmd.putExtra("isAlreadyRemote", true);
			cmd.putExtra("valueId", remoteObjInfoSys.getIdFromObject(value));
		}else{
			RemoteObjectWrapper wrapper = null;
			RemoteObjectInfo rinfo = remoteObjInfoSys.getObjectInfoFromId(id);
			List<Long> serverThreadIdList = new ArrayList<Long>();
			boolean isFirst = true;
			Object[] threadIdArray =  rinfo.ClientThreadId.toArray();
			try {
				for(int i =0;i<threadIdArray.length;i++){
					long ClientThreadId = (Long) threadIdArray[i];
					long serverThreadId = remoteObjInfoSys.getServerThreadId(ClientThreadId);
					serverThreadIdList.add(serverThreadId);
					if(isFirst){
						wrapper = remoteObjInfoSys.SaveObjectInfoInAnotherThread(value,ClientThreadId);
						isFirst = false;
					}
					else
						remoteObjInfoSys.SaveObjectInfoInAnotherThread(value,ClientThreadId);
				}
			} catch (RemoteExecutionFailedException e) {
				ServerFieldSetIdPool.returnPosition(cmdId);
				for(int i =0;i<threadIdArray.length;i++){
					long ClientThreadId = (Long) threadIdArray[i];
					remoteObjInfoSys.removeObjectInfoInAnotherThread(value, ClientThreadId);
				}
				throw(e);
			}
			cmd.putExtra("isAlreadyRemote", false);
			cmd.putExtra("valueWrapper", wrapper);
			cmd.putExtra("ThreadIdList", serverThreadIdList);
		}
		try {
			Log.i(Tag, "Start setting remote field, object id = "+id+", field name = "+field+
					", value = "+value);
			socketHandler.transmit(cmd);
		} catch (IOException e) {
			ServerFieldSetIdPool.returnPosition(cmdId);
			RemoteObjectInfo rinfo = remoteObjInfoSys.getObjectInfoFromId(id);
			Object[] threadIdArray =  rinfo.ClientThreadId.toArray();
			for(int i =0;i<threadIdArray.length;i++){
				long ClientThreadId = (Long) threadIdArray[i];
				remoteObjInfoSys.removeObjectInfoInAnotherThread(value, ClientThreadId);
			}
			throw(new RemoteExecutionFailedException(e.getMessage()));
		}
		Command retCmd;
		try {
			retCmd = socketHandler.WaitForCommand(COMMAND.FIELD_SET_RETURN,cmdId,5000);
				
		} catch (RemoteExecutionFailedException e) {
			ServerFieldSetIdPool.returnPosition(cmdId);
			throw(e);
		}
		ServerFieldSetIdPool.returnPosition(cmdId);
		
		if((Boolean)retCmd.getExtra("hasException")){
			RemoteObjectInfo rinfo = remoteObjInfoSys.getObjectInfoFromId(id);
			Object[] threadIdArray =  rinfo.ClientThreadId.toArray();
			for(int i =0;i<threadIdArray.length;i++){
				long ClientThreadId = (Long) threadIdArray[i];
				remoteObjInfoSys.removeObjectInfoInAnotherThread(value, ClientThreadId);;
			}
			
			Log.e(Tag, "There is a problem when trying to set server field!");
			if(((String)retCmd.getExtra("exceptionType")).equals("RemoteExecutionFailedException"))
				throw((RemoteExecutionFailedException) retCmd.getExtra("exception"));
			if(((String)retCmd.getExtra("exceptionType")).equals("NullPointerException"))
				throw((NullPointerException) retCmd.getExtra("exception"));		
		}
		
		Log.i(Tag, "Setting field succeed!");
	}
	
	/**
	 * get field of an object in the server, and the field in the client object will be refreshed.
	 * This method will block the thread until remote field is got or exception is caught.
	 * If the field cannot be got, this method will throw a RemoteExecutionFailedException
	 * @param thisObject
	 * 		the object in client
	 * @param fieldName
	 *      the field name
	 * @return
	 * 		the field value
	 * @throws RemoteExecutionFailedException
	 * 			if the field cannot be got
	 */
	Object getFieldFromServer(Object thisObject, String fieldName) throws RemoteExecutionFailedException{
		if(!socketHandler.isConnected())
			throw(new RemoteExecutionFailedException("Can not connect to server!"));
		int id = remoteObjInfoSys.getIdFromObject(thisObject);
		int cmdId = ServerFieldGetIdPool.getPosition();
		Command cmd = new Command(COMMAND.OBJECT_REQUEST,cmdId);
		cmd.putExtra("id", id);
		cmd.putExtra("field", fieldName);
		try {
			Log.i(Tag, "Asking for object field "+thisObject.getClass().getName()+"."+fieldName+" from server,"
					+" object id: "+id);
			socketHandler.transmit(cmd);
		} catch (IOException e) {
			ServerFieldGetIdPool.returnPosition(cmdId);
			throw(new RemoteExecutionFailedException(e.getMessage()));
		}
		Command reply;
		try {
			reply = socketHandler.WaitForCommand(COMMAND.OBJECT_REQUEST_RETURN,cmdId,5000);
		} catch (RemoteExecutionFailedException e) {
			ServerFieldGetIdPool.returnPosition(cmdId);
			throw(e);
		}	
		
		if((Boolean)reply.getExtra("hasException")){			
			Log.e(Tag, "There is a problem when trying to get server field!");
			ServerFieldGetIdPool.returnPosition(cmdId);
			if(((String)reply.getExtra("exceptionType")).equals("RemoteExecutionFailedException"))
				throw((RemoteExecutionFailedException) reply.getExtra("exception"));
			if(((String)reply.getExtra("exceptionType")).equals("NullPointerException"))
				throw((NullPointerException) reply.getExtra("exception"));		
		}
		
		ObjectSynchronizationInfo objInfo = (ObjectSynchronizationInfo) reply.getExtra("objInfo");
		Object[] newObjArray = (Object[]) reply.getExtra("newObjArray");
		Object value = null;
		try {
			value = CodeHandler.getFieldValue(thisObject.getClass(), fieldName, thisObject);
			Set<Object> skipObjects = new HashSet<Object>();
			value = SynchronizeObject(value,objInfo,skipObjects);
			if(StaticFieldVirtualParentObject.class.isInstance(thisObject)){
				StaticFieldVirtualParentObject vpo = (StaticFieldVirtualParentObject) thisObject;
				vpo.setValue(value, null);
			}else{
				try {
					CodeHandler.setFieldValue(thisObject.getClass(), fieldName, thisObject, value);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} 
		
		Command retCommand = new Command(COMMAND.OBJECT_REQUEST_RETURN,cmdId);
		RemoteObjectInfo rinfo = remoteObjInfoSys.getObjectInfoFromId(id);
		Object[] threadArray =  rinfo.ClientThreadId.toArray();
		List<Long> serverThreadIdList = new ArrayList<Long>();
		RemoteObjectWrapper[] wrappers = new RemoteObjectWrapper[newObjArray.length];
		try {
			for(int i =0;i<threadArray.length;i++){
				long threadId = (Long) threadArray[i];
				long serverThread = remoteObjInfoSys.getServerThreadId(threadId);
				serverThreadIdList.add(serverThread);
			}
		} catch (RemoteExecutionFailedException e) {
			ServerFieldGetIdPool.returnPosition(cmdId);
			throw(e);
		}
		
		for(int i=0;i<newObjArray.length;i++){	
			boolean isFirst = true;
			for(int j =0;j<threadArray.length;j++){
				long threadId = (Long) threadArray[j];
				if(isFirst){
					wrappers[i] = remoteObjInfoSys.SaveObjectInfo_Sole(newObjArray[i], threadId);
					isFirst = false;
				}else
					remoteObjInfoSys.SaveObjectInfo_Sole(newObjArray[i], threadId);
			}
			wrappers[i].setObject(null);
		}
		retCommand.putExtra("wrappers", wrappers);
		retCommand.putExtra("threadList", serverThreadIdList);
		try {
			socketHandler.transmit(retCommand);
		} catch (IOException e) {
			ServerFieldGetIdPool.returnPosition(cmdId);
			for(int i=0;i<newObjArray.length;i++){
				remoteObjInfoSys.removeObjectInfo_Sole(newObjArray[i]);
			}
			throw(new RemoteExecutionFailedException(e.getMessage()));
		}
		ServerFieldGetIdPool.returnPosition(cmdId);
						
		Log.i(Tag, "Field got successful, value = "+value);
		
		return value;
	}
	
	
	/**
	 * synchronize an object 
	 * @param needSync
	 * 			the object that need synchronizarion
	 * @param syncInfo
	 * 			information of synchronization
	 * @param skipObjects
	 * 			a set of objects that should be skipped in the synchronization processing
	 * @return
	 * 			object that has been synchronized
	 */
	private Object SynchronizeObject(Object needSync, ObjectSynchronizationInfo syncInfo, final Set<Object> skipObjects){
		if(syncInfo == null) return null;
		if(needSync == null) return syncInfo.getObject();
		if(syncInfo.getObject() == null) return null;		
		Class<?> type = syncInfo.getObject().getClass();
		if(BasicType.isBasicType(type)||type.isEnum()||type.isPrimitive())
			return syncInfo.getObject();
		synchronized(needSync){
			if(skipObjects.contains(needSync)){
				if(syncInfo.isNewObject())
					return syncInfo.getObject();
				else
					return needSync;
			}
			syncInfo.setNeedSynchronizationObj(needSync);
			syncInfo.ScanObjectTree(new TreeScanner(){
				@Override
				public void onScanning(Object obj) {
					ObjectSynchronizationInfo info = (ObjectSynchronizationInfo)obj;
					Object dataObject = info.getObject();
					if(dataObject == null) return;
					Class<?> clazz = dataObject.getClass();
					if(BasicType.isBasicType(clazz) || clazz.isEnum() || clazz.isPrimitive())
						return;
					ObjectSynchronizationInfo parent = info.getParent();
					Object parentNeedSychronizationObj = null;
					if(parent != null)
						parentNeedSychronizationObj = parent.getNeedSychronizarionObj();
					String fieldName = info.getFieldName();
					if(info.isNewObject()){
						Object object = dataObject;		
						info.setNeedSynchronizationObj(object);
						if(parent!=null && !skipObjects.contains(parentNeedSychronizationObj))
							try {
								CodeHandler.setFieldValue(parentNeedSychronizationObj.getClass(), fieldName,
										parentNeedSychronizationObj, object);
							} catch (Exception e) {
								e.printStackTrace();
							} 
						if(info.sonNum() == 0)
							skipObjects.add(object);
						if(parent!=null && parent.isSynchronized())
							skipObjects.add(parent.getNeedSychronizarionObj());
					}else{
						int id = info.getObjectId();
						Object object = remoteObjInfoSys.getObjectInfoFromId(id).obj;
						info.setNeedSynchronizationObj(object);
						if(parent!=null && !skipObjects.contains(parentNeedSychronizationObj))
							try {
								CodeHandler.setFieldValue(parentNeedSychronizationObj.getClass(), fieldName,
										parentNeedSychronizationObj, object);
							} catch (Exception e) {
								e.printStackTrace();
							}
						if(skipObjects.contains(object))
							return;
						Field[] fields = object.getClass().getDeclaredFields();
						for(Field field:fields){
							int modifier=field.getModifiers();
							if(Modifier.isStatic(modifier)) continue;
							if(Modifier.isTransient(modifier)) continue;
							String fieldname = field.getName();
							if(fieldname.equals("ENVIRONMENT")) continue;
							if(fieldname.equals("REMOTE_OBJECT_ID")) continue;
							if(fieldname.equals("REFERENCE_NUM")) continue;
							Class<?> fieldType = field.getType();
							try {
								Object value = CodeHandler.getFieldValue(field, dataObject);
								if(value == null)
									CodeHandler.setFieldValue(field, object, value);
								else{
									Class<?> valueType = value.getClass();
									if(BasicType.isBasicType(fieldType) || field.isEnumConstant() || fieldType.isPrimitive() 
										|| BasicType.isBasicType(valueType) || valueType.isEnum() || valueType.isPrimitive()){							
										CodeHandler.setFieldValue(field, object, value);
		
									}
								}
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
						
						if(info.sonNum() == 0)
							skipObjects.add(object);
						if(parent!=null && parent.isSynchronized())
							skipObjects.add(parent.getNeedSychronizarionObj());
					}
					
				}
				
			});
			if(syncInfo.isNewObject())
				return syncInfo.getObject();
			else
				return needSync;
		}
	}
	
}


