package com.google.inject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Finds all annotation databases. AnnotationDatabase can be generated using RoboGuice annotation compiler.
 * By default the roboguice annotation database is taken into account, and this can't be modified.
 * <br/>
 * You can add custom annotation databases by adding them to your manifest : 
 * <pre> 
 *  &lt;meta-data android:name="roboguice.annotations.packages"
 *    android:value="myPackage" /&gt;
 * </pre>
 * In that case, RoboGuice will load both <code>roboguice.AnnotationDatabaseImpl</code> and <code>myPackage.AnnotationDatabaseImpl</code>.
 * More packages containing AnnotationDatabases can be added, separated by commas. 
 * @author SNI
 */
public class AnnotationDatabaseFinder {
    
    private HashSet<String> classesContainingInjectionPointsSet = new HashSet<String>();
    private HashMap<String, Map<String, Set<String>>> mapAnnotationToMapClassContainingInjectionToInjectedFieldSet = new HashMap<String, Map<String, Set<String>>>();
    private HashMap<String, Map<String, Set<String>>> mapAnnotationToMapClassContainingInjectionToInjectedMethodSet = new HashMap<String, Map<String, Set<String>>>();
    private HashMap<String, Map<String, Set<String>>> mapAnnotationToMapClassContainingInjectionToInjectedConstructorSet = new HashMap<String, Map<String, Set<String>>>();
    private HashSet<String> bindableClassesSet = new HashSet<String>();

    public AnnotationDatabaseFinder(String[] additionalPackageNames) {
        try {
            for( String pkg : additionalPackageNames ) {
                String annotationDatabaseClassName = "AnnotationDatabaseImpl";
                if( pkg != null && !"".equals(pkg) ) {
                    annotationDatabaseClassName = pkg + "." + annotationDatabaseClassName;
                }
                AnnotationDatabase annotationDatabase = getAnnotationDatabaseInstance(annotationDatabaseClassName);
                addAnnotationDatabase(annotationDatabase);
            }
        } catch (InstantiationException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public HashSet<String> getClassesContainingInjectionPointsSet() {
        return classesContainingInjectionPointsSet;
    }
    
    public HashMap<String, Map<String, Set<String>>> getMapAnnotationToMapClassContainingInjectionToInjectedFieldSet() {
        return mapAnnotationToMapClassContainingInjectionToInjectedFieldSet;
    }

    public HashMap<String, Map<String, Set<String>>> getMapAnnotationToMapClassContainingInjectionToInjectedMethodSet() {
        return mapAnnotationToMapClassContainingInjectionToInjectedMethodSet;
    }
    
    public HashMap<String, Map<String, Set<String>>> getMapAnnotationToMapClassContainingInjectionToInjectedConstructorSet() {
        return mapAnnotationToMapClassContainingInjectionToInjectedConstructorSet;
    }
    
    public Set<String> getBindableClassesSet() {
        return bindableClassesSet;
    }

    private AnnotationDatabase getAnnotationDatabaseInstance(String annotationDatabaseClassName) throws ClassNotFoundException, InstantiationException,
    IllegalAccessException {
        Class<?> annotationDatabaseClass = Class.forName( annotationDatabaseClassName);
        AnnotationDatabase annotationDatabase = (AnnotationDatabase) annotationDatabaseClass.newInstance();
        return annotationDatabase;
    }

    private void addAnnotationDatabase(AnnotationDatabase annotationDatabase) {
        annotationDatabase.fillAnnotationClassesAndFieldsNames(mapAnnotationToMapClassContainingInjectionToInjectedFieldSet);
        annotationDatabase.fillAnnotationClassesAndMethods(mapAnnotationToMapClassContainingInjectionToInjectedMethodSet);
        annotationDatabase.fillAnnotationClassesAndConstructors(mapAnnotationToMapClassContainingInjectionToInjectedConstructorSet);
        annotationDatabase.fillClassesContainingInjectionPointSet(classesContainingInjectionPointsSet);
        annotationDatabase.fillBindableClasses(bindableClassesSet);
        //System.out.println(mapAnnotationToMapClassWithInjectionNameToMethodSet.toString());
    }

}
