package com.Josh.library.core.component;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.Josh.library.core.exception.InvokeMethodFailureException;

import dalvik.system.DexClassLoader;
import android.content.Context;
import android.util.Log;

/**
 * This class provides methods to load bytecode at runtime dynamically,
 *you can use the loaded classes in you applications, as long as you
 *have the bytecode (apk file) and some information about the classes,
 * e.g. name of classes, fields, methods and their parameter types. Operations
 * in this class bases on Java reflection.
 * @author Josh
 * @version 1.0
 */
public class CodeHandler {
	
	static private final String Tag="CodeHandler";
	private Context context;
	private DexClassLoader cLoader;
	private boolean hasLoadedCode = false;
	private String libDir;
	
	public CodeHandler(Context c){
		context=c;
	}
	
	/**
	 * Load an apk file and extract its dex and libraries, the dex will be optimized after extraction,
	 * in ART runtime the dex will be compiled into native code. This method should be called BEFORE 
	 * calling of any other unstatic methods in this class, otherwise these methods will NOT work properly. 
	 * This method also loads native libraries in the apk.
	 * 
	 * @param apkPath
	 * 			The path of the apk file          
	 */
	public void LoadAPK(String apkPath){
		if(apkPath==null) return;
		String cachePath = context.getCacheDir().getPath();
		String apkName = new File(apkPath).getName();
		apkName = apkName.replace(".apk", "");
		String optimizedDexPath = cachePath + "/code";
		File file = new File(optimizedDexPath);
		if(!file.exists())
			file.mkdir();
		String libPath = cachePath + "/libs";
		file = new File(libPath);
		if(!file.exists())
			file.mkdir();
		libPath = libPath + "/" + apkName;
		file = new File(libPath);
		if(!file.exists())
			file.mkdir();
		libDir = libPath;
		File apk = new File(apkPath);
		UnpackLibraries(libPath,apk);
		Log.d(Tag, "Start loading classes...");
		cLoader = new DexClassLoader(apkPath,optimizedDexPath,libPath,context.getClassLoader());	
		Log.d(Tag, "Loading classes succeed");
		Log.d(Tag, "Load apk file finnished!");
		Log.i(Tag, "cLoader="+cLoader);
		hasLoadedCode = true;
	}
	
	/**
	 * if this handler has loaded apk file
	 * @return
	 * 		has loaded apk file or not
	 */
	public boolean hasLoadedCode(){
		return this.hasLoadedCode;
	}
	
	/**
	 * Unpack all libraries, this method will be called in LoadAPK(String) method
	 * @param libDirs
	 * 		the directory in which extracted libraries will be put
	 * @param apk
	 * 		the apk file
	 */
	@SuppressWarnings("unchecked")
	private void UnpackLibraries(String libDirs, File apk){
		Log.d(Tag, "Start extracting native libraries...");
		Long startTime = System.nanoTime();

		ZipFile apkFile;
		Set<String> nameSet = new HashSet<String>();
		try {
			apkFile = new ZipFile(apk);
			
			//load x86 libraries
			Enumeration<ZipEntry> entries = (Enumeration<ZipEntry>) apkFile
					.entries();
			ZipEntry entry;
			while (entries.hasMoreElements()) {
				entry = entries.nextElement();

				if (entry.getName().matches("lib/x86/(.*).so")) {
					Log.d(Tag, "Matching APK entry - " + entry.getName());
					// Unzip the lib file from apk
					BufferedInputStream is = new BufferedInputStream(apkFile
							.getInputStream(entry));
					
					String name = entry.getName().replace("lib/x86/", "");
					nameSet.add(name);
					
					File libFile = new File(libDirs+ "/" + name);
					// Create the file if it does not exist
					if (!libFile.exists()) {
						// Let the error propagate if the file cannot be created
						// - handled by IOException
						libFile.createNewFile();
					}

					Log.d(Tag, "Writing lib file to "
							+ libFile.getAbsolutePath());
					FileOutputStream fos = new FileOutputStream(libFile);
					int BUFFER = (int) entry.getSize();
					BufferedOutputStream dest = new BufferedOutputStream(fos,
							BUFFER);

					byte data[] = new byte[BUFFER];
					int count = 0;
					while ((count = is.read(data, 0, BUFFER)) != -1) {
						dest.write(data, 0, count);
					}
					dest.flush();
					dest.close();
					is.close();
				}
			}
			
			//Load arm libraries
			entries = (Enumeration<ZipEntry>) apkFile
					.entries();
			while (entries.hasMoreElements()) {
				entry = entries.nextElement();

				if (entry.getName().matches("lib/armeabi(.*)/(.*).so")) {
					Log.d(Tag, "Matching APK entry - " + entry.getName());
					// Unzip the lib file from apk
					BufferedInputStream is = new BufferedInputStream(apkFile
							.getInputStream(entry));
					
					String name = null;
					if(entry.getName().matches("lib/armeabi/(.*).so"))	
						name = entry.getName().replace("lib/armeabi/", "");
					else
						name = entry.getName().replace("lib/armeabi-v7a/", "");
						
						
					if(nameSet.contains(name)){
						Log.d(Tag, "Already unpacked, skip it!");
						continue;
					}
					
					nameSet.add(name);
					
					File libFile = new File(libDirs+ "/" + name);
					// Create the file if it does not exist
					if (!libFile.exists()) {
						// Let the error propagate if the file cannot be created
						// - handled by IOException
						libFile.createNewFile();
					}

					Log.d(Tag, "Writing lib file to "
							+ libFile.getAbsolutePath());
					FileOutputStream fos = new FileOutputStream(libFile);
					int BUFFER = (int) entry.getSize();
					BufferedOutputStream dest = new BufferedOutputStream(fos,
							BUFFER);

					byte data[] = new byte[BUFFER];
					int count = 0;
					while ((count = is.read(data, 0, BUFFER)) != -1) {
						dest.write(data, 0, count);
					}
					dest.flush();
					dest.close();
					is.close();
				}
			}

		} catch (IOException e) {
			Log.d(Tag, "ERROR: File unzipping error " + e);
		}
		Log.d(Tag, "Unzipping libraries finished, duration - "
				+ ((System.nanoTime() - startTime) / 1000000) + "ms");
		
		
	}
	
	/**
	 * return the explicit library path of a given library file name
	 * @param libName
	 * 		the full name of a library file
	 * @return
	 * 		the explicit path of the file
	 */
	public String getLibraryPath(String libName){
		return this.libDir+"/"+libName;
	}
	
	/**
	 * Get a specific class through class name, if a class is a nested class, use "package.outter$inner" as the class name
	 * @param className
	 * 			The name of the class
	 * @return
	 * 		the class, null if it does not exist
	 */
	public Class<?> getClass(String className) {

		if(cLoader==null) return null;
		try {
			Class<?> c = cLoader.loadClass(className);
//			Log.d(Tag,"Class found: ");
//			Log.i(Tag, Modifier.toString(c.getModifiers())+" "+c.getName());
			return c;
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;

	}
	
	/**
	 * get the class loader
	 * @return classLoader
	 * 		the class loader, or null if code is not loaded
	 */
	public ClassLoader getClassLoader(){
		return this.cLoader;
	}

	/**
	 * Get a specific field of a class through field name and class name, 
	 * if a class is a nested class, use "package.outter$inner" as the class name
	 * @param className
	 * 			The name of the class
	 * @param fieldName
	 * 			The name of the field
	 * @return
	 * 		the field, null if it does not exist or the class does not exist
	 */
	public Field getField(String className, String fieldName){
		return getField(getClass(className),fieldName);
	}
	
	/**
	 * Get a specific field of a class through field name and class
	 * @param clazz
	 * 			The class
	 * @param fieldName
	 * 			The name of the field
	 * @return
	 * 		the field, null if it does not exist or the class does not exist
	 */
	static public Field getField(Class<?> clazz, String fieldName){
		if(clazz==null) return null;
		
		try {
			Field f= clazz.getDeclaredField(fieldName);
//			Log.d(Tag,"Field found: ");
//			Log.i(Tag, Modifier.toString(f.getModifiers())+" "+f.getType()+" "+clazz.getName()+"::"+f.getName());
			return f;
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		}
		
		
		return null;
	}
	
	/**
	 * Get the value of a field of a specific class, if a class is a nested class, 
	 * use "package.outter$inner" as the class name
	 * @param className
	 * 			The name of the class
	 * @param fieldName
	 * 			The name of the field
	 * @param obj
	 * 			The object of the class
	 * @return
	 * 			The value of the field of the specific object
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws NoSuchFieldException 
	 */
	public Object getFieldValue(String className, String fieldName, Object obj) throws IllegalAccessException, IllegalArgumentException, NoSuchFieldException{
		
		return getFieldValue(getField(className,fieldName),obj);
		
	}
	
	/**
	 * Get the value of a field of a specific class
	 * @param clazz
	 * 			The class
	 * @param fieldName
	 * 			The name of the field
	 * @param obj
	 * 			The object of the class
	 * @return
	 * 			The value of the field of the specific object
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws NoSuchFieldException 
	 */
	static public Object getFieldValue(Class<?> clazz, String fieldName, Object obj) throws IllegalAccessException, IllegalArgumentException, NoSuchFieldException{
		
		return getFieldValue(getField(clazz,fieldName),obj);
		
	}
	
	/**
	 * Get the value of a field
	 * @param field
	 * 			The field
	 * @param obj
	 * 			The object of the class
	 * @return
	 * 			The value of the field of the specific object
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws NoSuchFieldException 
	 */
	static public Object getFieldValue(Field field, Object obj) throws IllegalAccessException, IllegalArgumentException, NoSuchFieldException{
		
		if(field==null)
			throw(new NoSuchFieldException());
		
		field.setAccessible(true);
		return field.get(obj);
		
	}
	
	
	/**
	 * Set the value of a field of a specific class, if a class is a nested class, 
	 * use "package.outter$inner" as the class name
	
	 * @param className
	 * 			Name of the class
	 * @param fieldName
	 * 			Name of the field
	 * @param obj
	 * 			Object of the class
	 * @param value
	 * 			Value to set
	 * @return 
	 * @return
	 * 			true if the value is set successfully
	 * 			,false if it is not set
	 * @throws IllegalArgumentException 
	 * @throws IllegalAccessException 
	 */
	public void setFieldValue(String className, String fieldName, Object obj, Object value) throws IllegalAccessException, IllegalArgumentException{
		setFieldValue(getField(className,fieldName), obj, value);
	}
	
	/**
	 * Set the value of a field of a specific class
	
	 * @param clazz
	 * 			The class
	 * @param fieldName
	 * 			Name of the field
	 * @param obj
	 * 			Object of the class
	 * @param value
	 * 			Value to set
	 * @return
	 * 			true if the value is set successfully
	 * 			,false if it is not set
	 * @throws IllegalArgumentException 
	 * @throws IllegalAccessException 
	 */
	static public void setFieldValue(Class<?> clazz, String fieldName, Object obj, Object value) throws IllegalAccessException, IllegalArgumentException{
		setFieldValue(getField(clazz,fieldName), obj, value);
	}
	
	/**
	 * Set the value of a field	
	 * @param field
	 * 			The field
	 * @param obj
	 * 			Object of the class
	 * @param value
	 * 			Value to set
	 * @return
	 * 			true if the value is set successfully
	 * 			,false if it is not set
	 * @throws IllegalArgumentException 
	 * @throws IllegalAccessException 
	 */
	static public void setFieldValue(Field field, Object obj, Object value) throws IllegalAccessException, IllegalArgumentException{
		field.setAccessible(true);
		if(obj!=null)
			synchronized(obj){
				field.set(obj, value);
			}
		else
			field.set(obj, value);
	}
	
	/**
	 * Get a new instance of specific class. If a class is a nested class, use "package.outter$inner" as the class name, and 
	 * the first parameter type should be its higher class, the first parameter should be its higher object
	 * 
	 * @param className
	 * 			The name of the class to be instantiated          
	 * @param paramTypes
	 *          Array of the name of parameter types
	 * @param params
	 * 			Array of the parameters
	 * @return a object of the class
	 * @throws ClassNotFoundException 
	 * @throws NoSuchMethodException 
	 * @throws InvocationTargetException 
	 * @throws IllegalArgumentException 
	 * @throws IllegalAccessException 
	 * @throws InstantiationException 
	 */
	public Object instantiateClass(String className, String[] paramTypes, Object[] params) throws InvokeMethodFailureException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException{	
		return instantiateClass(getClass(className),paramTypes,params);
	}
	
	
	/**
	 * Get a new instance of specific class. If a class is a nested class, 
	 * the first parameter type should be its higher class, the first parameter should be its higher object
	 * 
	 * @param clazz
	 * 			The class to be instantiated          
	 * @param paramTypes
	 *          Array of the name of parameter types
	 * @param params
	 * 			Array of the parameters
	 * @return a object of the class
	 * @throws ClassNotFoundException 
	 * @throws NoSuchMethodException 
	 * @throws InvocationTargetException 
	 * @throws IllegalArgumentException 
	 * @throws IllegalAccessException 
	 * @throws InstantiationException 
	 */
	static public Object instantiateClass(Class<?> clazz, String[] paramTypes, Object[] params) throws InvokeMethodFailureException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException{		
				
		if(paramTypes!=null && params!=null && paramTypes.length!=params.length)
			throw(new InvokeMethodFailureException());
		
		if(paramTypes==null && params!=null)
			throw(new InvokeMethodFailureException());
		
		if(paramTypes!=null && params==null)
			throw(new InvokeMethodFailureException());
		
		if(clazz==null)
			throw(new ClassNotFoundException());
		
		return instantiateClass(getConstructor(clazz,paramTypes),params);
	}
	
	/**
	 * Get a new instance through constructor. If a class is a nested class, 
	 * the first parameter type should be its higher class, the first parameter should be its higher object
	 * 
	 * @param ct
	 * 			The constructor          
	 * @param params
	 * 			Array of the parameters
	 * @return a object of the class
	 * @throws InvokeMethodFailureException 
	 * @throws InvocationTargetException 
	 * @throws IllegalArgumentException 
	 * @throws IllegalAccessException 
	 * @throws InstantiationException 
	 
	 */
	static public Object instantiateClass(Constructor<?> ct, Object[] params) throws InvokeMethodFailureException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {	
		Object ret=null;		
				
		
		if(ct==null)
			throw(new InvokeMethodFailureException());
		ct.setAccessible(true);
		ret=ct.newInstance(params);		
		return ret;
	}
	
	
	/**
	 * Invoke specific method of a class, if a class is a nested class, use "package.outter$inner" as the class name
	 * 
	 * @param className
	 * 			The name of the class which the method belongs to   
	 * @param invokObj
	 *          The instance of the class which calls the method
	 * @param methodName
	 * 			The name of the method         
	 * @param paramTypes
	 *          Array of the name of parameter types
	 * @param params
	 * 			Array of the parameters
	 * @return the return object of the called method
	 * @throws ClassNotFoundException 
	 * @throws NoSuchMethodException 
	 * @throws InvocationTargetException 
	 * @throws IllegalArgumentException 
	 * @throws IllegalAccessException 
	 */
	public Object invokeMethod(String className, Object invokObj, String methodName, String[] paramTypes, Object[] params) throws InvokeMethodFailureException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException{		
		return invokeMethod(getClass(className),invokObj,methodName,paramTypes,params);
	}
	
	/**
	 * Invoke specific method of a class
	 * 
	 * @param clazz
	 * 			The class which the method belongs to   
	 * @param invokObj
	 *          The instance of the class which calls the method
	 * @param methodName
	 * 			The name of the method         
	 * @param paramTypes
	 *          Array of the name of parameter types
	 * @param params
	 * 			Array of the parameters
	 * @return the return object of the called method
	 * @throws ClassNotFoundException 
	 * @throws NoSuchMethodException 
	 * @throws InvocationTargetException 
	 * @throws IllegalArgumentException 
	 * @throws IllegalAccessException 
	 */
	static public Object invokeMethod(Class<?> clazz, Object invokObj, String methodName, String[] paramTypes, Object[] params) throws InvokeMethodFailureException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException{			
		
		if(methodName==null )
			throw(new InvokeMethodFailureException());	
		
		if(paramTypes!=null && params!=null && paramTypes.length!=params.length)
			throw(new InvokeMethodFailureException());
		
		if(paramTypes==null && params!=null)
			throw(new InvokeMethodFailureException());
		
		if(paramTypes!=null && params==null)
			throw(new InvokeMethodFailureException());
			
		if(clazz==null)
			throw(new ClassNotFoundException());
		return invokeMethod(getMethod(clazz,methodName,paramTypes),invokObj,params);
	}
	
	/**
	 * Invoke specific method of a object
	 * 
	 * @param method
	 * 			The method   
	 * @param invokObj
	 *          The instance of the class which calls the method
	 * @param params
	 * 			Array of the parameters
	 * @return the return object of the called method
	 * @throws InvocationTargetException 
	 * @throws IllegalArgumentException 
	 * @throws IllegalAccessException 
	 */
	static public Object invokeMethod(Method method, Object invokObj, Object[] params) throws InvokeMethodFailureException, IllegalAccessException, IllegalArgumentException, InvocationTargetException{		
		Object ret=null;		
		
		if(method==null )
			throw(new InvokeMethodFailureException());	
		
		method.setAccessible(true);
		ret=method.invoke(invokObj, params);			

		return ret;
	}
	
	/**
	 * Get a method of a specific class, if a class is a nested class, use "package.outter$inner" as the class name
	 * @param cl
	 * 			The class name
	 * @param mName
	 * 			The method name
	 * @param paramTypes
	 * 			The parameter type name array
	 * @return
	 * 			The found method
	 * @throws NoSuchMethodException
	 * 			if cannot find matched method
	 */
	static public Method getMethod(Class<?> cl, String mName, String[] paramTypes) throws NoSuchMethodException{
		
		Method[] m=cl.getDeclaredMethods();
		
		for(int i=0;i<m.length;i++){
//			Log.i(Tag, "method name["+i+"]="+m[i].getName());
			if(!m[i].getName().equals(mName))
				continue;
			
			Class<?>[] types=m[i].getParameterTypes();
			
			if(paramTypes==null){
				if(types.length!=0)
					continue;
			}
			else
				if(!(types.length==paramTypes.length))
					continue;
			
			boolean isSame=true;
			
			for(int j=0;j<types.length;j++)
				if(!types[j].getName().equals(paramTypes[j])){
					isSame=false;
					break;
				}
			
			if(isSame){
/*				String str=Modifier.toString(m[i].getModifiers())+" "+m[i].getReturnType()+" "+cl.getName()+"::"+ m[i].getName()+"(";
				for(int j=0;j<types.length;j++){
					str+=types[j];
					if(j!=types.length-1)
						str+=", ";
				}
				str+=")";
				
				Log.d(Tag,"Method found: ");
				Log.i(Tag, str);*/
				return m[i];
			}
				
		}
		
		throw(new NoSuchMethodException());
		
		
	}

	/**
	 * Get a constructor of a specific class. If the class is a nested class, use "package.outter$inner" as the class name, and 
	 * the first parameter type should be its higher class
	 * @param cl
	 * 		Name of the class
	 * @param paramTypes
	 * 		Parameter type name array
	 * @return
	 * 		The found constructor
	 * @throws NoSuchMethodException
	 * 			if cannot find matched constructor
	 */
static public Constructor<?> getConstructor(Class<?> cl, String[] paramTypes) throws NoSuchMethodException{
		
		Constructor<?>[] ct=cl.getDeclaredConstructors();
		
		for(int i=0;i<ct.length;i++){ 
			
			Class<?>[] types=ct[i].getParameterTypes();
			
			if(paramTypes==null){
				if(types.length!=0)
					continue;
			}
			else
				if(!(types.length==paramTypes.length))
					continue;
			
			boolean isSame=true;
			
			for(int j=0;j<types.length;j++)
				if(!types[j].getName().equals(paramTypes[j])){
					isSame=false;
					break;
				}
			
			if(isSame){
/*				String str=Modifier.toString(ct[i].getModifiers())+" "+ ct[i].getName()+"(";
				for(int j=0;j<types.length;j++){
					str+=types[j];
					if(j!=types.length-1)
						str+=", ";
				}
				str+=")";
				Log.d(Tag,"Constructor found: ");
				Log.i(Tag, str);*/
				return ct[i];
			}
				
		}
		
		throw(new NoSuchMethodException());
	}	
	
}


