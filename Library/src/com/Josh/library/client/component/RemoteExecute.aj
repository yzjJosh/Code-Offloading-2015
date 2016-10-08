package com.Josh.library.client.component;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

import org.aspectj.lang.reflect.FieldSignature;
import org.aspectj.lang.reflect.MethodSignature;

import com.Josh.library.client.interfaces.IgnoreRemote;
import com.Josh.library.client.interfaces.IgnoreWarning;
import com.Josh.library.client.interfaces.Remote;
import com.Josh.library.client.interfaces.Remoteable;
import com.Josh.library.core.component.MethodPackage;
import com.Josh.library.core.component.RemoteObjectWrapper;
import com.Josh.library.core.component.StaticFieldVirtualParentObject;
import com.Josh.library.core.exception.RemoteExecutionFailedException;
import com.Josh.library.core.exception.RemoteMethodException;

import android.util.Log;


 /**
  * this is the main aspect of the offloading system, this aspect has following functions:
  * 1. intercept methods and insert offloading code into those methods
  * 2. intercept field setting and getting to enable client to get field from server, and server to get field from client
  * 3. introduce interface com.Josh.library.client.interfaces.Remoteable to all user-defined classes
  * 4. introduce some fields to all classes that implement interface com.Josh.library.client.interfaces.Remoteable
  * 5. provide some static methods to read or modify fields that are introduced by this aspect
  * 6. intercept log output method Log.i and Log.d in order to control log output
  * 7. intercept load library methods to make it possible to load library in server
  * 8. declare some error and warning in order to prevent choosing offloading methods that may cause problems
  * @author Josh
  *
  */
public aspect RemoteExecute { 
	
	static private final String Tag="RemoteExecuteAspect"; 
	 
	public pointcut Aspect() : within(RemoteExecute);
	
	//this pointcut is in the calling of remoteable methods
	public pointcut remoteExecutePointCut() : call(@Remote * (!com.Josh.library..*+) || (com.Josh.library.client.interfaces.Remoteable+).*(..))
												&& !Aspect(); 
	
	//this pointcut is in the getting of object fields
	public pointcut FieldGet() : get(!Environment (!com.Josh.library..*+ || com.Josh.library.client.interfaces.Remoteable+).*) && withincode(* *.*(..))
												&& !Aspect();

	//this pointcut is in the setting of obejct fields
	public pointcut FieldSet() : set(!Environment (!com.Josh.library..*+ || com.Josh.library.client.interfaces.Remoteable+).*) && withincode(* *.*(..))
												&& !Aspect();
	
	//this pointcut is in the calling of load library methods
	public pointcut nativeLibLoading() : call(void java.lang.System.load*(java.lang.String)) && !Aspect();
	
	//this pointcut is in the calling of log output method (Log.i, Log.d)
	public pointcut logCut() : (call(int android.util.Log.i(String,String)) || call(int android.util.Log.d(String,String)))
									&& within(com.Josh.library..*);
	
	
	//add interfaces to all visable classes, so that they all implements Serializable
	declare parents : !com.Josh.library..*+ && !android..* && !java..* && !dalvik..* && !org.aspectj..* && !(@IgnoreRemote *) implements Remoteable;
	
	//add fields to all Remoteable instances, in order to figure out their environment.
	private Environment Remoteable.ENVIRONMENT=Environment.LOCAL;
	private int Remoteable.REMOTE_OBJECT_ID = 0;
	private int Remoteable.REFERENCE_NUM = 0;

	
	/**
	 *  method handler
	 * @return result
	 * @throws InvocationTargetException 
	 */
	Object around() throws Error,RuntimeException  : remoteExecutePointCut(){

		ClientEngine engine=ClientEngine.getClientEngine();
		MethodSignature signature=(MethodSignature)thisJoinPoint.getSignature();
		String fullName = engine.getMethodName(signature);
		Database db = engine.getDatabase();
		
		//If this method is called on server, take its original operation 
		if(RemoteExecutionEngine.getEnvironment()==Environment.SERVER){
			long time = System.currentTimeMillis();
			Object result = proceed();
			time = System.currentTimeMillis() - time;
			db.setMethodNormalExecutionTime(fullName,(int)time);
			return result;
		}
				 		
		//else, if the engine is not started or it cannot run remotely, run locally		
		if(! (engine.isStarted() && engine.canExecuteRemotely(fullName))){
			long time = System.currentTimeMillis();
			Object result = proceed();
			time = System.currentTimeMillis() - time;
			db.setMethodNormalExecutionTime(fullName,(int)time);
			return result;
		}
		
		//else, get method information
		Object thisObject=thisJoinPoint.getTarget();							
		String methodName=signature.getName();
		String methodClassName=signature.getDeclaringTypeName();
		int modifiers = signature.getModifiers();

		//inserializable class, execute locally!
		if(!Modifier.isStatic(modifiers) && !Serializable.class.isInstance(thisObject)){
			Log.e(Tag, "Error: Class "+methodClassName+" is not serializable! Method "+
					fullName+" will execute locally!");
			long time = System.currentTimeMillis();
			Object result = proceed();
			time = System.currentTimeMillis() - time;
			db.setMethodNormalExecutionTime(fullName,(int)time);
			return result;
		}
		
		//Check if the return type is Serializable
		Class<?> returnType = signature.getReturnType();
		if(!returnType.isPrimitive() && !Serializable.class.isAssignableFrom(returnType)){
			Log.e(Tag, "Error: Return type "+returnType.getName()+" is not serializable! Method "+
					fullName+" will execute locally!");
			long time = System.currentTimeMillis();
			Object result = proceed();
			time = System.currentTimeMillis() - time;
			db.setMethodNormalExecutionTime(fullName,(int)time);
			return result;
		}
		
		Object[] parameters=thisJoinPoint.getArgs();
		Class<?>[] paramTypes=signature.getParameterTypes();
		String[] paramTypeNames=new String[paramTypes.length];
		for(int i=0;i<paramTypes.length;i++){
			paramTypeNames[i]=paramTypes[i].getName();
			
			//inSerializable parameter, execute locally!
			if(!Serializable.class.isInstance(parameters[i])){
				Log.e(Tag, "Error: Parameter type "+paramTypeNames[i]+" is not serializable! Method "+
						fullName+" will execute locally!");
				long time = System.currentTimeMillis();
				Object result = proceed();
				time = System.currentTimeMillis() - time;
				db.setMethodNormalExecutionTime(fullName,(int)time);
				return result;
			}
		}
		
		//Wrap and add save remote objects
		RemoteObjectWrapper thisObjectwrapper = null;
		RemoteObjectWrapper[] paramWrappers = new RemoteObjectWrapper[parameters.length];
		try {
			thisObjectwrapper = engine.getRemoteObjectInfoSystem().SaveObjectInfo(thisObject);
			for(int i=0;i<parameters.length;i++){
				paramWrappers[i] = engine.getRemoteObjectInfoSystem().SaveObjectInfo(parameters[i]);
			}
		} catch (RemoteExecutionFailedException e1) {
			Log.e(Tag, "RemoteExecutionFailedException : "+e1.getMessage());
			engine.getRemoteObjectInfoSystem().removeAllRemoteObjectInCurrentThread();
			String str = "Unable to execute method "+fullName;
			Log.e(Tag,str);
			long time = System.currentTimeMillis();
			Object result = proceed();
			time = System.currentTimeMillis() - time;
			db.setMethodNormalExecutionTime(fullName,(int)time);
			return result;
		}
				
		//Package information and execute the method remotely
		MethodPackage Package=new MethodPackage(methodClassName,methodName,paramTypeNames,
						thisObjectwrapper,paramWrappers);
		Object result = null;
		Exception exception = null;
		try {
			long time = System.currentTimeMillis();
			result = engine.executeMethodRemotely(Package);
			time = System.currentTimeMillis() - time;
			db.setMethodRemoteExecutionTime(fullName, (int)time);
		} catch (Exception e) {
			exception = e;
		}
		 
		//remove object information
		engine.getRemoteObjectInfoSystem().removeAllRemoteObjectInCurrentThread();
		
		if(exception!=null){
			if(RemoteExecutionFailedException.class.isInstance(exception)){
				String str = "Unable to execute method "+fullName;
				Log.e(Tag,str);
				long time = System.currentTimeMillis();
				Object r = proceed();
				time = System.currentTimeMillis() - time;
				db.setMethodNormalExecutionTime(fullName,(int)time);
				return r;
			}
			if(InvocationTargetException.class.isInstance(exception)){
				Throwable cause = exception.getCause();
				//catch error and throw it!
				if(Error.class.isInstance(cause))
					throw((Error) cause);
				//catch runtimeException and throw it!
				if(RuntimeException.class.isInstance(cause))
					throw((RuntimeException) cause);
				//catch exception in the remtoe method, wrap it as a RuntimeException and throw it!
				throw(new RemoteMethodException((Exception)cause));
			}
		}
		
		return result;
		
	}
	
	/**
	 * get field handler
	 * @return requested field
	 */
	Object around() : FieldGet(){
		
		//check operation environment
		if(RemoteExecutionEngine.getEnvironment() == Environment.SERVER){
			//do when run remotely
			//----------------------------
									
			//get the modifier
			FieldSignature signature = (FieldSignature)thisJoinPoint.getSignature();
			int modifiers = signature.getModifiers();
			
			
			//unstatic and untransientt field, must be in the server, get it directly
			if(!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers))
				return proceed();
			
			String className = signature.getDeclaringTypeName();
			String fieldName = signature.getName();
			
			//Transient field, error!
			if(Modifier.isTransient(modifiers)){
				String errorInfo = "Error, try to get transient field: "+Modifier.toString(modifiers)+" "
						+signature.getFieldType().getName()+" "+className+"."+
						fieldName;
				Log.e(Tag,errorInfo );
				throw(new RuntimeException("REMOTE_EXECUTION_FAILED"));
			}
			
			//static field, get it	
			Object result = null;
			try {
				result = RemoteExecutionEngine.getStaticField(className, fieldName);
			}catch(NullPointerException e) {
				throw(e);
			} catch (Exception e) {
				//error
				String errorInfo = "Unable to get static field "+Modifier.toString(modifiers)+" "
						+signature.getFieldType().getName()+" "+className+"."+
						fieldName;
				Log.e(Tag,errorInfo );
				throw(new RuntimeException("REMOTE_EXECUTION_FAILED"));
			}
			return result;
			
		}else{
			//do when run locally
			//---------------------------------------	
			
			ClientEngine engine = ClientEngine.getClientEngine();
			
			//engine is not started, run directly
			if(!engine.isStarted())
				return proceed();
			
			
			FieldSignature signature = (FieldSignature)thisJoinPoint.getSignature();
			int modifiers = signature.getModifiers();
						
			//transient field, must be local
			if(Modifier.isTransient(modifiers))
				return proceed();
			
			Object obj = thisJoinPoint.getTarget();
			
			//object is inserializable, must be local
			if(!Modifier.isStatic(modifiers) && !Serializable.class.isInstance(obj))
				return proceed();
			
			String className = signature.getDeclaringTypeName();
			String fieldName = signature.getName();
			
			//static field, set its virtual parent object
			if(Modifier.isStatic(modifiers)){				
					
				//build vitrual object if it doesn't exist, and get it
				StaticFieldVirtualParentObject virtualObject =
						ClientEngine.getClientEngine().getRemoteObjectInfoSystem()
						.getStaticFieldVirtualParentObject(className, fieldName);
					
				//set virtual parent object as parent of the static field
				obj = virtualObject;
				fieldName = "obj";
				className = StaticFieldVirtualParentObject.class.getName();
			}
			
			RemoteObjectInformationSystem info = engine.getRemoteObjectInfoSystem();
			if(info.isRemote(obj)){
				//object is remote, get the remote object field
				try {
					return engine.getFieldFromServer(obj,fieldName);
				} catch (RemoteExecutionFailedException e) {
					//error
					String errorInfo = "Unable to get field "+Modifier.toString(modifiers)+" "
							+signature.getFieldType().getName()+" "+className+"."+
							fieldName;
					Log.e(Tag,errorInfo );
					return proceed();
				}
			}else
			//get field directly
				return proceed();
		}		
	}
	
	/**
	 * field set handler
	 * @return
	 */
	Object around(Object value) : FieldSet() && args(value){
		if(RemoteExecutionEngine.getEnvironment() == Environment.SERVER){
			//do when running remotely
			//---------------------

			FieldSignature signature = (FieldSignature)thisJoinPoint.getSignature();
			int modifiers = signature.getModifiers();
			
			String className = signature.getDeclaringTypeName();
			String fieldName = signature.getName();
			
			//transient field, error!
			if(Modifier.isTransient(modifiers)){
				String errorInfo = "Error, try to set transient field: "+Modifier.toString(modifiers)+" "
						+signature.getFieldType().getName()+" "+className+"."+
						fieldName;
				Log.e(Tag,errorInfo );
				throw(new RuntimeException("REMOTE_EXECUTION_FAILED"));
			}
					
			
			//if the field is static, check if we have got it, if not, get it firstly
			if(Modifier.isStatic(modifiers)){
				
				// if the value is inserializable, show the warning
				if(value!=null && !Serializable.class.isInstance(value)){
					String errorInfo =  "Try to set static field "+Modifier.toString(modifiers)+" "
							+signature.getFieldType().getName()+" "+className+"."+
							fieldName+" with an inserializable value, this value cannot"
							+ " be transmitted to client!";
					Log.e(Tag, errorInfo);
					throw (new RuntimeException("REMOTE_EXECUTION_FAILED"));
				}
				
				try {
					RemoteExecutionEngine.getStaticField(className, fieldName);
				}catch(NullPointerException e) {
					throw(e);
				} catch (Exception e) {
					//error
					String errorInfo = "Unable to get static field "+Modifier.toString(modifiers)+" "
							+signature.getFieldType().getName()+" "+className+"."+
							fieldName;
					Log.e(Tag,errorInfo );
					throw(new RuntimeException("REMOTE_EXECUTION_FAILED"));
				}
			}
		
			//set field
			return proceed(value);
			
		}else{
			// do when running locally
			//-----------------------
			
			ClientEngine engine = ClientEngine.getClientEngine();
			
			//engine is not started, run directly
			if(!engine.isStarted())
				return proceed(value);
			
			
			FieldSignature signature = (FieldSignature)thisJoinPoint.getSignature();
			int modifiers = signature.getModifiers();
						
			//transient field, must be local
			if(Modifier.isTransient(modifiers))
				return proceed(value);
			
			Object obj = thisJoinPoint.getTarget();
			
			//value is inserializable, must be local
			if(!Serializable.class.isInstance(value))
				return proceed(value);
			
			String className = signature.getDeclaringTypeName();
			String fieldName = signature.getName();
			
			//static field
			if(Modifier.isStatic(modifiers)){
								
				//build vitrual object if it doesn't exist, and get it
				StaticFieldVirtualParentObject virtualObject =
						ClientEngine.getClientEngine().getRemoteObjectInfoSystem()
						.getStaticFieldVirtualParentObject(className, fieldName);
					
				//set virtual parent object as parent of the static field
				obj = virtualObject;
				fieldName = "obj";
				className = StaticFieldVirtualParentObject.class.getName();
					
			}else
				//unstatic field and inSerializable object, must be local
				if(!Serializable.class.isInstance(obj))
					return proceed(value);				
			
			
			RemoteObjectInformationSystem info = engine.getRemoteObjectInfoSystem();
			
			if(info.isRemote(obj)){
				//Target object is remote, set value of remote server!
				int id = info.getIdFromObject(obj);
				try {
					engine.setFieldToServer(value, id, fieldName);
				} catch (RemoteExecutionFailedException e) {
					e.printStackTrace();
					Log.e(Tag, "Unable to get remote field: "+className+"."+fieldName+
							", object id: "+id);
				}
				
			}
			else
				//object is local, set local object field!	
				return proceed(value);
						
		}
		
		return proceed(value);
	}
	
	
	/**
	 * Unwrap checked exception and throws the original exception
	 * @param exception 
	 * 			the wrapped checked exception
	 * @throws Exception 
	 * 			unwrapped exception
	 */
	after() throwing(RemoteMethodException exception) throws Exception :
		remoteExecutePointCut() && (execution(* *.*(..) throws(Exception+))) {
		Log.e(Tag, "Caught exception from remote mehod!");
		throw(exception.getOriginalException());
	}
	
	/**
	 * turn on or off log cat output of debugging information
	 * @return
	 */
	Object around() : logCut(){
		if(ClientEngine.getClientEngine().isDebugOn())
			return proceed();
		else 
			return null;
	}
	
	/**
	 * handle native library loading...
	 */
	void around(String lib) : nativeLibLoading() && args(lib) {
		if(RemoteExecutionEngine.getEnvironment() == Environment.LOCAL)
			//if the environment is local, load it directly
			proceed(lib);
		else{
			// if the environment is server
			MethodSignature signature = (MethodSignature) thisJoinPoint.getSignature();
			String name = signature.getName();
			String libName = null;
			if(name.equals("loadLibrary"))
				libName = "lib" + lib +".so";
			else
				libName = new File(lib).getName();
			
			try {
				//get the absolute path from a library file name
				String path = RemoteExecutionEngine.getNativeLibPath(libName);
				//load the library
				System.load(path);
			} catch (Exception e) {
				Log.e(Tag, "Unable to load library "+libName);
			} catch (UnsatisfiedLinkError e){
				Log.e(Tag, "Unable to load library "+libName);
			}
		}
	}
	
	
	/**
	 * Set the environment variable in Remoteable calsses
	 * @param obj  object
	 * @param envi  environment
	 */
	public static void setEnvironment(Object obj, Environment envi){
		if(Remoteable.class.isInstance(obj))
			synchronized(obj){
				((Remoteable)obj).ENVIRONMENT = envi;		
			}
	}
	
	/**
	 * Set the id variable in Remoteable calsses
	 * @param obj
	 * @param id
	 */
	public static void setId(Object obj, int id){
		if(Remoteable.class.isInstance(obj))
			synchronized(obj){
				((Remoteable)obj).REMOTE_OBJECT_ID = id;
			}
	}
	
	/**
	 * Set the reference number variable in Remoteable calsses
	 * @param obj
	 * @param referenceNum
	 */
	public static void setReferenceNum(Object obj, int referenceNum){
		if(Remoteable.class.isInstance(obj)){
			synchronized(obj){
				((Remoteable)obj).REFERENCE_NUM = referenceNum;
			}
		}
	}
	
	/**
	 * Get the environment variable in Remoteable calsses
	 * @param obj
	 * @return
	 */
	public static Environment getEnvironment(Object obj){
		if(Remoteable.class.isInstance(obj))
			return ((Remoteable)obj).ENVIRONMENT;
		else
			return null;
	}
	
	/**
	 * Get the id variable in Remoteable calsses
	 * @param obj
	 * @return
	 */
	public static int getId(Object obj){
		if(Remoteable.class.isInstance(obj))
			return ((Remoteable)obj).REMOTE_OBJECT_ID;
		else
			return -1;
	}
	
	/**
	 * Get the reference number variable in Remoteable calsses
	 * @param obj
	 * @return
	 */
	public static int getReferenceNum(Object obj){
		if(Remoteable.class.isInstance(obj))
			return ((Remoteable)obj).REFERENCE_NUM;		
		else
			return -1;
	}


	
	public pointcut libraryAnnotationMethods() : execution(@Remote * (com.Josh.library..*+) && !(com.Josh.library.client.interfaces.Remoteable+).*(..));
	
	/*Inform erro when finding a component non-serializable. This error declare cannot check
	  every component, especially some class fields that does not appear in parameter or returning
	  but is used inside the method. If some problem happens when carring on remote operation, 
	  please check if every component is serialized.
	*/
	declare error : (remoteExecutePointCut() && !libraryAnnotationMethods()) && 
					(execution(@Remote * (!(java.io.Serializable+)).*(..)) 
					||execution(@Remote * *(!(java.io.Serializable+) && java.lang.Object+,..))
					||execution(@Remote * *(..,!(java.io.Serializable+) && java.lang.Object+,..))
					||execution(@Remote * *(..,!(java.io.Serializable+) && java.lang.Object+))
					||execution(@Remote !(java.io.Serializable+) && java.lang.Object+ *(..))
					)
					&& !Aspect()
	: "error: Remote methods require host class, parameter type, and return type serializable,"
			+ " please make sure they all implement java.io.Serializable interface! If this "+
			"information occurs when using java.lang.Object, please use java.io.Serializable instead!";
	
	/*
	 * Inform error when some needed component does not implement Remoteable interface.				
	 */
	declare error : (remoteExecutePointCut() && !libraryAnnotationMethods()) && 
					(execution(@Remote * (!(com.Josh.library.client.interfaces.Remoteable+) && !java..*).*(..)) 
					||execution(@Remote * *(!(com.Josh.library.client.interfaces.Remoteable+) && !java..* && java.lang.Object+,..))
					||execution(@Remote * *(..,!(com.Josh.library.client.interfaces.Remoteable+) && !java..* && java.lang.Object+,..))
					||execution(@Remote * *(..,!(com.Josh.library.client.interfaces.Remoteable+) && !java..* && java.lang.Object+))
					)
					&& !Aspect()
	: "error: Remote methods require customized host class and parameter type remoteable,"
			+ " please make sure they all implement com.Josh.library.interfaces.Remoteable interface!";				
	
					
	declare error : remoteExecutePointCut() && (execution(* *.*(..) throws(Exception+ && !Exception)))
	: "error: remote method with exception throwing should declare its exception as java.lang.Exception!";
					
	//Stop declaring library methods as remote methods.
	declare error : libraryAnnotationMethods()&& !Aspect():
		"error: This method cannot be executed remotely!";
	
	//stop creating new thread in remote method!
	declare error : (withincode(@Remote * (!com.Josh.library..*+) || (com.Josh.library.client.interfaces.Remoteable+).*(..))
					 &&	call(* java.lang.Thread+.start(..))):
		"error: Remote method should be a signal thread!";
	
	/*
	 * Declare warning when found some android API is used inside the remote method. This warning
	 * is to make sure everything inside the method is operatable in server. This warning cannnot
	 * check sub-methods called inside the remote method, so please make sure every operation is
	 * appliable in remote server. If you know an android API is also usable in server, you can
	 * add @IgnoreWarning annotation on the method to ignore the warning.
	 */
	declare warning : (withincode(@Remote * (!com.Josh.library..*+) || (com.Josh.library.client.interfaces.Remoteable+).*(..))
					&& !withincode(@IgnoreWarning * *(..)) &&( call(android..* *(..))|| call(* android..* (..))
					|| get(android..* *.*) || set(android..* *.*) || call(* *(android..*,..)) || call(* *(..,android..*,..))
					|| call(* *(..,android..*)) || call(dalvik..* *(..))|| call(* dalvik..* (..))
					|| get(dalvik..* *.*) || set(dalvik..* *.*) || call(* *(dalvik..*,..)) || call(* *(..,dalvik..*,..))
					|| call(* *(..,dalvik..*))))
					|| ((execution(@Remote * !com.Josh.library..*+.*(android..*,..)) || execution(@Remote * !com.Josh.library..*+.*(..,android..*,..)) 
					||	execution(@Remote * !com.Josh.library..*+.*(..,android..*)) || execution(@Remote * !com.Josh.library..*+.*(dalvik..*,..)) 
					|| execution(@Remote * !com.Josh.library..*+.*(..,dalvik..*,..)) 
					||	execution(@Remote * !com.Josh.library..*+.*(..,dalvik..*)))
					&& !execution(@IgnoreWarning * *(..)) )
					&& !Aspect():
						"warning: Remote method may be unable to deal with android API operations.";
			 		
	
					
}
