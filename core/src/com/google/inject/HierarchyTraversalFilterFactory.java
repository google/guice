package com.google.inject;

/**
 * Creates {@link HierarchyTraversalFilter} that can be used to filter 
 * the type hierarchy when looking for injection points of a given type.
 * @author SNI
 */
public class HierarchyTraversalFilterFactory {

    public HierarchyTraversalFilter createHierarchyTraversalFilter() {
        return new HierarchyTraversalFilter();
    }
}
