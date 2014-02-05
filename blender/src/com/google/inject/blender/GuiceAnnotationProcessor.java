package com.google.inject.blender;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * An annotation processor that detects classes that need to receive injections.
 * @author MikeBurton
 * @author SNI  
 */
@SupportedAnnotationTypes({"com.google.inject.Inject", "javax.inject.Inject", "com.google.inject.Provides"})
@SupportedSourceVersion(SourceVersion.RELEASE_6)
@SupportedOptions({"guiceAnnotationDatabasePackageName"})
public class GuiceAnnotationProcessor extends AbstractProcessor {

    private AnnotationDatabaseGenerator annotationDatabaseGenerator = new AnnotationDatabaseGenerator();

    //TODO add a HashMap<String, Set<String>>

    /**
     * Maps each annotation name to an inner map.
     * The inner map maps classes (containing injection points) names to the list of injected field names.
     */
    private HashMap<String, Map<String, Set<String>> > mapAnnotationToMapClassContainingInjectionToInjectedFieldSet;
    /**
     * Maps each annotation name to an inner map.
     * The inner map maps classes (containing injection points) names to the list of injected method names and parameters classes.
     */
    private HashMap<String, Map<String, Set<String>> > mapAnnotationToMapClassContainingInjectionToInjectedMethodSet;
    /**
     * Maps each annotation name to an inner map.
     * The inner map maps classes (containing injection points) names to the list of injected constructors parameters classes.
     */
    private HashMap<String, Map<String, Set<String>> > mapAnnotationToMapClassContainingInjectionToInjectedConstructorsSet;

    /** Contains all classes that contain injection points. */
    private HashSet<String> classesContainingInjectionPointsSet = new HashSet<String>();

    /** Contains all classes that can be injected into a class with injection points.*/
    private HashSet<String> bindableClasses;
    /** Name of the package to generate the annotation database into.*/
    private String annotationDatabasePackageName;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        annotationDatabasePackageName = processingEnv.getOptions().get("guiceAnnotationDatabasePackageName");
        mapAnnotationToMapClassContainingInjectionToInjectedFieldSet = new HashMap<String, Map<String,Set<String>> >();
        mapAnnotationToMapClassContainingInjectionToInjectedMethodSet = new HashMap<String, Map<String,Set<String>> >();
        mapAnnotationToMapClassContainingInjectionToInjectedConstructorsSet = new HashMap<String, Map<String,Set<String>> >();
        bindableClasses = new HashSet<String>();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // Not sure why, but sometimes we're getting called with an empty list of annotations.
        if(annotations.isEmpty())
            return true;

        for( TypeElement annotation : annotations ) {
            String annotationClassName = getTypeName(annotation);
            //merge the 2 inject annotations
            if( "javax.inject.Inject".equals(annotationClassName) ) {
                annotationClassName = "com.google.inject.Inject";
            }
            
            for( Element injectionPoint : roundEnv.getElementsAnnotatedWith(annotation)) {
                if( injectionPoint.getEnclosingElement() instanceof TypeElement && injectionPoint instanceof VariableElement ) {
                    addFieldToAnnotationDatabase(annotationClassName, injectionPoint);
                } else if( injectionPoint.getEnclosingElement() instanceof ExecutableElement && injectionPoint instanceof VariableElement ) {
                    addParameterToAnnotationDatabase(annotationClassName, injectionPoint);
                } else if( injectionPoint instanceof ExecutableElement ) {
                    addMethodOrConstructorToAnnotationDatabase(annotationClassName, injectionPoint);
                } else if( injectionPoint instanceof TypeElement ) {
                    addClassToAnnotationDatabase(injectionPoint);
                }
            }
        }


        for( Map<String, Set<String>> entryAnnotationToclassesContainingInjectionPoints : mapAnnotationToMapClassContainingInjectionToInjectedFieldSet.values() ) {
            classesContainingInjectionPointsSet.addAll(entryAnnotationToclassesContainingInjectionPoints.keySet());
        }

        for( Map<String, Set<String>> entryAnnotationToclassesContainingInjectionPoints : mapAnnotationToMapClassContainingInjectionToInjectedMethodSet.values() ) {
            classesContainingInjectionPointsSet.addAll(entryAnnotationToclassesContainingInjectionPoints.keySet());
        }

        for( Map<String, Set<String>> entryAnnotationToclassesContainingInjectionPoints : mapAnnotationToMapClassContainingInjectionToInjectedConstructorsSet.values() ) {
            classesContainingInjectionPointsSet.addAll(entryAnnotationToclassesContainingInjectionPoints.keySet());
        }

        JavaFileObject jfo;
        try {
            String className = "AnnotationDatabaseImpl";
            if( annotationDatabasePackageName != null && !annotationDatabasePackageName.isEmpty() ) {
                className = annotationDatabasePackageName+'.'+className;
            }
            jfo = processingEnv.getFiler().createSourceFile( className );
            annotationDatabaseGenerator.generateAnnotationDatabase(jfo, annotationDatabasePackageName, mapAnnotationToMapClassContainingInjectionToInjectedFieldSet, mapAnnotationToMapClassContainingInjectionToInjectedMethodSet, mapAnnotationToMapClassContainingInjectionToInjectedConstructorsSet, classesContainingInjectionPointsSet, bindableClasses);
        } catch (IOException e) {
            e.printStackTrace();
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
        }

        return true;
    }

    private void addClassToAnnotationDatabase(Element injectionPoint) {
        TypeElement typeElementRequiringScanning = (TypeElement) injectionPoint;
        String typeElementName = getTypeName(typeElementRequiringScanning);
        //System.out.printf("Type: %s, is injected\n",typeElementName);
        classesContainingInjectionPointsSet.add(typeElementName);
    }

    private void addFieldToAnnotationDatabase(String annotationClassName, Element injectionPoint) {
        String injectionPointName;
        String injectedClassName = getTypeName(injectionPoint);
        bindableClasses.add( injectedClassName );
        injectionPointName = injectionPoint.getSimpleName().toString();

        TypeElement typeElementRequiringScanning = (TypeElement) injectionPoint.getEnclosingElement();
        String typeElementName = getTypeName(typeElementRequiringScanning);
        //System.out.printf("Type: %s, injection: %s \n",typeElementName, injectionPointName);
        addToInjectedFields(annotationClassName, typeElementName, injectionPointName);
    }

    private void addParameterToAnnotationDatabase(String annotationClassName, Element injectionPoint) {
        Element enclosing = injectionPoint.getEnclosingElement();
        String injectionPointName = enclosing.getSimpleName().toString();
        for( VariableElement variable : ((ExecutableElement)enclosing).getParameters() ) {
            String parameterTypeName = getTypeName(variable);
            bindableClasses.add( parameterTypeName );
            injectionPointName += ":"+parameterTypeName;
        }

        TypeElement typeElementRequiringScanning = (TypeElement) ((ExecutableElement) injectionPoint.getEnclosingElement()).getEnclosingElement();
        String typeElementName = getTypeName(typeElementRequiringScanning);
        //System.out.printf("Type: %s, injection: %s \n",typeElementName, injectionPointName);
        if( injectionPointName.startsWith("<init>") ) {
            addToInjectedConstructors(annotationClassName, typeElementName, injectionPointName );
        } else {
            addToInjectedMethods(annotationClassName, typeElementName, injectionPointName );
        }
    }

    private void addMethodOrConstructorToAnnotationDatabase(String annotationClassName, Element injectionPoint) {
        String injectionPointName = injectionPoint.getSimpleName().toString();
        for( VariableElement variable : ((ExecutableElement)injectionPoint).getParameters() ) {
            String parameterTypeName = getTypeName((TypeElement)((DeclaredType)variable.asType()).asElement());
            bindableClasses.add( parameterTypeName );
            injectionPointName += ":"+parameterTypeName;
        }

        TypeElement typeElementRequiringScanning = (TypeElement) injectionPoint.getEnclosingElement();
        String typeElementName = getTypeName(typeElementRequiringScanning);

        //System.out.printf("Type: %s, injection: %s \n",typeElementName, injectionPointName);
        if( injectionPointName.startsWith("<init>") ) {
            addToInjectedConstructors(annotationClassName, typeElementName, injectionPointName );
        } else {
            addToInjectedMethods(annotationClassName, typeElementName, injectionPointName );
        }
    }

    protected void addToInjectedConstructors(String annotationClassName, String typeElementName, String injectionPointName) {
        addToInjectedMembers(annotationClassName, typeElementName, injectionPointName, mapAnnotationToMapClassContainingInjectionToInjectedConstructorsSet);
    }

    protected void addToInjectedMethods(String annotationClassName, String typeElementName, String injectionPointName) {
        addToInjectedMembers(annotationClassName, typeElementName, injectionPointName, mapAnnotationToMapClassContainingInjectionToInjectedMethodSet);
    }

    protected void addToInjectedFields(String annotationClassName, String typeElementName, String injectionPointName) {
        addToInjectedMembers(annotationClassName, typeElementName, injectionPointName, mapAnnotationToMapClassContainingInjectionToInjectedFieldSet);
    }


    private String getTypeName(TypeElement typeElementRequiringScanning) {
        if( typeElementRequiringScanning.getEnclosingElement() instanceof TypeElement ) {
            return getTypeName(typeElementRequiringScanning.getEnclosingElement()) + "$" + typeElementRequiringScanning.getSimpleName().toString();
        } else {
            return typeElementRequiringScanning.getQualifiedName().toString();
        }
    }

    private String getTypeName(Element injectionPoint) {
        String injectedClassName = null;
        final TypeMirror fieldTypeMirror = injectionPoint.asType();
        if( fieldTypeMirror instanceof DeclaredType ) {
            injectedClassName = getTypeName((TypeElement)((DeclaredType)fieldTypeMirror).asElement());
        } else if( fieldTypeMirror instanceof PrimitiveType ) {
            injectedClassName = fieldTypeMirror.getKind().name();
        }
        return injectedClassName;
    }

    private void addToInjectedMembers(String annotationClassName, String typeElementName, String injectionPointName, HashMap<String, Map<String, Set<String>> > mapAnnotationToMapClassWithInjectionNameToMembersSet) {
        Map<String, Set<String>> mapClassWithInjectionNameToMemberSet = mapAnnotationToMapClassWithInjectionNameToMembersSet.get( annotationClassName );
        if( mapClassWithInjectionNameToMemberSet == null ) {
            mapClassWithInjectionNameToMemberSet = new HashMap<String, Set<String>>();
            mapAnnotationToMapClassWithInjectionNameToMembersSet.put(annotationClassName, mapClassWithInjectionNameToMemberSet);
        }

        Set<String> injectionPointNameSet = mapClassWithInjectionNameToMemberSet.get(typeElementName);
        if( injectionPointNameSet == null ) {
            injectionPointNameSet = new HashSet<String>();
            mapClassWithInjectionNameToMemberSet.put(typeElementName, injectionPointNameSet);
        }
        injectionPointNameSet.add(injectionPointName);
    }
}
