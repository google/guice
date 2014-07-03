package com.google.inject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Will filter in or out classes based on the information gathered by the annotation
 * preprocessor of RoboGuice. A class is filtered in if it contains an injection point
 * or its super classes contain an injection point.<br/>
 * Once a class is filtered in has having injection points,
 * its super classes are kept as long as they satisfy the filtering operated
 * by {@link RoboGuiceHierarchyTraversalFilter}. Otherwise, the class will be rejected
 * by the filter.
 * @author SNI
 */
public class AnnotatedGuiceHierarchyTraversalFilter extends HierarchyTraversalFilter {
    private boolean hasHadInjectionPoints;
    private HashMap<String, Map<String, Set<String>>> mapAnnotationToMapClassWithInjectionNameToConstructorSet;
    private HashMap<String, Map<String, Set<String>>> mapAnnotationToMapClassWithInjectionNameToMethodSet;
    private HashMap<String, Map<String, Set<String>>> mapAnnotationToMapClassWithInjectionNameToFieldSet;
    private HashSet<String> classesContainingInjectionPointsSet = new HashSet<String>();
    private HierarchyTraversalFilter delegate;

    public  AnnotatedGuiceHierarchyTraversalFilter(AnnotationDatabaseFinder annotationDatabaseFinder, HierarchyTraversalFilter delegate ) {
        this.delegate = delegate;
       
        mapAnnotationToMapClassWithInjectionNameToFieldSet = annotationDatabaseFinder.getMapAnnotationToMapClassContainingInjectionToInjectedFieldSet();
        mapAnnotationToMapClassWithInjectionNameToMethodSet = annotationDatabaseFinder.getMapAnnotationToMapClassContainingInjectionToInjectedMethodSet();
        mapAnnotationToMapClassWithInjectionNameToConstructorSet = annotationDatabaseFinder.getMapAnnotationToMapClassContainingInjectionToInjectedConstructorSet();
        classesContainingInjectionPointsSet = annotationDatabaseFinder.getClassesContainingInjectionPointsSet();

        if(classesContainingInjectionPointsSet.isEmpty())
            throw new IllegalStateException("Unable to find Annotation Database which should be output as part of annotation processing");
    }

    @Override
    public void reset( ) {
        delegate.reset();
        hasHadInjectionPoints = false;
    }
    
    @Override
    public boolean isWorthScanning(Class<?> c) {
        if( hasHadInjectionPoints ) {
            return delegate.isWorthScanning(c);
        } else if( c != null ) {
            do {
                if( classesContainingInjectionPointsSet.contains(c.getName()) ) {
                    hasHadInjectionPoints = true;
                    return true;
                }
                c = c.getSuperclass();
            } while( delegate.isWorthScanning(c) );
        }  
        return false;
    }

    @Override
    public boolean isWorthScanningForFields(String annotationClassName, Class<?> c) {
        Map<String, Set<String>> classesContainingInjectionPointsForAnnotation;
        if( hasHadInjectionPoints ) {
            return delegate.isWorthScanning(c);
        } else if( c != null ) {
            classesContainingInjectionPointsForAnnotation = mapAnnotationToMapClassWithInjectionNameToFieldSet.get(annotationClassName);
            if( classesContainingInjectionPointsForAnnotation == null ) {
                return false;
            }
            do {
                if( classesContainingInjectionPointsForAnnotation.containsKey(c.getName()) ) {
                    hasHadInjectionPoints = true;
                    return true;
                }
                c = c.getSuperclass();
            } while( delegate.isWorthScanning(c) );
        }  
        return false;
    }

    @Override
    public Set<Field> getAllFields(String annotationClassName, Class<?> c) {
        Map<String, Set<String>> classesContainingInjectionPointsForAnnotation = mapAnnotationToMapClassWithInjectionNameToFieldSet.get(annotationClassName);

        if( c != null && classesContainingInjectionPointsForAnnotation!= null ) {
            Set<String> fieldNameSet = classesContainingInjectionPointsForAnnotation.get(c.getName());
            if( fieldNameSet != null ) {
                Set<Field> fieldSet = new HashSet<Field>();
                try {
                    for( String fieldName : fieldNameSet ) {
                        fieldSet.add( c.getDeclaredField(fieldName));
                    }
                    return fieldSet;
                } catch( Exception ex ) {
                    ex.printStackTrace();
                }
            }
        }
        //costly but should not happen
        return Collections.emptySet();
    }

    @Override
    public boolean isWorthScanningForMethods(String annotationClassName, Class<?> c) {
        Map<String, Set<String>> classesContainingInjectionPointsForAnnotation;

        if( hasHadInjectionPoints ) {
            return delegate.isWorthScanning(c);
        } else if( c != null ) {
            classesContainingInjectionPointsForAnnotation = mapAnnotationToMapClassWithInjectionNameToMethodSet.get(annotationClassName);
            if( classesContainingInjectionPointsForAnnotation == null ) {
                return false;
            }
            do {
                if( classesContainingInjectionPointsForAnnotation.containsKey(c.getName()) ) {
                    hasHadInjectionPoints = true;
                    return true;
                }
                c = c.getSuperclass();
            } while( delegate.isWorthScanning(c) );
        }  
        return false;
    }
    
    @Override
    public Set<Method> getAllMethods(String annotationClassName, Class<?> c) {
        
        //System.out.printf("map of methods : %s \n",mapAnnotationToMapClassWithInjectionNameToMethodSet.toString());
        Map<String, Set<String>> classesContainingInjectionPointsForAnnotation = mapAnnotationToMapClassWithInjectionNameToMethodSet.get(annotationClassName);

        if( c != null && classesContainingInjectionPointsForAnnotation!= null ) {
            Set<String> methodNameSet = classesContainingInjectionPointsForAnnotation.get(c.getName());
            if( methodNameSet != null ) {
                Set<Method> methodSet = new HashSet<Method>();
                try {
                    for( String methodNameAndParamClasses : methodNameSet ) {
                        //System.out.printf("Getting method %s of class %s \n",methodNameAndParamClasses,c.getName());
                        String[] split = methodNameAndParamClasses.split(":");
                        String methodName = split[0];
                        Class<?>[] paramClass = new Class[split.length-1];
                        for( int i=1;i<split.length;i++) {
                            paramClass[i-1] = getClass().getClassLoader().loadClass(split[i]);
                        }
                        methodSet.add( c.getDeclaredMethod(methodName, paramClass));
                    }
                    return methodSet;
                } catch( Exception ex ) {
                    ex.printStackTrace();
                }
            }
        }
        //costly but should not happen
        return Collections.emptySet();
    }

    @Override
    public boolean isWorthScanningForConstructors(String annotationClassName, Class<?> c) {
        Map<String, Set<String>> classesContainingInjectionPointsForAnnotation;

        if( hasHadInjectionPoints ) {
            return delegate.isWorthScanning(c);
        } else if( c != null ) {
            classesContainingInjectionPointsForAnnotation = mapAnnotationToMapClassWithInjectionNameToConstructorSet.get(annotationClassName);
            if( classesContainingInjectionPointsForAnnotation == null ) {
                return false;
            }
            do {
                if( classesContainingInjectionPointsForAnnotation.containsKey(c.getName()) ) {
                    hasHadInjectionPoints = true;
                    return true;
                }
                c = c.getSuperclass();
            } while( delegate.isWorthScanning(c) );
        }  
        return false;
    }
    
    public Set<Constructor<?>> getAllConstructors(String annotationClassName, Class<?> c) {
        
        //System.out.printf("map of methods : %s \n",mapAnnotationToMapClassWithInjectionNameToConstructorSet.toString());
        Map<String, Set<String>> classesContainingInjectionPointsForAnnotation = mapAnnotationToMapClassWithInjectionNameToConstructorSet.get(annotationClassName);

        if( c != null && classesContainingInjectionPointsForAnnotation!= null ) {
            Set<String> methodNameSet = classesContainingInjectionPointsForAnnotation.get(c.getName());
            if( methodNameSet != null ) {
                Set<Constructor<?>> methodSet = new HashSet<Constructor<?>>();
                try {
                    for( String methodNameAndParamClasses : methodNameSet ) {
                        //System.out.printf("Getting method %s of class %s \n",methodNameAndParamClasses,c.getName());
                        String[] split = methodNameAndParamClasses.split(":");
                        Class<?>[] paramClass = new Class[split.length-1];
                        for( int i=1;i<split.length;i++) {
                            paramClass[i-1] = getClass().getClassLoader().loadClass(split[i]);
                        }
                        methodSet.add( c.getDeclaredConstructor( paramClass));
                    }
                    return methodSet;
                } catch( Exception ex ) {
                    ex.printStackTrace();
                }
            }
        }
        //costly but should not happen
        return Collections.emptySet();
    }
}