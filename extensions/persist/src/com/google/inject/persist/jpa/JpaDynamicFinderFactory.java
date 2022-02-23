package com.google.inject.persist.jpa;

import com.google.inject.Inject;
import com.google.inject.persist.finder.DynamicFinder;
import com.google.inject.persist.finder.Finder;
import com.google.inject.spi.Message;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import org.aopalliance.intercept.MethodInvocation;

class JpaDynamicFinderFactory {

	public FinderCreationResult createFinder(Class<?> iface) {
		Set<Message> messages = validateDynamicFinder(iface);
		if (!messages.isEmpty()) {
			return new FinderCreationResult(null, messages);
		}

		return new FinderCreationResult(
			new InvocationHandler() {
				@Inject
				JpaFinderProxy finderProxy;

				@Override
				public Object invoke(final Object thisObject, final Method method, final Object[] args)
					throws Throwable {

					// Don't intercept non-finder methods like equals and hashcode.
					if (!method.isAnnotationPresent(Finder.class)) {
						// NOTE(user): This is not ideal, we are using the invocation handler's equals
						// and hashcode as a proxy (!) for the proxy's equals and hashcode.
						return method.invoke(this, args);
					}

					return finderProxy.invoke(
						new MethodInvocation() {
							@Override
							public Method getMethod() {
								return method;
							}

							@Override
							public Object[] getArguments() {
								return null == args ? new Object[0] : args;
							}

							@Override
							public Object proceed() throws Throwable {
								return method.invoke(thisObject, args);
							}

							@Override
							public Object getThis() {
								throw new UnsupportedOperationException(
									"Bottomless proxies don't expose a this.");
							}

							@Override
							public AccessibleObject getStaticPart() {
								throw new UnsupportedOperationException();
							}
						});
				}
			}, Collections.emptySet());
	}

	private Set<Message> validateDynamicFinder(Class<?> iface) {
		Set<Message> messages = new HashSet<>();
		if (!iface.isInterface()) {
			messages.add(new Message(iface + " is not an interface. Dynamic Finders must be interfaces."));
		}

		for (Method method : iface.getMethods()) {
			DynamicFinder finder = DynamicFinder.from(method);
			if (null == finder) {
				messages.add(new Message(
					"Dynamic Finder methods must be annotated with @Finder, but "
						+ iface
						+ "."
						+ method.getName()
						+ " was not"));
			}
		}
		return messages;
	}

	static class FinderCreationResult {
		private InvocationHandler handler;
		private Set<Message> errors;

		public FinderCreationResult(InvocationHandler handler, @Nonnull Set<Message> errors) {
			this.handler = handler;
			this.errors = errors;
		}

		public InvocationHandler getHandler() {
			return handler;
		}

		public Set<Message> getErrors() {
			return errors;
		}

		public boolean hasErrors() {
			return !errors.isEmpty();
		}
	}
}
