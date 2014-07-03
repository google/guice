package com.google.inject;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;

import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.binder.ScopedBindingBuilder;

/**
 * Basically, a binder that does nothing.
 * @author Mike Burton
 * @param <T> ??
 */
public class NoOpAnnotatedBindingBuilder<T> implements AnnotatedBindingBuilder<T> {
    private ScopedBindingBuilder scopedBindingBuilder = new NoOpScopedBindingBuilder();
    private NoOpLinkedBindingBuilder<T> noOpLinkedBindingBuilder = new NoOpLinkedBindingBuilder<T>();

    @Override
    public LinkedBindingBuilder<T> annotatedWith(Class<? extends Annotation> annotationType) {
        return noOpLinkedBindingBuilder;
    }

    @Override
    public LinkedBindingBuilder<T> annotatedWith(Annotation annotation) {
        return this;
    }

    @Override
    public ScopedBindingBuilder to(Class<? extends T> implementation) {
        return scopedBindingBuilder;
    }

    @Override
    public ScopedBindingBuilder to(TypeLiteral<? extends T> implementation) {
        return scopedBindingBuilder;
    }

    @Override
    public ScopedBindingBuilder to(Key<? extends T> targetKey) {
        return scopedBindingBuilder;
    }

    @Override
    public void toInstance(T instance) {
        //nothing
    }

    @Override
    public ScopedBindingBuilder toProvider(Provider<? extends T> provider) {
        return scopedBindingBuilder;
    }

    @Override
    public ScopedBindingBuilder toProvider(Class<? extends javax.inject.Provider<? extends T>> providerType) {
        return scopedBindingBuilder;
    }

    @Override
    public ScopedBindingBuilder toProvider(TypeLiteral<? extends javax.inject.Provider<? extends T>> providerType) {
        return scopedBindingBuilder;
    }

    @Override
    public ScopedBindingBuilder toProvider(Key<? extends javax.inject.Provider<? extends T>> providerKey) {
        return scopedBindingBuilder;
    }

    @Override
    public <S extends T> ScopedBindingBuilder toConstructor(Constructor<S> constructor) {
        return scopedBindingBuilder;
    }

    @Override
    public <S extends T> ScopedBindingBuilder toConstructor(Constructor<S> constructor, TypeLiteral<? extends S> type) {
        return scopedBindingBuilder;
    }

    @Override
    public void in(Class<? extends Annotation> scopeAnnotation) {
        //nothing
    }

    @Override
    public void in(Scope scope) {
        //nothing
    }

    @Override
    public void asEagerSingleton() {
        //nothing
    }

	@Override
	public ScopedBindingBuilder toProvider(javax.inject.Provider<? extends T> provider) {
		return scopedBindingBuilder;
	}

    private class NoOpLinkedBindingBuilder<U> implements LinkedBindingBuilder<U> {
        @Override
        public ScopedBindingBuilder to(Class<? extends U> implementation) {
            return scopedBindingBuilder;
        }

        @Override
        public ScopedBindingBuilder to(TypeLiteral<? extends U> implementation) {
            return scopedBindingBuilder;
        }

        @Override
        public ScopedBindingBuilder to(Key<? extends U> targetKey) {
            return scopedBindingBuilder;
        }

        @Override
        public void toInstance(U instance) {
            //nothing
        }

        @Override
        public ScopedBindingBuilder toProvider(Provider<? extends U> provider) {
            return scopedBindingBuilder;
        }

        @Override
        public ScopedBindingBuilder toProvider(Class<? extends javax.inject.Provider<? extends U>> providerType) {
            return scopedBindingBuilder;
        }

        @Override
        public ScopedBindingBuilder toProvider(TypeLiteral<? extends javax.inject.Provider<? extends U>> providerType) {
            return scopedBindingBuilder;
        }

        @Override
        public ScopedBindingBuilder toProvider(Key<? extends javax.inject.Provider<? extends U>> providerKey) {
            return scopedBindingBuilder;
        }

        @Override
        public <S extends U> ScopedBindingBuilder toConstructor(Constructor<S> constructor) {
            return scopedBindingBuilder;
        }

        @Override
        public <S extends U> ScopedBindingBuilder toConstructor(Constructor<S> constructor, TypeLiteral<? extends S> type) {
            return scopedBindingBuilder;
        }

        @Override
        public void in(Class<? extends Annotation> scopeAnnotation) {
            //nothing
        }

        @Override
        public void in(Scope scope) {
            //nothing
        }

        @Override
        public void asEagerSingleton() {
            //nothing
        }

		@Override
		public ScopedBindingBuilder toProvider(javax.inject.Provider<? extends U> provider) {
			return scopedBindingBuilder;
		}
    }

    private static class NoOpScopedBindingBuilder implements ScopedBindingBuilder {

        @Override
        public void in(Class<? extends Annotation> scopeAnnotation) {
            //nothing
        }

        @Override
        public void in(Scope scope) {
            //nothing
        }

        @Override
        public void asEagerSingleton() {
            //nothing
        }
    }
}
