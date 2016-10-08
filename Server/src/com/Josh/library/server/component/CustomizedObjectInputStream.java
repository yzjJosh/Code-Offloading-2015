package com.Josh.library.server.component;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.io.StreamCorruptedException;

import com.Josh.library.core.component.CodeHandler;


/**
 * CustomizedObjectInputStream extends ObjectInputStream, it enables the stream to
 * load classes from other apk files.
 * @author Josh
 *
 */
public class CustomizedObjectInputStream extends ObjectInputStream {
	private CodeHandler codeHandler;
	
	public CustomizedObjectInputStream(InputStream input, CodeHandler handler)
			throws StreamCorruptedException, IOException {
		super(input);
		this.codeHandler = handler;
	}
	
	@Override
	protected Class<?> resolveClass(ObjectStreamClass desc) throws ClassNotFoundException, IOException{
		if(codeHandler.hasLoadedCode())
			return codeHandler.getClass(desc.getName());
		else
			return super.resolveClass(desc);
    }	
}
