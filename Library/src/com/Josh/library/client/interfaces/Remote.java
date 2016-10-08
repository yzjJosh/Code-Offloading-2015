package com.Josh.library.client.interfaces;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * This annotation will tell the engine this method can be operated remotely.
 * Note that in order to make remote declare easy, all accessible calsses will implement
 *  serializable interface automatically. However, classes whose source code is invisible
 *  , such as android classes or Java classes, have not implemented serializable interface. 
 *  If inserializable classes relate to your remote methods, please declare it as transient. 
 *  Operation in the remote method should NOT interact with local resources, e.g.
 *   using the GPS, sensors, get thread or ask for process information.
 * @author Josh
 *
 */
@Target(ElementType.METHOD)
public @interface Remote {

}
