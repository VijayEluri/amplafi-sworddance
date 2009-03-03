/**
 * Copyright 2006-2008 by Amplafi. All rights reserved.
 * Confidential.
 */
package com.sworddance.beans;

/**
 * @author patmoore
 *
 */
public interface ProxyFactory {

    public <I,O extends I> I getProxy(O realObject, String...propertyChains);
}
