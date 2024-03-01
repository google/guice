package com.google.inject.internal;

import org.junit.rules.Timeout;
import org.junit.Rule;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;

import org.junit.rules.ExpectedException;

import java.util.List;
import java.util.HashSet;

import com.google.inject.Binding;

import java.util.ArrayList;

import com.google.inject.Key;

import java.lang.reflect.Constructor;

import com.google.inject.TypeLiteral;

import java.util.Set;

import com.google.inject.spi.Dependency;
import org.mockito.MockedStatic;
import com.google.inject.spi.InjectionPoint;

import static org.mockito.Mockito.doNothing;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mockStatic;

import org.junit.Ignore;

public class ConstructorInjectorSapientGeneratedJunit4Test {

    @Rule()
    public Timeout timeoutRule = Timeout.seconds(5);

    private final ConstructionProxy<java.lang.Object> constructionProxyMock = mock(ConstructionProxy.class, "ConstructionProxy<Object> constructionProxy");

    private final ConstructionContext<java.lang.Object> constructionContextMock = mock(ConstructionContext.class);

    private final Constructor constructorMock = mock(Constructor.class);

    private final InternalContext contextMock = mock(InternalContext.class);

    private final Dependency<?> dependencyMock = mock(Dependency.class);

    private final InjectorImpl.InjectorOptions injectorImplInjectorOptionsMock = mock(InjectorImpl.InjectorOptions.class);

    private final Key keyMock = mock(Key.class);

    private final MembersInjectorImpl<java.lang.Object> membersInjectorImplMock = mock(MembersInjectorImpl.class);

    private final ProvisionListenerStackCallback<java.lang.Object> provisionListenerStackCallbackMock = mock(ProvisionListenerStackCallback.class);

    private final TypeLiteral typeLiteralMock = mock(TypeLiteral.class);

    @Rule()
    public ExpectedException thrown = ExpectedException.none();

    //Sapient generated method id: ${49f22cac-3604-3497-90db-d26edb0e7397}, hash: DCD02567C05D90BE46F869ECA4B0F33A
    @Test()
    public void constructWhenConstructionContextIsConstructing() throws InternalProvisionException, InvocationTargetException {
        /* Branches:
         * (constructionContext.isConstructing()) : true
         */
        //Arrange Statement(s)
        doReturn(injectorImplInjectorOptionsMock).when(contextMock).getInjectorOptions();
        doReturn(typeLiteralMock).when(keyMock).getTypeLiteral();
        doReturn(Object.class).when(typeLiteralMock).getRawType();
        Set<InjectionPoint> injectionPointSet = new HashSet<>();
        SingleParameterInjector<?>[] singleParameterInjectorArray = new SingleParameterInjector[]{};
        ConstructorInjector<java.lang.Object> target = new ConstructorInjector(injectionPointSet, constructionProxyMock, singleParameterInjectorArray, membersInjectorImplMock);
        doReturn(constructionContextMock).when(contextMock).getConstructionContext(target);
        doReturn(true).when(constructionContextMock).isConstructing();
        java.lang.Object object = new java.lang.Object();
        doReturn(object).when(constructionContextMock).createProxy(injectorImplInjectorOptionsMock, java.lang.Object.class);
        Dependency dependency = Dependency.get(keyMock);
        //Act Statement(s)
        java.lang.Object result = target.construct(contextMock, dependency, provisionListenerStackCallbackMock);
        //Assert statement(s)
        assertThat(result, equalTo(object));
        verify(contextMock).getInjectorOptions();
        verify(keyMock).getTypeLiteral();
        verify(typeLiteralMock).getRawType();
        verify(contextMock).getConstructionContext(target);
        verify(constructionContextMock).isConstructing();
        verify(constructionContextMock).createProxy(injectorImplInjectorOptionsMock, java.lang.Object.class);
    }

    //Sapient generated method id: ${4d517ada-89b7-3a9e-9289-6ee0971952e5}, hash: F273C0D0DD99D49493C6E61964D4030A
    @Test()
    public void constructWhenContextGetInjectorOptionsNotDisableCircularProxies() throws InternalProvisionException, InvocationTargetException {
        /* Branches:
         * (constructionContext.isConstructing()) : false
         * (t != null) : true
         * (context.getInjectorOptions().disableCircularProxies) : false
         */
        //Arrange Statement(s)
        doReturn(injectorImplInjectorOptionsMock).when(contextMock).getInjectorOptions();
        Set<InjectionPoint> injectionPointSet = new HashSet<>();
        SingleParameterInjector<?>[] singleParameterInjectorArray = new SingleParameterInjector[]{};
        ConstructorInjector<java.lang.Object> target = new ConstructorInjector(injectionPointSet, constructionProxyMock, singleParameterInjectorArray, membersInjectorImplMock);
        doReturn(constructionContextMock).when(contextMock).getConstructionContext(target);
        doReturn(false).when(constructionContextMock).isConstructing();
        java.lang.Object object = new java.lang.Object();
        doReturn(object).when(constructionContextMock).getCurrentReference();
        //Act Statement(s)
        java.lang.Object result = target.construct(contextMock, dependencyMock, provisionListenerStackCallbackMock);
        //Assert statement(s)
        assertThat(result, equalTo(object));
        verify(contextMock).getInjectorOptions();
        verify(contextMock).getConstructionContext(target);
        verify(constructionContextMock).isConstructing();
        verify(constructionContextMock).getCurrentReference();
    }

    //Sapient generated method id: ${5518bfd1-919d-3a21-9890-4da022deb59b}, hash: A540D7A50620E5161323DA156AC70484
    @Test()
    public void constructWhenProvisionCallbackIsNull() throws InternalProvisionException, InvocationTargetException {
        /* Branches:
         * (constructionContext.isConstructing()) : false
         * (t != null) : false
         * (provisionCallback == null) : true
         */
        //Arrange Statement(s)
        MembersInjectorImpl<java.lang.Object> localMembersInjectorMock = mock(MembersInjectorImpl.class);
        InternalContext internalContextMock = mock(InternalContext.class);
        try (MockedStatic<SingleParameterInjector> singleParameterInjector = mockStatic(SingleParameterInjector.class)) {
            java.lang.Object object = new java.lang.Object();
            doNothing().when(localMembersInjectorMock).injectMembers(object, internalContextMock, false);
            doNothing().when(localMembersInjectorMock).notifyListeners(object);
            java.lang.Object[] objectArray = new java.lang.Object[]{};
            SingleParameterInjector<?>[] singleParameterInjectorArray = new SingleParameterInjector[]{};
            singleParameterInjector.when(() -> SingleParameterInjector.getAll(internalContextMock, singleParameterInjectorArray)).thenReturn(objectArray);
            Set<InjectionPoint> injectionPointSet = new HashSet<>();
            ConstructorInjector<java.lang.Object> target = new ConstructorInjector(injectionPointSet, constructionProxyMock, singleParameterInjectorArray, localMembersInjectorMock);
            doReturn(object).when(constructionProxyMock).newInstance(objectArray);
            doReturn(constructionContextMock).when(internalContextMock).getConstructionContext(target);
            doReturn(false).when(constructionContextMock).isConstructing();
            doReturn(null).when(constructionContextMock).getCurrentReference();
            doNothing().when(constructionContextMock).startConstruction();
            doNothing().when(constructionContextMock).setProxyDelegates(object);
            doNothing().when(constructionContextMock).finishConstruction();
            doNothing().when(constructionContextMock).setCurrentReference(object);
            doNothing().when(constructionContextMock).removeCurrentReference();
            ProvisionListenerStackCallback<java.lang.Object> provisionListenerStackCallback = null;
            //Act Statement(s)
            java.lang.Object result = target.construct(internalContextMock, dependencyMock, provisionListenerStackCallback);
            //Assert statement(s)
            assertThat(result, equalTo(object));
            verify(localMembersInjectorMock).injectMembers(object, internalContextMock, false);
            verify(localMembersInjectorMock).notifyListeners(object);
            singleParameterInjector.verify(() -> SingleParameterInjector.getAll(internalContextMock, singleParameterInjectorArray), atLeast(1));
            verify(constructionProxyMock).newInstance(objectArray);
            verify(internalContextMock).getConstructionContext(target);
            verify(constructionContextMock).isConstructing();
            verify(constructionContextMock).getCurrentReference();
            verify(constructionContextMock).startConstruction();
            verify(constructionContextMock).setProxyDelegates(object);
            verify(constructionContextMock, times(2)).finishConstruction();
            verify(constructionContextMock).setCurrentReference(object);
            verify(constructionContextMock).removeCurrentReference();
        }
    }
}
