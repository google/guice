package com.google.inject.spi;

import com.google.common.base.Preconditions;
import com.google.inject.Module;
import com.google.inject.internal.util.StackTraceElements;
import com.google.inject.internal.util.StackTraceElements.InMemoryStackTraceElement;

/**
 * Associated to a {@link Module module}, provides the module class name, the parent module {@link
 * ModuleSource source}, and the call stack that ends just before the module {@link
 * Module#configure(Binder) configure(Binder)} method invocation.  
 */
final class ModuleSource {
   
  /**
   * The class name of module that this {@link ModuleSource} associated to. This value is null when
   * the module class {@link Class#getName() getName()} returns null.
   */
  private final String moduleClassName;
  
  /**
   * The parent {@link ModuleSource module source}.
   */
  private final ModuleSource parent;
  
  /** 
   * The chunk of call stack that starts from the parent module {@link Module#configure(Binder) 
   * configure(Binder)} call and ends just before the module {@link Module#configure(Binder) 
   * configure(Binder)} method invocation. For a module without a parent module the chunk starts 
   * from the bottom of call stack.
   */
  private final InMemoryStackTraceElement[] partialCallStack;  

  /**
   * Creates a new {@link ModuleSource} with a {@literal null} parent.
   * @param module the corresponding module
   * @param partialCallStack the chunk of call stack that starts from the parent module {@link 
   * Module#configure(Binder) configure(Binder)} call and ends just before the module {@link 
   * Module#configure(Binder) configure(Binder)} method invocation
   */
  ModuleSource(Module module, StackTraceElement[] partialCallStack) {
    this(null, module, partialCallStack);
  } 
  
 /**
   * Creates a new {@link ModuleSource} Object.
   * @param parent the parent module {@link ModuleSource source} 
   * @param module the corresponding module
   * @param partialCallStack the chunk of call stack that starts from the parent module {@link 
   * Module#configure(Binder) configure(Binder)} call and ends just before the module {@link 
   * Module#configure(Binder) configure(Binder)} method invocation
   */
  private ModuleSource(
      /* @Nullable */ ModuleSource parent, Module module, StackTraceElement[] partialCallStack) {
    Preconditions.checkNotNull(module, "module cannot be null.");
    Preconditions.checkNotNull(partialCallStack, "partialCallStack cannot be null.");
    this.parent = parent;
    this.moduleClassName = module.getClass().getName();
    this.partialCallStack = StackTraceElements.convertToInMemoryStackTraceElement(partialCallStack);
  }
  
  /** 
   * Returns the corresponding module class name. The value can be null.
   *
   * @see Class#getName()
   */
  String getModuleClassName() {
    return moduleClassName;
  }

  /**
   * Returns the chunk of call stack that starts from the parent module {@link 
   * Module#configure(Binder) configure(Binder)} call and ends just before the module {@link 
   * Module#configure(Binder) configure(Binder)} method invocation
   */
  StackTraceElement[] getPartialCallStack() {
    int chunkSize = partialCallStack.length;
    StackTraceElement[] callStack = new StackTraceElement[chunkSize];
    System.arraycopy(partialCallStack, 0, callStack, 0, chunkSize);
    return callStack;
  }
  
  /** 
   * Creates and returns a child {@link ModuleSource} corresponding to the {@link Module module}.
   * @param module the corresponding module
   * @param partialCallStack the chunk of call stack that starts from the parent module {@link 
   * Module#configure(Binder) configure(Binder)} call and ends just before the module {@link 
   * Module#configure(Binder) configure(Binder)} method invocation
   */
  ModuleSource createChild(Module module, StackTraceElement[] partialCallStack) {
    return new ModuleSource(this, module, partialCallStack);
  }

  /** 
   * Returns the parent module {@link ModuleSource source}. 
   */
  ModuleSource getParent() {
    return parent;
  }
  
  /**
   * Returns the size of {@link ModuleSource ModuleSources} chain (all parents) that ends at this 
   * object.
   */
  int size() {
    if (parent == null) {
      return 1;
    }
    return parent.size() + 1;
  }
  
  /**
   * Returns the size of call stack that ends just before the module {@link Module#configure(Binder)
   * configure(Binder)} method invocation (see {@link #getStackTrace()}). 
   */
  int getStackTraceSize() {
    if (parent == null) {
      return partialCallStack.length;
    }
    return parent.getStackTraceSize() + partialCallStack.length;  
  }  
  
  /**
   * Returns the full call stack that ends just before the module {@link Module#configure(Binder) 
   * configure(Binder)} method invocation. 
   */
  StackTraceElement[] getStackTrace() {
    StackTraceElement[] callStack = new StackTraceElement[getStackTraceSize()];
    int cursor = 0; // Index 0 stores the top Module.configure() 
    ModuleSource current = this;
    while (current != null) {
      StackTraceElement[] chunk = 
          StackTraceElements.convertToStackTraceElement(current.partialCallStack);
      int chunkSize = chunk.length;
      System.arraycopy(chunk, 0, callStack, cursor, chunkSize);
      current = current.parent;
      cursor = cursor + chunkSize;
    }
    return callStack;
  }
}
