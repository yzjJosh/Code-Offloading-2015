package com.Josh.library.client.interfaces;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Using this annotatin will ignore warning caused by inappropriate 
 * {@link com.Josh.library.core.Remote} annotation. 
 * @author Josh
 *
 */
@Target(ElementType.METHOD)
public @interface IgnoreWarning {

}
