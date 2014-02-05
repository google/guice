package com.google.inject.blender;

import java.io.IOException;
import java.io.PrintWriter;
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

    public void generateAnnotationDatabase(JavaFileObject jfo, final String packageName, final HashMap<String, Map<String, Set<String>>> mapAnnotationToMapClassWithInjectionNameToFieldSet,
            HashMap<String, Map<String, Set<String>>> mapAnnotationToMapClassWithInjectionNameToMethodSet,
            HashMap<String, Map<String, Set<String>>> mapAnnotationToMapClassWithInjectionNameToConstructorSet, final HashSet<String> classesContainingInjectionPointsSet, HashSet<String> bindableClasses) throws IOException {

        Properties props = new Properties();
        props.put("resource.loader", "class");
        props.put("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        Velocity.init(props);

        VelocityContext context = new VelocityContext();

        context.put("packageName", packageName);
        context.put("mapAnnotationToMapClassWithInjectionNameToFieldSet", mapAnnotationToMapClassWithInjectionNameToFieldSet);
        context.put("mapAnnotationToMapClassWithInjectionNameToMethodSet", mapAnnotationToMapClassWithInjectionNameToMethodSet);
        context.put("mapAnnotationToMapClassWithInjectionNameToConstructorSet", mapAnnotationToMapClassWithInjectionNameToConstructorSet);
        context.put("classesContainingInjectionPointsSet", classesContainingInjectionPointsSet);
        context.put("injectedClasses", bindableClasses);

        Template template = null;

        PrintWriter w =  null;
        try {
            template = Velocity.getTemplate("templates/AnnotationDatabaseImpl.vm");
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
}
