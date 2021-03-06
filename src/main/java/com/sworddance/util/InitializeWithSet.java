/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the
 * License.
 */

package com.sworddance.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Used to provide create a List when initialization is required.
 * @author patmoore
 * @param <V>
 *
 */
public class InitializeWithSet<V> implements Callable<Set<V>> {
    @SuppressWarnings("unchecked")
    public static final InitializeWithSet INSTANCE = new InitializeWithSet(false);
    @SuppressWarnings("unchecked")
    public static final InitializeWithSet INSTANCE_THREAD_SAFE = new InitializeWithSet(true);
    private final boolean threadsafe;


    /**
     * @param threadsafe
     */
    public InitializeWithSet(boolean threadsafe) {
        this.threadsafe=threadsafe;
    }


    /**
     * @see java.util.concurrent.Callable#call()
     */
    public Set<V> call() throws Exception {
        return this.threadsafe?Collections.newSetFromMap(new ConcurrentHashMap<V, Boolean>()): new HashSet<V>();
    }

    public static <V> InitializeWithSet<V> get(boolean threadsafe) {
        return threadsafe?INSTANCE_THREAD_SAFE:INSTANCE;
    }

}
