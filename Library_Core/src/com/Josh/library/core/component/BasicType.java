package com.Josh.library.core.component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

/**
 * This class provides method to check if a class is basic type. A basic type 
 * is the type whose instances cannot be modified once it is constructed.
 * @author Josh
 *
 */
public class BasicType {
	private static final Set<Class<?>> BASIC_TYPES = getBasicTypes();

	/**
	 * Check if a class is a basic type. A basic type is the type whose instances cannot
	 *  be modified once it is constructed. Basic type includes Boolean, Character,
	 * Byte, Short, Integer, Long, Float, Double, Void, String, BigInteger, BigDecimal
	 * @param clazz
	 * 		the checked type
	 * @return
	 * 		is basic type or not
	 */
	   public static boolean isBasicType(Class<?> clazz)
	   {
	       return BASIC_TYPES.contains(clazz);
	   }

	   /**
	    * get a set that contains all basic types
	    * @return
	    * 		a set that contains all basic types
	    */
	   private static Set<Class<?>> getBasicTypes()
	   {
	       Set<Class<?>> ret = new HashSet<Class<?>>();
	       ret.add(Boolean.class);
	       ret.add(Character.class);
	       ret.add(Byte.class);
	       ret.add(Short.class);
	       ret.add(Integer.class);
	       ret.add(Long.class);
	       ret.add(Float.class);
	       ret.add(Double.class);
	       ret.add(Void.class);
	       ret.add(String.class);
	       ret.add(BigInteger.class);
	       ret.add(BigDecimal.class);
	       return ret;
	   }
}
