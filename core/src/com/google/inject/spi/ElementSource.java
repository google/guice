package com.google.inject.spi;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.internal.util.StackTraceElements;
import com.google.inject.internal.util.StackTraceElements.InMemoryStackTraceElement;

import java.util.List;

/**
 * Contains information about where and how an {@link Element element} was bound.
 * <p> 
 * The {@link #getDeclaringSource() declaring source} refers to a location in source code that 
 * defines the Guice {@link Element element}. For example, if the element is created from a method
 * annotated by {@literal @Provides}, the declaring source of element would be the method itself. 
 * <p>
 * The creation call stack is the sequence of calls ends at one of {@link Binder} {@code bindXXX()} 
 * methods and eventually defines the element. Note that {@link #getStackTrace()} lists {@link 
 * StackTraceElement StackTraceElements} in reverse chronological order. The first element (index 
 * zero) is the last method call and the last element is the first method invocation.
 * <p>
 * The sequence of class names of {@link Module modules} involved in the element creation can be 
 * retrieved by {@link #getModuleClassNames()}. Similar to the call stack, the order is reverse 
 * chronological. The first module (index 0) is the module that installs the {@link Element 
 * element}. The last module is the root module.
 * <p>
 * In order to support the cases where a Guice {@link Element element} is created from another
 * Guice {@link Element element} (original) (e.g., by {@link Element#applyTo()}), it also
 * provides a reference to the original element source ({@link #getOriginalElementSource()}).
 */
public final class ElementSource {

  /** 
   * The {@link ElementSource source} of element that this element created from (if there is any), 
   * otherwise {@code null}.
   */
  final ElementSource originalElementSource;
  
  /** The {@link ModuleSource source} of module creates the element. */
  final ModuleSource moduleSource;
  
  /** 
   * The partial call stack that starts at the last module {@link Module#Configure(Binder) 
   * configure(Binder)} call. 
   */
  final InMemoryStackTraceElement[] partialCallStack;
  
  /** 
   * Refers to a single location in source code that causes the element creation. It can be any 
   * object such as {@link Constructor}, {@link Method}, {@link Field}, {@link StackTraceElement}, 
   * etc. For example, if the element is created from a method annotated by {@literal @Provides}, 
   * the declaring source of element would be the method itself.
   */
  final Object declaringSource;

  /**
   * Creates a new {@ElementSource} from the given parameters. 
   * @param originalElementSource The source of element that this element created from (if there is 
   * any), otherwise {@code null}.
   * @param declaringSource the source (in)directly declared the element.
   * @param moduleSource the moduleSource when the element is bound
   * @param partialCallStack the partial call stack from the top module to where the element is 
   * bound
   */
  ElementSource(/* @Nullable */ ElementSource originalSource, Object declaringSource, 
      ModuleSource moduleSource, StackTraceElement[] partialCallStack) {
    Preconditions.checkNotNull(moduleSource, "moduleSource cannot be null.");
    Preconditions.checkNotNull(partialCallStack, "partialCallStack cannot be null.");
    Preconditions.checkNotNull(declaringSource, "declaringSource cannot be null.");
    this.originalElementSource = originalSource;
    this.moduleSource = moduleSource;
    this.partialCallStack = StackTraceElements.convertToInMemoryStackTraceElement(partialCallStack);
    this.declaringSource = declaringSource;
  }
  
  /**
   * Returns the {@link ElementSource} of the element this was created or copied from.  If this was 
   * not created or copied from another element, returns {@code null}.
   */
  public ElementSource getOriginalElementSource() {
    return originalElementSource;
  }
  
  /**
   * Returns a single location in source code that defines the element. It can be any object
   * such as {@link Constructor}, {@link Method}, {@link Field}, {@link StackTraceElement}, etc. For
   * example, if the element is created from a method annotated by {@literal @Provides}, the 
   * declaring source of element would be the method itself.
   */
  public Object getDeclaringSource() {
    return declaringSource;
  }
  
  /**
   * Returns the class names of modules involved in creating this {@link Element}.  The first
   * element (index 0) is the class name of module that defined the element, and the last element
   * is the class name of root module. In the cases where the class name is null an empty string
   * is returned.
   */
  public List<String> getModuleClassNames() {
    String[] classNames = new String[moduleSource.size()];
    ModuleSource current = moduleSource;
    int cursor = 0;
    while (current != null) {
      classNames[cursor] = current.getModuleClassName();
      if (classNames[cursor] == null) {
          classNames[cursor] = "";
      }
      current = current.getParent();
      cursor++;
    }
    return ImmutableList.<String>copyOf(classNames);
  }
  
  /**
   * Returns the position of {@link Module#configure(Binder) configure(Binder)} method call in the 
   * {@link #getStackTrace() stack trace} for modules that their classes returned by 
   * {@link #getModuleClassNames()}. For example, if the stack trace looks like the following:
   * <p>
   * {@code
   *  0 - Binder.bind(),
   *  1 - ModuleTwo.configure(),
   *  2 - Binder.install(),
   *  3 - ModuleOne.configure(),
   *  4 - theRest(). 
   * }
   * <p>
   * 1 and 3 are returned.   
   */
  public List<Integer> getModuleConfigurePositionsInStackTrace() {
    int size = moduleSource.size();
    Integer[] positions = new Integer[size];
    int position = partialCallStack.length;
    positions[0] = position;
    ModuleSource current = moduleSource;
    int cursor = 1;
    while (cursor < size) {
      position += current.getPartialCallStack().length;
      positions[cursor] = position;
      current = current.getParent();
      cursor++;
    }
    return ImmutableList.<Integer>copyOf(positions);
  }
  
  /**
   * Returns the sequence of method calls that ends at one of {@link Binder} {@code bindXXX()} 
   * methods and eventually defines the element. Note that {@link #getStackTrace()} lists {@link 
   * StackTraceElement StackTraceElements} in reverse chronological order. The first element (index 
   * zero) is the last method call and the last element is the first method invocation.
   */
  public StackTraceElement[] getStackTrace() {
    int modulesCallStackSize = moduleSource.getStackTraceSize();
    int chunkSize = partialCallStack.length;
    int size = moduleSource.getStackTraceSize() + partialCallStack.length;
    StackTraceElement[] callStack = new StackTraceElement[size];
    System.arraycopy(
        StackTraceElements.convertToStackTraceElement(partialCallStack), 0, callStack, 0, 
        chunkSize);
    System.arraycopy(moduleSource.getStackTrace(), 0, callStack, chunkSize, modulesCallStackSize);
    return callStack;
  }
  
  /**
   * Returns {@code getDeclaringSource().toString()} value.
   */
  @Override
  public String toString() {
    return getDeclaringSource().toString();
  }
}
