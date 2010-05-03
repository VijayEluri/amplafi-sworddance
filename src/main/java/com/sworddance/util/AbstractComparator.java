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

import java.util.Comparator;

/**
 * because I always forget what should be negative / positive and the javadoc on Comparator always confuses me esp. wrt nulls :-P
 * @author patmoore
 * @param <T>
 *
 */
public abstract class AbstractComparator<T> implements Comparator<T> {

    /**
     * do the null checks
     * @param o1
     * @param o2
     * @return negative number: o1 < o2, o1 == null o2 != null; positive number o1 != null and o2 == null or o1 > o2; 0 otherwise.
     */
    protected Integer doCompare(T o1, T o2) {
        if ( o1 == o2) {
            return 0;
        } else if ( o1 == null ) {
            return -1;
        } else if ( o2 == null ) {
            return 1;
        } else {
            return null;
        }
    }
}
