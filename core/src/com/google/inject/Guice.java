/*
 * Copyright (C) 2007 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject;

import java.util.Arrays;

import com.google.inject.internal.InternalInjectorCreator;

/**
 * The entry point to the Guice framework. Creates {@link Injector}s from
 * {@link Module}s.
 *
 * <p>Guice supports a model of development that draws clear boundaries between
 * APIs, Implementations of these APIs, Modules which configure these
 * implementations, and finally Applications which consist of a collection of
 * Modules. It is the Application, which typically defines your {@code main()}
 * method, that bootstraps the Guice Injector using the {@code Guice} class, as
 * in this example:
 * <pre>
 *     public class FooApplication {
 *       public static void main(String[] args) {
 *         Injector injector = Guice.createInjector(
 *             new ModuleA(),
 *             new ModuleB(),
 *             . . .
 *             new FooApplicationFlagsModule(args)
 *         );
 *
 *         // Now just bootstrap the application and you're done
 *         FooStarter starter = injector.getInstance(FooStarter.class);
 *         starter.runApplication();
 *       }
 *     }
 * </pre>
 */
public final class Guice {

    private static HierarchyTraversalFilterFactory hierarchyTraversalFilterFactory = new HierarchyTraversalFilterFactory();
    private static AnnotationDatabaseFinder annotationDatabaseFinder;

    private Guice() {}

    /**
     * Creates an injector for the given set of modules. This is equivalent to
     * calling {@link #createInjector(Stage, Module...)} with Stage.DEVELOPMENT.
     *
     * @throws CreationException if one or more errors occur during injector
     *     construction
     */
    public static Injector createInjector(Module... modules) {
        return createInjector(Arrays.asList(modules));
    }

    /**
     * Creates an injector for the given set of modules. This is equivalent to
     * calling {@link #createInjector(Stage, Iterable)} with Stage.DEVELOPMENT.
     *
     * @throws CreationException if one or more errors occur during injector
     *     creation
     */
    public static Injector createInjector(Iterable<? extends Module> modules) {
        return createInjector(Stage.DEVELOPMENT, modules);
    }

    /**
     * Creates an injector for the given set of modules, in a given development
     * stage.
     *
     * @throws CreationException if one or more errors occur during injector
     *     creation.
     */
    public static Injector createInjector(Stage stage, Module... modules) {
        return createInjector(stage, Arrays.asList(modules));
    }

    /**
     * Creates an injector for the given set of modules, in a given development
     * stage.
     *
     * @throws CreationException if one or more errors occur during injector
     *     construction
     */
    public static Injector createInjector(Stage stage,
            Iterable<? extends Module> modules) {
        doSetAnnotationDatabaseFinderToModules(modules);
        return new InternalInjectorCreator()
        .stage(stage)
        .addModules(modules)
        .build();
    }

    /**
     * Creates a {@link HierarchyTraversalFilter} using the {@link #hierarchyTraversalFilterFactory}.
     * @return a new filter that can be used to selectively prune classes traversed by Guice to find injection points.
     */
    public static HierarchyTraversalFilter createHierarchyTraversalFilter() {
        HierarchyTraversalFilter hierarchyTraversalFilter = hierarchyTraversalFilterFactory.createHierarchyTraversalFilter();
        if( annotationDatabaseFinder == null ) {
            return hierarchyTraversalFilter;
        } else {
            return new AnnotatedGuiceHierarchyTraversalFilter(annotationDatabaseFinder, hierarchyTraversalFilter);
        }
    }

    /**
     * Sets a factory to create {@link HierarchyTraversalFilter} instances.
     * @param hierarchyTraversalFilterFactory the new factory used by Guice to prune hierarchy trees when finding injection points.
     */
    public static void setHierarchyTraversalFilterFactory(HierarchyTraversalFilterFactory hierarchyTraversalFilterFactory) {
        Guice.hierarchyTraversalFilterFactory  = hierarchyTraversalFilterFactory;
    }

    /**
     * Sets the names of packages to take into account to find annotation databases. 
     * @param packageNames the names of packages to take into account to find annotation databases.
     */
    public static void setAnnotationDatabasePackageNames(final String[] packageNames) {
        if( packageNames != null && packageNames.length != 0 ) {
            annotationDatabaseFinder = new AnnotationDatabaseFinder(packageNames);
        } else {
            annotationDatabaseFinder = null;
        }
    }

    public static AnnotationDatabaseFinder getAnnotationDatabaseFinder() {
        return annotationDatabaseFinder;
    }

    private static void doSetAnnotationDatabaseFinderToModules(Iterable<? extends Module> modules) {
        for( Module module : modules ) {
            if( module instanceof AbstractModule ) {
                ((AbstractModule)module).setAnnotationDatabaseFinder(annotationDatabaseFinder);
            }
        }
    }
}
