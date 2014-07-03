package com.google.inject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * A {@link } allows to prune the type hierarchy and speeding up Guice.
 * Subclasses can significantly decrease time needed for injection by
 * discarding classes that can be scanned by Guice to look for injection
 * points.
 * By default, this class only filters out null and {@link Object} class.
 * @author SNI
 */
public class HierarchyTraversalFilter {

    /**
     * Decides whether or not class c is worth looking for injection points.
     * @param c a class being inspected by Guice to find injection points.
     * @return true if class c should be inspected, false otherwise.
     */
    public boolean isWorthScanning(Class<?> c) {
        return c != null && c != Object.class;
    }
    
    /**
     * Decides whether or not class c is worth looking for injection points.
     * @param c a class being inspected by Guice to find injection points.
     * @return true if class c should be inspected, false otherwise.
     */
    public boolean isWorthScanningForFields(String AnnotationClassName, Class<?> c) {
        return isWorthScanning(c);
    }
    
    public Set<Field> getAllFields(String annotationClassName, Class<?> c) {
        HashSet<Field> set = new HashSet<Field>();
        for( Field field : c.getDeclaredFields() ) {
            set.add(field);
        }
        return set;
    }

    /**
     * Decides whether or not class c is worth looking for injection points.
     * @param c a class being inspected by Guice to find injection points.
     * @return true if class c should be inspected, false otherwise.
     */
    public boolean isWorthScanningForMethods(String AnnotationClassName, Class<?> c) {
        return isWorthScanning(c);
    }
    
    public Set<Method> getAllMethods(String annotationClassName, Class<?> c) {
        HashSet<Method> set = new HashSet<Method>();
        for( Method method : c.getDeclaredMethods() ) {
            set.add(method);
        }
        return set;
    }
    
    /**
     * Decides whether or not class c is worth looking for injection points.
     * @param c a class being inspected by Guice to find injection points.
     * @return true if class c should be inspected, false otherwise.
     */
    public boolean isWorthScanningForConstructors(String AnnotationClassName, Class<?> c) {
        return isWorthScanning(c);
    }
    
    public Set<Constructor<?>> getAllConstructors(String annotationClassName, Class<?> c) {
        HashSet<Constructor<?>> set = new HashSet<Constructor<?>>();
        for( Constructor<?> method : c.getDeclaredConstructors() ) {
            set.add(method);
        }
        return set;
    }

    public void reset() {
        //nothing to do. Stateless.
    }

}
