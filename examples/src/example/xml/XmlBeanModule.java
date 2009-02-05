package example.xml;

import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Binder;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import safesax.Element;
import safesax.ElementListener;
import safesax.Parsers;
import safesax.RootElement;
import safesax.StartElementListener;

public class XmlBeanModule implements Module {

  final URL xmlUrl;

  Locator locator;
  Binder originalBinder;
  BeanBuilder beanBuilder;

  public XmlBeanModule(URL xmlUrl) {
    this.xmlUrl = xmlUrl;
  }

  public void configure(Binder binder) {
    this.originalBinder = binder;

    try {
      RootElement beans = new RootElement("beans");
      locator = beans.getLocator();

      Element bean = beans.getChild("bean");
      bean.setElementListener(new BeanListener());

      Element property = bean.getChild("property");
      property.setStartElementListener(new PropertyListener());

      Reader in = new InputStreamReader(xmlUrl.openStream());
      Parsers.parse(in, beans.getContentHandler());
    }
    catch (Exception e) {
      originalBinder.addError(e);
    }
  }

  /** Handles "binding" elements. */
  class BeanListener implements ElementListener {

    public void start(final Attributes attributes) {
      Binder sourced = originalBinder.withSource(xmlSource());

      String typeString = attributes.getValue("type");

      // Make sure 'type' is present.
      if (typeString == null) {
        sourced.addError("Missing 'type' attribute.");
        return;
      }

      // Resolve 'type'.
      Class<?> type;
      try {
        type = Class.forName(typeString);
      }
      catch (ClassNotFoundException e) {
        sourced.addError(e);
        return;
      }

      // Look for a no-arg constructor.
      try {
        type.getConstructor();
      }
      catch (NoSuchMethodException e) {
        sourced.addError("%s doesn't have a no-arg constructor.");
        return;
      }

      // Create a bean builder for the given type.
      beanBuilder = new BeanBuilder(type);
    }

    public void end() {
      if (beanBuilder != null) {
        beanBuilder.build();
        beanBuilder = null;
      }
    }
  }

  /** Handles "property" elements. */
  class PropertyListener implements StartElementListener {

    public void start(final Attributes attributes) {
      Binder sourced = originalBinder.withSource(xmlSource());

      if (beanBuilder == null) {
        // We must have already run into an error.
        return;
      }

      // Check for 'name'.
      String name = attributes.getValue("name");
      if (name == null) {
        sourced.addError("Missing attribute name.");
        return;
      }

      Class<?> type = beanBuilder.type;

      // Find setter method for the given property name.
      String setterName = "set" + capitalize(name);
      Method setter = null;
      for (Method method : type.getMethods()) {
        if (method.getName().equals(setterName)) {
          setter = method;
          break;
        }
      }
      if (setter == null) {
        sourced.addError("%s.%s() not found.", type.getName(), setterName);
        return;
      }

      // Validate number of parameters.
      Type[] parameterTypes = setter.getGenericParameterTypes();
      if (parameterTypes.length != 1) {
        sourced.addError("%s.%s() must take one argument.",
            setterName, type.getName());
        return;
      }

      // Add property descriptor to builder.
      Provider<?> provider = sourced.getProvider(Key.get(parameterTypes[0]));
      beanBuilder.properties.add(
          new Property(setter, provider));
    }
  }

  static String capitalize(String s) {
    return Character.toUpperCase(s.charAt(0)) +
        s.substring(1);
  }

  static class Property {

    final Method setter;
    final Provider<?> provider;

    Property(Method setter, Provider<?> provider) {
      this.setter = setter;
      this.provider = provider;
    }

    void setOn(Object o) {
      try {
        setter.invoke(o, provider.get());
      }
      catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
      catch (InvocationTargetException e) {
        throw new RuntimeException(e);
      }
    }
  }

  class BeanBuilder {
    
    final List<Property> properties = new ArrayList<Property>();
    final Class<?> type;

    BeanBuilder(Class<?> type) {
      this.type = type;
    }

    void build() {
      addBinding(type);
    }

    <T> void addBinding(Class<T> type) {
      originalBinder.withSource(xmlSource())
          .bind(type).toProvider(new BeanProvider<T>(type, properties));
    }
  }

  static class BeanProvider<T> implements Provider<T> {

    final Class<T> type;
    final List<Property> properties;

    BeanProvider(Class<T> type, List<Property> properties) {
      this.type = type;
      this.properties = properties;
    }

    public T get() {
      try {
        T t = type.newInstance();
        for (Property property : properties) {
          property.setOn(t);
        }
        return t;
      }
      catch (InstantiationException e) {
        throw new RuntimeException(e);
      }
      catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
  }

  Object xmlSource() {
    return xmlUrl + ":" + locator.getLineNumber();
  }
}
