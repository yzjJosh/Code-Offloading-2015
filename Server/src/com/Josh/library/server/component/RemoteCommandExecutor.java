package com.Josh.library.server.component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.StreamCorruptedException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.Josh.library.core.component.BasicType;
import com.Josh.library.core.component.CodeHandler;
import com.Josh.library.core.component.MethodPackage;
import com.Josh.library.core.component.ObjectSynchronizationInfo;
import com.Josh.library.core.component.RemoteObjectWrapper;
import com.Josh.library.core.component.StaticFieldVirtualParentObject;
import com.Josh.library.core.exception.InvokeMethodFailureException;
import com.Josh.library.core.exception.RemoteExecutionFailedException;
import com.Josh.library.core.interfaces.TreeScanner;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

/**
 * This class deals with remote command, it provides methods that will be used when recieving
 * a command from client.
 * @author Josh
 *
 */
@SuppressLint("UseSparseArrays")
public class RemoteCommandExecutor {
	private CodeHandler handler;
	private String APKPath; 
	private ServerSignalHandler signalHandler;
	static private final String Tag = "Executor";
	
	public RemoteCommandExecutor(Context context, ServerSocketHandler socketHandler){
		handler = new CodeHandler(context);
		APKPath = context.getCacheDir().getPath()+"/apk";
		signalHandler = new ServerSignalHandler(socketHandler);		
	}
	
	
	/**
	 * Load an apk file. This method will load the classes in the bytecode, optimize the dex
	 * file, extract the native libraries, and save them in a directory.
	 * @param apkName
	 * 		the name of the apk file
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 */
	public void LoadAPK(String apkName){
		handler.LoadAPK(APKPath+"/"+apkName);
		try {
			handler.invokeMethod("com.Josh.library.client.component.RemoteExecutionEngine",
					null, "setEnvironment_Server", null, null);
			handler.setFieldValue("com.Josh.library.client.component.RemoteExecutionEngine",
					"handler", null, signalHandler);
		} catch (Exception e) {
			e.printStackTrace();
		} 
	}
	
	/**
	 * Get the absolute path of a native library from file name
	 * @param libName
	 * 		name of the native library file
	 * @return
	 * 		absolute path of the library
	 */
	public String getLibraryPath(String libName){
		return handler.getLibraryPath(libName);
	}
	
	
	/**
	 * Get the class loader that loads client classes
	 * @return
	 * 		the class loader, or null if has not loaded client code
	 */
	public ClassLoader getClassLoader(){
		return handler.getClassLoader();
	}
	
	/**
	 * Check if an apk file already exists
	 * @param apkName
	 * 		the name of the apk file
	 * @return
	 * 		if it exists or not
	 */
	public boolean hasAPK(String apkName){
	/*	File file = new File(APKPath,apkName);
		return file.exists();*/
		
		//for easier debug
		return false;
	}

	
	/**
	 * Save apk bytes to an file
	 * @param apkName
	 * 		the name of saved apk file
	 * @param data
	 * 		array of bytes
	 * @throws IOException
	 */
	public void saveAPK(String apkName,byte[] data) throws IOException{
		File dir = new File(APKPath);
		if(!dir.exists())
			dir.mkdir();
		File file = new File(APKPath,apkName);
		FileOutputStream fos = new FileOutputStream(file);
		fos.write(data);
		fos.close();
	}
	

	/**
	 * Execute an remote method, after execution finished, all objects that are binded to this thread
	 * will be removed, their synchronization information will be saved in a map, and the 
	 * return object's synchronization information will be returned.
	 * @param Package
	 * 		the package that contains method information
	 * @param objSyncMap
	 * 		a map to save synchronization information of objects that are binded to current thread
	 * @return
	 * 		the synchronization information of the return object
	 * @throws RemoteExecutionFailedException
	 * 		if there is a problem when executing the method
	 * @throws InvocationTargetException
	 * 		if the method itself throws an exception
	 */
	public ObjectSynchronizationInfo executeMethod(MethodPackage Package, Map<Integer,ObjectSynchronizationInfo> objSyncMap) throws RemoteExecutionFailedException, InvocationTargetException{
		ServerEngine engine = ServerEngine.getServerEngine();
		if(!engine.isStarted()){
			Log.e(Tag, "Error: Server engine is not started!");
			throw(new RemoteExecutionFailedException("Server engine is not started!"));
		}
		
		RemoteObjectWrapper objectWrapper = Package.getObjectWrapper();
		RemoteObjectWrapper[] paramWrappers = Package.getParamWrappers();
		String clazz = Package.getClassname();
		String method = Package.getMethodName();
		String[] paramTypes = Package.getParamTypeName();
		
		
		ObjectInfo info = engine.getObjectInfo();
		Object obj = info.unWrapObject(objectWrapper);
			
		Object[] params = new Object[paramWrappers.length];
		for(int i=0; i<paramWrappers.length;i++)
			params[i] = info.unWrapObject(paramWrappers[i]);
		
		Object result = null;
		RemoteExecutionFailedException remoteException = null;
		InvocationTargetException methodException = null;
		ObjectSynchronizationInfo resultSync = null;
		
		try {
			String methodName = Package.toString(handler.getClassLoader());
			Log.i(Tag, "start executing method "+methodName+" from client!");
			result = handler.invokeMethod(clazz, obj, method, paramTypes, params);
			info.getRemoteObjectsSychronizationInfoInCurrentThread(objSyncMap,this.getClassLoader());
			resultSync = info.getObjectSynchronizationInfo(result);
			info.removeAllRemoteObjectInCurrentThread();
			Log.i(Tag, "Method "+methodName+" execution finished!");
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			remoteException = new RemoteExecutionFailedException("Failed when executing method");
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
			remoteException = new RemoteExecutionFailedException("Failed when executing method");
		} catch (IllegalAccessException e) {
			e.printStackTrace();
			remoteException = new RemoteExecutionFailedException("Failed when executing method");
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
			remoteException = new RemoteExecutionFailedException("Failed when executing method");
		} catch (InvocationTargetException e) {			
			Throwable cause = e.getCause();
			if(RuntimeException.class.isInstance(cause)){
				String msg = cause.getMessage();
				if(msg!=null && msg.equals("REMOTE_EXECUTION_FAILED"))
					remoteException = new RemoteExecutionFailedException("Failed when executing method");
				else
					methodException = e;
			}else{
				methodException = e;
				cause.printStackTrace();
			}
		} catch (InvokeMethodFailureException e) {
			e.printStackTrace();
			remoteException = new RemoteExecutionFailedException("Failed when executing method");
		} catch (RemoteExecutionFailedException e){
			Log.e(Tag, e.getMessage());
			remoteException = e;
		}
			
		if(remoteException!= null) {
			Log.e(Tag, "A RemoteExecutionFailedException occured when executing method "+Package.toString(handler.getClassLoader())+", sending it back...");
			info.removeAllRemoteObjectInCurrentThread();
			throw(remoteException);
		}
		if(methodException!= null){
			Log.e(Tag, "An InvocationTargetException occured when executing method "+Package.toString(handler.getClassLoader())+", sending it back...");
			info.removeAllRemoteObjectInCurrentThread();
			throw(methodException);
		}
			
		return resultSync;
	}
	
	/**
	 * Get value of an object field
	 * @param id
	 * 		the id of the object
	 * @param field
	 * 		the field name
	 * @return
	 * 		the value of the field
	 * @throws RemoteExecutionFailedException
	 * 		if cannot get the value of the field
	 */
	public Object getObjectField(int id, String field) throws RemoteExecutionFailedException{
		ObjectInfo info = ServerEngine.getServerEngine().getObjectInfo();
		if(field == null){
			Log.e(Tag, "The field name is null");
			throw(new RemoteExecutionFailedException("Field name is null, object id:"+id));
		}
		
		Object obj = info.getObject(id);
		if(obj == null){
			Log.e(Tag, "Do not have object with id:"+id+", operation failed");
			throw(new RemoteExecutionFailedException("Do not have object with id:"+id));
		}
		if(StaticFieldVirtualParentObject.class.isInstance(obj)){
			StaticFieldVirtualParentObject vpo = (StaticFieldVirtualParentObject) obj;
			vpo.updateValue(getClassLoader());
			return vpo.getValue();
		}
		try {
			return CodeHandler.getFieldValue(obj.getClass(), field, obj);
		}
		catch (Exception e) {
			e.printStackTrace();
			throw(new RemoteExecutionFailedException("Unable to get field of class "+obj.getClass().getName()));
		} 
	}
	
	/**
	 * Set value of a specific field in an object
	 * @param value
	 * 		the value to set
	 * @param id
	 * 		id of the object
	 * @param field
	 * 		name of the field
	 * @throws RemoteExecutionFailedException
	 * 		if cannot set the field
	 */
	public void setField(Object value, int id, String field) throws RemoteExecutionFailedException{
		ObjectInfo info = ServerEngine.getServerEngine().getObjectInfo();
		Object obj = info.getObject(id);
		if(obj == null){
			Log.e(Tag, "Do not have object with id:"+id+", operation failed");
			throw(new RemoteExecutionFailedException("Do not have object with id:"+id));
		}
		if(StaticFieldVirtualParentObject.class.isInstance(obj)){
			((StaticFieldVirtualParentObject)obj).setValue(value, getClassLoader());
			return;
		}
		try {
			CodeHandler.setFieldValue(obj.getClass(), field, obj, value);
//			Log.i(Tag, "After setting ,field value is "+CodeHandler.getFieldValue(obj.getClass(), field, obj));
		}catch(NullPointerException e){
			throw(e);
		}
		catch (Exception e) {
			e.printStackTrace();
			throw(new RemoteExecutionFailedException("Unable to set field of class "+obj.getClass().getName()));
		}
	}
	
	/**
	 * Get the customized object input stream, a customized object input stream can input
	 * objects whose classes are from other apk files.
	 * @param input
	 * 		the input stream
	 * @return
	 * 		the customized object input stream
	 * @throws StreamCorruptedException
	 * @throws IOException
	 */
	public ObjectInputStream getObjectInputStream(InputStream input) throws StreamCorruptedException, IOException{
		return new CustomizedObjectInputStream(input,handler);
	}
	
	/**
	 * Get a set of new objects from an ObjectSynchronizationInfo tree, a new object is an object that does not 
	 * have an id, it is instantiated in server.
	 * @param syncInfo
	 * 		the synchronization information tree
	 * @return
	 * 		a set of new objects
	 */
	public Set<Object> getNewObjectSet(ObjectSynchronizationInfo syncInfo){
		final Set<Object> objSet = new HashSet<Object>();
		if(syncInfo==null) return objSet;
		syncInfo.ScanObjectTree(new TreeScanner(){
			@Override
			public void onScanning(Object obj) {
				ObjectSynchronizationInfo info = (ObjectSynchronizationInfo) obj;
				if(info.isNewObject()){
					Object object = info.getObject();
					if(object == null) return;
					Class<?> type = object.getClass();
					if(type.isPrimitive()) return;
					if(type.isEnum()) return;
					if(BasicType.isBasicType(type)) return;
					if(type.isAnnotation()) return;
					objSet.add(object);	
				}
			}
			
		});
		return objSet;
	}
	
}
