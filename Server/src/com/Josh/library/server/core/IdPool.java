package com.Josh.library.server.core;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import android.util.Log;

/**
 * A pool that provides unique id
 * @author Josh
 *
 */
public class IdPool {
	private Set<Integer> idPool = new HashSet<Integer>();
	private final int Increment = 50;
	private int range;
	private static String Tag = "IdPool";
	
	public IdPool(int size){
		range = size;
		for(int i=0; i<size; i++)
			idPool.add(i);
	}
	
	
	/**
	 * get an unique id from this id pool, the id will not be the same as another id from 
	 * this pool unless that id is returned
	 * @return
	 * 		an id
	 */
	public synchronized int getPosition(){
		synchronized(idPool){
			Iterator<Integer> it = idPool.iterator();
			if(it.hasNext()){
				int result = it.next();
				it.remove();
				return result;
			}else{
				Log.i(Tag, "Id pool overflowed, size = "+range+" enlarging id pool!");
				int newRange = range + Increment;
				for(;range<newRange;range++)
					idPool.add(range);
				Log.i(Tag, "Enlarging finished! size = "+range);
				return getPosition();
			}
		}
		
	}
	
	/**
	 * return an id to this id pool, so that this id can be got from this pool again
	 * @param position
	 * 		the return id
	 */
	public void returnPosition(int position){
		synchronized(idPool){
			idPool.add(position);
		}
	}
}
