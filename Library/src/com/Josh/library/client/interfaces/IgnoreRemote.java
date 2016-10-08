package com.Josh.library.client.interfaces;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * In order to make data transmition easily, all the visible classes will implement
 * {@link com.Josh.library.client.interfaces.Remoteable} interface automatically, 
 * which extends {@link java.io.Serializable} interface. If for some reasons you do not 
 * want a class be serializable, please add this annotation on the class or interface, 
 * and it will be ignored. Please remember methods with a {@link com.Josh.library.core.Remote} 
 * annotation should NOT have its class with this annotation.
 * @author Josh
 *
 */
@Target(ElementType.TYPE)
public @interface IgnoreRemote {

}
