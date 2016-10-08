package com.Josh.library.core.component;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
  
/**
 * Command object is the only object that can be transmitted between server and client, a Command
 * contains all information that need to be transmitted. Each Command MUST have a command type (COMMAND) 
 * and a command id. 
 * @author Josh
 *
 */
public class Command implements Serializable{
	/**
	 * Command types
	 * @author Josh
	 *
	 */
	public enum COMMAND{EXECUTE_METHOD,		EXECUTE_METHOD_RESULT_RETURN,
						EXECUTE_METHOD_THREAD_ID_RETURN,
						OBJECT_REQUEST,		OBJECT_REQUEST_RETURN,
						FIELD_SET,			FIELD_SET_RETURN,
						CODE_TRANSMIT,		CODE_TRANSMIT_RETURN,
						PING,				PING_RETURN};
	private COMMAND cmd;
	private HashMap<String,Object> Extra = new HashMap<String,Object>();
	private int id;
	private static Set<COMMAND> returnSet = new HashSet<COMMAND>();
	private static final long serialVersionUID = 1L;
	
	static{
		// add return COMMAND to the return COMMAND set
		returnSet.add(COMMAND.EXECUTE_METHOD_THREAD_ID_RETURN);
		returnSet.add(COMMAND.EXECUTE_METHOD_RESULT_RETURN);
		returnSet.add(COMMAND.FIELD_SET_RETURN);
		returnSet.add(COMMAND.OBJECT_REQUEST_RETURN);
		returnSet.add(COMMAND.CODE_TRANSMIT_RETURN);
		returnSet.add(COMMAND.PING_RETURN);
	}
	
	public Command(COMMAND cmd, int commandId){
		this.cmd=cmd;
		this.id = commandId;
	}
	
	/**
	 * Get the command id of a Command
	 * @return
	 * 		the command id
	 */
	public int getCommandId(){
		return this.id;
	}
	
	/**
	 * Check if a Command is a return Command
	 * @param cmd
	 * 		the Command
	 * @return
	 * 		if it is return Command or not
	 */
	public static boolean isReturnCommand(Command cmd){
		if(returnSet.contains(cmd.getCOMMAND()))
			return true;
		else
			return false;
	}
	
	/**
	 * Put extra objects to this Command, the extra objects MUST implement Serializable interface.
	 * Each extra object is mapped from a specific key to the object, if this Command already has
	 * a key, put the key again will replace the old object with the new one.  
	 * @param Key
	 * 		the key
	 * @param obj
	 * 		the value
	 */
	public void putExtra(String Key, Object obj){
		Extra.put(Key, obj);
	}
	
	/**
	 * Remove a specific key and its value
	 * @param key
	 * 		the key to remove
	 */
	public void removeExtra(String key){
		Extra.remove(key);
	}
	
	/**
	 * Get a specific extra object
	 * @param Key
	 * 		the key of the object
	 * @return
	 * 		the object
	 */
	public Object getExtra(String Key){
		return Extra.get(Key);
	}
	
	/**
	 * get the command type (COMMAND) of this Command
	 * @return
	 * 		the command type (COMMAND)
	 */
	public COMMAND getCOMMAND(){
		return cmd;
	}
}
