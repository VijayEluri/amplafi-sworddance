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

package com.sworddance.beans;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sworddance.util.CUtilities.*;

import com.sworddance.util.ApplicationIllegalArgumentException;
import com.sworddance.util.NotNullIterator;

import org.apache.commons.lang.StringUtils;

/**
 * Provides some general utility methods so that bean operations can be used more easily.
 *
 * Note that an instance of a BeanWorker is not linked to a class. This allows "duck-typing" operations.
 * @author patmoore
 *
 */
public class BeanWorker {

    private static final Pattern PROPERTY_METHOD_PATTERN = Pattern.compile("(is|set|get)(([A-Z])(\\w*))$");
    private static final Pattern GET_METHOD_PATTERN = Pattern.compile("(is|get)(([A-Z])(\\w*))$");
    private static final Pattern SET_METHOD_PATTERN = Pattern.compile("(set)(([A-Z])(\\w*))$");

    /**
     * This list of property names is the list of the only properties that the BeanWorker is allowed to modify.
     * Specifically, "foo.goo" does not mean the BeanWorker is allowed to modify the "foo" property - only "foo"'s "goo" property can be modified.
     */
    private List<String> propertyNames = new ArrayList<String>();
    // key = class, key = (each element in) propertyNames value = chain of methods to get to value.
    // TODO in future cache into a second map.
    private final MapByClass<ConcurrentMap<String,PropertyMethodChain>> methodsMap = new MapByClass<ConcurrentMap<String,PropertyMethodChain>>();

    /**
     * Allow intermediate Properties to be accessed.
     *
     * For example, if the property list is "child.grandchild" then "child" could be accessed directly. Useful for deep copying.
     */
    private final boolean allowIntermediateProperties;
    public BeanWorker() {
        this.allowIntermediateProperties = false;
    }
    public BeanWorker(String... propertyNames) {
        this(false, propertyNames);
    }
    /**
     * @param propertyNames
     */
    public BeanWorker(Collection<String> propertyNames) {
        this(false, propertyNames);
    }
    public BeanWorker(boolean allowIntermediateProperties, String... propertyNames) {
        this.allowIntermediateProperties = allowIntermediateProperties;
    	addPropertyNames(propertyNames);
    }
    public BeanWorker(boolean allowIntermediateProperties, Collection<String> propertyNames) {
        this.allowIntermediateProperties = allowIntermediateProperties;
        addPropertyNames(propertyNames);
    }
    /**
     * @param propertyNames the propertyNames to set
     */
    public void setPropertyNames(List<String> propertyNames) {
        this.propertyNames.clear();
        this.addPropertyNames(propertyNames);
    }
    public void addPropertyNames(String... additionalPropertyNames) {
        addPropertyNames(Arrays.asList(additionalPropertyNames));
    }
    public void addPropertyNames(Collection<String> additionalPropertyNames) {
        if ( isNotEmpty(additionalPropertyNames)) {
            // TODO: validate that there are no trailing/leading '.'
            this.propertyNames.addAll(additionalPropertyNames);
            // sorted so that when creating intermediate propertyChains we can create them read-only because we know that all the
            // explicit read/write properties have been created already.
            // See getMethodMap()
            Collections.sort(this.propertyNames);
        }
    }
    /**
     * @return the propertyNames
     */
    public List<String> getPropertyNames() {
        return propertyNames;
    }
    public String getPropertyName(int index) {
        return isNotEmpty(this.propertyNames) && index < this.propertyNames.size()?this.propertyNames.get(index) : null;
    }

    /**
     * Follows the propertyPath starting at base until null or until the end.
     * @param <T>
     * @param base
     * @param property
     * @return null or the property
     */
    @SuppressWarnings("unchecked")
    public <T> T getValue(Object base, String property) {
        T result = null;
        if ( base != null && property != null ) {
            // TODO: ideally readOnly = true but this would screw up later code that did need to write value.
            PropertyMethodChain methodChain = getPropertyMethodChain(base.getClass(), property);
            if ( methodChain != null ) {
                result = (T) methodChain.getValue(base);
            }
        }
        return result;
    }
    /**
     * Ask for the first property specified. Useful for BeanWorkers with only 1 specified property.
     * @param base
     * @return null or the property
     */
    public <T> T getValue(Object base) {
        T result = this.getValue(base, this.getPropertyName(0));
        return result;
    }
    public void setValue(Object base, String property, Object value) {
        if ( base != null && property != null ) {
            PropertyMethodChain methodChain = getPropertyMethodChain(base.getClass(), property);
            if ( methodChain != null ) {
                methodChain.setValue(base, value);
            }
        }
    }
    protected PropertyMethodChain getPropertyMethodChain(Class<?> clazz, String property) {
        Map<String, PropertyMethodChain> classMethodMap = getMethodMap(clazz);
        PropertyMethodChain methodChain = classMethodMap.get(property);
        return methodChain;
    }
    /**
     * For example, "grandparent.parent.child" will return a Method
     * chain of length 3 ( "getGrandparent().getParent().getChild()" )
     *
     * @param clazz
     * @param property "grandparent.parent.child"
     * @param readOnly
     * @return a chain of {@link Method}s that when sequentially called will return a result.
     */
    protected PropertyMethodChain getPropertyMethodChainAddIfAbsent(Class<?> clazz, String property, boolean readOnly) {
        ConcurrentMap<String, PropertyMethodChain> classMethodMap = getMethodMap(clazz);
        PropertyMethodChain methodChain = addPropertyMethodChainIfAbsent(clazz, classMethodMap, property, readOnly);
        return methodChain;
    }

    public Class<?> getPropertyType(Class<?> clazz) {
        return this.getPropertyType(clazz, this.getPropertyName(0));
    }
    public Class<?> getPropertyType(Class<?> clazz, String property) {
        PropertyMethodChain chain = getPropertyMethodChain(clazz, property);
        if ( chain == null) {
            chain = getFirst(newPropertyMethodChain(clazz, property, true, false));
            // TODO should put in the methodChain
        }
        return chain.getReturnType();
    }
    /**
     * Each class has its own version of the PropertyMethodChain map.
     * @param clazz
     * @return PropertyMethodChain map for the passed class.
     */
    protected ConcurrentMap<String, PropertyMethodChain> getMethodMap(Class<?> clazz) {
        ConcurrentMap<String, PropertyMethodChain> propMap;
        if ( !methodsMap.containsKey(clazz)) {
            propMap = new ConcurrentHashMap<String, PropertyMethodChain>();
            for(String property: NotNullIterator.<String>newNotNullIterator( getPropertyNames())) {
                addPropertyMethodChainIfAbsent(clazz, propMap, property, false);
            }
            methodsMap.putIfAbsent(clazz, propMap);
        }
        propMap = methodsMap.get(clazz);

        return propMap;
    }
    /**
     * @param clazz
     * @param propMap
     * @param propertyName
     * @param readOnly if true and if propertyMethodChain has not been found then only the get method is searched for.
     * @return the propertyMethodChain
     * @throws ApplicationIllegalArgumentException if the propertyName is not actually a property.
     */
    protected PropertyMethodChain addPropertyMethodChainIfAbsent(Class<?> clazz, ConcurrentMap<String, PropertyMethodChain> propMap, String propertyName, boolean readOnly)
        throws ApplicationIllegalArgumentException {
        if (!propMap.containsKey(propertyName)) {
            List<PropertyMethodChain> propertyMethodChains = newPropertyMethodChain(clazz, propertyName, readOnly, allowIntermediateProperties);
            ApplicationIllegalArgumentException.valid(isNotEmpty(propertyMethodChains), clazz, " has no property named '",propertyName,"'");
            for(PropertyMethodChain propertyMethodChain: propertyMethodChains) {
                propMap.putIfAbsent(propertyMethodChain.getProperty(), propertyMethodChain);
            }
        } else {
            // TODO: check to see if readOnly is false
        }
        return propMap.get(propertyName);
    }


    /**
     * @param clazz property's class
     * @param property property name
     * @param readOnly readonly property
     * @return the propertyMethodChain
     */
    protected List<PropertyMethodChain> newPropertyMethodChain(Class<?> clazz, String property, boolean readOnly, boolean expanded) {
        try {
            String[] splitProps = property.split("\\.");
            List<PropertyAdaptor> completePropertyMethodList = getMethods(clazz, splitProps, readOnly);
            List<PropertyMethodChain> propertyMethodChains;
            if ( !expanded) {
                propertyMethodChains = Arrays.asList(new PropertyMethodChain(clazz, property, readOnly, completePropertyMethodList));
            } else {
                propertyMethodChains = new ArrayList<PropertyMethodChain>();
                List<PropertyAdaptor> propertyMethodList = new ArrayList<PropertyAdaptor>();
                StringBuilder sb = new StringBuilder();
                for(int i = 0; i < splitProps.length; i++ ) {
                    sb.append(splitProps[i]);
                    propertyMethodList.add(completePropertyMethodList.get(i));
                    boolean notLast = i < splitProps.length-1;
                    // make readOnly if readOnly parameter or if intermediate PropertyMethodChain.
                    propertyMethodChains.add(new PropertyMethodChain(clazz, sb.toString(), readOnly||notLast, propertyMethodList));
                    sb.append(".");
                }
            }
            return propertyMethodChains;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    /**
     * collects a chain of property methods that are called sequentially to get the final result.
     * @param clazz
     * @param propertyNamesList
     * @param readOnly only look for a getter
     * @return the chain of methods.
     */
    protected List<PropertyAdaptor> getMethods(Class<?> clazz, String[] propertyNamesList, boolean readOnly) {
        Class<?>[] parameterTypes = new Class<?>[0];
        List<PropertyAdaptor> propertyMethodChain = new ArrayList<PropertyAdaptor>();
        for(Iterator<String> iter = Arrays.asList(propertyNamesList).iterator(); iter.hasNext();) {
            String propertyName = iter.next();
            PropertyAdaptor propertyAdaptor = new PropertyAdaptor(propertyName);
            propertyAdaptor.setGetter(clazz, parameterTypes);
            if ( !iter.hasNext() && !readOnly) {
                // only get the setter on the last iteration because PropertyMethodChain is only allowed to set the property at the
                // end of the chain. No other property along the way can be set.
                propertyAdaptor.initSetter(clazz);
            }
            if ( propertyAdaptor.isExists()) {
                clazz = propertyAdaptor.getReturnType();
                propertyMethodChain.add(propertyAdaptor);
            } else {
                throw new IllegalArgumentException(StringUtils.join(propertyNamesList)+" has bad property " + propertyName);
            }
        }
        return propertyMethodChain;
    }

    protected String getPropertyName(Method method) {
        String methodName = method.getName();
        return this.getPropertyName(methodName);
    }
    protected String getPropertyName(String methodName) {
        Matcher matcher = PROPERTY_METHOD_PATTERN.matcher(methodName);
        String propertyName;
        if (matcher.find()) {
            propertyName = matcher.group(3).toLowerCase()+matcher.group(4);
        } else {
            propertyName = null;
        }
        return propertyName;
    }
    protected String getGetterPropertyName(String methodName) {
        Matcher matcher = GET_METHOD_PATTERN.matcher(methodName);
        String propertyName;
        if (matcher.find()) {
            propertyName = matcher.group(3).toLowerCase()+matcher.group(4);
        } else {
            propertyName = null;
        }
        return propertyName;
    }
    protected String getSetterPropertyName(String methodName) {
        Matcher matcher = SET_METHOD_PATTERN.matcher(methodName);
        String propertyName;
        if (matcher.find()) {
            propertyName = matcher.group(3).toLowerCase()+matcher.group(4);
        } else {
            propertyName = null;
        }
        return propertyName;
    }
}
