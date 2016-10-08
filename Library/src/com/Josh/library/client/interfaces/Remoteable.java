package com.Josh.library.client.interfaces;

import java.io.Serializable;


/**
 * This is the interface that enables class to be transmitted to the server.
 * Developer need not implement this interface manually, cause all user-defined
 * classes have already implemented this interface via aspectJ.
 * @author Josh
 *
 */
public interface Remoteable extends Serializable {
}
