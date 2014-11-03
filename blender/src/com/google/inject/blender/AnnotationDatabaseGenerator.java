package com.google.inject.blender;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.String;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.tools.JavaFileObject;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;

/**
 * Generates a AnnotationDatabase implementation for RoboGuice.
 * @author Mike Burton
 * @author SNI TODO use javawriter
 */
public class AnnotationDatabaseGenerator {

    private String templatePath;
    private String packageName;
    private HashMap<String, Map<String, Set<String>>> mapAnnotationToMapClassWithInjectionNameToFieldSet;
    private HashMap<String, Map<String, Set<String>>> mapAnnotationToMapClassWithInjectionNameToMethodSet;
    private HashMap<String, Map<String, Set<String>>> mapAnnotationToMapClassWithInjectionNameToConstructorSet;
    private HashSet<String> classesContainingInjectionPointsSet;
    private HashSet<String> bindableClasses;

    public void generateAnnotationDatabase(JavaFileObject jfo) throws IOException {

        Properties props = new Properties();
        props.put("resource.loader", "class");
        props.put("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        Velocity.init(props);

        VelocityContext context = createVelocityContext();

        Template template = null;

        PrintWriter w =  null;
        try {
            template = Velocity.getTemplate(templatePath);
            w = new PrintWriter(jfo.openWriter());
            template.merge(context, w);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new IOException("Impossible to generate annotation database.", ex);
        } finally {
            if( w != null ) {
                try {
                    w.close();
                } catch( Exception ex ) {
                    ex.printStackTrace();
                    throw new IOException("Impossible to close annotation database.", ex);
                }
            }
        }
    }

    protected VelocityContext createVelocityContext() {
        VelocityContext context = new VelocityContext();
        context.put("packageName", packageName);
        context.put("mapAnnotationToMapClassWithInjectionNameToFieldSet", mapAnnotationToMapClassWithInjectionNameToFieldSet);
        context.put("mapAnnotationToMapClassWithInjectionNameToMethodSet", mapAnnotationToMapClassWithInjectionNameToMethodSet);
        context.put("mapAnnotationToMapClassWithInjectionNameToConstructorSet", mapAnnotationToMapClassWithInjectionNameToConstructorSet);
        context.put("classesContainingInjectionPointsSet", classesContainingInjectionPointsSet);
        context.put("injectedClasses", bindableClasses);
        return context;
    }

    public String getTemplatePath() {
        return templatePath;
    }

    public void setTemplatePath(String templatePath) {
        this.templatePath = templatePath;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public HashMap<String, Map<String, Set<String>>> getMapAnnotationToMapClassWithInjectionNameToFieldSet() {
        return mapAnnotationToMapClassWithInjectionNameToFieldSet;
    }

    public void setMapAnnotationToMapClassWithInjectionNameToFieldSet(HashMap<String, Map<String, Set<String>>> mapAnnotationToMapClassWithInjectionNameToFieldSet) {
        this.mapAnnotationToMapClassWithInjectionNameToFieldSet = mapAnnotationToMapClassWithInjectionNameToFieldSet;
    }

    public HashMap<String, Map<String, Set<String>>> getMapAnnotationToMapClassWithInjectionNameToMethodSet() {
        return mapAnnotationToMapClassWithInjectionNameToMethodSet;
    }

    public void setMapAnnotationToMapClassWithInjectionNameToMethodSet(HashMap<String, Map<String, Set<String>>> mapAnnotationToMapClassWithInjectionNameToMethodSet) {
        this.mapAnnotationToMapClassWithInjectionNameToMethodSet = mapAnnotationToMapClassWithInjectionNameToMethodSet;
    }

    public HashMap<String, Map<String, Set<String>>> getMapAnnotationToMapClassWithInjectionNameToConstructorSet() {
        return mapAnnotationToMapClassWithInjectionNameToConstructorSet;
    }

    public void setMapAnnotationToMapClassWithInjectionNameToConstructorSet(HashMap<String, Map<String, Set<String>>> mapAnnotationToMapClassWithInjectionNameToConstructorSet) {
        this.mapAnnotationToMapClassWithInjectionNameToConstructorSet = mapAnnotationToMapClassWithInjectionNameToConstructorSet;
    }

    public HashSet<String> getClassesContainingInjectionPointsSet() {
        return classesContainingInjectionPointsSet;
    }

    public void setClassesContainingInjectionPointsSet(HashSet<String> classesContainingInjectionPointsSet) {
        this.classesContainingInjectionPointsSet = classesContainingInjectionPointsSet;
    }

    public HashSet<String> getBindableClasses() {
        return bindableClasses;
    }

    public void setBindableClasses(HashSet<String> bindableClasses) {
        this.bindableClasses = bindableClasses;
    }
}
